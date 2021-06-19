(ns md2c8e.confluence
  (:require [clojure.string :as str]
            [cognitect.anomalies :as anom]
            [hato.client :as hc]
            [md2c8e.anomalies :refer [anom fault]]
            [md2c8e.markdown :as md])
  (:import [java.io File]))

(def ^:private constants
  {:connect-timeout 5000
   :request-timeout 10000})

(defn- api-url
  [confluence-root-url first-path-segment & more-path-segments]
  {:pre [(str/ends-with? confluence-root-url "/")]}
  (str confluence-root-url
       "rest/api/"
       (->> (cons first-path-segment more-path-segments)
            (map #(if (keyword? %) (name %) (str %)))
            (str/join "/"))))

(defn- ensure-trailing-slash
  [s]
  (if (str/ends-with? s "/")
      s
      (str s "/")))

(defn- exception->fault
  [e]
  (let [res (ex-data e)]
    (merge (fault ::anom/message (str e)
                  ::exception e)
           (when res
             {::response res})
           (when-let [msg (and (map? res) (get-in res [:body :message]))]
             {::anom/message msg}))))

(defn make-client
  [confluence-root-url username api-key]
  (let [urlf        (partial api-url (ensure-trailing-slash confluence-root-url))
        http-client (hc/build-http-client {:connect-timeout (:connect-timeout constants)
                                           :redirect-policy :never
                                           :version :http-1.1
                                           :cookie-policy :none})
        base-req-opts {:http-client http-client
                       :accept :json
                       :as :json
                       :headers  {"Authorization" (->> api-key
                                                       (str username ":")
                                                       .getBytes
                                                       (.encodeToString (java.util.Base64/getEncoder))
                                                       (str "Basic "))}
                       :coerce :always
                       :timeout (:request-timeout constants)
                       :throw-exceptions? true}]
  ; The first two are just for reference, debugging, etc.
  {::base-req-opts base-req-opts
   ::confluence-root-url confluence-root-url
   ::req (fn [method url-segments first-opt-key first-opt-val & more-opts]
           (try (hc/request (-> (apply assoc base-req-opts first-opt-key first-opt-val more-opts)
                                (assoc :request-method method
                                       :url (apply urlf url-segments))))
                (catch Exception e (exception->fault e))))}))

(defn get-page-by-id
  "Returns the page with the supplied ID, or an ::anom/anomaly.
   The value of ::anom/category will vary depending on the circumstances:
    * :not-found — the page simply doesn’t exist
    * :fault — something went wrong"
  [page-id {:keys [::req] :as _client}]
  (let [res (req :get [:content page-id] :query-params {:expand "version,space"})
        not-found? (and (anom res) (= (get-in res [::response :status]) 404))]
    (cond
      ;; check not-found? first, because in that (special) case res will be an anom as well
      not-found? {::anom/category :not-found
                  ::anom/message (format "No page with ID %s exists" page-id)
                  ::page-id page-id
                  ::response (::response res)}
      (anom res) res
      :else (:body res))))

(defn- get-page-by-title
  "Get the page with the supplied title, or nil if no such page is found."
  [title space-key {:keys [::req] :as _client}]
  (let [res (req :get [:content] :query-params {:spaceKey space-key :title title :expand "version"})
        results (get-in res [:body :results])]
    (cond (anom res)            res
          (not (coll? results)) (fault ::response res ::anom/message "Search results malformed")
          (> (count results) 1) (fault ::response res ::anom/message "Too many search results")
          :else                 (first results))))

(defn- update-page
  [{id           :id
    {vn :number} :version :as _current-page}
   title
   body
   {:keys [::req] :as _client}]
  {:pre [(number? vn)]}
  (let [form-params {:version {:number (inc vn)}
                     :type :page
                     :title title
                     :body {:storage {:value body, :representation :storage}}}
        res (req :put [:content id] :content-type :json, :form-params form-params)]
    (or (anom res)
        (:body res))))

(defn- create-page
  [space-key parent-id title body {:keys [::req] :as _client}]
  (let [form-params {:type :page
                     :title title
                     :space {:key space-key}
                     :body {:storage {:value body, :representation :storage}}
                     :ancestors [{:type :page :id parent-id}]}
        res (req :post [:content] :content-type :json, :form-params form-params)]
     (or (anom res)
         (:body res))))

(defn- enrich-err
  [err operation source-file title body]
  (let [tmpfile (File/createTempFile (or (and source-file (.getName source-file))
                                         title)
                                     ".xhtml")]
    (spit tmpfile body)
    (-> (assoc err ::operation operation)
        (update ::anom/message #(str % " --  XHTML body written to: " tmpfile)))))

(defn upsert
  "Useful for when we want to publish a page that may or may not have already been published; we
  don’t know, and we don’t have an id for it.
  If successful, returns a map with [::operation ::page]. ::page is a representation of the page
  that was updated or created. Therefore it probably contains, among other keys, 'id' and 'version'.
  If unsuccessful, returns an ::anom/anomaly with the additional key ::response."
  [{:keys [::title ::body ::md/source] :as _page} space-key parent-id client]
  (let [get-res   (get-page-by-title title space-key client)
        page      (when-not (anom get-res) get-res)
        op (cond (anom get-res) :none
                 page           :update
                 :else          :create)
        op-res (case op
                     :update (update-page page title body client)
                     :create (create-page space-key parent-id title body client)
                     :none)
        err (or (anom get-res)
                (anom op-res))]
    (if err
      (enrich-err err op (::md/fp source) title body)
      {::operation op
       ::page op-res
       ::versions {::prior (get-in page [:version :number])
                   ::new (get-in op-res [:version :number])}})))

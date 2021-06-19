# md2c8e

md2c8e — “Markdown to Confluence” — is a tool for publishing sets of [Markdown][markdown] documents
to [Confluence][confluence].


## Status

This tool is in a very early stage of development, and is probably not suitable for anyone but its
developers to use.

**If** you’re familiar with [Clojure][clojure], Markdown, **and**
Confluence, **and** you’re comfortable reading the source code thoroughly before usage, **and**
you’re comfortable running the tool from a Clojure [REPL][repl] — then it *might* not be a terrible
idea for you to try using this tool.


## Documentation

* [Contributing][contributing]
* [Developing and Testing][dev-and-test]


## Basic Usage

The tool can be invoked like so:

```shell
clojure -Sdeps '{:deps {md2c8e {:git/url "https://github.com/FundingCircle/md2c8e" :sha "..."}}}' \
        -m md2c8e.cli \
        publish \
        --source-dir <path-to-source-dir> \
        --root-page-id <int> \
        --site-root-url <url> \
        --username <str> \
        --api-key <str>
```

**NB:** make sure you replace the value of `:sha` (the `...`) with the SHA of the tip of the branch
`master` of this repo.

Does that seem kinda janky? Yeah, we know. We’ll get around to providing native executable binaries
soon.


## Origin story

This tool was created by the Architecture team at [Funding Circle][fc-gh]. The team was authoring
and publishing documentation to an internal [GitHub][github] repository but needed to publish the
documentation to Confluence as well.

For what it’s worth, here are the original requirements from the internal ticket that drove the
inception of this tool:

> ### Requirements:
>
> The solution/implementation:
>
> * …should be a script that can be run easily via a CircleCI job
>   * …and that can be run on every merge to master, so that every time we update the documents in
>     the repo we automatically update the documents in Confluence
> * …should automatically translate links between the documents here in GitHub so that they point to
>   the correct corresponding document in Confluence
> * …should convert Markdown to Confluence markup with a high degree of fidelity
>   * Alternatively, we might experiment with converting to HTML and submitting that to Confluence
>   * We might want to try invoking Pandoc to perform the conversion for us
>
> #### Nice-to-haves
>
> It’d be nice if:
>
> * The solution was fairly general, so we could use it for other docsets and/or other repos
> * The script would publish docs to stable document IDs in Confluence, so links wouldn’t break, so
>   page history would accumulate, etc.
>   * This might require storing some state somewhere, but that might be worth it.


[clojure]: https://clojure.org
[confluence]: https://www.atlassian.com/software/confluence
[contributing]: docs/contributing.md
[dev-and-test]: docs/dev.md
[fc-gh]: https://github.com/FundingCircle/
[github]: https://github.com/
[markdown]: https://en.wikipedia.org/wiki/Markdown
[repl]: https://en.wikipedia.org/wiki/REPL

# newfact

A sneak peek of a new refactoring tool for clojure

## Usage

For now, you need to use it from a repl:

    (use 'newfact.core)
    (require 'source.file)
    (rename "source/file.clj" (find-ns 'source.file) 'old 'new)

For now, the only user facing function is `newfact.core/rename`,
which is invoked like:

    (rename "src/newfact/core.clj" *ns* 'found 'rewuschel)

It prints the file with all references to `found` replaced by `rewuschel`.

`newfact.find/references` might also be of interest. It does the
lexical analysis.

## Algorithm

The replacement algorithm honors lexical scope. To do this, it expands
macros according to the passed namespace and traces symbol occurrences
to the builtin clojure special forms.

## Reader

*Newfact* uses a custom reader to analyze source forms. The
implementation is shamelessly ripped of from ClojureScript and
adapted to the JVM environment. Hat tip to *clojure/core* for the
elegant, easy to work with, implementation.

It is modified slightly to store stream positions in the meta data for
each read symbol. That facilitates completely separate analysis and
refactor-step phases.

## License

Copyright (C) 2011 Herwig Hochleitner a.k.a. bendlas

Distributed under the Eclipse Public License, the same as Clojure.

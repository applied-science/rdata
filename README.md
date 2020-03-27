# rdata

A wrapper around [Renjin](https://www.renjin.org) to provide a
convenient way to load the contents of file saved in
[R](https://www.r-project.org/foundation/)'s
[RData](https://www.loc.gov/preservation/digital/formats/fdd/fdd000470.shtml)
format in Clojure.

One might want to do this because they have found an interesting
dataset that has been published in this format.


## Installation

We have not yet released to clojars, so the recommended installation is with deps.edn:

For deps.edn users:

``` clojure
appliedsciencestudio/rdata {:git/url "https://github.com/appliedsciencestudio/rdata/"
                            :sha "151e6dead06b38995f1f30b09d954a060f7a2a9c"}
```

Because of a `deps.edn` issue with transitive dependencies which
depend on non-standard repositories, you must have the key/value pair
`"bedatadriven" {:url
"https://nexus.bedatadriven.com/content/groups/public/"` in the
`:mvn/repos` of your `deps.edn` file. For instance:

``` clojure
:mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"}
            "clojars" {:url "https://clojars.org/repo"}
            "bedatadriven" {:url "https://nexus.bedatadriven.com/content/groups/public/"}
```

You can also build a deployable jar of this library:

    $ clojure -A:jar

or install it locally:

    $ clojure -A:install


## Usage

This library exports a single useful function, `read-rdata`, which --
somewhat predictably -- reads a file saved in the RData format used by
R.

The file contents are returned as nested maps (RData files can contain
arbitrarily nested data). The top-most level of the returned structure
is a key/value mapping from name to dataset, while the leaf nodes will
always be `vector`s of some primitive type (`int`, `double`, `inst`,
and so on).

The R attributes stored with each value are attached to the Clojure
translation of that value as Clojure `metadata`.

```clojure
(def mers
  (read-rdata "test/data/mers_korea_2015.RData" {:key-fn keyword}))

(keys mers)
;;=> (:mers_korea_2015)

(-> mers :mers_korea_2015 keys)
;;=> (:linelist :contacts)

(-> mers :mers_korea_2015 :linelist keys)
;;=> (:id :age :age_class :sex :place_infect :reporting_ctry :loc_hosp :dt_onset :dt_report :week_report :dt_start_exp :dt_end_exp :dt_diag :outcome :dt_death)

(-> mers :mers_korea_2015 :linelist :place_infect)
[1 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2 2]

;; hm, place 1 or place 2? Maybe the metadata can tell us what this means...
(-> mers :mers_korea_2015 :linelist :place_infect meta)
;;=> {:class ["factor"], :levels ["Middle East" "Outside Middle East"]}

;; Ah, it's a two value factor (note that R values start from 1, so one
;; must decrement the factor's index to look it up in the vector held in
;; the meta.
```


## License

Copyright © 2020 Applied Science

Distributed under the MIT License.

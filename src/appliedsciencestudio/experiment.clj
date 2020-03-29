(ns appliedsciencestudio.experiment
  (:import (org.renjin.sexp SEXP Vector ListVector IntVector Logical Symbol Null StringArrayVector PairList)))

;; Protocol-based implementation taken from clojisr, currently used
;; experimentally to compare with our version

(defprotocol Clojable
  (-java->clj [this]))

(defn java->clj
  [java-obj]
  (some-> java-obj
          -java->clj))

(extend-type Object
  Clojable
  (-java->clj [this] this))

;; Renjin represents a dataframe as a ListVector.
;; Its elements are are the columns,
;; and the "names" attribute holds the column names.
(defn df->maps
  [^ListVector df]
  (let [column-names (map keyword (lang/->attr df :names))]
    (->> df
         (map java->clj)
         (apply map (fn [& row-elements]
                      (zipmap column-names row-elements))))))

(defn NULL->nil
  [obj]
  (if (= Null/INSTANCE obj)
    nil
    obj))

(defn ->attr
  [^SEXP sexp attr-name]
  (-> sexp
      (.getAttribute (Symbol/get (name attr-name)))
      NULL->nil
      (->> (mapv #(if (string? %)
                    (keyword %)
                    %)))))

(defn ->names
  [^SEXP sexp]
  (some->> (->attr sexp "names")
           (mapv keyword)))

(defn ->class
  [^SEXP sexp]
  (some->> (->attr sexp "class")
           (mapv keyword)))

(defn renjin-vector->clj
  [transf v]
  (if (some #(= % :data.frame) (->class v))
    (df->maps v)
    (let [names (->names v)
          dim   (->attr v :dim)]
      (->> v
           (map-indexed (fn [i x]
                          (when (not (.isElementNA ^Vector v ^int i))
                            (transf x))))
           ((if (seq names)
              ;; A named list or vector will be translated to a map.
              (partial zipmap names)
              (if (seq dim)
                ;; A matrix will be translated to a vector of vectors
                (fn [values]
                  (->> values
                       (partition (second dim))
                       (#(do (println %) %))
                       (mapv vec)))
                ;; A regular list or vector will be translated to a vector.
                vec)))))))

(extend-type Vector
  Clojable
  (-java->clj [this]
    (renjin-vector->clj java->clj
                        this)))

(extend-type IntVector
  Clojable
  (-java->clj [this]
    (if (.isNumeric this)
      (renjin-vector->clj java->clj
                          this)
      ;; else - a factor
      (renjin-vector->clj  (comp java->clj
                                 (->attr this :levels)
                                 dec)
                           this))))

(extend-type PairList
  Clojable
  (-java->clj [this]
    (renjin-vector->clj java->clj
                        (.toVector this))))

(extend-type Logical
  Clojable
  (-java->clj [this]
    ({Logical/TRUE  true
      Logical/FALSE false}
     this)))

(extend-type Symbol
  Clojable
  (-java->clj [this]
    (symbol (.toString this))))

(extend-type Null
  Clojable
  (-java->clj [this]
    nil))

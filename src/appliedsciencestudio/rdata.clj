(ns appliedsciencestudio.rdata
  (:import (org.renjin.primitives.io.serialization RDataReader)))

(defn r-date-to-java-date
  "The RData format returns dates as Doubles (!). This function massages
  them into java.util.Date instances."
  [the-double]
  (java.util.Date. (.longValue (* 86400000 the-double))))

(declare clojurize-sexp)

(defn attributes->metadata
  "Retrieve the attributes from an R object and return them as a Clojure map."
  [key-fn sexp]
  (let [pair-list (.asPairList (.getAttributes sexp))]
    (if (= (class pair-list) org.renjin.sexp.Null)
      {}
      (into {} (map #(vector (key-fn %1) (clojurize-sexp key-fn %2))
                    (.getNames pair-list)
                    (.values pair-list))))))

(defn clojurize-vector
  "Convert an R vector into a clojure vector, preserving the attributes
  as clojure metadata on the vector."
  [key-fn sexp]
  (let [the-meta (attributes->metadata key-fn sexp)]
    (with-meta
      (mapv (if (= ["Date"] (get the-meta (key-fn "class")))
              r-date-to-java-date
              (partial clojurize-sexp key-fn))
            sexp)
      the-meta)))
  
(defn clojurize-sexp
  "Recursively unpack a nested set of R sexps into a clojure
  representation."
  [key-fn sexp]
  (condp = (class sexp)
    org.renjin.sexp.PairList$Node (apply array-map
                                         (mapcat #(vector (key-fn %1) (clojurize-sexp key-fn %2))
                                              (.getNames sexp)
                                              (.values sexp)))
    org.renjin.sexp.ListVector  (with-meta
                                  (apply array-map
                                         (mapcat #(vector (key-fn %) (clojurize-sexp key-fn (.get sexp (str %))))
                                              (.getNames sexp)))
                                  (attributes->metadata key-fn sexp))
    org.renjin.sexp.IntArrayVector (clojurize-vector key-fn sexp)
    org.renjin.sexp.IntBufferVector (clojurize-vector key-fn sexp)
    org.renjin.sexp.DoubleArrayVector (clojurize-vector key-fn sexp)
    org.renjin.sexp.StringArrayVector (clojurize-vector key-fn sexp)
    org.renjin.primitives.io.serialization.StringByteArrayVector (mapv identity sexp) ; XXX
    ;; primitive type leaf nodes
    java.lang.Double sexp      
    java.lang.String sexp
    java.lang.Integer sexp
    (class sexp))) ; emit classname if an unmapped class shows up
;; TODO  org.renjin.primitives.vector.RowNamesVector

(defn read-rdata
  "Read an RData formatted file into nested clojure data structures. NB
  I've used Clojure's metadata feature to store the attributes from
  the original file. There is an optional second argument, which is a
  map of options. The only options supported at the moment is
  `key-fn`, which allows one to pass a function to be applied to all
  strings being treated as keys during conversion."
  ([filename] (read-rdata filename {}))  
  ([filename {:keys [key-fn]
              :or {key-fn identity}}]
   (->> (with-open [gis (java.util.zip.GZIPInputStream. (clojure.java.io/input-stream filename))]
         (.readFile (org.renjin.primitives.io.serialization.RDataReader. gis)))
        (clojurize-sexp key-fn))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; experimental CSV support -- here there be dragons!

(def read-csv-fn
  (org.renjin.sexp.FunctionCall/newCall
   (org.renjin.sexp.Symbol/get "::")
   (into-array org.renjin.sexp.SEXP [(org.renjin.sexp.Symbol/get "utils") (org.renjin.sexp.Symbol/get "read.csv")])))

;; TODO add these parameters?
(comment  " 
numerals=c(allow.loss, warn.loss, no.loss),
as.is=!(stringsAsFactors),
colClasses=NA,
nrows=-(1.0),
check.names=TRUE,
fill=!(blank.lines.skip),
flush=FALSE,
stringsAsFactors=default.stringsAsFactors(),
fileEncoding=,
encoding=unknown,
text=<missing_arg>")

;; XXX it has a hard time with thousands separators, like "1,000", but
;; works well with an alternate decimal specifier.
(defn read-csv
  "This is a wrapper around R's CSV reader as an experiment. Do not use it."
  ([filename] (read-csv filename {}))
  ([filename {:keys [header? sep quote dec
                     strip-white? skip-blank-lines?
                     skip-nil? allow-escapes?
                     nil-string
                     ;; comment-char (defaults to #)
                     ;; col-names row-names
                     ;; col-names-fn
                     ;; file-encoding
                     ;; skip (default 0.0)
                     ]}]
   (let [args (org.renjin.sexp.PairList$Builder.)]
     (.add args (org.renjin.sexp.StringVector/valueOf (.getAbsolutePath (java.io.File. filename))))
     ;; factor conversion might not make sense?
     (.add args "stringsAsFactors", org.renjin.sexp.LogicalVector/FALSE) 
     (when header? (.add args "header", org.renjin.sexp.LogicalVector/TRUE))
     (when sep (.add args "sep", (org.renjin.sexp.StringVector/valueOf sep)))
     (when dec (.add args "dec", (org.renjin.sexp.StringVector/valueOf dec)))
     (when skip-nil? (.add args "skipNul", org.renjin.sexp.LogicalVector/TRUE))
     (when skip-blank-lines? (.add args "blank.lines.skip", org.renjin.sexp.LogicalVector/TRUE))
     (when strip-white? (.add args "strip.white", org.renjin.sexp.LogicalVector/TRUE))
     (when allow-escapes? (.add args "allowEscapes", org.renjin.sexp.LogicalVector/TRUE))
     (when nil-string
       (.add args "na.strings" (org.renjin.sexp.StringVector/valueOf nil-string)))
     (clojurize-sexp
      (.evaluate (org.renjin.eval.Context/newTopLevelContext)
                 (org.renjin.sexp.FunctionCall. read-csv-fn (.build args)))))))

;;(read-csv "resources/COVID-19/csse_covid_19_data/csse_covid_19_time_series/time_series_19-covid-Confirmed.csv")
;;(read-csv "resources/deutschland.covid19cases.tsv" {:sep "\t" :dec ","})
;;(->>  vals (map meta))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; input stream wrapper to gracefully handled ZIP and BZ files

;; public static InputStream decompress(File file) throws IOException {
;;     int b1;
;;     int b2;
;;     int b3;
;;     try(FileInputStream in = new FileInputStream(file)) {
;;       b1 = in.read();
;;       b2 = in.read();
;;       b3 = in.read();
;;     }
;;     if(b1 == GzFileConnection.GZIP_MAGIC_BYTE1 && b2 == GzFileConnection.GZIP_MAGIC_BYTE2) {
;;       return new GZIPInputStream(new FileInputStream(file));
;;     } else if(b1 == 0xFD && b2 == '7') {
;;       // See http://tukaani.org/xz/xz-javadoc/org/tukaani/xz/XZInputStream.html
;;       // Set a memory limit of 64mb, if this is not sufficient, it will throw
;;       // an exception rather than an OutOfMemoryError, which will terminate the JVM
;;       return new XZInputStream(new FileInputStream(file), 64 * 1024 * 1024);
;;     } else if (b1 == 'B' && b2 == 'Z' && b3 == 'h' ) {
;;       return new BZip2CompressorInputStream(new FileInputStream(file));
;;     } else {
;;       return new FileInputStream(file);
;;     }
;;   }

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; leftovers

;; (def script-engine
;;   (.getScriptEngine (org.renjin.script.RenjinScriptEngineFactory.)))

;;(.eval script-engine (str "utils::read.csv(file=\"resources/COVID-19/csse_covid_19_data/csse_covid_19_time_series/time_series_19-covid-Confirmed.csv\", header=TRUE)"))
;;(.eval script-engine (str "utils::read.csv(file='resources/deutschland.state-population.tsv', header=TRUE, sep='\\t')"))
;;(.eval script-engine "library(readr)")

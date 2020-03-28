(ns appliedsciencestudio.rdata-test
  (:require [clojure.test :refer :all]
            [appliedsciencestudio.rdata :refer [read-rdata make-serializer clojurize-sexp]]))

(def eval-r
  "An instance of the Renjin script engine, which we will use to generate test data."
  (let [engine (.getScriptEngine (org.renjin.script.RenjinScriptEngineFactory.))]
    (fn [script]
      (.eval engine script))))

(defn r->clj
  "A helper function to convert R data to clj w/ keyword keys."
  [key-fn sexp]
  (clojurize-sexp key-fn (make-serializer) sexp))

(deftest simple-tests
  ;; originally taking ideas from https://scicloj.github.io/clojisr/resources/public/clojisr/v1/tutorial-test/index.html#more-data-conversion-examples
  (testing "Generate some data using R, then convert it to clojure structures."
    (testing "named list"
      (is (= (r->clj identity (eval-r "list(a=1,b=c(10,20),c='hi!')"))
             {"a" [1.0],
              "b" [10.0 20.0],
              "c" ["hi!"]})))
    (testing "booleans"
      (is (= (r->clj identity (eval-r "TRUE"))
             [true]))
      (is (= (r->clj identity (eval-r "FALSE"))
             [false]))
      (is (= (r->clj identity (eval-r "NA"))
             ;; XXX
             [nil])))
    #_ (testing "null/nil"
         (is (= (r->clj keyword (eval-r "NULL"))
                nil)))
    (is (= (r->clj identity (eval-r "c(10,20,30)"))
           [10.0 20.0 30.0]))
    (is (= (r->clj identity (eval-r "list(A=1,B=2,'#123strange ()'=3)"))
           {"A" [1.0], "B" [2.0], "#123strange ()" [3.0]}))
    (is (= (r->clj keyword (eval-r "list(a=1:10,b='hi!')"))
           {:a [1 2 3 4 5 6 7 8 9 10], :b ["hi!"]}))
    (is (= (r->clj keyword (eval-r "list(a=1,b=c(10,20),c='hi!')"))
           {:a [1.0], :b [10.0 20.0], :c ["hi!"]}))))

;; (r->clj (eval-r "table(c('a','b','a','b','a','b','a','b'), c(1,1,2,2,3,3,1,1))"))

;; In R this is:
;;   1 2 3
;; a 2 1 1
;; b 2 1 1
;; ... but rdata currently returns:
;; => [2 2 1 1 1 1]
;;... with this meta:
;; {:class ["table"], :dim [2 3], :dimnames #:appliedsciencestudio.rdata{:unnamed-1 ["a" "b"], :unnamed-2 ["a" "b"]}}

;; clojisr gives this, which I'm not sure is what I'd want:
;; {["1" "a"] 2,
;;  ["1" "b"] 2,
;;  ["2" "a"] 1,
;;  ["2" "b"] 1,
;;  ["3" "a"] 1,
;;  ["3" "b"] 1}

;; these first datasets were taken from https://github.com/reconhub/outbreaks
(deftest sars-test
  (testing "Load some demo data from the SARS 2003 dataset, access it using the string keys provided by R."
    (let [data (read-rdata "test/data/sars_canada_2003.RData" )
          sars (get data "sars_canada_2003")
          dates (get sars "date")
          cases (get sars "cases_travel")]
      (is (not (nil? data)))
      (is (= '("date" "cases_travel" "cases_household" "cases_healthcare" "cases_other")
             (keys sars)))
      (is (= 110 (count dates)))
      (is (= (first cases) 1))
      (is (= (last cases) 0)))))

(deftest zika-test
  (testing "Load some data from the Zika 2015 dataset, converting keys to keywords"
    (let [data (read-rdata "test/data/zika_girardot_2015.RData" {:key-fn keyword})
          zika (-> data :zika_girardot_2015)
          dates (-> zika :date)
          cases (-> zika :cases)]
      (is (not (nil? data)))
      (is (= '(:date :cases) (keys zika)))
      (is (= 93 (count dates)))
      (is (= (first dates) #inst "2015-10-19T00:00:00.000-00:00"))
      (is (= (last dates) #inst "2016-01-22T00:00:00.000-00:00"))
      (is (= (first cases) 1))
      (is (= (last cases) 1)))))

(deftest mers-test
  (testing "Load some data from the multilayered MERS Korea 2015 dataset, converting keys to keywords"
    (let [data (read-rdata "test/data/mers_korea_2015.RData" {:key-fn keyword})
          linelist (-> data :mers_korea_2015 :linelist)]
      (is (= '(:from :to :exposure :diff_dt_onset)
             (keys (-> data :mers_korea_2015 :contacts))))
      (is (= '(:id :age :age_class :sex :place_infect
                   :reporting_ctry :loc_hosp :dt_onset :dt_report
                   :week_report :dt_start_exp :dt_end_exp :dt_diag
                   :outcome :dt_death)
             (keys linelist)))
      (is (= (first (:outcome linelist)) 1))
      (is (= (last (:outcome linelist)) 1))
      (is (= (first (:id linelist)) "SK_1"))
      (is (= (last (:id linelist)) "SK_162"))
      (is (= (first (:age linelist)) 68))
      (is (= (last (:age linelist)) 33)))))

;; https://github.com/EmilHvitfeldt/paletteer/blob/master/data/palettes_d.rda
(deftest palettes-test
  (testing "Load some colour palettes from an uncompressed RData file, converting keys to keywords"
    (let [palettes (-> (read-rdata "test/data/palettes_d.rda" {:key-fn keyword}) :palettes_d)]
      (is (= (keys palettes)
             '(:awtools :basetheme :calecopal :colorblindr :colRoz :dichromat :dutchmasters :DresdenColor
               :fishualize :futurevisions :ggsci :ggpomological :ggthemes :ggthemr :ghibli :grDevices
               :IslamicArt :jcolors :LaCroixColoR :lisa :nationalparkcolors :NineteenEightyR :nord :ochRe
               :palettetown :pals :Polychrome :MapPalettes :miscpalettes :palettesForR :PNWColors
               :rcartocolor :RColorBrewer :Redmonder :RSkittleBrewer :tidyquant :trekcolors :tvthemes
               :unikn :vapeplot :vapoRwave :werpals :wesanderson :yarrr)))
      (is (= (-> palettes :wesanderson)
             {:BottleRocket1 ["#A42820" "#5F5647" "#9B110E" "#3F5151" "#4E2A1E" "#550307" "#0C1707"],
              :BottleRocket2 ["#FAD510" "#CB2314" "#273046" "#354823" "#1E1E1E"],
              :Rushmore1 ["#E1BD6D" "#EABE94" "#0B775E" "#35274A" "#F2300F"],
              :Rushmore ["#E1BD6D" "#EABE94" "#0B775E" "#35274A" "#F2300F"],
              :Royal1 ["#899DA4" "#C93312" "#FAEFD1" "#DC863B"],
              :Royal2 ["#9A8822" "#F5CDB4" "#F8AFA8" "#FDDDA0" "#74A089"],
              :Zissou1 ["#3B9AB2" "#78B7C5" "#EBCC2A" "#E1AF00" "#F21A00"],
              :Darjeeling1 ["#FF0000" "#00A08A" "#F2AD00" "#F98400" "#5BBCD6"],
              :Darjeeling2 ["#ECCBAE" "#046C9A" "#D69C4E" "#ABDDDE" "#000000"],
              :Chevalier1 ["#446455" "#FDD262" "#D3DDDC" "#C7B19C"],
              :FantasticFox1 ["#DD8D29" "#E2D200" "#46ACC8" "#E58601" "#B40F20"],
              :Moonrise1 ["#F3DF6C" "#CEAB07" "#D5D5D3" "#24281A"],
              :Moonrise2 ["#798E87" "#C27D38" "#CCC591" "#29211F"],
              :Moonrise3 ["#85D4E3" "#F4B5BD" "#9C964A" "#CDC08C" "#FAD77B"],
              :Cavalcanti1 ["#D8B70A" "#02401B" "#A2A475" "#81A88D" "#972D15"],
              :GrandBudapest1 ["#F1BB7B" "#FD6467" "#5B1A18" "#D67236"],
              :GrandBudapest2 ["#E6A0C4" "#C6CDF7" "#D8A499" "#7294D4"],
              :IsleofDogs1 ["#9986A5" "#79402E" "#CCBA72" "#0F0D0E" "#D9D0D3" "#8D8680"],
              :IsleofDogs2 ["#EAD3BF" "#AA9486" "#B6854D" "#39312F" "#1C1718"]})))))

;; courtesy of @generateme :)
(deftest unnamed-test
  (testing "Load some data containing unnamed pairs, converting keys to keywords"
    (is (= (read-rdata "test/data/totest.rda" {:key-fn keyword})
           {:partiallyNamedList
            {:appliedsciencestudio.rdata/unnamed-1 ["noname"],
             :n ["withname"],
             :appliedsciencestudio.rdata/unnamed-2 ["noname"],
             :appliedsciencestudio.rdata/unnamed-3 ["noname"],
             :a [1.0],
             :b [2.0],
             :c [3.0]},
            :matrixRowAndColumnNames [1 2 3 4 5 6]}))))

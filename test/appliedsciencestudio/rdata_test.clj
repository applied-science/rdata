(ns appliedsciencestudio.rdata-test
  (:require [clojure.test :refer :all]
            [appliedsciencestudio.rdata :refer :all]))

;; these test datasets were taken from https://github.com/reconhub/outbreaks

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

;; Ah, it's a two value factor (not that R values start from 1, so one
;; must `dec` the factor's index to look it up in the vector held in
;; the meta.

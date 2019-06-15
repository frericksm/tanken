(ns tanken-daten.collect-test
  (:require
   [tanken-daten.collect :as collect]
[tanken-daten.storage :as storage]
[tanken-daten.system :as system]
[tanken-daten.adac-reader :as adac]
   [clojure.test :refer :all]
   [datomic.api :as d  :only [q db]]
   ))

(deftest test-extract-data
  (let [system (storage/start (system/system))
        conn (get-in system [:db :connection ])
        data  (collect/extract-data (datomic.api/db conn) adac/adac-ids)]
     (testing ""
      (is (not (nil? conn)))
    
(is   (not (nil? data )))
(is (= #{ :adac-id :tankstelle :opening-times :preismeldungen} (set (keys (first data))))))
))

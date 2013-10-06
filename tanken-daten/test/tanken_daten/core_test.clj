(ns tanken-daten.core-test
  (:require [clojure.pprint]
            [clojure.test :refer :all]
            [tanken.core :refer :all] :reload-all))

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))


(def ts1 "testseite1.html")

(def ts2 "testseite2.html")

(extract-preise ts1)


(extract-preise ts2)


(->> "U:/Userdaten/Ablage/Tankstellenpreise/preise.db"
           (clojure.java.io/reader)
           java.io.PushbackReader.
           (clojure.edn/read )
           (filter #(= "Super" (:treibstoff %)))
           (sort-by (juxt :datum-zeit :tankstelle ))
           (map #(update-in % [:tankstelle] (comp str first)))
           (map #(update-in % [:datum-zeit] (fn [d] (format "%1$Td %1$TH:%1$TM" d))))
           (map #(select-keys % [:tankstelle :preis :datum-zeit]))
           ;(map :preis)
           (clojure.pprint/pprint)
           )

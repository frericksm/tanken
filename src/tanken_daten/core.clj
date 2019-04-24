(ns tanken-daten.core
  (:require [tanken-daten.collect :as c]
[tanken-daten.adac-reader :as adac-reader ]))


(defn -main
  "Loops over collect-data. Waits 17 min between the calls of collect-data.
  args[0] is the path to the database file"
  [& args]
  (println args)
  (let [db-file (first args)]
    (while true
       (println "Collect at: " (java.util.Date.))
       (prn (adac-reader/collect-data adac-reader/adac-ids))
       (Thread/sleep (* 5000  #_60 #_17)))))


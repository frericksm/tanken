(ns tanken-daten.core
  (:require [tanken-daten.collect :as c]))


(defn -main
  "Loops over collect-data. Waits 17 min between the calls of collect-data.
  args[0] is the path to the database file"
  [& args]
  (println args)
  (let [db-file (first args)]
    (while true
       (println "Collect at: " (java.util.Date.))
       (c/collect-data db-file)
       (Thread/sleep (* 1000 60 17)))))


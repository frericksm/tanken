(ns tanken-daten.core
  (:require [tanken-daten.system  :as system ]))


(defn -main
  "Start the system"
  []
  (let [s (system/system)]
  (system/start s )))


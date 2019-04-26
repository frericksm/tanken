(ns tanken-daten.collect
  (:use [clojure.pprint])
  (:require [tanken-daten.storage :as storage]
            [tanken-daten.adac-reader :as adac]
            [clojure.set :as set]))

(defn adacid-to-dbid [alle-tankstellen-db alle-adac-ids]
  (let [a2e  (reduce (fn [m e] (assoc m (:tanken.station/adac-id e) (:db/id e)))
                     {}
                     alle-tankstellen-db)]
    (->> alle-adac-ids
         (reduce (fn [a id] (assoc a
                             id
                             (get a2e id (storage/tempid))))
                 {}))))



(defn insert [vec pos item] 
  (apply conj (subvec vec 0 pos) item (subvec vec pos)))

(defn transform-to-tx
  "Vervollständigt die Transaktionen um die :db/id und die Beziehungen zwischen den Entities"
  [a2e {:keys [adac-id] :as daten}]
  (let [e-id  (get a2e adac-id)]
    (-> daten
        (update-in [:tankstelle]
                   (fn [t] (-> t
                              (assoc :db/id e-id)
                              (assoc :tanken.station/adac-id adac-id))))

        (update-in [:opening-times]
                   (fn [ot] 
                     (->> ot
                          (map #(assoc % :db/id e-id)))))
        
        (update-in [:preismeldungen]
                   (fn [preismeldungen]
                     (->> preismeldungen
                          (map #(assoc % :db/id (storage/tempid)))
                          (map #(assoc % :tanken.preismeldung/station [:tanken.station/adac-id adac-id]))
                         ))))))

(defn extract-data
  ""
  [conn adac-ids]
  (let [alle-tankstellen-db (storage/alle-tankstellen (datomic.api/db conn))
        alle-adac-ids-db    (->> alle-tankstellen-db (map :tanken.station/adac-id))
        alle-adac-ids       (set/union alle-adac-ids-db adac-ids)
        a2e                 (adacid-to-dbid alle-tankstellen-db alle-adac-ids)
        data                (adac/collect-data alle-adac-ids)]
    (map (partial transform-to-tx a2e) data)))


(defn to-tx
  [{:keys [tankstelle opening-times preismeldungen]}]
  (-> (cons tankstelle nil) 
      (concat preismeldungen)
      (concat opening-times)
      ))

(defn collect 
  "Sammelt die Daten zu den den 'adac-ids und speichert sie in der datomic connection 'conn"
  [conn adac-ids]
  (->> (extract-data conn adac-ids)
               (map to-tx)
               (apply concat)
               (flatten)
               ;((fn [d] (prn d) d))
               (storage/load-data conn)
               )
  )
(defn task-factory 
  "Liefert eine Funktion, die collect  aufruft"
  [conn adac-ids]
  (fn []
    (do (println "\nRunning ...")
        (try (collect conn adac-ids)
          
          (catch Exception e
            (println "caught exception: " (.getMessage e)))))))

(defn start 
  "Startet diese Komponente"
  [system]
  (let [scheduler (->> system :scheduler)
        conn      (->> system :db :connection)
        task      (task-factory conn adac/adac-ids)
stations-tx (adac/stations-tx)]

(storage/store-stations-to-db conn stations-tx ) ;; lade alle tankstelle in die db
(task)     ;; führe einmal die taks aus
#_(.scheduleWithFixedDelay scheduler task 0 1 java.util.concurrent.TimeUnit/MINUTES) ;; führe die Task regelmässig aus
  system))

(defn stop [system]
  system)



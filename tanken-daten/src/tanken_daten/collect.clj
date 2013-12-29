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

(defn preismeldung-id
  "Erzeugt eine eindeutige Id zu einer Preismeldung"
  [adac-id pm]
  (let [zeitpunkt (:tanken.preismeldung/zeitpunkt pm)
        sorte     (:tanken.preismeldung/sorte pm)]
    (storage/nameUUID adac-id zeitpunkt sorte)))

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
                          (map #(assoc % :tanken.preismeldung/station e-id))
                          (map #(assoc % :tanken.preismeldung/id
                                       (preismeldung-id adac-id %)))))))))

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

(defn task-factory [conn adac-ids]
  (fn []
    (do (println "\nRunning ...")
        (try
          (->> (extract-data conn adac-ids)
               (map to-tx)
               (apply concat)
               (flatten)
               ;((fn [d] (prn d) d))
               (storage/load-data conn)
               )
          (catch Exception e
            (println "caught exception: " (.getMessage e)))))))

(defn start [system]
  (let [scheduler (->> system :scheduler)
        conn      (->> system :db :connection)
        task      (task-factory conn adac/adac-ids)]
    (.scheduleWithFixedDelay scheduler task 0 17 java.util.concurrent.TimeUnit/MINUTES))
  system)

(defn stop [system]
  system)



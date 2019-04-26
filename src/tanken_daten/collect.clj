(ns tanken-daten.collect
  (:use [clojure.pprint])
  (:require [tanken-daten.storage :as storage]
            [tanken-daten.adac-reader :as adac]
            [tanken-daten.datomic-utils :as datomic-utils]
            [datomic.api :as api]
            [clojure.set :as set]))


(defn alle-tankstellen
  "Liefert alle Entities, die ein Attribut :tanken.station/adac-id besitzen"
  [db]
  (->> (api/q '[:find ?e 
                :in $
                :where [ ?e :tanken.station/adac-id ]]
              db)
       (map #(api/entity db (first %)))))

(defn alle-preismeldungen
  "Liefert alle Entities zu Preismeldungen.
Das sind Entities mit dem Attribut :tanken.preismeldung/id"
  [db]
  (api/q '[:find ?pm
           :in $data
           :where [$data ?pm :tanken.preismeldung/id]]
           db))
(defn adacid-to-dbid [alle-tankstellen-db alle-adac-ids]
  (let [a2e  (reduce (fn [m e] (assoc m (:tanken.station/adac-id e) (:db/id e)))
                     {}
                     alle-tankstellen-db)]
    (->> alle-adac-ids
         (reduce (fn [a id] (assoc a
                             id
                             (get a2e id (datomic-utils/tempid))))
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
                          (map #(assoc % :db/id (datomic-utils/tempid)))
                          (map #(assoc % :tanken.preismeldung/station [:tanken.station/adac-id adac-id]))
                         ))))))

(defn extract-data
  ""
  [conn adac-ids]
  (let [alle-tankstellen-db (alle-tankstellen (datomic.api/db conn))
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

(defn upsert-tankstelle-tx
  "Liefert die Transaktion zum Insert oder Update der Daten einer Tankstelle.
   Beachte: das Attribut :tanken.station/adac-id hat das Attribut :db/unique mit Wert :db.unique/identity"
  [adac-id name betreiber strasse plz ort]
  [{:db/id (api/tempid :db.part/user)
    :tanken.station/adac-id   adac-id
    :tanken.station/name      name
    :tanken.station/betreiber betreiber
    :tanken.station/strasse   strasse
    :tanken.station/plz       plz
    :tanken.station/ort       ort}])
(defn upsert-preismeldung-tx
  "Liefert die Transaktion zum Insert oder Update einer Preismeldung"
  [adac-id zeitpunkt sorte preis]
  (
   [{:db/id (api/tempid :db.part/user)
     :tanken.preismeldung/station   [:tanken.station/adac-id adac-id ]
     :tanken.preismeldung/sorte     sorte
     :tanken.preismeldung/zeitpunkt zeitpunkt
     :tanken.preismeldung/preis     preis}]))

(defn store-stations-to-db 
  "Speichert die Transaction tx in der connection conn"
  
  [conn tx]
  (api/transact conn tx))




(defn collect 
  "Sammelt die Daten zu den den 'adac-ids und speichert sie in der datomic connection 'conn"
  [conn adac-ids]
  (->> (extract-data conn adac-ids)
               (map to-tx)
               (apply concat)
               (flatten)
               ;((fn [d] (prn d) d))
               (datomic-utils/load-data conn)
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

(store-stations-to-db conn stations-tx ) ;; lade alle tankstelle in die db
(task)     ;; führe einmal die taks aus
#_(.scheduleWithFixedDelay scheduler task 0 1 java.util.concurrent.TimeUnit/MINUTES) ;; führe die Task regelmässig aus
  system))

(defn stop [system]
  system)



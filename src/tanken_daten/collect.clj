(ns tanken-daten.collect
  (:use [clojure.pprint])
  (:require [tanken-daten.storage :as storage]
[tanken-daten.queries :as queries]
            [tanken-daten.adac-reader :as adac]
            [tanken-daten.datomic-utils :as datomic-utils]
            [datomic.api :as api]
            [ com.rpl.specter :as specter  :refer :all]
            [clojure.set :as set]))



(defn alle-tankstellen
  "Liefert alle Entities, die ein Attribut :tanken.station/adac-id besitzen"
  [db]
  (as-> (queries/alle-tankstellen-query) x
(api/q x db)
(map #(api/entity db (first %)) x )))

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

(defn preismeldung-db-id 
  "Liefert die entity-id der Preismeldung der Tankstelle mit der 'adac-id zur 'sorte"
  [db sorte station-lookup-ref]
  (let [adac-id (second station-lookup-ref )]
  (as-> (queries/preismeldung-db-id-query sorte adac-id) x
(api/q x db) x
(if (empty? x) 
(datomic-utils/tempid)
(first x)))))

(defn transform-to-tx
  "Vervollständigt die Transaktionen um die :db/id und die Beziehungen zwischen den Entities"
  [db daten]
(as-> daten x
(specter/transform [:preismeldungen ALL] 
 (fn [{:tanken.preismeldung/keys [sorte station] :as m}] 
   (assoc m :db/id (preismeldung-db-id db  sorte station))) x)




  #_(update-in [:tankstelle] ;; TODO: mit specter transformieren
                   (fn [t] (-> t
                              (assoc :db/id [:tanken.station/adac-id adac-id]))))

        #_(update-in [:opening-times]
                   (fn [ot] 
                     (->> ot
                          (map #(assoc % :db/id e-id)))))) ;; TODO e-id ersetzen
        

;; TODO in jede Preismeldung die db/id hinzufügen


)

(defn extract-data
  ""
  [db  adac-ids]
  (let [alle-tankstellen-db (alle-tankstellen db)
        alle-adac-ids    (->> alle-tankstellen-db (map :tanken.station/adac-id))
        data                (adac/collect-data alle-adac-ids)
        result (map (partial transform-to-tx db) data)
        ]
result ))


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



(defn update-tankstellen
  "Aktualisiert die beschreibenden Daten einer Tankstelle(Adresse,..."
  [conn data]

;; TODO implementieren 
  #_(->> data
               (map to-tx)
               (apply concat)
               (flatten)
               ;((fn [d] (prn d) d))
               (datomic-utils/load-data conn)
               )
  )

(defn update-opening-times
  "Aktualisiert die Öffnungszeiten einer Tankstelle"
  [conn data]
;; TODO implementieren
  #_(->> data
               (map to-tx)
               (apply concat)
               (flatten)
               ;((fn [d] (prn d) d))
               (datomic-utils/load-data conn)
               )
  )
(defn collect-aktuelle-preise 
  "Sammelt die Daten zu den den 'adac-ids und speichert sie in der datomic connection 'conn"
  [conn data]
  (->> data
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
    (do (println "\nRunning ..." (java.util.Date.))
(let [ db (datomic.api/db conn)
      data (extract-data db adac-ids)]
        (try (update-tankstellen conn data)
             (update-opening-times conn data)
             (collect-aktuelle-preise conn data)
          
          (catch Exception e
            (println "caught exception: " e (.getMessage e))))))))

(defn start 
  "Startet diese Komponente"
  [system]
  (let [scheduler (->> system :scheduler)
        conn      (->> system :db :connection)
        task      (task-factory conn adac/adac-ids)
stations-tx (adac/stations-tx)]

(store-stations-to-db conn stations-tx ) ;; lade alle tankstelle in die db
(task)     ;; führe einmal die task aus
(.scheduleWithFixedDelay scheduler task 0 13 java.util.concurrent.TimeUnit/MINUTES) ;; führe die Task regelmässig aus
  system))

(defn stop [system]
  system)



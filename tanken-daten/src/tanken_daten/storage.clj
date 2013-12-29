(ns tanken-daten.storage
  (:use [clojure.pprint])
  (:require [clojure.java.io :as io]
            [datomic.api :as api]
            [tanken-daten.datomic-utils :as u]))

(defn nameUUID
  "Erzeugt eine UUID aus den name-parts"
  [& name-parts]
  (->> name-parts
       (apply print-str)
       (.getBytes)
       (java.util.UUID/nameUUIDFromBytes)
       ))

(defn tempid []
  (api/tempid :db.part/user))

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
  [station adac-id zeitpunkt sorte preis]
  (let [id (nameUUID adac-id zeitpunkt sorte)]
    [{:db/id (api/tempid :db.part/user)
      :tanken.preismeldung/id        id
      :tanken.preismeldung/station   station
      :tanken.preismeldung/sorte     sorte
      :tanken.preismeldung/zeitpunkt zeitpunkt
      :tanken.preismeldung/preis     preis}]))

(defn alle-tankstellen
  "Liefert alle Entities, die ein Attribut :tanken.station/adac-id besitzen"
  [db]
  (->> (api/q '[:find ?e 
                :in $data
                :where [$data ?e :tanken.station/adac-id]
                ]
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

(defn load-data [conn tx-data]
  (api/transact conn tx-data))

(defn load-schema
  "Load datomic schema "
  [conn]
  (->> "tanken.dtm"
       (io/resource)
       (slurp)
       (read-string)
       (u/do-tx-db conn)))

(defn delete-db [system]
  (api/delete-database (->> system :db :uri)))

(defn start
  "Start des storage.
Erzeugt die datomic-Database zur URL des System (Pfad [:db :uri]).
Lädt ggf. das Schema und legt in system unter dem Pfad [:db :connection] eine Connection zur DB im System ab.
Gibt das modifizierte system zurück."
  [{:keys [db] :as system}]
  (if-let  [uri (get-in system [:db :uri])]
    (let [created (api/create-database uri)
          conn    (api/connect uri)]
      (if created (load-schema conn))
      (assoc-in system [:db :connection] conn))
    system))


(defn stop [{:keys [db] :as system}]
  (if-let [conn (get-in system [:db :connection])]
    (do
      (api/release conn)
      (update-in  system [:db :connection] (constantly nil)))
    system))



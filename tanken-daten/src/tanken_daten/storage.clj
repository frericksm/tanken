(ns tanken-daten.storage
  (:use [clojure.pprint])
  (:require [net.cgrand.enlive-html :as e]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [datomic.api :as api]
            [tanken-daten.datomic-utils :as u]))


(defn create-tankstelle
  "Speichert die Daten einer Tanstelle"
  [conn adac-id name betreiber strasse plz ort]
  (let [query-result (api/q '[:find ?station
                              :in $ ?adac-id
                              :where [?station :tanken.station/adac-id ?adac-id]]
                            (api/db conn)
                            adac-id)]
    (if (empty? query-result)
      (api/transact conn
                    [{:db/id #db/id[:db.part/user -1]
                      :tanken.station/adac-id   adac-id
                      :tanken.station/name      name
                      :tanken.station/betreiber betreiber
                      :tanken.station/strasse   strasse
                      :tanken.station/plz       plz
                      :tanken.station/ort       ort}]))))

(defn alle-tankstellen
  "Liefert alle Entities, die ein Attribut :tanken.station/adac-id besitzen"
  [db]
  (->> (api/q '[:find ?e 
                :in $data
                :where [$data ?e :tanken.station/adac-id]
                ]
              db)
       (map #(api/entity db (first %)))))

(defn create-preismeldung
  "Speichert eine Preismeldung"
  [conn adac-id zeitpunkt sorte preis]  
  (api/transact conn [[:tanken/erzeuge-preismeldung adac-id zeitpunkt sorte preis]]))

(defn alle-preismeldungen
  "Liefert alle Entities, die ein A" [db]
  (api/q '[:find ?pm
           :in $data
           :where [$data ?pm :tanken.preismeldung/zeitpunkt]]
           db))

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
      ;(delete-db system)
      (api/release conn)
      (update-in  system [:db :connection] (constantly nil)))
    system))



(ns tanken-daten.storage
  "Speicherung der Daten in datomic"
  (:use [clojure.pprint])
  (:require [clojure.java.io :as io]
            [datomic.api :as api]
            [tanken-daten.datomic-utils :as u]))

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



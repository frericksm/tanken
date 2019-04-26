(ns tanken-daten.datomic-utils
  (:require [datomic.api :as api]))

(defn- do-tx-helper [conn partition data-seq]
  (let [data (for [data-map data-seq]
               (assoc data-map :db/id (api/tempid partition)))]
    @(api/transact conn data)))

(defn do-tx-db [conn data-seq]
  (do-tx-helper conn :db.part/db data-seq))

(defn do-tx-user [conn data-seq]
  (do-tx-helper conn :db.part/user data-seq))
(defn tempid "Erzeugt eine temp-id für eine datomic transaction" 
  []
  (api/tempid :db.part/user))


(defn load-data [conn tx-data]
  (api/transact conn tx-data))

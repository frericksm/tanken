(ns tanken-daten.queries

(:use [clojure.pprint])
(:require [tanken-daten.storage :as storage]
            [tanken-daten.adac-reader :as adac]
            [tanken-daten.datomic-utils :as datomic-utils]
            [datomic.api :as api]
            [ com.rpl.specter :as specter  :refer :all]
            [clojure.set :as set]))


(defn merge-queries
  "Merges datomic queries. The queries are expected to be in map form"
  [& queries]
  (apply merge-with into queries))


(defn alle-tankstellen-query
  "Liefert alle Entities, die ein Attribut :tanken.station/adac-id besitzen"
  []
  (let [?e (gensym "?e")]
  (merge-queries {:find [?e]}
  {:where [[ ?e :tanken.station/adac-id ]]})))

(defn preismeldung-db-id-query
  "Liefert alle Entities, die ein Attribut :tanken.station/adac-id besitzen"
  [sorte adac-id]
  (let [?pm (gensym "?pm")
        ?e (gensym "?e")
        ;;?sorte (gensym "?sorte")
        ;;?adac-id (gensym "?adac-id")
        ?station (gensym "?station")]
  (merge-queries 
{:find [?pm]}
{:where [[?pm :tanken.preismeldung/sorte sorte]
         [?pm :tanken.preismeldung/station ?station]
         [?station :tanken.station/adac-id adac-id]]})))

(ns tanken-daten.analyze
  (:use (incanter core stats charts)))

(defn db-as-set [db-file]
  "Reads the wohle db als clojure set from file"
  (->> db-file
       (clojure.java.io/reader)
       java.io.PushbackReader.
       (clojure.edn/read )))

(defn gasolines
  "Returns the set of observed gasolines"
  [db-file]
  (->>  db-file
        db-as-set 
        (map :treibstoff)
        set))

(defn stations
  "Returns the observed stations as set of their names"
  [db-file]
  (->>  db-file
        db-as-set 
        (map :tanstelle)
        set))

(defn prices-timeseries
  "Returns a list vom vectors. Each vector consists of a time and the associated price"
  [entities tankstelle gasoline]
  (->>  entities
        (filter #(= gasoline (:tanken.preismeldung/sorte %)))
        (filter #(= tankstelle (:tankstelle %)))
        (sort-by (juxt :datum-zeit :tankstelle ))
        ;(map #(update-in % [:tankstelle] (comp str first)))
        (map #(update-in % [:tanken.preismeldung/zeitpunkt] (fn [d] (.getTime d))))
        (map (juxt :tanken.preismeldung/zeitpunkt :tanken.preismeldung/preis) )
        (partition 2 1)
        (map (fn [[[t1 v1][t2 v2]]] [[t1 v1] [(- t2 1) v1]]))
        (apply concat)
        ))


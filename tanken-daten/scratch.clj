(ns scratch
(:use (incanter core stats charts))
(:require [clojure.pprint]
          [tanken-daten.collect :as tdc] :reload-all))


(def db-file "/home/michael/Dokumente/Tankstellenpreise/preise.db")

;;(def db-file "U:/Userdaten/Ablage/Tankstellenpreise/preise.db")

(tdc/collect-data db-file)

(defn preise [db-file tankstelle sorte]
    (->>   db-file
           (clojure.java.io/reader)
           java.io.PushbackReader.
           (clojure.edn/read )
           (filter #(= sorte (:treibstoff %)))
           (sort-by (juxt :datum-zeit :tankstelle ))
           (map #(update-in % [:tankstelle] (comp str first)))
           (filter #(= tankstelle (:tankstelle %)))
           ;;(map #(update-in % [:datum-zeit] (fn [d] (format "%1$Td %1$TH:%1$TM" d))))
           (map #(update-in % [:datum-zeit] (fn [d] (.getTime d))))
           ;;(map #(select-keys % [:tankstelle :preis :datum-zeit]))
           (map (juxt :datum-zeit :preis) )
           (partition 2 1)
           (map (fn [[[t1 v1][t2 v2]]] [[t1 v1] [(- t2 1) v1]]))
           (apply concat)
           ))

(defn x-vals [list-of-x-y-pairs]
  (->> list-of-x-y-pairs (map first)) )

(defn y-vals [list-of-x-y-pairs]
  (->> list-of-x-y-pairs (map second)) )

(def treibstoff "Super")

(def w-super (preise db-file "W" treibstoff))
(def r-super (preise db-file "R" treibstoff))
(def s-super (preise db-file "S" treibstoff))

(def v (time-series-plot (x-vals r-super) (y-vals r-super)  :title (str "Preis je Liter " treibstoff) :x-label "Zeit" :y-label "EUR"))
;(def v (time-series-plot (x-vals w-super) (y-vals w-super)))
;(def v (time-series-plot (x-vals s-super) (y-vals s-super)))

:;r-super
;(add-lines v (x-vals r-super) (y-vals r-super))

(add-lines v (x-vals w-super) (y-vals w-super))
(add-lines v (x-vals s-super) (y-vals s-super))


(view v)





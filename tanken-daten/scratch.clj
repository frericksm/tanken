(ns scratch
(:use (incanter core stats charts))
(:require [clojure.pprint]
          [tanken-daten.core :refer :all] :reload-all))


(defn preise [tankstelle sorte]
    (->> "U:/Userdaten/Ablage/Tankstellenpreise/preise.db"
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


(def w-super (preise "W" "Super"))
(def r-super (preise "R" "Super"))
(def s-super (preise "S" "Super"))

(def v (time-series-plot (x-vals r-super) (y-vals r-super)))
;(def v (time-series-plot (x-vals w-super) (y-vals w-super)))
;(def v (time-series-plot (x-vals s-super) (y-vals s-super)))

:;r-super
;(add-lines v (x-vals r-super) (y-vals r-super))

(add-lines v (x-vals w-super) (y-vals w-super))
(add-lines v (x-vals s-super) (y-vals s-super))


(view v)




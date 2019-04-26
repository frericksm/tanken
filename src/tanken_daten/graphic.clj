(ns tanken-daten.graphic
  #_(:use (incanter core stats charts))
  (:require [clojure.pprint]
          [tanken-daten.analyze :as a] ))



(defn x-vals [list-of-x-y-pairs]
  (->> list-of-x-y-pairs (map first)) )

(defn y-vals [list-of-x-y-pairs]
  (->> list-of-x-y-pairs (map second)) )

(comment 
  (def treibstoff "Super")

  (def w-super (a/prices-timeseries db-file "W" treibstoff))
  (def r-super (a/prices-timeseries db-file "R" treibstoff))
  (def s-super (a/prices-timeseries db-file "S" treibstoff))


  (def v (time-series-plot (x-vals r-super) (y-vals r-super)  :title (str "Preis je Liter " treibstoff) :x-label "Zeit" :y-label "EUR"))


  (add-lines v (x-vals w-super) (y-vals w-super))
  (add-lines v (x-vals s-super) (y-vals s-super))


  (view v)

  (defn timeseries-plot []))

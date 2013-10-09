(use 'tanken-daten.collect  :reload-all)
(use '(incanter core stats charts))

(def db-file "/home/michael/Dokumente/Tankstellenpreise/preise.db")

(collect-data db-file)

(def shell "Shell, ALBERSLOHER WEG 415")
(def ratio "Shell, ALBERSLOHER WEG 415")
(def westfalen "Westfalen, Albersloher Weg 580")

(def s "Super")
(def s10 "Super E10")

(def p (->> db-file
       (select shell s)
       ((fn [l] (concat l (list [(System/currentTimeMillis) (second (last l)) ]))))
       (partition 2 1)
       (map (fn [[[t1 v1] [t2 v2]]] (vector [t1 v1] [(- t2 1) v2])))
       (apply concat)
       ))

p

(defn x [p] (map first p))
(defn y [p] (map second p))

(def v (xy-plot (x p) (y p)))

(view v)




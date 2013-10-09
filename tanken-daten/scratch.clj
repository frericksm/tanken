(use 'tanken-daten.collect  :reload-all)

(def db-file "/home/michael/Dokumente/Tankstellenpreise/preise.db")

(collect-data db-file)

(def shell "Shell, ALBERSLOHER WEG 415")
(def ratio "Shell, ALBERSLOHER WEG 415")
(def westfalen "Westfalen, Albersloher Weg 580")

(def s "Super")
(def s10 "Super E10")

(->> db-file
     (select shell s)
     ;(partition 2 1)
     )

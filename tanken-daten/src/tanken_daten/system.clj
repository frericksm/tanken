(ns tanken-daten.system
  (:require [tanken-daten.storage :as storage]
            [tanken-daten.collect :as collect]))

(defn system
  "Returns a new instance of the whole application."
  []
  {:db        {:uri "datomic:dev://localhost:4334/tanken"}
   ;:cache     #<Atom {}>
   ;:handler   #<Fn ...>
   ;:server    #<Jetty ...>
   }
  )

(defn start-scheduler [system]
  (assoc system :scheduler
         (new java.util.concurrent.ScheduledThreadPoolExecutor 10))
  )

(defn stop-scheduler [system]
  (if-let [s (:scheduler system)]
    (.shutdownNow s))
  (dissoc system :scheduler))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [system]
  (-> system
      storage/start
      start-scheduler
      collect/start
      ))

(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [system]
  (-> system
      collect/stop
      stop-scheduler
      storage/stop
      ))

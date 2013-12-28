(ns tanken-client.simulated.services
   (:require [io.pedestal.app.protocols :as p]
            [io.pedestal.app.messages :as msg]
            [io.pedestal.app.util.platform :as platform]))

(def tankstellen
  (atom   (list
             {:id 1 :name "Westfalen" :adresse "Albersloher Weg 34"}
             {:id 2 :name "Shell" :adresse "Albersloher Weg 23"}
             {:id 3 :name "Ratio" :adresse "Albersloher Weg 3242"})))

(defn initialize [app]
  (doseq [{:keys [id adresse] :as tankstelle} (deref tankstellen)]
      (.log js/console (str "initialize: " name ":" id ":" adresse))
      (p/put-message (:input app)
                     {msg/type :initialize
                      msg/topic [:tankstellen id]
                      :value  tankstelle})))

(defrecord MockServices [app]
  p/Activity
  (start [this]
    ;(build-tree app)
    (initialize app)
    ;(receive-messages app)
  )
  (stop [this]))

(ns tanken-client.simulated.start
  (:require [io.pedestal.app.render.push.handlers.automatic :as d]
            [tanken-client.start :as start]
            [tanken-client.rendering :as rendering]
            [tanken-client.simulated.services :as services]
            [io.pedestal.app.protocols :as p]
            [goog.Uri]
            ;; This needs to be included somewhere in order for the
            ;; tools to work.
            [io.pedestal.app-tools.tooling :as tooling]))

(defn param [name]
  (let [uri (goog.Uri. (.toString  (.-location js/document)))]
    (.getParameterValue uri name)))

(defn ^:export main []
  (let [render-config (if (= "auto" (param "renderer"))
                      d/data-renderer-config
                      (rendering/render-config))
        app (start/create-app render-config)
        services (services/->MockServices (:app app))]
    ;(app/consume-effects (:app app) services/services-fn)
    (p/start services)
    app))

(ns ^:shared tanken-client.behavior
    (:require [clojure.string :as string]
              [io.pedestal.app.messages :as msg]))
;; While creating new behavior, write tests to confirm that it is
;; correct. For examples of various kinds of tests, see
;; test/tanken_client/behavior-test.clj.

(defn set-value-transform [old-value message]
  (let [value (:value message)]
    (.log js/console (str "set-value-transform: old-value:" old-value  ", message:" message))
    value))

(defn add-tankstelle-transform [old-value message]
  (let [tankstelle (:value message)]
    (.log js/console (str "add-tankstelle-transform: " tankstelle ))
    (assoc old-value (:id tankstelle) tankstelle)))

(def example-app
  ;; There are currently 2 versions (formats) for dataflow
  ;; description: the original version (version 1) and the current
  ;; version (version 2). If the version is not specified, the
  ;; description will be assumed to be version 1 and an attempt
  ;; will be made to convert it to version 2.
  {:version 2
   :transform [;;[:set-value [:greeting] set-value-transform]
               [:initialize [:tankstellen :*] set-value-transform]
               [:initialize [:sorten] set-value-transform]
               ]})

;; Once this behavior works, run the Data UI and record
;; rendering data which can be used while working on a custom
;; renderer. Rendering involves making a template:
;;
;; app/templates/tanken-client.html
;;
;; slicing the template into pieces you can use:
;;
;; app/src/tanken_client/html_templates.cljs
;;
;; and then writing the rendering code:
;;
;; app/src/tanken_client/rendering.cljs

(comment
  ;; The examples below show the signature of each type of function
  ;; that is used to build a behavior dataflow.

  ;; transform

  (defn example-transform [old-state message]
    ;; returns new state
    )

  ;; derive

  (defn example-derive [old-state inputs]
    ;; returns new state
    )

  ;; emit

  (defn example-emit [inputs]
    ;; returns rendering deltas
    )

  ;; effect

  (defn example-effect [inputs]
    ;; returns a vector of messages which effect the outside world
    )

  ;; continue

  (defn example-continue [inputs]
    ;; returns a vector of messages which will be processed as part of
    ;; the same dataflow transaction
    )

  ;; dataflow description reference

  {:transform [[:op [:path] example-transform]]
   :derive    #{[#{[:in]} [:path] example-derive]}
   :effect    #{[#{[:in]} example-effect]}
   :continue  #{[#{[:in]} example-continue]}
   :emit      [[#{[:in]} example-emit]]}
  )

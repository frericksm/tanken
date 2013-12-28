(ns tanken-client.html-templates
  (:use [io.pedestal.app.templates :only [tfn dtfn tnodes]]))

(defmacro tanken-client-templates
  []
  ;; Extract the 'hello' template from the template file tanken-client.html.
  ;; The 'dtfn' function will create a dynamic template which can be
  ;; updated after it has been attached to the DOM.
  ;;
  ;; To see how this template is used, refer to
  ;;
  ;; app/src/tanken_client/rendering.cljs
  ;;
  ;; The last argument to 'dtfn' is a set of fields that should be
  ;; treated as static fields (may only be set once). Dynamic templates
  ;; use ids to set values so you cannot dynamically set an id.
  ;;{:tanken-client-page (dtfn (tnodes "tanken-client.html" "hello") #{:id})}

  {:tankstelle-li (dtfn (tnodes "tanken-client.html" "tankstelle") )
   :tanken-client-page (dtfn (tnodes "tanken-client.html" "tankstellen" [[:li]]) )}
  )

;; Note: this file will not be reloaded automatically when it is changed.

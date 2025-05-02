(ns hello.focused-entity
  (:require [clojure.pprint :as pprint]
            [clojure.string :as string]
            [hyperlith.core :as h]))

(defn render-home [{:keys [db sid first-render] :as _req}]
  (let [snapshot @db
        people (:people snapshot)]
    (h/html
      [:main#morph.main {:data-signals "{focused: {id: null, attr: null, value: null}}"}
       [:div#people
        (map
         (fn [[id person]]
           [:div {:id (str "person-" (:id person))}
            [:h2 "Person " (:id person)]
            (map
             (fn [attr]
               [:label {:style {:display "block"}}
                (-> attr name string/capitalize) ":"
                [:input {:value (get person attr)
                         :type "text"
                         :data-initial-value (get person attr)
                         :data-entity-id (:id person)
                         :data-attribute-name attr
                         :data-on-focus (str "$focused.id = evt.target.dataset.entityId;"
                                             "$focused.attr = evt.target.dataset.attributeName;")
                         :data-on-blur (str "$focused.value = evt.target.value;"
                                            "$focused.value !== evt.target.dataset.initialValue && @post('update');")}]])
             [:name :hobby])])
         people)]])))

(defn update-handler [{:keys [db] :as req}]
  (let [signals (-> req :body)
        {:keys [id attr value]} (:focused signals)
        attr (keyword attr)]
    (swap! db assoc-in [:people id attr] value)))

(def default-shim-handler
  (h/shim-handler
   (h/html
     [:meta {:title "demo"}])))

(def router
  (h/router
   {[:get  "/"]              default-shim-handler
    [:post "/"]              (h/render-handler #'render-home
                                               {:br-window-size 19})
    [:post "/update"]       (h/action-handler #'update-handler)
    }))

(defn ctx-start []
  (let [db_ (atom {:people  {"1" {:id "1"
                                  :name "Stan"
                                  :hobby "fishing"}
                             "2" {:id "2"
                                  :name "Kenny"
                                  :hobby "boating"}
                             "3" {:id "3"
                                  :name "Cartman"
                                  :hobby "skiing"}}})]
    (add-watch db_ :refresh-on-change
               (fn [_ _ old-state new-state]
                 ;; Only refresh if state has changed
                 (when-not (= old-state new-state)
                   (h/refresh-all!))))
    {:db db_}))

(defn -main [& _]
  (h/start-app
   {:router         #'router
    :max-refresh-ms 200
    :ctx-start      ctx-start
    :ctx-stop       (fn [{:keys [game-stop]}] (game-stop))
    :csrf-secret    "whatever"
    :on-error       (fn [_ctx {:keys [_req error]}]
                      (pprint/pprint error)
                      (flush))}))

;; Refresh app when you re-eval file
(h/refresh-all!)

(comment
  (do (-main)
      nil)
  ;; (clojure.java.browse/browse-url "http://localhost:8080/")

  ;; stop server
  (((h/get-app) :stop))

  (def db (-> (h/get-app) :ctx :db))

  ,)

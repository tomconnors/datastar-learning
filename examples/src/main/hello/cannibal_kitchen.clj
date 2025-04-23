(ns hello.cannibal-kitchen
  (:require
   [charred.api :as charred]
   [datascript.core :as d]
   [dev.onionpancakes.chassis.compiler :as hc]
   [dev.onionpancakes.chassis.core :as h]
   [garden.core :as g]
   [mount.core :as mount]
   [org.httpkit.server :as http.server]
   [reitit.ring :as rr]
   [ring.util.response :as ruresp]
   [starfederation.datastar.clojure.adapter.http-kit :as d*.httpkit]
   [starfederation.datastar.clojure.api :as d*]
   [taoensso.telemere :as t]
   ))

(def config
  {:http/port 5679})

(defn- html-response [html]
  (-> (ruresp/response html)
      (ruresp/content-type "text/html")))

(def ^:private bufSize 1024)
(def read-json (charred/parse-json-fn {:async? false :bufsize bufSize}))
(def write-json- (charred/write-json-fn {}))
(defn write-json [data]
  (let [s (java.io.StringWriter.)
        _ (write-json- s data)]
    (.toString s)))

(defn get-signals
  "Get d* signals from a ring request"
  [req]
  (-> req d*/get-signals read-json))

;; -- Database and Schema --

(def schema
  {:employee/id { ;; :db/valueType :db.type/uuid
                 :db/unique :db.unique/identity}
   :employee/name {} #_{:db/valueType :db.type/string}
   :employee/rating {} #_{:db/valueType :db.type/long}
   :ingredient/kind {} #_{:db/valueType :db.type/keyword}
   :ingredient/name {} #_{:db/valueType :db.type/string}
   :ingredient/id { ;; :db/valueType :db.type/uuid
                   :db/unique :db.unique/identity}
   :recipe/id { ;; :db/valueType :db.type/uuid
               :db/unique :db.unique/identity}
   :recipe/name {} #_{:db/valueType :db.type/string}
   :recipe/ingredients {:db/valueType :db.type/ref
                        :db/isComponent true
                        :db/cardinality :db.cardinality/many}
   :recipe.ingredient/mg-per-serving { ;; :db/valueType :db.type/long
                                      }
   :recipe.ingredient/kind { ;; :db/valueType :db.type/keyword
                            }
   :recipe/instructions { ;; :db/valueType :db.type/string
                         }
   :user/notifications {:db/valueType :db.type/ref
                        :db/isComponent true
                        :db/cardinality :db.cardinality/many}
   :notification/id { ;; :db/valueType :db.type/uuid
                     :db/unique :db.unique/identity}
   :notification/time { ;; :db/valueType :db.type/instant
                       }
   :notification/html { ;; :db/valueType :db.type/string
                       }
   :user/undo-stack {:db/valueType :db.type/ref
                     :db/isComponent true
                     :db/cardinality :db.cardinality/many}
   :user/undo-stack-index { ;; :db/valueType :db.type/long
                           }
   })

(def seeds
  [{:employee/id (random-uuid)
    :employee/name "Fry"
    :employee/rating 3
    :ingredient/kind :human}
   {:employee/id (random-uuid)
    :employee/name "Hermes"
    :employee/rating 5
    :ingredient/kind :human}
   {:employee/id (random-uuid)
    :employee/name "Zoidberg"
    :employee/rating 1
    :ingredient/kind :lobster}
   {:employee/id (random-uuid)
    :ingredient/id (random-uuid)
    :ingredient/name "Wernstrom"
    :employee/name "Wernstrom"
    :ingredient/kind :human}
   {:recipe/id (random-uuid)
    :recipe/name "Person à la King"
    :recipe/ingredients [{:recipe.ingredient/mg-per-serving (* 1000 100)
                          :recipe.ingredient/kind :human}]
    :recipe/instructions (write-json {})}
   {:recipe/id (random-uuid)
    :recipe/name "Lobster Bisque"
    :recipe/ingredients [{:recipe.ingredient/mg-per-serving (* 1000 100)
                          :recipe.ingredient/kind :lobster}]
    :recipe/instructions (write-json {})}])

(defn init-conn []
  (let [conn (d/create-conn schema)]
    (d/transact! conn seeds)
    conn))

(defonce conn (init-conn))

(comment
  (alter-var-root #'conn (constantly (init-conn)))
  )

;; -- Home Page --

(def common-css
  [[:html :body
    {:padding "0"
     :margin "0"
     :width "100vw"
     :height "100vh"}]
   [:body
    {:display "grid"
     :grid-template-columns "1fr"
     :grid-template-rows "auto 1fr"}]
   [:header
    {:background-color "#D9D9D9"
     :font-size "32px"
     :display "flex"
     :align-items "center"}]])

(def home-css
  (g/css {:pretty-print? true}
         common-css
         [:main
          {:display "grid"
           :grid-template-columns "1fr 1fr 1fr"
           :grid-template-rows "1fr"}]
         [:section
          {:display "flex"
           :flex-direction "column"}]
         ["section:not(:last-of-type)"
          {:border-right "1px solid black"}]
         [:.dropArea
          {:flex-grow "1"}]
         [:.droppable
          {:background-color "aquamarine" }]
         [:.droppableActive
          {:box-shadow "0px 0px 6px 3px #34b64a"}]
         [:h1 {:border-bottom "1px solid black"
               :margin "0"
               :padding ".5em 1em"}]
         [:.card {:display "block"
                  :margin ".5em"}]
         [:.unavailable {:opacity "0.5"}]))

(def employee-page-css
  (g/css {:pretty-print? true}
         common-css
         [:main {:padding "2em"
                 :display "flex"
                 :flex-direction "column"}]))



(defn employee-list [db]
  (let [items (->> (d/q '[:find [(pull ?e [:employee/id :employee/name :ingredient/kind]) ...]
                          :in $
                          :where
                          [?e :employee/id]
                          (not [?e :ingredient/id])]
                        db)
                   (sort-by :employee/name))]
    [:div
     {:id "employees-drop-area"
      :data-on-drop__prevent "@post('/drop-employee')"
      :data-on-dragenter "evt.dataTransfer.types.includes('ingredient/id') && ($_activeDropTarget = 'employee');"
      :data-on-dragleave "evt.dataTransfer.types.includes('ingredient/id') && ($_activeDropTarget = null);"
      ;; :data-on-dragover__prevent "evt.dataTransfer.dropEffect = 'move'"
      :data-on-dragover "evt.dataTransfer.types.includes('ingredient/id') ? evt.preventDefault() : null;"
      :data-class "{droppable: $dragging.type === 'ingredient', droppableActive: $_activeDropTarget === 'employee', dropArea: true}"}
     (map
      (fn [{:keys [employee/id employee/name ingredient/kind]}]
        [:a {:href (str "/employee/" id)
             :style {:color "unset" :text-decoration "unset"}}
         [:sl-card {:class "card"
                    :id (str "employee-" id)
                    :draggable "true"
                    :data-on-dragstart (str "evt.dataTransfer.setData('employee/id', '" id "');"
                                            "evt.dataTransfer.dropEffect = 'move';"
                                            "$dragging.type = 'employee';"
                                            "$dragging.id   = '" id "';")
                    :data-on-dragend (str "$dragging.type = null;"
                                          "$dragging.id = null;"
                                          "$_activeDropTarget = null;")}
          [:h2 name]
          (when kind
            [:p kind])]])
      items)]))

(defn ingredient-list [db]
  (let [items (->> (d/q '[:find [(pull ?e [:ingredient/id
                                           :ingredient/kind
                                           :ingredient/name]) ...]
                          :in $
                          :where [?e :ingredient/id]]
                        db)
                   (sort-by :ingredient/name))]
    [:div
     {:id "ingredients-drop-area"
      :data-on-drop__prevent "@post('/drop-ingredient')"
      :data-on-dragenter "evt.dataTransfer.types.includes('employee/id') && ($_activeDropTarget = 'ingredients');"
      :data-on-dragleave "evt.dataTransfer.types.includes('employee/id') && ($_activeDropTarget = null);"
      ;;:data-on-dragover__prevent "evt.dataTransfer.dropEffect = 'move'"
      :data-on-dragover "evt.dataTransfer.types.includes('employee/id') ? evt.preventDefault() : null;"
      :data-class "{droppable: $dragging.type === 'employee', droppableActive: $_activeDropTarget === 'ingredients', dropArea: true}"}
     (map
      (fn [{:ingredient/keys [id kind name]}]
        [:sl-card {:class "card"
                   :draggable "true"
                   :data-on-dragstart (str "evt.dataTransfer.setData('ingredient/id', '" id "');"
                                           "evt.dataTransfer.dropEffect = 'move';"
                                           "$dragging.type = 'ingredient';"
                                           "$dragging.id   = '" id "';")
                   :data-on-dragend (str "$dragging.type = null;"
                                         "$dragging.id = null;"
                                         "$_activeDropTarget = null;")}
         [:h2 name]
         (when kind
           [:p kind])])
      items)]))

(defn recipe-list [db]
  (let [available-ingredient-kinds (->> (d/q '[:find [?k ...]
                                               :in $
                                               :where
                                               [?e :ingredient/id]
                                               [?e :ingredient/kind ?k]]
                                             db)
                                        set)
        recipes (->> (d/q '[:find [(pull ?e [:recipe/id
                                             {:recipe/ingredients [:recipe.ingredient/kind]}
                                             :recipe/name]) ...]
                            :in $
                            :where [?e :recipe/id]]
                          db)
                     (sort-by :recipe/name))]
    (map
     (fn [{:recipe/keys [id ingredients name]}]
       (let [available (every?
                        (fn [{:keys [recipe.ingredient/kind]}]
                          (contains? available-ingredient-kinds kind))
                        ingredients)
             card [:sl-card {:class ["card" (when-not available "unavailable")]}
                   [:h2 name]
                   (when (seq ingredients)
                     [:ul
                      (map
                       (fn [{:recipe.ingredient/keys [kind]}]
                         [:li kind])
                       ingredients)])]]
         [:a {:href (str "/recipe/" id)
              :style {:color "unset" :text-decoration "unset"}}
          (if available
            card
            [:sl-tooltip {:content "Unavailable"}
             card])]))
     recipes)))

(defn main-element-hiccup [db]
  [:main {:id "main"}
   [:section
    [:h1 "Employees"]
    (employee-list db)]
   [:section
    [:h1 "Ingredients"]
    (ingredient-list db)]
   [:section
    [:h1 "Recipes"]
    (recipe-list db)]])

(defn static-assets []
  [[:script {:type "module"
             :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.0-beta.11/bundles/datastar.js"}]

   [:link {:rel "stylesheet"
           :href "https://cdn.jsdelivr.net/npm/@shoelace-style/shoelace@2.20.1/cdn/themes/light.css"}]
   [:script {:type "module"
             :src "https://cdn.jsdelivr.net/npm/@shoelace-style/shoelace@2.20.1/cdn/shoelace.js"}]])

(defn home-page-html [db]
  (h/html
   (hc/compile
    [:html {:lang "en"}
     [:head
      [:title "Cannibal Kitchen"]
      (static-assets)
      [:style home-css]]
     [:body {:data-signals (write-json {:dragging {:type nil :id nil}
                                        :_activeDropTarget nil})}
      [:header
       [:sl-icon-button {:name "list" :label "Menu"}]
       "Cannibal Kitchen"
       [:span {:style {:flex-grow "1"}}]
       [:sl-tooltip {:content "Undo"}
        [:sl-icon-button {:name "arrow-counterclockwise" :label "Undo"}]]
       [:sl-tooltip {:content "Redo"}
        [:sl-icon-button {:name "arrow-clockwise" :label "Redo"}]]
       [:sl-tooltip {:content "Notifications"}
        [:sl-icon-button {:name "bell" :label "Notifications"}]]]
      (main-element-hiccup db)]])))

(defn get-home [req]
  (let [db (d/db conn)]
    (html-response (home-page-html db))))

(defn employee->ingredient! [id]
  (let [db (d/db conn)
        id (parse-uuid id)
        e (d/entity db [:employee/id id])
        dbid (:db/id e)]
    (d/transact conn [[:db/add dbid :ingredient/id (random-uuid)]
                      [:db/add dbid :ingredient/name (or (:employee/name e)
                                                         "New Ingredient")]])))

(defn ingredient->employee! [id]
  (let [db (d/db conn)
        id (parse-uuid id)
        e (d/entity db [:ingredient/id id])
        dbid (:db/id e)]
    (when (:employee/id e)
      (d/transact conn [[:db/retract dbid :ingredient/id id]]))))

(defn drop-ingredient-handler [request]
  (let [{:strs [dragging]} (-> request get-signals)
        {:strs [id type]} dragging]
    (d*.httpkit/->sse-response
     request
     {d*.httpkit/on-open
      (fn [sse]
        (d*/with-open-sse sse
          (case type
            "employee" (employee->ingredient! id))
          (d*/merge-fragment! sse (h/html (main-element-hiccup (d/db conn))))))})))

(defn drop-employee-handler [request]
  (let [{:strs [dragging]} (-> request get-signals)
        {:strs [id type]} dragging]
    (d*.httpkit/->sse-response
     request
     {d*.httpkit/on-open
      (fn [sse]
        (d*/with-open-sse sse
          (case type
            "ingredient" (ingredient->employee! id))
          (d*/merge-fragment! sse (h/html (main-element-hiccup (d/db conn))))))})))

(defn employee-page-html [db id]
  (let [ident [:employee/id (parse-uuid id)]
        employee (d/entity db ident)]
    (h/html
     (hc/compile
      [:html {:lang "en"}
       [:head
        [:title "Cannibal Kitchen Employee: " (:employee/name employee)]
        (static-assets)
        [:style employee-page-css]]
       [:body {:data-signals (write-json {:employee {:name (:employee/name employee)
                                                     :rating (:employee/rating employee)}})}
        [:header
         [:sl-icon-button {:name "list" :label "Menu"}]
         [:a {:href "/"} "Cannibal Kitchen"]
         [:span {:style {:flex-grow "1"}}]
         [:sl-tooltip {:content "Undo"}
          [:sl-icon-button {:name "arrow-counterclockwise" :label "Undo"}]]
         [:sl-tooltip {:content "Redo"}
          [:sl-icon-button {:name "arrow-clockwise" :label "Redo"}]]
         [:sl-tooltip {:content "Notifications"}
          [:sl-icon-button {:name "bell" :label "Notifications"}]]]
        [:main
         [:sl-input {:label "Name"
                     :data-bind "$employee.name"
                     :data-on-sl-change (str "@patch('/employee/" id "')") }]
         [:label {:style {:margin-top "1em"}} "Rating"
          [:sl-rating {:style {:display "block"}
                       :label "Rating"
                       :data-bind "$employee.rating"
                       :data-on-sl-change (str
                                           "console.log('sl-change', evt, ctx);"
                                           (str "@patch('/employee/" id "')"))}]]]]]))))

(defn not-found-html []
  (h/html
   (hc/compile
    [:html {:lang "en"}
     [:head
      [:title "Cannibal Kitchen"]
      (static-assets)]
     [:body
      "Not Found"]])))

(defn get-employee [req]
  (let [db (d/db conn)
        id (-> req :path-params :id)
        ident [:employee/id (parse-uuid id)]
        e (d/entity db ident)]
    (if e
      (html-response (employee-page-html db id))
      (html-response (not-found-html)))))

(defn patch-employee [request]
  (let [{:strs [employee]} (-> request get-signals)
        {:strs [name rating]} employee
        id-str (-> request :path-params :id)
        id (parse-uuid id-str)]
    (d*.httpkit/->sse-response
     request
     {d*.httpkit/on-open
      (fn [sse]
        (d*/with-open-sse sse
          (d/transact conn [{:employee/id id
                             :employee/name name
                             :employee/rating rating}])
          (d*/merge-fragment! sse (h/html (employee-page-html (d/db conn) id-str)))))})))

(def recipe-page-css
  (g/css {:pretty-print? true}
         common-css
         [:main {:padding "2em"
                 :display "flex"
                 :flex-direction "column"}]))

(defn recipe-page-html [db id]
  (let [ident [:recipe/id (parse-uuid id)]
        employee (d/entity db ident)]
    (h/html
     (hc/compile
      [:html {:lang "en"}
       [:head
        [:title "Cannibal Kitchen Recipe: " (:recipe/name employee)]
        (static-assets)
        [:style recipe-page-css]]
       [:body {:data-signals (write-json {:recipe {:name (:recipe/name employee)
                                                   :instructions "todo"}})}
        [:header
         [:sl-icon-button {:name "list" :label "Menu"}]
         [:a {:href "/"} "Cannibal Kitchen"]
         [:span {:style {:flex-grow "1"}}]
         [:sl-tooltip {:content "Undo"}
          [:sl-icon-button {:name "arrow-counterclockwise" :label "Undo"}]]
         [:sl-tooltip {:content "Redo"}
          [:sl-icon-button {:name "arrow-clockwise" :label "Redo"}]]
         [:sl-tooltip {:content "Notifications"}
          [:sl-icon-button {:name "bell" :label "Notifications"}]]]
        [:main
         [:sl-input {:label "Name"
                     :data-bind "$recipe.name"
                     :data-on-sl-change (str "@patch('/recipe/" id "')") }]
         ;; TODO
         #_[:label
            "Instructions"
            ]
         ]]]))))

(defn get-recipe [req]
  (let [db (d/db conn)
        id (-> req :path-params :id)
        ident [:recipe/id (parse-uuid id)]
        e (d/entity db ident)]
    (if e
      (html-response (recipe-page-html db id))
      (html-response (not-found-html)))))

(defn patch-recipe [request]
  (let [{:strs [recipe]} (-> request get-signals)
        {:strs [name ]} recipe
        id-str (-> request :path-params :id)
        id (parse-uuid id-str)]
    (d*.httpkit/->sse-response
     request
     {d*.httpkit/on-open
      (fn [sse]
        (d*/with-open-sse sse
          (d/transact conn [{:recipe/id id
                             :recipe/name name}])
          (d*/merge-fragment! sse (h/html (recipe-page-html (d/db conn) id-str)))))})))

(def routes
  [["/" {:get {:handler get-home}}]
   ["/drop-ingredient" {:post {:handler drop-ingredient-handler}}]
   ["/drop-employee" {:post {:handler drop-employee-handler}}]
   ["/employee/:id" {:get {:handler get-employee}
                     :patch {:handler patch-employee}}]
   ["/recipe/:id" {:get {:handler get-recipe}
                   :patch {:handler patch-recipe}}]])

(def router (rr/router routes))

(def http-handler (rr/ring-handler router))

(defn start-http-server []
  (let [server (http.server/run-server http-handler
                                       {:port (:http/port config)
                                        :legacy-return-value? false})]
    (t/log! {:level :info :data {:port (http.server/server-port server)}}
            "Started HTTP Server")
    server))

(defn stop-http-server [server]
  (http.server/server-stop! server))

(mount/defstate http-server
  :start (start-http-server)
  :stop (stop-http-server http-server))

(comment
  (mount/start #'http-server)
  (mount/stop)
  )

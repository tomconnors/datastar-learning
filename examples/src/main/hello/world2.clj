(ns hello.world2
  (:require
   [charred.api :as charred]
   [dev.onionpancakes.chassis.compiler :as hc]
   [dev.onionpancakes.chassis.core :as h]
   [mount.core :as mount]
   [org.httpkit.server :as http.server]
   [reitit.ring :as rr]
   [reitit.ring.middleware.parameters :as rmparams]
   [ring.util.response :as ruresp]
   [starfederation.datastar.clojure.adapter.http-kit :as d*.httpkit]
   [starfederation.datastar.clojure.api :as d*]
   [taoensso.telemere :as t]
   ))

(def config
  {:http/port 5679})

(defn home [_req]
  (->
   (ruresp/response
    (h/html
     (hc/compile
      [:html {:lang "en"}
       [:head
        [:title "Datastar Demo"]
        [:script {:src "https://unpkg.com/@tailwindcss/browser@4"}]
        [:script {:type "module"
                  :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.0-beta.11/bundles/datastar.js"}]]
       [:body.bg-white.dark:bg-gray-900.text-lg.max-w-xl.mx-auto.my-16
        [:div {:data-signals "{delay: 400, message: 'hello world'}"
               :class "bg-white dark:bg-gray-800 text-gray-500 dark:text-gray-400 rounded-lg px-6 py-8 ring shadow-xl ring-gray-900/5 space-y-2"}
         [:div.flex.justify-between.items-center
          [:h1.text-gray-900.dark:text-white.text-3xl.font-semibold
           "Datastar SDK Demo"]
          [:img {:src "https://data-star.dev/static/images/rocket.png"
                 :alt "Rocket"
                 :width "64"
                 :height "64"}]]
         [:p.mt-2
          "SSE events will be streamed from the backend to the frontend."]
         [:div.space-x-2
          [:label {:for "delay"} "Delay in milliseconds"]
          [:input {:data-bind-delay true
                   :id "delay"
                   :type "number"
                   :step "100"
                   :min "0"
                   :class "w-36 rounded-md border border-gray-300 px-3 py-2 placeholder-gray-400 shadow-sm focus:border-sky-500 focus:outline focus:outline-sky-500 dark:disabled:border-gray-700 dark:disabled:bg-gray-800/20"}]]
         [:div.space-x-2
          [:label {:for "message-input"} "The message to send"]
          [:input {:data-bind-message true
                   :id "message-input"
                   :type "text"
                   :class "w-36 rounded-md border border-gray-300 px-3 py-2 placeholder-gray-400 shadow-sm focus:border-sky-500 focus:outline focus:outline-sky-500 dark:disabled:border-gray-700 dark:disabled:bg-gray-800/20"}]]
         [:button {:data-on-click "@post('/hello-world')"
                   :class "rounded-md bg-sky-500 px-5 py-2.5 leading-5 font-semibold text-white hover:bg-sky-700 hover:text-gray-100 cursor-pointer"}
          "Start"]]
        [:div.my-16.text-8xl.font-bold.text-transparent
         {:style "background: linear-gradient(to right in oklch, red, orange, yellow, green, blue, blue, violet); background-clip: text"}
         [:div#message "Hello, world!"]]]])))
   (ruresp/content-type "text/html")))

(def ^:private bufSize 1024)
(def read-json (charred/parse-json-fn {:async? false :bufsize bufSize}))

(defn get-signals [req]
  (-> req d*/get-signals read-json))

(defn ->frag [message i]
  (h/html
   (hc/compile
    [:div {:id "message"}
     (subs message 0 (inc i))])))

(defn hello-world [request]
  (let [{:strs [delay message]} (-> request get-signals)
        message-count (count message)]
    (d*.httpkit/->sse-response
     request
     {d*.httpkit/on-open
      (fn [sse]
        (d*/with-open-sse sse
          (dotimes [i message-count]
            (d*/merge-fragment! sse (->frag message i))
            (Thread/sleep delay))))})))

(def routes
  [["/" {:handler home}]
   ["/hello-world" {:handler hello-world
                    :middleware [rmparams/parameters-middleware]}]])

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

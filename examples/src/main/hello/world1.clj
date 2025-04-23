(ns hello.world1
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
  {:http/port 5678})

(defn home "Handle requests for the home page"
  [_req]
  (ruresp/resource-response "public/index.html"))

(def ^:private bufSize 1024)
(def read-json (charred/parse-json-fn {:async? false :bufsize bufSize}))

(defn get-signals
  "Get datastar signals from a ring request"
  [req]
  (-> req d*/get-signals read-json))

(def message "Hello World.")
(def message-count (count message))

(defn ->frag [i]
  (h/html
   (hc/compile
    [:div {:id "message"}
     (subs message 0 (inc i))])))

(defn hello-world
  "Handle requests to set up the message load process, which sends the #message div to the client repeatedly, each time including more of `message`."
  [request]
  (let [d (-> request get-signals (get "delay") int)]
    (d*.httpkit/->sse-response
     request
     {d*.httpkit/on-open ;; just a keyword. Stored in a var so we get a nice docstring.
      (fn [sse]
        (d*/with-open-sse sse
          (dotimes [i message-count]
            (d*/merge-fragment! sse (->frag i))
            (Thread/sleep d))))})))

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

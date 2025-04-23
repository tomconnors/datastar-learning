(ns hello.optimistic-update
  (:require
   [charred.api :as charred]
   [dev.onionpancakes.chassis.compiler :as hc]
   [dev.onionpancakes.chassis.core :as h]
   [garden.core :as g]
   [garden.stylesheet :as gstyles]
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

(def css
  (g/css
   {:pretty-print? false}
   [:main {:display "flex" :align-items "center" :justify-content "center"}]))

(def rsvp* (atom false))

(def ^:private bufSize 1024)
(def read-json (charred/parse-json-fn {:async? false :bufsize bufSize}))
(def write-json- (charred/write-json-fn {}))
(defn write-json [data]
  (let [s (java.io.StringWriter.)
        _ (write-json- s data)]
    (.toString s)))

(comment
  (read-json "{\"foo\": 1}")
  (-> (write-json {:foo "bar"})
      (h/escape-text)
      )
  )

(defn input []
  [:input {:type "checkbox"
           :data-signals (write-json {:checked @rsvp*})
           :data-bind "$checked"
           :data-on-change "@post('/rsvp')"
           :id "rsvp-input"}])

(comment
  (h/html (input))
  )

(defn home [_req]
  (->
   (ruresp/response
    (h/html
     (hc/compile
      [:html {:lang "en"}
       [:head
        [:script {:type "module"
                  :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.0-beta.11/bundles/datastar.js"}]
        [:style css]]
       [:body
        [:main
         [:label
          (input)
          "Will you come to my birthday party?"]]]])))
   (ruresp/content-type "text/html")))

(defn rsvp [request]
  (d*.httpkit/->sse-response
   request
   {d*.httpkit/on-open
    (fn [sse]
      (d*/with-open-sse sse
        (swap! rsvp* not)
        #_(when (> (rand) 0.5)
          (swap! rsvp* not))
        (d*/merge-fragment! sse (h/html (input)))))}))

(def routes
  [["/" {:handler home}]
   ["/rsvp" {:handler rsvp}]])

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

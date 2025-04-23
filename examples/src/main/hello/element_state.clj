(ns hello.element-state
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
  (g/css {:pretty-print? false}
   (gstyles/at-keyframes
    "spin"
    ["100%" {:rotate "360deg"}])
   [:#pictures
    {:display "grid"
     :grid-template-columns "1fr 1fr"
     :grid-template-rows "1fr 1fr"
     :align-items "center"
     :padding "16px"
     :justify-items "center"}]
   [:img {:animation "5s linear spin infinite"
          :max-width "25vw"}]))

(defn pics [invert]
  (let [style {:filter (when invert "invert(1)")}]
    [[:img#v1 {:src "https://upload.wikimedia.org/wikipedia/commons/f/fc/Swamp_Thing_4.jpg"
               :style style}]
     [:img#v2 {:src "https://upload.wikimedia.org/wikipedia/commons/7/73/Space_Aliens_Grill_%26_Bar_Alien_Statues_Fargo_ND_2022.jpg"
               :style style}]
     [:img#v3 {:src "https://upload.wikimedia.org/wikipedia/commons/4/44/Dobermann_dog._%E2%80%9CCanis_lupus_familiaris%E2%80%9D.jpg"
               :style style}]
     [:img#v4 {:src "https://upload.wikimedia.org/wikipedia/commons/5/5c/Pot-bellied_pigs_in_Lisbon_Zoo_2008.jpg"
               :style style}]]))

(def cycler
  (let [pos* (atom 0)]
    (fn [xs]
      (take (count xs)
            (drop (swap! pos* inc)
                  (cycle xs))))))

(comment (cycler [1 2 3]))

(defn pictures [invert]
  (into [:div#pictures] (cycler (pics invert))))

(def invert* (atom true))

(defn home [_req]
  (->
   (ruresp/response
    (h/html
     (hc/compile
      [:html {:lang "en"}
       [:head
        [:title "Cool Pictures"]
        [:script {:src "https://unpkg.com/@tailwindcss/browser@4"}]
        [:script {:type "module"
                  :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.0-beta.11/bundles/datastar.js"}]
        [:style css]]
       [:body
        (pictures (swap! invert* not))
        [:div {:style {:display "flex"
                       :align-items "center"
                       :justify-content "center"
                       :margin-top "4em"}}
         [:button {:data-on-click "@post('/shuffle')"}
          "Shuffle"]]]])))
   (ruresp/content-type "text/html")))

(def ^:private bufSize 1024)
(def read-json (charred/parse-json-fn {:async? false :bufsize bufSize}))

(defn shuffler [request]
  (d*.httpkit/->sse-response
   request
   {d*.httpkit/on-open
    (fn [sse]
      (d*/with-open-sse sse
        (let [html (h/html (pictures (swap! invert* not)))]
          (prn "html:" html)
          (d*/merge-fragment! sse html))))}))

(def routes
  [["/" {:handler home}]
   ["/shuffle" {:handler shuffler}]])

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

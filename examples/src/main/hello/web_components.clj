(ns hello.web-components
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


(defn home [_req]
  (->
   (ruresp/response
    (h/html
     (hc/compile
      [:html {:lang "en"}
       [:head
        [:title "Cool Videos"]
        [:script {:src "https://unpkg.com/@tailwindcss/browser@4"}]
        [:script {:type "module"
                  :src "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.0-beta.11/bundles/datastar.js"}]
        [:script {:src "https://unpkg.com/@fluentui/web-components" :type "module"}]
        [:link {:rel "stylesheet"
                :href "https://cdn.jsdelivr.net/npm/@shoelace-style/shoelace@2.20.1/cdn/themes/light.css"}]
        [:script {:type "module"
                  :src "https://cdn.jsdelivr.net/npm/@shoelace-style/shoelace@2.20.1/cdn/shoelace.js"}]
        [:style css]]
       [:body
        [:sl-relative-time {:date "2020-07-15T09:17:00-04:00"}]
        ]
       #_[:body
          [:h4 "Multi-expand"]
          [:fluent-accordion {:style "max-width: 350px;"}
           [:fluent-accordion-item  
            [:span {:slot "heading"} "Item 1"]
            [:div {:class "panel"} "Panel one content"]]
           [:fluent-accordion-item  
            [:span {:slot "heading"} "Item 2"]
            [:div {:class "panel"} "Panel 2 content"]]
           [:fluent-accordion-item  
            [:span {:slot "heading"} "Item 3"]
            [:div {:class "panel"} "Panel 3 content"]]]
          [:h4 "Single-expand"]
          [:fluent-accordion {:expand-mode "single"}
           [:fluent-accordion-item  
            [:span {:slot "heading"} "Item 1"]
            [:div {:class "panel"} "Panel one content"]]
           [:fluent-accordion-item  
            [:span {:slot "heading"} "Item 2"]
            [:div {:class "panel"} "Panel 2 content"]]
           [:fluent-accordion-item  
            [:span {:slot "heading"} "Item 3"]
            [:div {:class "panel"} "Panel 3 content"]]
           [:fluent-accordion-item  
            [:span {:slot "heading"} "Item 4"]
            [:div {:class "panel"} "Panel 4 content"]]]
          ]])))
   (ruresp/content-type "text/html")))

(def ^:private bufSize 1024)
(def read-json (charred/parse-json-fn {:async? false :bufsize bufSize}))

(def routes
  [["/" {:handler home}]])

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

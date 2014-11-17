(ns taxis.server
  (:import (java.util Date))
  (:use [org.httpkit.server :only [run-server]])
  (:require [ring.middleware.reload :as reload]
            [compojure.handler :refer [site]]
            [compojure.core :as core :refer [GET POST defroutes]]
            [compojure.route :as route :refer [files not-found]]
            [chord.http-kit :refer [wrap-websocket-handler]]
            [clojure.core.async :refer [<! >! chan go-loop put!]]))

(def clients (atom {}))

(defn- store-channel [id ch type]
  (when-not (contains? (get @clients type) id)
    (swap! clients assoc-in [type id] ch)))

(defn- send-message [msg dest src]
  (if-let [tx-c (get-in @clients [:taxi dest])]
    (put! tx-c msg)
    (if-let [ps-c (get-in @clients [:pass dest])]
      (put! ps-c msg)
      (put! src {:error "Destination ws not found on server"}))))

(defn- broadcast-message [msg]
  (doseq [[_ ch] (:pass @clients)]
    (put! ch msg)))

(defn- handle-message [ws-ch {:keys [type src] :as msg}]
  (prn (str "Clients: " @clients))
  (if (or (nil? type) (nil? src))
    (prn (str "Got poorly formatted message: " msg))
    (let [{:keys [data dest]} msg]
      (store-channel src ws-ch type)
      (if (nil? data)
        (prn (str "Got poorly formatted message: " msg))
        (if dest
          (send-message data dest ws-ch)
          (broadcast-message data))))))

(defn ws-handler [{:keys [ws-channel]}]
  (go-loop []
           (when-let [{:keys [message error]} (<! ws-channel)]
             (if error
               (prn (format "Error: '%s'" (pr-str error)))
               (do
                 (prn (format "Received: '%s' at %s." (pr-str message) (Date.)))
                 (handle-message ws-channel message)))
             (recur))))

(defroutes all-routes
           (GET "/ws" [] (-> ws-handler
                             (wrap-websocket-handler)))
           (POST "/oauth/:token" [token] (str "Hello World: " token))
           (files "/" {:root "."})
           (not-found "<h1><p>Page not found</p></h1>"))

(defn -main [& args]
  (let [handler (reload/wrap-reload (site #'all-routes))]
    (run-server handler {:port 8080})))
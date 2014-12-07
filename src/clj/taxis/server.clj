(ns taxis.server
  (:import (java.util Date))
  (:use [org.httpkit.server :only [run-server]])
  (:require [environ.core :refer [env]]
            [cemerick.piggieback :as piggieback]
            [weasel.repl.websocket :as weasel]
            [ring.middleware.reload :as reload]
            [compojure.handler :refer [site]]
            [compojure.core :as core :refer [GET POST defroutes]]
            [compojure.route :as route :refer [files not-found]]
            [chord.http-kit :refer [wrap-websocket-handler]]
            [clojure.core.async :refer [<! >! chan go-loop put!]]
            [clojure.set :refer [map-invert]]
            [taxis.database :as db])
  (:gen-class :main true))

(def is-dev? (env :is-dev))

(def clients (atom {}))

(defn- client-registration [email]
  (if (db/get-user-by-email email)
    (let [type (db/get-type-of-user email)]
      (cond
        (= type "passenger") :pass
        (= type "taxi") :taxi))
    :unregistered))

(defn- store-channel [id ch type]
  (when-not (contains? (get @clients type) id)
    (swap! clients assoc-in [type id] ch)))

(defn- register-user
  [email type ans-ch]
  (println (str "Creating user with email: " email ", type: " type))
  (if (db/save-user email type)
    (put! ans-ch :ok)
    (put! ans-ch {:error "Couldn't create user"})))

(defn- remove-channel [channel]
  (prn (str "Connected clients: " @clients))
  (let [taxis  (map-invert (:taxi @clients))
        pass   (map-invert (:pass @clients))
        rvs-t  (map-invert (dissoc taxis channel))
        rvs-p  (map-invert (dissoc pass channel))]
    (swap! clients #(assoc % :taxi rvs-t :pass rvs-p))
    (prn "Client left.")
    (prn (str "Connected clients: " @clients))))

(defn- send-message [msg dest src-channel src-email]
  (if (= msg :registered?)
    (put! src-channel {:registered (client-registration src-email)})
    (if-let [tx-c (get-in @clients [:taxi dest])]
      (put! tx-c msg)
      (if-let [ps-c (get-in @clients [:pass dest])]
        (put! ps-c msg)
        (put! src-channel {:error "Destination ws not found on server"})))))

(defn- broadcast-message [msg]
  (doseq [[_ ch] (:pass @clients)]
    (put! ch msg)))

(defn- handle-message [ws-ch {:keys [type src] :as msg}]
  (if (or (nil? type) (nil? src))
    (prn (str "Got poorly formatted message: " msg))
    (let [{:keys [data dest]} msg]
      (when-not (= data :registered?)
        (store-channel src ws-ch type))
      (if (nil? data)
        (prn (str "Got poorly formatted message: " msg))
        (if (= data :register)
          (register-user src type ws-ch)
          (if dest
            (send-message data dest ws-ch src)
            (broadcast-message data)))))))

(defn ws-handler [{:keys [ws-channel]}]
  (go-loop []
           (if-let [{:keys [message error]} (<! ws-channel)]
             (do
               (if error
                 (prn (format "Error: '%s'" (pr-str error)))
                 (do
                   (prn (format "Received: '%s' at %s." (pr-str message) (Date.)))
                   (handle-message ws-channel message)))
               (recur))
             (remove-channel ws-channel))))

(defroutes all-routes
           (GET "/ws" [] (-> ws-handler
                             (wrap-websocket-handler)))
           (POST "/oauth/:token" [token] (str "Hello World: " token))
           (files "/" {:root "."})
           (not-found "<h1><p>Page not found</p></h1>"))

(def http-handler
  (if is-dev?
    (reload/wrap-reload (site #'all-routes))
    (site all-routes)))

(defn -main [& [port]]
  (db/migrate)
  (let [port (Integer. (or port (env :port) 5000))]
    (run-server http-handler {:port port})))

(defn browser-repl []
  (piggieback/cljs-repl :repl-env (weasel/repl-env :ip "0.0.0.0" :port 9001)))
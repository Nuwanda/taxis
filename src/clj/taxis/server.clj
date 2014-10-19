(ns taxis.server
  (:use [org.httpkit.server :only [run-server]])
  (:require [ring.middleware.reload :as reload]
            [compojure.handler :refer [site]]
            [compojure.core :as core :refer [GET defroutes]]
            [compojure.route :as route :refer [files not-found]]
            [clj-json.core :as json]))

(defn json-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string data)})

(defroutes all-routes
           (GET "/tests" [] (json-response "Hello World"))
           (GET "/tests/:id" [id] (json-response (str "Hello World: " id)))
           (files "/" {:root "."})
           (not-found "<p>Page not found</p>"))

(defn -main [& args]
  (let [handler (reload/wrap-reload (site #'all-routes))]
    (run-server handler {:port 8080})))
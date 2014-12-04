(ns taxis.database
  (:import (java.net URI))
  (:use [korma.db])
  (:use [korma.core])
  (:require [environ.core :refer [env]]
            [clojure.java.jdbc.deprecated :as sql]
            [clojure.java.jdbc :as new-sql]
            [clojure.string :as string]))

(def is-dev? (env :is-dev))

(def dev-db (postgres {:db "taxis"
                         :user "nuwanda"
                         :password ""}))

(def prod-db
  (let [db-uri (URI. (System/getenv "DATABASE_URL"))]
    (->> (string/split (.getUserInfo db-uri) #":")
         (#(identity {:db (last (string/split (System/getenv "DATABASE_URL") #"\/"))
                      :host (.getHost db-uri)
                      :port (.getPort db-uri)
                      :user (% 0)
                      :password (% 1)
                      :ssl true
                      :sslfactory "org.postgresql.ssl.NonValidatingFactory"}))
         (postgres))))

(def db-url (or (env :database-url)
                "postgresql://localhost:5432/taxis"))

(defentity roles)
(defentity users
           (belongs-to roles))

(defn- populate-db
  "Populate roles table"
  []
  (transaction
    (insert roles
            (values [{:name "taxi"}
                     {:name "passenger"}]))
    (insert users
            (values [{:email "taxi@gmail.com" :roles_id 1}
                     {:email "pass@gmail.com" :roles_id 2}]))))

(defn migrated? []
  (-> (new-sql/query
        db-url
        [(str "select count(*) from information_schema.tables "
              "where table_name='users'")])
      first :count pos?))

(defn- create-tables
  "Create a users and roles table"
  []
  (do
    (sql/create-table
      "roles"
      [:id "SERIAL" "PRIMARY KEY"]
      [:name "TEXT" "NOT NULL"])
    (sql/create-table
      "users"
      [:id "SERIAL" "PRIMARY KEY"]
      [:email "TEXT" "NOT NULL"]
      [:roles_id "SERIAL" "REFERENCES roles(id)"])
    (sql/do-commands "CREATE INDEX USERIDX ON users(email)")))

(defn- invoke-with-connection [f]
  (sql/with-connection
    db-url
    (sql/transaction
      (f))))

(defn migrate
  []
  (if is-dev?
    (defdb db dev-db)
    (defdb db prod-db))
  (if-not (migrated?)
    (do
      (println "Creating DB: ")
      (invoke-with-connection create-tables)
      (populate-db))
    (println "DB already created")))
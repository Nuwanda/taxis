(ns taxis.database
  (:use [korma.db])
  (:use [korma.core])
  (:require [environ.core :refer [env]]
            [clojure.java.jdbc.deprecated :as sql]
            [clojure.java.jdbc :as new-sql]))

(defdb dev-db (postgres {:db "taxis"
                         :user "nuwanda"
                         :password ""}))

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
  (if-not (migrated?)
    (do
      (println "Creating DB: ")
      (invoke-with-connection create-tables)
      (populate-db))
    (println "DB already created")))
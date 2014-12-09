(ns taxis.database
  (:import (java.net URI))
  (:use [korma.db])
  (:use [korma.core])
  (:require [environ.core :refer [env]]
            [clojure.java.jdbc.deprecated :as sql]
            [clojure.java.jdbc :as new-sql]
            [clojure.string :as string]))

(def is-dev? (env :is-dev))

(def db-url (or (env :database-url)
                "postgresql://localhost:5432/taxis"))

(def dev-db (postgres {:db "taxis"
                         :user "nuwanda"
                         :password ""}))

(defn prod-db
  []
  (let [db-uri (URI. (env :database-url))]
    (->> (string/split (.getUserInfo db-uri) #":")
         (#(identity {:db (last (string/split (System/getenv "DATABASE_URL") #"\/"))
                      :host (.getHost db-uri)
                      :port (.getPort db-uri)
                      :user (% 0)
                      :password (% 1)
                      :ssl true
                      :sslfactory "org.postgresql.ssl.NonValidatingFactory"}))
         (postgres))))

(defentity roles)
(defentity users
           (belongs-to roles))
(defentity rides
           (belongs-to users))
(defentity joinedrides
           (belongs-to users)
           (belongs-to rides))

(defn- populate-db
  "Populate roles table"
  []
  (transaction
    (insert roles
            (values [{:name "taxi"}
                     {:name "passenger"}]))
    (insert users
            (values [{:email "taxi@gmail.com" :roles_id 1 :rating 0 :numvotes 0}
                     {:email "pass@gmail.com" :roles_id 2 :rating 0 :numvotes 0}]))))

(defn get-user-by-email
  "Return a user id for the given email"
  [email]
  (if-let [user (seq (select users
                        (where {:email email})
                        (fields :id)))]
    (-> user first :id)
    nil))

(defn get-type-of-user
  [email]
  (if-let [role (seq (select users
                             (where {:email email})
                             (with roles)
                             (fields [:roles.name :role])))]
    (-> role first :role)
    nil))

(defn get-joined-rides
  [email]
  (let [user (get-user-by-email email)]
    (seq (select joinedrides
            (with rides
                  (with users
                        (fields :email :rating :numvotes)))
            (where {:users_id user})))))

(defn get-past-rides
  [email]
  (let [user (get-user-by-email email)]
    (seq (select joinedrides
                 (with rides
                       (with users
                             (fields :email :rating :numvotes)))
                 (where (and {:users_id user}
                             {:rated false}
                             (raw "rides.date < CURRENT_DATE")))))))

(defn get-rides-of-user
  [email]
  (let [user (get-user-by-email email)]
    (seq (select rides
                      (where {:users_id user})))))

(defn get-all-rides
  "Get all 'joinable' rides and driver info"
  [user]
  (let [user (get-user-by-email user)]
    (seq (select rides
                 (with users
                       (fields :email :rating :numvotes))
                 (where (and
                          {:driving  true}
                          {:seats    [> 0]}
                          {:users_id [not= user]}
                          {:id       [not-in (subselect joinedrides
                                                        (where {:users_id user})
                                                        (fields :rides_id))]}
                          (or {:recurrent true}
                              (raw "rides.date > CURRENT_DATE"))))))))

(defn user-join-ride
  [user ride]
  (let [user (get-user-by-email user)]
    (transaction
      (insert joinedrides
              (values {:users_id user
                       :rides_id ride
                       :rated false}))
      (update rides
              (set-fields {:seats (raw "seats - 1")})
              (where {:id ride})))))

(defn user-leave-ride
  [email ride]
  (let [user (get-user-by-email email)]
    (delete joinedrides
            (where (and {:users_id user}
                        {:rides_id ride})))))

(defn delete-ride
  [ride]
  (transaction
    (delete joinedrides
            (where {:rides_id ride}))
    (delete rides
            (where {:id ride}))))

(defn rate-ride
  [ride rating]
  (if-let [drivers (seq (select users
                           (where {:id [in (subselect rides
                                                      (fields :users_id)
                                                      (where {:id [in (subselect joinedrides
                                                                                 (fields :rides_id)
                                                                                 (where {:id ride}))]}))]})))]
    (let [driver (first drivers)
          new-rating (/ (+ (* (:rating driver)
                              (:numvotes driver))
                           rating)
                        (+ (:numvotes driver) 1))]
      (transaction
        (update users
                (where {:id (:id driver)})
                (set-fields {:rating new-rating
                             :numvotes (raw "numvotes + 1")}))
        (update joinedrides
                (where {:id ride})
                (set-fields {:rated true}))))
    nil))

(defn save-ride
  "Save a ride to the database"
  [user ride]
  (let [user_id (get-user-by-email user)]
    (insert rides
            (values {:users_id    user_id
                     :origin      (:origin ride)
                     :destination (:destination ride)
                     :driving     (:driving ride )
                     :date        (raw (str "to_date(" "'" (:date ride) "'" ",'DD Month YYYY')"))
                     :time        (raw (str "to_timestamp(" "'" (:time ride) "'" ",'HH12:MI AM')"))
                     :recurrent   (:recurrent ride)
                     :monday      (:monday ride)
                     :tuesday     (:tuesday ride)
                     :wednesday   (:wednesday ride)
                     :thursday    (:thursday ride)
                     :friday      (:friday ride)
                     :saturday    (:saturday ride)
                     :sunday      (:sunday ride)
                     :cash        (:cash ride)
                     :seats       (:seats ride)
                     :price       (:price ride)
                     :notes       (:notes ride)}))))

(defn update-ride
  [user ride]
  1)

(defn save-user
  "Save a user to the database"
  [email type]
  (cond
    (= type :taxi) (insert users
                           (values [{:email email :roles_id 1 :rating 0 :numvotes 0}]))
    (= type :pass) (insert users
                           (values [{:email email :roles_id 2 :rating 0 :numvotes 0}]))
    :else          nil))

(defn- migrated? []
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
      [:rating "NUMERIC" "NOT NULL"]
      [:numvotes "NUMERIC" "NOT NULL"]
      [:roles_id "SERIAL" "REFERENCES roles(id)"])
    (sql/create-table
      "rides"
      [:id "SERIAL" "PRIMARY KEY"]
      [:users_id "SERIAL" "REFERENCES users(id)"]
      [:origin "TEXT" "NOT NULL"]
      [:destination "TEXT" "NOT NULL"]
      [:driving "BOOLEAN" "NOT NULL"]
      [:date "DATE"]
      [:time "TIME" "NOT NULL"]
      [:recurrent "BOOLEAN" "NOT NULL"]
      [:monday "BOOLEAN"]
      [:tuesday "BOOLEAN"]
      [:wednesday "BOOLEAN"]
      [:thursday "BOOLEAN"]
      [:friday "BOOLEAN"]
      [:saturday "BOOLEAN"]
      [:sunday "BOOLEAN"]
      [:cash "BOOLEAN"]
      [:seats "SMALLINT"]
      [:price "NUMERIC"]
      [:notes "TEXT"])
    (sql/create-table
      "joinedrides"
      [:id "SERIAL" "PRIMARY KEY"]
      [:users_id "SERIAL" "REFERENCES users(id)"]
      [:rides_id "SERIAL" "REFERENCES rides(id)"]
      [:rated "BOOLEAN" "NOT NULL"])
    (sql/do-commands "CREATE INDEX USERIDX ON users(email)")
    (sql/do-commands "CREATE INDEX ORIGIDX ON rides(origin)")
    (sql/do-commands "CREATE INDEX DESTIDX ON rides(destination)")))

(defn- invoke-with-connection [f]
  (sql/with-connection
    db-url
    (sql/transaction
      (f))))

(defn migrate
  []
  (if is-dev?
    (defdb db dev-db)
    (defdb db (prod-db)))
  (if-not (migrated?)
    (do
      (println "Creating DB: ")
      (invoke-with-connection create-tables)
      (populate-db))
    (println "DB already created")))
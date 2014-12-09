(ns taxis.rides.list
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [put! <! close!]]
            [taxis.utils :as util]
            [chord.client :refer [ws-ch]]
            [secretary.core :as secretary]))

(defn- receive-rides
  [owner]
  (go
    (let [srv-ch (om/get-state owner :server-chan)
          msg    (<! srv-ch)]
      (if-let [rides (:message msg)]
        (om/set-state! owner :rides rides)
        (js/alert "Error getting rides from server")))))

(defn- format-time
  [time]
  (let [time (js/Date. time)]
    (str (.getHours time) ":" (.getMinutes time))))

(defn- format-date
  [date]
  (let [months ["January" "February" "March" "April" "May" "June"
                "July" "August" "October" "November" "December"]
        date   (js/Date. date)]
    (str (.getDate date) " " (get months (- (.getMonth date) 1)) ", " (.getFullYear date))))

(defn- edit-ride
  [data owner]
  (put! (om/get-shared owner :edit-chan) data))

(defn- delete-ride
  [data owner]
  (put! (om/get-shared owner :del-chan) (:id data)))

(defcomponent my-ride
              [data owner]
              (render [_]
                      (dom/tr
                        (dom/td (:origin data))
                        (dom/td (:destination data))
                        (dom/td (format-date (:date data)))
                        (dom/td (format-time (:time data)))
                        (dom/td (dom/span {:class "glyphicon glyphicon-ok"
                                           :style {:display (util/display
                                                              (:cash data))}})
                                (dom/span {:class "glyphicon glyphicon-remove"
                                           :style {:display (util/display
                                                              (not (:cash data)))}}))
                        (dom/td (:seats data))
                        (dom/td (str "€" (:price data)))
                        (dom/td (:notes data))
                        (dom/td (dom/div {:class "btn-group"}
                                         (dom/button {:class    "btn btn-default"
                                                      :on-click #(edit-ride data owner)}
                                                     (dom/span {:class "glyphicon glyphicon-pencil"}))
                                         (dom/button {:class    "btn btn-default"
                                                      :on-click #(delete-ride data owner)}
                                                     (dom/span {:class "glyphicon glyphicon-trash"})))))))

(defn- join-ride
  [id]
  (secretary/dispatch! (str "/ride/join/" id)))

(defcomponent ride
              [data owner]
              (render [_]
                      (dom/tr
                        (dom/td (:email data))
                        (dom/td (if (> (:numvotes data) 0)
                                  (str (:rating data) "/5")
                                  "N/A"))
                        (dom/td (:origin data))
                        (dom/td (:destination data))
                        (dom/td (format-date (:date data)))
                        (dom/td (format-time (:time data)))
                        (dom/td (dom/span {:class "glyphicon glyphicon-ok"
                                           :style {:display (util/display
                                                              (:cash data))}})
                                (dom/span {:class "glyphicon glyphicon-remove"
                                           :style {:display (util/display
                                                              (not (:cash data)))}}))
                        (dom/td (:seats data))
                        (dom/td (str "€" (:price data)))
                        (dom/td (:notes data))
                        (dom/td (dom/button {:class "btn btn-primary"
                                             :on-click #(join-ride (:id data))}
                                            "Join Ride")))))

(defn- rate-ride
  [id owner]
  (let [r-ch (om/get-shared owner :rate-chan)
        rating (om/get-state owner :rating)]
    (put! r-ch {:id id :rating rating})
    (om/set-state! owner :modal? false)))

(defcomponent past-ride
              [data owner]
              (init-state [_]
                          {:modal? false
                           :rating 5})
              (render-state [_ {:keys [modal? rating]}]
                            (dom/tr
                              (dom/td (:email data))
                              (dom/td (if (> (:numvotes data) 0)
                                        (str (:rating data) "/5")
                                        "N/A"))
                              (dom/td (:origin data))
                              (dom/td (:destination data))
                              (dom/td (format-date (:date data)))
                              (dom/td (format-time (:time data)))
                              (dom/td (dom/div
                                        (dom/button {:class       "btn btn-primary"
                                                     :on-click    #(om/set-state! owner :modal? true)}
                                                    "Rate Ride")
                                        (dom/div {:class "modal"
                                                  :style {:display (util/display modal?)}}
                                                 (dom/div {:class "modal-dialog"}
                                                          (dom/div {:class "modal-content"}
                                                                   (dom/div {:class "modal-header"}
                                                                            (dom/button {:class       "close"
                                                                                         :aria-hidden "true"
                                                                                         :on-click    #(om/set-state! owner :modal? false)}
                                                                                        (dom/span {:class "glyphicon glyphicon-remove"}))
                                                                            (dom/h3 "Please rate your satisfaction with the ride"))
                                                                   (dom/div {:class "modal-body"}
                                                                            (dom/div {:class "btn-group"
                                                                                      :style {:text-align "center"}}
                                                                                     (dom/button {:class    (str "btn btn-default" (util/active-rating 1 rating))
                                                                                                  :on-click #(om/set-state! owner :rating 1)}
                                                                                                 (dom/span {:class "glyphicon glyphicon-star"}))
                                                                                     (dom/button {:class    (str "btn btn-default" (util/active-rating 2 rating))
                                                                                                  :on-click #(om/set-state! owner :rating 2)}
                                                                                                 "2"(dom/span {:class "glyphicon glyphicon-star"}))
                                                                                     (dom/button {:class    (str "btn btn-default" (util/active-rating 3 rating))
                                                                                                  :on-click #(om/set-state! owner :rating 3)}
                                                                                                 "3"(dom/span {:class "glyphicon glyphicon-star"}))
                                                                                     (dom/button {:class    (str "btn btn-default" (util/active-rating 4 rating))
                                                                                                  :on-click #(om/set-state! owner :rating 4)}
                                                                                                 "4" (dom/span {:class "glyphicon glyphicon-star"}))
                                                                                     (dom/button {:class    (str "btn btn-default" (util/active-rating 5 rating))
                                                                                                  :on-click #(om/set-state! owner :rating 5)}
                                                                                                 "5" (dom/span {:class "glyphicon glyphicon-star"}))))
                                                                   (dom/div {:class "modal-footer"}
                                                                            (dom/button {:class    "btn btn-primary"
                                                                                         :on-click #(rate-ride (:id data) owner)} "Rate"))))))))))

(defn- leave-ride
  [id owner]
  (put! (om/get-shared owner :rate-chan) id))

(defcomponent joined-ride
              [data owner]
              (render [_]
                      (dom/tr
                        (dom/td (:email data))
                        (dom/td (if (> (:numvotes data) 0)
                                  (str (:rating data) "/5")
                                  "N/A"))
                        (dom/td (:origin data))
                        (dom/td (:destination data))
                        (dom/td (format-date (:date data)))
                        (dom/td (format-time (:time data)))
                        (dom/td (dom/span {:class "glyphicon glyphicon-ok"
                                           :style {:display (util/display
                                                              (:cash data))}})
                                (dom/span {:class "glyphicon glyphicon-remove"
                                           :style {:display (util/display
                                                              (not (:cash data)))}}))
                        (dom/td (:seats data))
                        (dom/td (str "€" (:price data)))
                        (dom/td (:notes data))
                        (dom/td (dom/button {:class "btn btn-primary"
                                             :on-click #(leave-ride (:rides_id data) owner)}
                                            "Leave Ride")))))

(defn- filter-rides
  [data owner location event]
  (let [value (.. event -target -value)
        pred  #(not= -1 (.indexOf (location %) value))]
    (om/set-state! owner :rides (seq (filter pred data)))))

(defcomponent all-rides-table
              [data owner]
              (init-state [_]
                          {:rides nil})
              (will-mount [_]
                          (om/set-state! owner :rides data))
              (render-state [_ {:keys [rides]}]
                            (dom/div {:class "row"}
                                     (dom/div {:class "col-md-10 col-md-offset-1"}
                                              (dom/h3 "Filter rides")
                                              (dom/label "Origin: ")
                                              (dom/input {:type        "text"
                                                          :placeholder "Origin"
                                                          :on-change   #(filter-rides data owner  :origin %)})
                                              (dom/label "Destination: ")
                                              (dom/input {:type        "text"
                                                          :placeholder "Destination"
                                                          :on-change   #(filter-rides data owner :destination %)})
                                              (dom/br)
                                              (dom/br)
                                              (dom/table {:class "table table-bordered table-hover"}
                                                         (dom/thead
                                                           (dom/tr
                                                             (dom/th "Driver")
                                                             (dom/th "Driver Rating")
                                                             (dom/th "Origin")
                                                             (dom/th "Destination")
                                                             (dom/th "Date")
                                                             (dom/th "Time")
                                                             (dom/th "Cash payment only?")
                                                             (dom/th "Seats Available")
                                                             (dom/th "Price asked")
                                                             (dom/th "Notes")
                                                             (dom/th "")))
                                                         (dom/tbody
                                                           (for [ride-data rides]
                                                             (om/build ride ride-data))))))))

(defcomponent my-rides-table
              [data owner]
              (init-state [_]
                          {:rides nil})
              (will-mount [_]
                          (om/set-state! owner :rides data))
              (render-state [_ {:keys [rides]}]
                            (dom/div {:class "row"}
                                     (dom/div {:class "col-md-10 col-md-offset-1"}
                                              (dom/h3 "Filter your rides")
                                              (dom/label "Origin: ")
                                              (dom/input {:type        "text"
                                                          :placeholder "Origin"
                                                          :on-change   #(filter-rides data owner  :origin %)})
                                              (dom/label "Destination: ")
                                              (dom/input {:type        "text"
                                                          :placeholder "Destination"
                                                          :on-change   #(filter-rides data owner :destination %)})
                                              (dom/br)
                                              (dom/br)
                                              (dom/table {:class "table table-bordered table-hover"}
                                                         (dom/thead
                                                           (dom/tr
                                                             (dom/th "Origin")
                                                             (dom/th "Destination")
                                                             (dom/th "Date")
                                                             (dom/th "Time")
                                                             (dom/th "Cash payment only?")
                                                             (dom/th "Seats Available")
                                                             (dom/th "Price asked")
                                                             (dom/th "Notes")
                                                             (dom/th "Edit/Delete Ride")))
                                                         (dom/tbody
                                                           (for [ride-data rides]
                                                             (om/build my-ride ride-data))))))))

(defcomponent joined-rides-table
              [data owner]
              (init-state [_]
                          {:rides nil})
              (will-mount [_]
                          (om/set-state! owner :rides data))
              (render-state [_ {:keys [rides]}]
                            (dom/div {:class "row"}
                                     (dom/div {:class "col-md-10 col-md-offset-1"}
                                              (dom/h3 "Filter rides")
                                              (dom/label "Origin: ")
                                              (dom/input {:type        "text"
                                                          :placeholder "Origin"
                                                          :on-change   #(filter-rides data owner  :origin %)})
                                              (dom/label "Destination: ")
                                              (dom/input {:type        "text"
                                                          :placeholder "Destination"
                                                          :on-change   #(filter-rides data owner :destination %)})
                                              (dom/br)
                                              (dom/br)
                                              (dom/table {:class "table table-bordered table-hover"}
                                                         (dom/thead
                                                           (dom/tr
                                                             (dom/th "Driver")
                                                             (dom/th "Driver Rating")
                                                             (dom/th "Origin")
                                                             (dom/th "Destination")
                                                             (dom/th "Date")
                                                             (dom/th "Time")
                                                             (dom/th "Cash payment only?")
                                                             (dom/th "Seats Available")
                                                             (dom/th "Price asked")
                                                             (dom/th "Notes")
                                                             (dom/th "")))
                                                         (dom/tbody
                                                           (for [ride-data rides]
                                                             (om/build joined-ride ride-data))))))))

(defcomponent past-rides-table
              [data owner]
              (init-state [_]
                          {:rides nil})
              (will-mount [_]
                          (om/set-state! owner :rides data))
              (render-state [_ {:keys [rides]}]
                            (dom/div {:class "row"}
                                     (dom/div {:class "col-md-10 col-md-offset-1"}
                                              (dom/h3 "Filter your rides")
                                              (dom/label "Origin: ")
                                              (dom/input {:type        "text"
                                                          :placeholder "Origin"
                                                          :on-change   #(filter-rides data owner  :origin %)})
                                              (dom/label "Destination: ")
                                              (dom/input {:type        "text"
                                                          :placeholder "Destination"
                                                          :on-change   #(filter-rides data owner :destination %)})
                                              (dom/br)
                                              (dom/br)
                                              (dom/table {:class "table table-bordered table-hover"}
                                                         (dom/thead
                                                           (dom/tr
                                                             (dom/th "Driver")
                                                             (dom/th "Driver Rating")
                                                             (dom/th "Origin")
                                                             (dom/th "Destination")
                                                             (dom/th "Date")
                                                             (dom/th "Time")
                                                             (dom/th "")))
                                                         (dom/tbody
                                                           (for [ride-data rides]
                                                             (om/build past-ride ride-data))))))))

(defn- unserialize-ride
  "Prepare ride info for database serialization"
  [data ride]
  (om/transact! data #(assoc-in % [:ride :id] (:id ride)))
  (om/transact! data #(assoc-in % [:ride :first-step :origin :locality] (:origin ride)))
  (om/transact! data #(assoc-in % [:ride :first-step :destination :locality] (:destination ride)))
  (om/transact! data #(assoc-in % [:ride :first-step :driving?] (:driving ride)))
  (om/transact! data #(assoc-in % [:ride :second-step :date] (:date ride)))
  (om/transact! data #(assoc-in % [:ride :second-step :time] (:time ride)))
  (om/transact! data #(assoc-in % [:ride :second-step :recurrent?] (:recurrent ride)))
  (om/transact! data #(assoc-in % [:ride :second-step :weekdays :monday] (:monday ride)))
  (om/transact! data #(assoc-in % [:ride :second-step :weekdays :tuesday] (:tuesday ride)))
  (om/transact! data #(assoc-in % [:ride :second-step :weekdays :wednesday] (:wednesday ride)))
  (om/transact! data #(assoc-in % [:ride :second-step :weekdays :thursday] (:thursday ride)))
  (om/transact! data #(assoc-in % [:ride :second-step :weekdays :friday] (:friday ride)))
  (om/transact! data #(assoc-in % [:ride :second-step :saturday] (:saturday ride)))
  (om/transact! data #(assoc-in % [:ride :second-step :sunday] (:sunday ride)))
  (om/transact! data #(assoc-in % [:ride :third-step :cash?] (:cash ride)))
  (om/transact! data #(assoc-in % [:ride :third-step :seats] (:seats ride)))
  (om/transact! data #(assoc-in % [:ride :third-step :price] (:price ride)))
  (om/transact! data #(assoc-in % [:ride :third-step :notes] (:notes ride))))

(defn- wait-for-edit
  [data owner]
  (go
    (let [e-ch (om/get-shared owner :edit-chan)]
      (when-let [ride (<! e-ch)]
        (unserialize-ride data ride)
        (secretary/dispatch! "/ride/edit")))))

(defn- wait-for-delete
  [data owner]
  (go
    (let [d-ch (om/get-shared owner :del-chan)
          s-ch (om/get-state owner :server-chan)]
      (when-let [id (<! d-ch)]
        (put! s-ch {:src  (:logged @data)
                    :type :placeholder
                    :data {:del-ride id}
                    :dest (:logged @data)})
        (let [answer (<! s-ch)]
          (if (= (:message answer) :ok)
            (secretary/dispatch! "/ride/list/mine")
            (.log js/console "Error deleting ride")))))))

(defn- wait-for-leave
  [data owner]
  (go
    (let [d-ch (om/get-shared owner :leave-chan)
          s-ch (om/get-state owner :server-chan)]
      (when-let [id (<! d-ch)]
        (put! s-ch {:src  (:logged @data)
                    :type :placeholder
                    :data {:leave-ride {:id id}}
                    :dest (:logged @data)})
        (let [answer (<! s-ch)]
          (if (= (:message answer) :ok)
            (secretary/dispatch! "/ride/list/joined")
            (.log js/console "Error leaving ride")))))))

(defn- wait-for-rate
  [data owner]
  (go
    (let [d-ch (om/get-shared owner :rate-chan)
          s-ch (om/get-state owner :server-chan)]
      (when-let [{:keys [id rating]} (<! d-ch)]
        (put! s-ch {:src  (:logged @data)
                    :type :placeholder
                    :data {:rate-ride {:id id :rating rating}}
                    :dest (:logged @data)})
        (let [answer (<! s-ch)]
          (if (= (:message answer) :ok)
            (secretary/dispatch! "/ride/list/past")
            (.log js/console "Error rating ride")))))))

(defcomponent get-rides
              [data owner {:keys [type]}]
              (init-state [_]
                          {:rides       nil
                           :server-chan nil})
              (will-mount [_]
                          (go
                            (let [{:keys [ws-channel error]} (<! (ws-ch "ws://localhost:5000/ws"))]
                              (if error
                                (js/alert "Error connecting server")
                                (do
                                  (om/set-state! owner :server-chan ws-channel)
                                  (receive-rides owner)
                                  (wait-for-edit data owner)
                                  (wait-for-delete data owner)
                                  (wait-for-leave data owner)
                                  (wait-for-rate data owner)
                                  (cond
                                    (= type :all)    (put! ws-channel {:src  (:logged @data)
                                                                       :type :placeholder
                                                                       :data :all-rides
                                                                       :dest (:logged @data)})
                                    (= type :mine)   (put! ws-channel {:src  (:logged @data)
                                                                       :type :placeholder
                                                                       :data :get-rides
                                                                       :dest (:logged @data)})
                                    (= type :joined) (put! ws-channel {:src  (:logged @data)
                                                                       :type :placeholder
                                                                       :data :joined-rides
                                                                       :dest (:logged @data)})
                                    (= type :past)   (put! ws-channel {:src  (:logged @data)
                                                                       :type :placeholder
                                                                       :data :past-rides
                                                                       :dest (:logged @data)})))))))
              (will-unmount [_]
                            (close! (om/get-shared owner :edit-chan))
                            (close! (om/get-shared owner :del-chan))
                            (close! (om/get-shared owner :leave-chan))
                            (close! (om/get-shared owner :rate-chan)))
              (render-state [_ {:keys [rides]}]
                            (dom/div
                              (when rides
                                (if (= rides :no-rides)
                                  (cond
                                    (= type :all)    (dom/h2 "There are no rides available to join")
                                    (= type :mine)   (dom/h2 "You have no rides saved")
                                    (= type :joined) (dom/h2 "You have not joined any rides")
                                    (= type :past)   (dom/h2 "You have no rides left to rate"))
                                  (cond
                                    (= type :all)    (om/build all-rides-table rides)
                                    (= type :mine)   (om/build my-rides-table rides)
                                    (= type :joined) (om/build joined-rides-table rides)
                                    (= type :past)   (om/build past-rides-table rides)))))))

(defcomponent all-rides
              [data owner]
              (render [_]
                      (om/build get-rides data {:opts {:type :all}})))

(defcomponent my-rides
              [data owner]
              (render [_]
                      (om/build get-rides data {:opts {:type :mine}})))

(defcomponent joined-rides
              [data owner]
              (render [_]
                      (om/build get-rides data {:opts {:type :joined}})))

(defcomponent past-rides
              [data owner]
              (render [_]
                      (om/build get-rides data {:opts {:type :past}})))


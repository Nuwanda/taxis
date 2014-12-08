(ns taxis.rides.list
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [put! <!]]
            [taxis.utils :as util]
            [chord.client :refer [ws-ch]]
            [secretary.core :as secretary]))

(defn- receive-rides
  [owner]
  (go
    (let [srv-ch (om/get-state owner :server-chan)
          msg  (<! srv-ch)]
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
                        (dom/td (:notes data)))))

(defn- join-ride
  [id]
  (secretary/dispatch! (str "/ride/join/" id)))

(defcomponent ride
              [data owner]
              (render [_]
                      (dom/tr
                        (dom/td (:email data))
                        (dom/td (if (> (:numvotes data) 0)
                                  (str (:rating data) "/10")
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
                                                             (dom/th "")
                                                             (dom/tbody
                                                               (for [ride-data rides]
                                                                 (om/build ride ride-data))))))))))

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
                                                             (dom/tbody
                                                               (for [ride-data rides]
                                                                 (om/build my-ride ride-data))))))))))

(defcomponent get-rides
              [data owner {:keys [all?]}]
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
                                  (if all?
                                    (put! ws-channel {:src  (:logged @data)
                                                      :type :placeholder
                                                      :data :all-rides
                                                      :dest (:logged @data)})
                                    (put! ws-channel {:src  (:logged @data)
                                                      :type :placeholder
                                                      :data :get-rides
                                                      :dest (:logged @data)})))))))
              (render-state [_ {:keys [rides]}]
                            (dom/div
                              (when rides
                                (if (= rides :no-rides)
                                  (if all?
                                    (dom/h2 "There are no rides available to join")
                                    (dom/h2 "You have no rides saved"))
                                  (if all?
                                    (om/build all-rides-table rides)
                                    (om/build my-rides-table rides)))))))

(defcomponent all-rides
              [data owner]
              (render [_]
                      (om/build get-rides data {:opts {:all? true}})))

(defcomponent my-rides
              [data owner]
              (render [_]
                      (om/build get-rides data)))


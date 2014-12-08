(ns taxis.rides.join
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [put! <!]]
            [chord.client :refer [ws-ch]]
            [goog.math.Integer :as gint]
            [taxis.utils :as util]
            [taxis.payments :as pay]))

(defn- payment
  [ride]
  (go
    (let [[status data] (<! (pay/create-payment (:price ride)))]
      (if (= status :ok)
        (pay/confirm-payment data)
        (.log js/console (str "failure: " data))))))

(defn- receive-confirmation
  [owner]
  (go
    (let [srv-ch (om/get-state owner :server-chan)
          answer (<! srv-ch)]
      (if (contains? (:message answer) :ok)
        (do
          (om/set-state! owner :ride (get-in answer [:message :ok]))
          (om/set-state! owner :success? true))
        (om/set-state! owner :failure? true)))))

(defcomponent join
              [data owner {:keys [id]}]
              (init-state [_]
                          {:server-chan nil
                           :success?    false
                           :failure?    false})
              (will-mount [_]
                          (go
                            (let [{:keys [ws-channel error]} (<! (ws-ch "ws://localhost:5000/ws"))]
                              (if error
                                (js/alert "Error connecting server")
                                (do
                                  (om/set-state! owner :server-chan ws-channel)
                                  (receive-confirmation owner)
                                  (put! ws-channel {:src  (:logged @data)
                                                    :type :placeholder
                                                    :data {:join-ride {:id (.toInt (gint/fromString id))}}
                                                    :dest (:logged @data)}))))))
              (render-state [_ {:keys [success? failure? ride]}]
                      (dom/div {:class "row"}
                               (dom/div {:class "col-md-10 col-md-offset-1"}
                                        (when success?
                                          (dom/div
                                            (dom/div {:class "alert alert-success alert-dismissible"
                                                      :role  "alert"
                                                      :style {:text-align "center"}}
                                                     "Successfully joined ride")
                                            (dom/div {:class "row"}
                                                     (dom/div {:class "col-md-6 col-md-offset-3"}
                                                              (dom/div {:class "form-group"}
                                                                       (dom/h2 "You can pay for this ride online, do you wish to do so?")
                                                                       (dom/div {:class "btn-group btn-group-justified"}
                                                                                (dom/div {:class "btn-group"}
                                                                                         (dom/button {:class    "btn btn-default"
                                                                                                      :type     "button"
                                                                                                      :on-click #(payment ride)}
                                                                                                     "Yes"))
                                                                                (dom/div {:class "btn-group"}
                                                                                         (dom/button {:class    "btn btn-default"
                                                                                                      :type     "button"
                                                                                                      :on-click #(js/alert "MOVE BACK TO RIDES")}
                                                                                                     "No"))))))))
                                        (when failure?
                                          (dom/div {:class "alert alert-danger"
                                                    :role  "alert"
                                                    :style {:text-align "center"}}
                                                    "Error joining ride"))))))
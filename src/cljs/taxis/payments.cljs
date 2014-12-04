(ns taxis.payments
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [chan <! put!]])
  (:import [goog.net XhrIo]))

(def base-url "http://mrafaelgomes.ddns.net/api")
(def api-key "m9ufZ6nIcAVA0xBWgDeWbqhAmQQ0aV6N")
(def pass "gMuT8YyBHB3fzIDD")

(def endpoints {:get-balance     {:url "/account/balance"
                                  :method "GET"}
                :create-payment  {:url "/payment/create"
                                  :method "POST"}})

(defn- handle-success
  "Checks if the request was successful"
  [response channel]
  (let [status (.-status response)]
    (if (= status "success")
      (put! channel [:success response])
      (put! channel [:error response]))))

(defn- <api-request
  "Async call to the api, returns a channel which will eventually hold the response"
  ([endpoint]
    (<api-request endpoint nil))
  ([endpoint data]
    (let [resp     (chan)
          endpoint (get endpoints endpoint)
          url      (str base-url (:url endpoint))
          type     (:method endpoint)
          hash     (js/btoa (str api-key ":" pass))
          headers  #js {:Authorization (str "Basic " hash)}
          scb      #(handle-success %1 resp)
          ecb      #(put! resp [:error %1])
          params   #js {:type        type
                        :url         url
                        :data        data
                        :cache       false
                        :crossDomain true
                        :headers     headers
                        :success     scb
                        :error       ecb}]
      (.log js/console (str "Request url: " url ", method: " type))
      (.log js/console hash)
      (.ajax js/$ params)
      resp)))

(defn get-balance
  "Get balance from account"
  []
  (go
    (let [[status data] (<! (<api-request :get-balance))]
      (cond
        (= status :success) (do
                              (.log js/console (str "Successly got balance"))
                              (set! js/window.resp data))
        (= status :error)   (do
                              (.log js/console (str "Error getting balance"))
                              (set! js/window.resp data))))))

(defn create-payment
  "Create a payment of the given ammount"
  [amount]
  (go
    (let [[status data] (<! (<api-request :create-payment  #js {:amount amount :currency "USD"}))]
      (cond
        (= status :success) (do
                              (.log js/console (str "Successly created a payment"))
                              (set! js/window.resp data))
        (= status :error)   (do
                              (.log js/console (str "Error creating payment"))
                              (set! js/window.resp data))))))

(defcomponent pay-button
              "Payment Service test button"
              [data owner]
              (render [_]
                      (dom/div {:style {:text-align "center"}}
                               (dom/button {:class "btn btn-primary"
                                            :on-click get-balance} "Balance")
                               (dom/br)
                               (dom/br)
                               (dom/button {:class "btn btn-primary"
                                            :on-click #(create-payment 10)} "Create Payment"))))
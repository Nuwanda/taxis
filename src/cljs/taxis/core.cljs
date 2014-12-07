(ns taxis.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [taxis.maps.main :as maps :refer [map-view]]
            [taxis.tests :as tests]
            [taxis.signin :as signin]
            [taxis.ride :as ride]
            [taxis.payments :as payments]
            [cljs.core.async :refer [chan put! <!]]
            [chord.client :refer [ws-ch]]
            [secretary.core :as secretary :refer-macros [defroute]]
            [goog.history.EventType :as EventType]
            [goog.events :as gevents]
            [weasel.repl :as weasel])
  (:import goog.History))

(enable-console-print!)
(weasel/connect "ws://localhost:9001" :verbose true)

(def app-state (atom {:events-in  nil
                      :events-out nil
                      :position   {:lat 38.752739
                                   :lon -9.184769}
                      :logged     false
                      :taxi?      false
                      :registering? false
                      :ride       {:first-step  {:driving?    true
                                                 :origin      {:lat nil :lon nil :marker nil :locality ""}
                                                 :destination {:lat nil :lon nil :marker nil :locality ""}}
                                   :second-step {:date       ""
                                                 :time       ""
                                                 :recurrent? false
                                                 :weekdays   {:monday    false
                                                              :tuesday   false
                                                              :wednesday false
                                                              :thursday  false
                                                              :friday    false}
                                                 :saturday   false
                                                 :sunday     false}
                                   :third-step  {:cash? false
                                                 :seats 1
                                                 :price 10
                                                 :notes ""}}}))

;;Routing
(def history (History.))

(defn on-navigate [event]
  (secretary/dispatch! (.-token event)))

(doto history
  (gevents/listen EventType/NAVIGATE on-navigate)
  (.setEnabled true))

(defcomponent placeholder [d o]
              (render [_]
                      (dom/div)))

(defcomponent home-placeholder [d o]
              (render [_]
                      (dom/h1 "Home")))

(defcomponent user-placeholder [d o]
              (render [_]
                      (dom/h1 "User Home")))

(defn- set-components
  [nav main]
  (do
    (om/root nav
             app-state
             {:target (. js/document (getElementById "test-buttons"))})
    (om/root main
             app-state
             {:target (. js/document (getElementById "app"))})))

(defroute "/" {}
          (set-components placeholder home-placeholder))

(defroute "/user" {}
          (set-components tests/role-buttons user-placeholder))

(defroute "/register" {}
          (set-components tests/role-buttons signin/signin-form))

(defroute "/taxi" {}
          (set-components tests/taxi-buttons maps/map-view))

(defroute "/pass" {}
          (set-components tests/pass-buttons maps/map-view))

(defroute "/ride/create" {}
          (set-components tests/pass-buttons ride/create-ride))

(defroute "/ride/create/2" {}
          (set-components tests/pass-buttons ride/second-step))

(defroute "/ride/create/3" {}
          (set-components tests/pass-buttons ride/final-step))

(defroute "/ride/create/done" {}
          (set-components tests/pass-buttons ride/ride-done))


;;Om rendering
#_(secretary/dispatch! "/ride/create")
(om/root signin/login-button
         app-state
         {:target (. js/document (getElementById "login-button"))
          :opts   {:client-id "dc21cc7bed16712733bb1b653618a1c4737ba13c"}})
(om/root home-placeholder
         nil
         {:target (. js/document (getElementById "app"))})
#_(om/root payments/pay-button
         nil
         {:target (. js/document (getElementById "app"))})
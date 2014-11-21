(ns taxis.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [taxis.maps :as maps :refer [map-view]]
            [taxis.tests :as tests]
            [taxis.signin :as signin]
            [taxis.ride :as ride]
            [cljs.core.async :refer [chan put! <!]]
            [chord.client :refer [ws-ch]]
            [secretary.core :as secretary :refer-macros [defroute]]
            [goog.history.EventType :as EventType]
            [goog.events :as gevents])
  (:import goog.History))

(enable-console-print!)

(def app-state (atom {:events-in  nil
                      :events-out nil
                      :position {:lat 38.752739
                                 :lon -9.184769}}))

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

(defroute "/" {}
          (do
            (om/root tests/role-buttons
                     app-state
                     {:target (. js/document (getElementById "test-buttons"))})
            (om/root placeholder
                     nil
                     {:target (. js/document (getElementById "app"))})))

(defroute "/login" {}
          (do
            (om/root placeholder
                     app-state
                     {:target (. js/document (getElementById "test-buttons"))})
            (om/root signin/signin-form
                     app-state
                     {:target (. js/document (getElementById "app"))})))

(defroute "/taxi" {}
          (do
            (om/root tests/taxi-buttons
                     app-state
                     {:target (. js/document (getElementById "test-buttons"))})
            (om/root maps/map-view
                     app-state
                     {:target (. js/document (getElementById "app"))})))

(defroute "/pass" {}
          (do
            (om/root tests/pass-buttons
                     app-state
                     {:target (. js/document (getElementById "test-buttons"))})
            (om/root maps/map-view
                     app-state
                     {:target (. js/document (getElementById "app"))})))

(defroute "/ride/create" {}
          (do
            (om/root placeholder nil {:target (. js/document (getElementById "test-buttons"))})
            (om/root ride/create-ride
                     app-state
                     {:target (. js/document (getElementById "app"))})))

(defroute "/ride/create/2" {}
          (do
            (om/root placeholder nil {:target (. js/document (getElementById "test-buttons"))})
            (om/root ride/second-step
                     app-state
                     {:target (. js/document (getElementById "app"))})))

(defroute "/ride/create/3" {}
          (do
            (om/root placeholder nil {:target (. js/document (getElementById "test-buttons"))})
            (om/root ride/final-step
                     app-state
                     {:target (. js/document (getElementById "app"))})))

(defroute "/ride/create/done" {}
          (do
            (om/root placeholder nil {:target (. js/document (getElementById "test-buttons"))})
            (om/root ride/ride-done
                     app-state
                     {:target (. js/document (getElementById "app"))})))


;;Om rendering
(om/root signin/login-button
         app-state
         {:target (. js/document (getElementById "test-buttons"))
          :opts   {:client-id "28a417b037e936b3b9310b47530217ccc797236b"}})
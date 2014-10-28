(ns taxis.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [taxis.maps :as maps :refer [map-view]]
            [taxis.tests :as tests]
            [cljs.core.async :refer [chan put! <!]]
            [chord.client :refer [ws-ch]]
            [secretary.core :as secretary :include-macros true :refer [defroute]]
            [goog.history.EventType :as EventType]
            [goog.events :as events])
  (:import goog.History))

(enable-console-print!)

(def app-state (atom {:events-in  (chan)
                      :events-out (chan)
                      :position {:lat 38.752739
                                 :lon -9.184769}}))

;;Routing

(def history (History.))

(defn on-navigate [event]
  (secretary/dispatch! (.-token event)))

(doto history
  (goog.events/listen EventType/NAVIGATE on-navigate)
  (.setEnabled true))

(defroute "/" {}
          (om/root tests/pass-buttons
                   app-state
                   {:target (. js/document (getElementById "test-buttons"))}))

(defroute "/taxi" {}
          (om/root tests/taxi-buttons
                   app-state
                   {:target (. js/document (getElementById "test-buttons"))}))


;;Om rendering
(om/root tests/role-buttons
         app-state
         {:target (. js/document (getElementById "test-buttons"))})

(om/root maps/map-view
         app-state
         {:target (. js/document (getElementById "app"))})
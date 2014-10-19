(ns taxis.core
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [taxis.maps :as maps :refer [map-view]]
            [cljs.core.async :refer [chan put!]]))

(enable-console-print!)

(def app-state (atom {:events (chan)
                      :position {:lat 38.752739
                                 :lon -9.184769}}))

(defn- rand-taxi []
  (let [current-lat (get-in @app-state [:position :lat])
        current-lon (get-in @app-state [:position :lon])
        lat (+ current-lat (- (rand 0.04) 0.02))
        lon (+ current-lon (- (rand 0.04) 0.02))
        id (rand-int 10)]
    {:taxi {:id id :lat lat :lon lon}}))

(defn- gen-taxis []
  (take 5 (repeatedly rand-taxi)))

(defcomponent buttons [{:keys [events]} owner]
              (display-name [_]
                            "Test-buttons")
              (render [_]
                      (dom/ul {:class "nav navbar-nav navbar-right"}
                        (dom/li
                                 (dom/button {:class "btn btn-primary"
                                              :on-click #(put! events [:center])}
                                             "Center on me"))
                        (dom/li
                                 (dom/button {:class "btn btn-primary"
                                              :on-click #(put! events [:add-taxis (gen-taxis)])}
                                             "Taxis")))))

(om/root maps/map-view
         app-state
         {:target (. js/document (getElementById "app"))})

(om/root buttons
         app-state
         {:target (. js/document (getElementById "test-buttons"))})
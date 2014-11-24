(ns taxis.tests
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [chan put! <! close!]]
            [chord.client :refer [ws-ch]]
            [secretary.core :as secretary]))

(defn- rand-taxi [clat clon]
  (let [lat (+ clat (- (rand 0.04) 0.02))
        lon (+ clon (- (rand 0.04) 0.02))
        id (rand-int 10)]
    {:taxi {:id id :lat lat :lon lon}}))

(defn- rand-coord [coord]
  (+ coord (- (rand 0.015) 0.0075)))

(defn- gen-taxis [lat lon]
  (take 5 (repeatedly #(rand-taxi lat lon))))

(defn- send-to-server [owner data]
  (let [out-ch (:events-out @data)
        srv-ch (om/get-state owner :server-chan)
        src    (om/get-state owner :id)]
    (go-loop []
             (if-let [[e v] (first (<! out-ch))]
               (let [lat (get-in @data [:position :lat])
                     lon (get-in @data [:position :lon])]
                 (cond
                   (= e :pickup) (put! srv-ch {:type :pass :src src :dest v :data [:pickup {:src src :lat lat :lon lon}]})
                   (= e :pu-accept) (put! srv-ch {:type :taxi :src src :dest v :data [:pu-accepted {:src src}]})
                   (= e :pu-reject) (put! srv-ch {:type :taxi :src src :dest v :data [:pu-rejected {:src src}]})
                   :else (.log js/console "Unexpected event"))
                 (recur))))))

(defn- receive-from-server [owner data]
  (go-loop []
           (if-let [msg (<! (om/get-state owner :server-chan))]
             (do
               (if (:error msg)
                 (js/alert "Error communicating with server")
                 (do
                   (print (str "Got: " msg))
                   (put! (:events-in @data) (:message msg))))
               (recur)))))

(defcomponent pass-buttons [data owner]
              (init-state [_]
                          {:server-chan nil
                           :id (rand-int 10)
                           :type :pass})
              (will-mount [_]
                          (go
                            (let [{:keys [ws-channel error]} (<! (ws-ch "ws://localhost:5000/ws"))]
                              (if error
                                (js/alert "Error connecting server")
                                (do
                                  (om/set-state! owner :server-chan ws-channel)
                                  (receive-from-server owner data)
                                  (send-to-server owner data))))))
              (will-unmount [_]
                            (close! (om/get-state owner :server-chan)))
              (render-state [_ {:keys [server-chan id]}]
                            (dom/ul {:class "nav navbar-nav navbar-left"}
                                    (dom/li
                                      (dom/a {:href ""
                                              :on-click (fn []
                                                          (put! server-chan {:data [:center]
                                                                             :src  id
                                                                             :type :pass
                                                                             :dest id})
                                                          false)}
                                             "Center on me"))
                                    (dom/li
                                      (dom/a {:href ""
                                              :on-click (fn []
                                                          (put! server-chan {:data [:add-taxis (gen-taxis
                                                                                                 (get-in @data [:position :lat])
                                                                                                 (get-in @data [:position :lon]))]
                                                                             :src  id
                                                                             :type :pass})
                                                          false)}
                                             "Taxis"))
                                    (dom/li
                                      (dom/a {:href ""
                                              :on-click (fn []
                                                          (secretary/dispatch! "/ride/create")
                                                          false)}
                                             "Create a Ride")))))

(defn- set-location [data]
  (let [c (chan)]
    (.getCurrentPosition js/navigator.geolocation #(put! c %))
    (go
      (let [pos (<! c)
            lat (.-latitude (.-coords pos))
            lon (.-longitude (.-coords pos))]
        (om/update! data [:position :lat] lat)
        (om/update! data [:position :lon] lon)))))

(defn- update-location [data owner]
  (let [sch  (om/get-state owner :server-chan)
        ich  (:events-in @data)
        id   (om/get-state owner :id)
        lat  (get-in @data [:position :lat])
        lon  (get-in @data [:position :lon])
        rlat (rand-coord lat)
        rlon (rand-coord lon)]
    (om/update! data [:position :lon] rlon)
    (om/update! data [:position :lat] rlat)
    (put! ich [:center {:lat rlat :lon rlon}])
    (put! sch {:data [:add-taxis  [{:taxi {:id  id
                                           :lat rlat
                                           :lon rlon}}]]
               :src id
               :type :taxi})))

(defcomponent taxi-buttons [data owner]
              (init-state [_]
                          {:server-chan nil
                           :id (rand-int 10)
                           :type :taxi})
              (will-mount [_]
                          (go
                            (let [{:keys [ws-channel error]} (<! (ws-ch "ws://localhost:5000/ws"))]
                              (if error
                                (js/alert "Error connecting server")
                                (do
                                  (om/set-state! owner :server-chan ws-channel)
                                  (receive-from-server owner data)
                                  (send-to-server owner data))))))
              (will-unmount [_]
                            (close! (om/get-state owner :server-chan)))
              (did-mount [_]
                         (set-location data))
              (render [_]
                      (dom/ul {:class "nav navbar-nav navbar-left"}
                              (dom/li
                                (dom/a {:href ""
                                        :on-click (fn []
                                                    (update-location data owner)
                                                    false)}
                                       "Update Position")))))

(defcomponent role-buttons [data owner]
              (render [_]
                      (dom/ul {:class "nav navbar-nav navbar-left"}
                        (dom/li
                          (dom/a {:href ""
                                  :on-click (fn []
                                              (secretary/dispatch! "/pass")
                                              false)}
                                 "Passenger"))
                        (dom/li
                          (dom/a {:href ""
                                  :on-click (fn []
                                              (secretary/dispatch! "/taxi")
                                              false)}
                                 "Taxi")))))
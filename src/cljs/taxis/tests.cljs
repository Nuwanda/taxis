(ns taxis.tests
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [chan put! <! close!]]
            [chord.client :refer [ws-ch]]
            [secretary.core :as secretary]
            [taxis.signin :as signin]))

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
        src    (:logged @data)]
    (go-loop []
             (if-let [[e v] (first (<! out-ch))]
               (let [lat (get-in @data [:position :lat])
                     lon (get-in @data [:position :lon])]
                 (cond
                   (= e :get-rides) (.log js/console "Want rides!")
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
                 (let [msg (:message msg)]
                   (print (str "Got: " msg))
                   (put! (:events-in @data) msg)))
               (recur)))))

(defcomponent pass-buttons [data owner]
              (init-state [_]
                          {:server-chan nil
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
                            (when (om/get-state owner :server-chan)
                              (close! (om/get-state owner :server-chan))))
              (render [_]
                      (dom/ul {:class "nav navbar-nav navbar-left"}
                              (dom/li
                                (dom/a {:href     ""
                                        :on-click (fn []
                                                    (secretary/dispatch! "/ride/create")
                                                    false)}
                                       "Create a Ride"))
                              (dom/li
                                (dom/a {:href     ""
                                        :on-click (fn []
                                                    (secretary/dispatch! "/ride/list/past")
                                                    false)}
                                       "Rate a Past Ride"))
                              (dom/li
                                (dom/a {:href     ""
                                        :on-click (fn []
                                                    (secretary/dispatch! "/ride/list")
                                                    false)}
                                       "Find Rides"))
                              (dom/li
                                (dom/a {:href     ""
                                        :on-click (fn []
                                                    (secretary/dispatch! "/ride/list/joined")
                                                    false)}
                                       "My Joined Rides"))
                              (dom/li
                                (dom/a {:href     ""
                                        :on-click (fn []
                                                    (secretary/dispatch! "/ride/list/mine")
                                                    false)}
                                       "My Created Rides")))))

(defcomponent offline-pass
              [data owner]
              (render [_]
                      (dom/ul {:class "nav navbar-nav navbar-left"}
                              (dom/li
                                (dom/a {:href     ""
                                        :on-click (fn []
                                                    (secretary/dispatch! "/pass")
                                                    false)}
                                       "Find Taxis"))
                              (dom/li
                                (dom/a {:href     ""
                                        :on-click (fn []
                                                    (secretary/dispatch! "/ride/create")
                                                    false)}
                                       "Create a Ride"))
                              (dom/li
                                (dom/a {:href     ""
                                        :on-click (fn []
                                                    (secretary/dispatch! "/ride/list/past")
                                                    false)}
                                       "Rate a Past Ride"))
                              (dom/li
                                (dom/a {:href     ""
                                        :on-click (fn []
                                                    (secretary/dispatch! "/ride/list")
                                                    false)}
                                       "Find Rides"))
                              (dom/li
                                (dom/a {:href     ""
                                        :on-click (fn []
                                                    (secretary/dispatch! "/ride/list/joined")
                                                    false)}
                                       "My Joined Rides"))
                              (dom/li
                                (dom/a {:href     ""
                                        :on-click (fn []
                                                    (secretary/dispatch! "/ride/list/mine")
                                                    false)}
                                       "My Created Rides")))))

(defn set-location [data]
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
        id   (:logged @data)
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
                            (when (om/get-state owner :server-chan)
                              (close! (om/get-state owner :server-chan))))
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

(defn- receive-registration
  [data owner]
  (go-loop []
           (if-let [msg (<! (om/get-state owner :server-chan))]
             (do
               (if (:error msg)
                 (js/alert "Error communicating with server")
                 (let [msg (:message msg)]
                   (print (str "Got: " msg))
                   (if (:registered msg)
                     (signin/handle-registration data msg)
                     (.log js/console "Unexpected message"))))
               (recur)))))

(defcomponent role-buttons [data owner]
              (init-state [_]
                          {:server-chan nil})
              (will-mount [_]
                          (go
                            (let [{:keys [ws-channel error]} (<! (ws-ch "ws://localhost:5000/ws"))]
                              (if error
                                (js/alert "Error connecting server")
                                (do
                                  (om/set-state! owner :server-chan ws-channel)
                                  (receive-registration data owner)
                                  (put! ws-channel {:src  (:logged @data)
                                                    :type :placeholder
                                                    :data :registered?
                                                    :dest (:logged @data)}))))))
              (will-unmount [_]
                            (when (om/get-state owner :server-chan)
                              (close! (om/get-state owner :server-chan))))
              (render [_]
                      (dom/div)))
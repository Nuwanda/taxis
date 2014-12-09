(ns taxis.signin
  (:require-macros [cljs.core.async.macros :refer [go-loop go]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [goog.dom :as gdom]
            [secretary.core :as secretary]
            [cljs.core.async :refer [timeout <! put! close!]]
            [chord.client :refer [ws-ch]]
            [taxis.utils :as util]))

(defn- logoff
  "Logoff the current user remotely and localy,
  redirects to home"
  [data]
  (om/update! data :logged false)
  (.Logoff js/IDService))

(defn- authorize
  "Authenticate user"
  []
  (.Login js/IDService))

(defn- logged?
  "Checks the current login status"
  []
  (let [email  (.. js/IDService -basicinfo -Email)
        connected? (not (nil? email))]
    (.log js/console (str "Logged in: " connected?))
    connected?))

(defn- update-login-status
  "Update app-state according to the current login status,
  also redirects to the main user page if logged"
  [data]
  (go-loop []
           (if (logged?)
             (do
               (om/update! data :logged (.. js/IDService -basicinfo -Email))
               (secretary/dispatch! "/user"))
             (do
               (<! (timeout 200))
               (recur)))))

(defcomponent login-button [data owner {:keys [client-id]}]
              (will-mount [_]
                          (let [init #js {:clientid client-id}]
                            (.Init js/IDService init)))
              (did-mount [_]
                         (update-login-status data))
              (render [_]
                      (dom/ul {:class "nav navbar-nav navbar-right"}
                              (dom/li
                                (if (:logged data)
                                  (dom/a {:href "#" :on-click (fn []
                                                                (logoff data)
                                                                false)}
                                         "Log Off")
                                  (dom/a {:href "#" :on-click (fn []
                                                                (authorize)
                                                                false)}
                                         "Log in"))))))

(defn handle-registration [data {:keys [registered]}]
  (let [registering? (:registering? @data)]
    (.log js/console (str "Registering?: " registering? ", registered: " registered))
    (cond
      (= registered :unregistered) (when-not registering? (secretary/dispatch! "/register"))
      (= registered :taxi) (do (om/update! data :taxi? true) (secretary/dispatch! "/taxi"))
      (= registered :pass) (do (om/update! data :taxi? false) (secretary/dispatch! "/pass")))))

(defn- submit [data owner e]
  (let [srv-chan (om/get-state owner :server-chan)
        src      (:logged @data)
        taxi?    (:taxi? @data)]
    (if taxi?
      (put! srv-chan {:src src
                      :type :taxi
                      :data :register
                      :dest src})
      (put! srv-chan {:src src
                      :type :pass
                      :data :register
                      :dest src}))
    (go
      (let [answer (<! srv-chan)]
        (.log js/console (str "Got message: " answer))
        (if (= (:message answer) :ok)
          (if taxi?
            (secretary/dispatch! "/taxi")
            (secretary/dispatch! "/pass"))
          (js/alert "Error registering user on server")))))
  (.preventDefault e))

(defcomponent signin-form [data owner]
              (init-state [_]
                          {:server-chan nil})
              (will-mount [_]
                          (om/update! data :registering? true)
                          (go
                            (let [{:keys [ws-channel error]} (<! (ws-ch "ws://localhost:5000/ws"))]
                              (if error
                                (js/alert "Error connecting server")
                                (om/set-state! owner :server-chan ws-channel)))))
              (will-unmount [_]
                            (when (om/get-state owner :server-chan)
                              (close! (om/get-state owner :server-chan))))
              (render [_]
                      (dom/div {:class "row"}
                               (dom/div {:class "col-md-6 col-md-offset-3"
                                         :style {:text-align "center"}}
                                        (dom/form {:role "form" :class "form-signin"}
                                                  (dom/h2 (str "Welcome to TaxiSharing " (.-Name js/IDService.basicinfo)))
                                                  (dom/div {:class "jumbotron"}
                                                           (dom/p "This is your first time using our services, do you which to register as a taxi?")
                                                           (dom/div {:class "btn-group btn-group-justified"}
                                                                    (dom/div {:class "btn-group"}
                                                                             (dom/button {:class    (str "btn btn-default "
                                                                                                         (util/active (:taxi? data)))
                                                                                          :type     "button"
                                                                                          :on-click #(om/update! data :taxi? true)}
                                                                                         "Yes"))
                                                                    (dom/div {:class "btn-group"}
                                                                             (dom/button {:class    (str "btn btn-default "
                                                                                                          (util/active (not (:taxi? data))))
                                                                                          :type     "button"
                                                                                          :on-click #(om/update! data :taxi? false)}
                                                                                         "No"))))
                                                  (dom/button {:class "btn btn-primary"
                                                               :type "submit"
                                                               :style {:margin-top "2px"}
                                                               :on-click #(submit data owner %)}
                                                              "Sign In"))))))
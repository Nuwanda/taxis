(ns taxis.signin
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [goog.dom :as gdom]
            [secretary.core :as secretary]
            [cljs.core.async :refer [timeout <!]]))

(defn- logoff
  "Logoff the current user remotely and localy"
  [data]
  (.Logoff js/IDService)
  (om/update! data :logged false))

(defn- authorize
  "Authenticate user and prevent default a.href onclick event"
  []
  (.Login js/IDService)
  false)

(defn- logged?
  "Checks the current login status"
  []
  (let [status (.. js/IDService -access_token -status)]
    (.log js/console (str "Login status:" status))
    (if (= status "connected")
      true
      false)))

(defn- update-login-status
  "Update app-state according to the current login status"
  [data]
  (go-loop []
           (if (logged?)
             (do
               (om/update! data :logged true)
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
                                  (dom/a {:href "/" :on-click #(logoff data)} "Log Off")
                                  (dom/a {:href "#/user" :on-click authorize} "Log in"))))))

(defn- submit [e]
  (.preventDefault e)
  (secretary/dispatch! "/"))

(defcomponent signin-form [data owner]
              (will-mount [_]
                          (let [link (gdom/createElement "link")
                                head (aget (gdom/getElementsByTagNameAndClass "head") 0)]
                            (doto link
                              (.setAttribute "href" "/public/css/signin.css")
                              (.setAttribute "type" "text/css")
                              (.setAttribute "rel" "stylesheet"))
                            (.appendChild head link)))
              (render [_]
                      (dom/div {:class "row"}
                               (dom/div {:class "col-md-4 col-md-offset-4"
                                         :style {:text-align "center"}}
                                        (dom/form {:role "form" :class "form-signin"}
                                                  (dom/h2 "Please Sign In")
                                                  (dom/input  {:type "email" :placeholder "Email address" :class "form-control"})
                                                  (dom/input  {:type "password" :placeholder "Password" :class "form-control"})
                                                  (dom/button {:class "btn btn-primary"
                                                               :type "submit"
                                                               :style {:margin-top "2px"}
                                                               :on-click submit}
                                                              "Sign In"))))))
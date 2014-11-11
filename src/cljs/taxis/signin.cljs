(ns taxis.signin
  (:require [om.core :as om :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [om-tools.dom :as dom :include-macros true]
            [goog.dom :as gdom]
            [secretary.core :as secretary]))

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
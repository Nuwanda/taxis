(ns taxis.auth
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]))

(defcomponent login-button
              "Wrapper for LoginService login button"
              [data owner]
              (render [_]
                      (dom/div
                        (dom/div {:id "login-root"}))))
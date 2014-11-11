(ns taxis.utils
  (:require
    [goog.dom :as gdom]
    [goog.events :as gevents]))

(defn- child-listen [id cb]
  (let [node (gdom/getElement (str "window-button" id))]
    (when node
      (cb id))))

(defn attach-event [id cb]
  (let [type gevents/EventType.CLICK]
    (gevents/listen js/document type #(child-listen id cb))))

(defn display [show?]
  (if show?
    nil
    "none"))

(defn- active [active?]
  (if active?
    " active"
    ""))
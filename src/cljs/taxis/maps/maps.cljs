(ns taxis.maps
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [put! <! chan timeout close!]]
            [taxis.utils :as utils]
            [taxis.maps.directions :as directions]))


(def *out-chan* nil)
(def *in-chan* nil)

(defn- handler
  "Handler for pickup request infowindow button, sends pickup request to the server"
  [id]
  (put! *out-chan* {:pickup id}))

(defn- accept-pu-handler
  []
  "Handler for accept button in pickup answer infowindow"
  (put! *in-chan* [:pu-accept]))

(defn- reject-pu-handler
  "Handler for reject button in pickup answer infowindow"
  []
  (put! *in-chan* [:pu-reject]))

(defn- create-content
  "Content string for pick up request infowindow"
  [id]
  (str "<div style=text-align:center;>"
       (str "<button id=" "'window-button-" id "' class=" "'btn btn-primary'" "onClick=taxis.maps.handler(" id ")" ">" "Request Pick-Up" "</button>")
       (str "</div>")))

(defn- create-pu-content
  "Content string for pick up answer infowindow"
  [id]
  (str "<div style=text-align:center ><h3>Pick-up Requested at this location</h3>"
       (str "<br/>")
       (str "<button id=" "'y-pick-button-" id "' class=" "'btn btn-success'" "onClick=taxis.maps.accept_pu_handler() >" "Accept" "</button>"
            (str "<button id=" "'n-pick-button-" id "' class=" "'btn btn-danger'" "onClick=taxis.maps.reject_pu_handler() >" "Reject" "</button>"
                 (str "</div>")))))

(defn- create-info
  "Creates and attaches info windows for a marker"
  [owner marker id]
  (let [c (create-content id)
        o #js {:content c}
        w (google.maps.InfoWindow. o)
        m (om/get-state owner :map)]
    (.addListener google.maps.event marker "click" (fn []
                                                     (om/set-state! owner :info-window w)
                                                     (.open w m marker)))))

(defn- get-marker
  "Checks component state for markers for the client id, returns nil if inexistent"
  [owner id]
  (let [markers (om/get-state owner :markers)]
    (get markers id)))

(defn- set-marker
  "Sets a marker on the given coordinates, optionally can take a map of options"
  ([owner lat lon id]
   (set-marker owner lat lon id {}))
  ([owner lat lon id {:keys [title animation image]}]
   (let [marker (get-marker owner id)
         pos (google.maps.LatLng. lat lon)
         options #js {:position pos :title title :animation animation :icon image}
         map (om/get-state owner :map)]
     (if marker
       (.setOptions marker options)
       (let [nm (google.maps.Marker. options)]
         (when-not (= id -1)
           (create-info owner nm id))
         (.setMap nm map)
         (om/update-state! owner :markers #(assoc % id nm)))))))

(defn- pan-map
  "Pans map to given coordinates"
  [owner lat lon]
  (let [center (google.maps.LatLng. lat lon)
        map (om/get-state owner :map)]
    (.panTo map center)))

(defn- add-taxi
  "Add a marker for a taxi to the map"
  [owner taxi]
  (let [lat (get-in taxi [:taxi :lat])
        lon (get-in taxi [:taxi :lon])
        id  (get-in taxi [:taxi :id])]
    (set-marker owner lat lon id {:title "Taxi" :image "public/images/taxi.png"})))

(defn- add-taxis
  "Takes a sequence of taxis and adds them to the map"
  [owner params]
  (let [addt (partial add-taxi owner)]
    (doall (map addt params))))

(defn- center-map
  "Get user current location using html5 GeoLocation and center map on it,
  also places a marker there"
  [data owner {:keys [lat lon] :as params}]
  (if-not params
    (let [c (chan)]
      (.getCurrentPosition js/navigator.geolocation #(put! c %))
      (go
        (let [pos (<! c)
              lat (.-latitude (.-coords pos))
              lon (.-longitude (.-coords pos))]
          (om/update! data [:position :lat] lat)
          (om/update! data [:position :lon] lon)
          (pan-map owner lat lon)
          (set-marker owner lat lon -1 {:title "You are here" :animation google.maps.Animation.DROP}))))
    (do
      (pan-map owner lat lon)
      (set-marker owner lat lon -1 {:title "You are here" :animation google.maps.Animation.DROP :image "public/images/taxi.png"}))))

(defn- pu-notice
  "Creates a marker and infowindow for incoming pick up request, stores request info"
  [owner {:keys [src lat lon] :as pu}]
  (let [content (create-pu-content src)
        opts    #js {:content content}
        win     (google.maps.InfoWindow. opts)
        map     (om/get-state owner :map)
        m-opts  {:title "Pick-up Request" :animation google.maps.Animation.DROP}]
    (om/set-state! owner :info-window win)
    (om/set-state! owner :pick-up pu)
    (set-marker owner lat lon src m-opts)
    (.open win map (get-marker owner src))))

(defn- close-info-window [owner]
  "Close an open info window"
  (let [w (om/get-state owner :info-window)]
    (when w
      (do
        (.close w)
        (om/set-state! owner :info-window nil)))))

(defn- accept-pickup
  "Sends a request acceptance"
  [data owner]
  (let [ch   (:events-out @data)
        pu   (om/get-state owner :pick-up)
        did  (:src pu)
        dlat (:lat pu)
        dlon (:lon pu)
        dest {:lat dlat :lon dlon}
        olat (get-in @data [:position :lat])
        olon (get-in @data [:position :lon])
        orig {:lat olat :lon olon}
        map  (om/get-state owner :map)]
    (close-info-window owner)
    (put! ch {:pu-accept did})
    (directions/display-route map orig dest)))

(defn- reject-pickup
  "Sends a request denial and clears the current request"
  [data owner]
  (let [ch   (:events-out @data)
        pu   (om/get-state owner :pick-up)
        dest (:src pu)]
    (close-info-window owner)
    (om/set-state! owner :pick-up {})
    (put! ch {:pu-reject dest})))

(defn- wait-for-pickup
  "Clears the info window and draws the route from the taxi to current location"
  [data owner {id :src}]
  (let [marker (get-marker owner id)
        pos    (.getPosition marker)
        olat   (.lat pos)
        olon   (.lng pos)
        dlat   (get-in @data [:position :lat])
        dlon   (get-in @data [:position :lon])
        dest   {:lat dlat :lon dlon}
        orig   {:lat olat :lon olon}
        map    (om/get-state owner :map)]
    (close-info-window owner)
    (directions/display-route map orig dest)))

(defn- event-loop [data owner]
  "Message handling loop"
  (go-loop []
           (if-let [[event params] (<! (:events-in @data))]
             (do
               (cond
                 (= event :center) (center-map data owner params)
                 (= event :add-taxis) (add-taxis owner params)
                 (= event :pickup) (pu-notice owner params)
                 (= event :pu-accept) (accept-pickup data owner)
                 (= event :pu-reject) (reject-pickup data owner)
                 (= event :pu-accepted) (wait-for-pickup data owner params)
                 :else (print "unknown event"))
               (recur)))))

(defcomponent map-view
              "Google Maps wrapper component"
              [data owner {:keys [lat lon zoom events-in] :or {lat 38.752739
                                                            lon -9.184769
                                                            zoom 14}}]
              (init-state [_]
                          {:map nil
                           :pick-up {}
                           :markers {}
                           :info-window nil})
              (will-mount [_]
                          (om/update! data :events-in (chan))
                          (om/update! data :events-out (chan)))
              (will-unmount [_]
                            (close! (:events-in data))
                            (close! (:events-out data)))
              (did-mount [_]
                         (let [map-node (om/get-node owner)
                               options #js {:center (google.maps.LatLng. lat lon)
                                            :zoom zoom
                                            :mapTypeId google.maps.MapTypeId.ROADMAP}]
                           (om/set-state! owner :map (google.maps.Map. map-node options))
                           (set! *out-chan* (:events-out data))
                           (set! *in-chan* (:events-in data))
                           (event-loop data owner)))
              (render [_]
                      (dom/div {:style {:height "550px"}})))
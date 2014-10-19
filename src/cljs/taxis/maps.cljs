(ns taxis.maps
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om.dom :refer [render-to-str]]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [put! <! chan timeout]]
            [taxis.utils :as utils]))


#_(defcomponent window-view [data owner]
              (render [_]
                      (dom/div {:style {:text-align "center"}}
                               (dom/button {:class "btn btn-primary"
                                            :id (str "window-button-" (:id data))}
                                           (str "ID: " (:id data))))))

(defn- handler [id]
  (js/alert (str "id: " id)))

(defn- create-content [id]
  #_(render-to-str (om/build window-view {:id id}))
  (str "<div style=text-align:center;>"
       (str "<button id=" "'window-button-" id "' class=" "'btn btn-primary'" "onClick=taxis.maps.handler(" id ")" ">" "ID:" id "</button>")
       (str "</div>")))

(defn- create-info [owner marker id]
  (let [c (create-content id)
        o #js {:content c}
        w (google.maps.InfoWindow. o)
        m (om/get-state owner :map)]
    (.addListener google.maps.event marker "click" #(.open w m marker))))

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
         (.setMap nm map)
         (create-info owner nm id)
         (om/update-state! owner :markers #(assoc % id nm)))))))

(defn- pan-map
  "Pans map to given coordinates"
  [owner lat lon]
  (let [center (google.maps.LatLng. lat lon)
        map (om/get-state owner :map)]
    (.panTo map center)))

(defn- add-taxis
  "Takes a sequence of taxis and adds them to the map"
  [owner params]
  (doseq [taxi params]
    (let [lat (get-in taxi [:taxi :lat])
          lon (get-in taxi [:taxi :lon])
          id  (get-in taxi [:taxi :id])]
      (set-marker owner lat lon id {:title "Taxi" :image "public/images/taxi.png"}))))

(defn- center-map
  "Get user current location using html5 GeoLocation and center map on it,
  also places a marker there"
  [data owner]
  (let [c (chan)]
    (.getCurrentPosition js/navigator.geolocation #(put! c %))
    (go
      (let [pos (<! c)
            lat (.-latitude (.-coords pos))
            lon (.-longitude (.-coords pos))]
        (om/update! data [:position :lat] lat)
        (om/update! data [:position :lon] lon)
        (pan-map owner lat lon)
        (set-marker owner lat lon -1 {:title "You are here" :animation google.maps.Animation.DROP})))))

(defn- event-loop [data owner]
  (go-loop []
           (let [[event params] (<! (:events @data))]
             (cond
               (= event :center) (center-map data owner)
               (= event :add-taxis) (do
                                      (print (str "got taxis: " params))
                                      (add-taxis owner params))
               :else (print "unknown event"))
             (recur))))

(defcomponent map-view
              "Google Maps wrapper component"
              [data owner {:keys [lat lon zoom events] :or {lat 38.752739
                                                            lon -9.184769
                                                            zoom 14}}]
              (init-state [_]
                          {:map nil
                           :markers {}})
              (display-name [_]
                            "Map")
              (did-mount [_]
                         (let [map-node (om/get-node owner)
                               options #js {:center (google.maps.LatLng. lat lon)
                                            :zoom zoom
                                            :mapTypeId google.maps.MapTypeId.ROADMAP}]
                           (om/set-state! owner :map (google.maps.Map. map-node options))
                           (event-loop data owner)))
              (render [_]
                      (dom/div {:style {:height "550px"}})))
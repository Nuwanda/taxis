(ns taxis.maps.directions
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [chan put! <!]]))

(defn request-directions [start end]
  (let [origin (google.maps.LatLng. (:lat start) (:lon start))
        dest   (google.maps.LatLng. (:lat end) (:lon end))
        mode   google.maps.TravelMode.DRIVING
        req    #js {:origin origin :destination dest :travelMode mode}
        ds     (google.maps.DirectionsService.)
        cb     (chan)]
    (.route ds req #(put! cb {:res %1 :status %2}))
    (go
      (let [{:keys [res status]} (<! cb)]
        (when (= status google.maps.DirectionsStatus.OK)
          res)))))

(defn display-route
  "Gets directions between two points and displays the result on the given map"
  [map start end]
  (go
    (let [dirs (<! (request-directions start end))
          opts #js {:suppressMarkers true}
          dr   (google.maps.DirectionsRenderer. opts)]
      (doto dr
        (.setMap map)
        (.setDirections dirs)))))
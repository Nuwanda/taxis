(ns taxis.maps.geocoding
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [put! <! chan]]))

(defn- get-city
  "Return city name from an array of reverse geocoding results"
  [res]
  (let [results  (js->clj res :keywordize-keys true)
        pred     #(some #{"locality"} (:types %))
        filtered (filter pred results)]
    (:formatted_address (first filtered))))

(defn- get-coords
  "Return coordinates and formatted address from an array of geocoding results"
  [res]
  (let [results (js->clj res :keywordize-keys true)
        car     (first results)
        address (:formatted_address car)
        pos     (:location (:geometry car))
        lat     (.lat pos)
        lon     (.lng pos)
        res     {:address address :lat lat :lon lon}]
    res))

(defn geocode
  "Make asynchronous geocoding request"
  ([location]
   (.log js/console (str "Getting coords for: " location))
   (let [geo  (google.maps.Geocoder.)
         opts #js {:address location}
         c    (chan)]
     (.geocode geo opts #(put! c {:res %1 :status %2}))
     (go
       (let [{:keys [res status]} (<! c)]
         (if (= status google.maps.GeocoderStatus.OK)
           (get-coords res)
           (js/alert (str "Error reverse geocoding position: " status)))))))
  ([lat lon]
   (.log js/console (str "Getting city from coords lat: " lat ", lon: "lon))
   (let [geo  (google.maps.Geocoder.)
         pos  (google.maps.LatLng. lat lon)
         opts #js {:latLng pos}
         c    (chan)]
     (.geocode geo opts #(put! c {:res %1 :status %2}))
     (go
       (let [{:keys [res status]} (<! c)]
         (if (= status google.maps.GeocoderStatus.OK)
           (get-city res)
           (js/alert (str "Error reverse geocoding position: " status))))))))
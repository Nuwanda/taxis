(ns taxis.ride
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [taxis.utils :as util]
            [taxis.maps.geocoding :as geo]
            [taxis.maps.directions :as directions]
            [cljs.core.async :refer [chan put! <!]]
            [clojure.string :refer [blank?]]
            [goog.dom :as gdom]
            [goog.math.Integer :as gint]
            [secretary.core :as secretary]))

(defn- pan-map
  "Pans map to given coordinates"
  [owner lat lon]
  (.log js/console (str "Panning to lat: " lat ", lon: " lon))
  (let [center (google.maps.LatLng. lat lon)
        map (om/get-state owner :map)]
    (.panTo map center)))

(defn- center-map
  "Get user current location using html5 GeoLocation and center map on it"
  [owner]
  (let [c (chan)]
    (.getCurrentPosition js/navigator.geolocation #(put! c %))
    (go
      (let [pos (<! c)
            lat (.-latitude (.-coords pos))
            lon (.-longitude (.-coords pos))
            ctr {:lat lat :lon lon}]
        (om/set-state! owner :center ctr)
        (pan-map owner lat lon)))))

(defn- create-marker
  "Returns a marker for the origin/destination"
  ([owner {:keys [lat lon] :as coords} type]
   (when coords
     (let [pos (google.maps.LatLng. lat lon)
           map (om/get-state owner :map)
           marker (om/get-state owner [type :marker])]
       (if marker
         (do
           (.setPosition marker pos)
           marker)
         (let [options #js {:position pos}
               marker  (google.maps.Marker. options)]
           (.setMap marker map)
           marker))))))

(defn- display-route [owner start end]
  "Requests route and displays it, creates a DirectionsRender object if one doesn't exist already"
  (go
    (let [dirs (<! (directions/request-directions start end))]
      (if-let [dr (om/get-state owner :dir-rend)]
        (.setDirections dr dirs)
        (let [opts #js {:suppressMarkers true}
              dr   (google.maps.DirectionsRenderer. opts)
              map  (om/get-state owner :map)]
          (doto dr
            (.setMap map)
            (.setDirections dirs))
          (om/set-state! owner :dir-rend dr))))))

(defn- try-route
  "Tries to create a route if origin and destination are both set"
  [owner]
  (let [origin (om/get-state owner :origin)
        dest   (om/get-state owner :destination)]
    (when (and (:marker origin) (:marker dest))
      (display-route owner origin dest))))

(defn- position-to-current
  "Set origin/destination to user's current location"
  [owner type]
  (let [ctr (om/get-state owner :center)]
    (go
      (let [lat      (:lat ctr)
            lon      (:lon ctr)
            marker   (create-marker owner ctr type)
            locality (<! (geo/geocode lat lon))
            point    {:lat lat :lon lon :marker marker :locality locality}]
        (om/set-state! owner type point)
        (try-route owner)))))

(defn- position-by-input
  "Set origin/destination using the text box input"
  [owner type]
  (let [locality (om/get-state owner [type :locality])
        last-req (om/get-state owner :last-req)]
    (when (not (blank? locality))
      (when (not (= locality last-req))
        (go
          (let [{:keys [address lat lon] :as res} (<! (geo/geocode locality))
                marker (create-marker owner res type)
                point {:lat lat :lon lon :marker marker :locality address}]
            (om/set-state! owner :last-req locality)
            (pan-map owner lat lon)
            (om/set-state! owner type point)
            (try-route owner)))))))

(defn- handle-input
  "Handle text input"
  [owner type event]
  (om/set-state! owner [type :locality] (.. event -target -value)))

(defn- select-text
  "Select the whole text on focus"
  [owner ref]
  (let [node (om/get-node owner ref)]
    (.select node)))

(defcomponent create-ride
              "Create ride first step: locations"
              [data owner {:keys [lat lon zoom] :or {lat 38.752739
                                                                 lon -9.184769
                                                                 zoom 14}}]
              (init-state [_]
                          {:dir-rend    nil
                           :last-req    ""
                           :driving?    true
                           :map         nil
                           :center      {:lat lat :lon lon}
                           :origin      {:lat nil :lon nil :marker nil :locality ""}
                           :destination {:lat nil :lon nil :marker nil :locality ""}})
              (did-mount [_]
                         (let [map-node (om/get-node owner "map")
                               options #js {:center (google.maps.LatLng. lat lon)
                                            :zoom zoom
                                            :mapTypeId google.maps.MapTypeId.ROADMAP}]
                           (om/set-state! owner :map (google.maps.Map. map-node options))
                           (center-map owner)))
              (render [_]
                      (dom/div {:class "row"}
                               (dom/h1 "Create Ride")
                               (dom/div {:class "col-md-6"}
                                        (dom/div {:role "form" :class "jumbotron"}
                                                 (dom/div {:class "form-group"}
                                                          (dom/label "Willing to drive?")
                                                          (dom/div {:class "btn-group btn-group-justified"}
                                                                   (dom/div {:class "btn-group"}
                                                                            (dom/button {:class     (str "btn btn-default "
                                                                                                         (util/active (om/get-state owner :driving?)))
                                                                                         :type      "button"
                                                                                         :on-click #(om/set-state! owner :driving? true)}
                                                                                         "Yes"))
                                                                   (dom/div {:class "btn-group"}
                                                                            (dom/button {:class     (str "btn btn-default "
                                                                                                         (util/active (not (om/get-state owner :driving?))))
                                                                                         :type      "button"
                                                                                         :on-click #(om/set-state! owner :driving? false)}
                                                                                        "No"))))
                                                 (dom/div {:class "form-group"}
                                                          (dom/label "Starting from: ")
                                                          (dom/div {:class "input-group"}
                                                                   (dom/input {:class       "form-control"
                                                                               :type        "text"
                                                                               :placeholder "Origin"
                                                                               :value       (om/get-state owner [:origin :locality])
                                                                               :on-change   #(handle-input owner :origin %)
                                                                               :on-blur     #(position-by-input owner :origin)
                                                                               :on-focus    #(select-text owner "orig")
                                                                               :on-mouse-up #(.preventDefault %)
                                                                               :ref         "orig"}
                                                                              (dom/span {:class "input-group-btn"}
                                                                                        (dom/button {:class    "btn btn-default"
                                                                                                     :on-click #(position-to-current owner :origin)}
                                                                                                    "Current")))))
                                                 (dom/div {:class "form-group"}
                                                          (dom/label "Going to: ")
                                                          (dom/div {:class "input-group"}
                                                                   (dom/input {:class       "form-control"
                                                                               :type        "text"
                                                                               :placeholder "Destination"
                                                                               :value       (om/get-state owner [:destination :locality])
                                                                               :on-change   #(handle-input owner :destination %)
                                                                               :on-blur     #(position-by-input owner :destination)
                                                                               :on-focus    #(select-text owner "dest")
                                                                               :on-mouse-up #(.preventDefault %)
                                                                               :ref         "dest"}
                                                                              (dom/span {:class "input-group-btn"}
                                                                                        (dom/button {:class    "btn btn-default"
                                                                                                     :on-click #(position-to-current owner :destination)}
                                                                                                    "Current")))))
                                                 (dom/button {:class "btn btn-primary pull-right"
                                                              :on-click #(secretary/dispatch! "/ride/create/2")}
                                                             "Next")))
                               (dom/div {:class "col-md-6"}
                                        (dom/div {:style {:height "350px"} :ref "map"})))))

(defn- every-weekday?
  "True if every weekday is checked"
  [owner]
  (let [days (om/get-state owner :weekdays)]
    (every? (fn [checked?] checked?) (vals days))))

(defn- toggle-all
  "Turn all weekdays into the checked argument state"
  [owner checked]
  (let [days (keys (om/get-state owner :weekdays))]
    (doseq [day days]
      (om/set-state! owner [:weekdays day] checked))))

(defn- toggle-weekdays
  "Toggle every weekday"
  [owner]
  (if (every-weekday? owner)
    (toggle-all owner false)
    (toggle-all owner true)))

(defn- toggle-item
  "Toggle the selected day"
  [owner day]
  (cond
    (= day :weekdays) (toggle-weekdays owner)
    (= day :saturday) (om/update-state! owner :saturday not)
    (= day :sunday)   (om/update-state! owner :sunday not)
    :else             (om/update-state! owner [:weekdays day] not)))

(defn- get-time
  "Set component state based on TimePicker value"
  [owner]
  (let [picker (.pickatime (js/$ ".timepicker") "picker")
        time   (.get picker "value")]
    (om/set-state! owner :time time)))

(defn- pick-time
  "Opens de TimePicker so that an hour can be selected"
  []
  (let [picker (js/$ ".timepicker")]
    (.pickatime picker)))

(defn- get-date
  "Set component state based on DatePicker value"
  [owner]
  (let [picker (.pickadate (js/$ ".datepicker") "picker")
        date   (.get picker "value")]
    (om/set-state! owner :date date)))

(defn- pick-date
  "Opens de DatePicker so that a date can be selected"
  []
  (let [picker (js/$ ".datepicker")]
    (.pickadate picker)))

(defcomponent second-step
              "Create ride second step: time and day"
              [data owner]
              (init-state [_]
                          {:date       ""
                           :time       ""
                           :recurrent? false
                           :weekdays   {:monday    false
                                        :tuesday   false
                                        :wednesday false
                                        :thursday  false
                                        :friday    false}
                           :saturday   false
                           :sunday     false})
              (will-mount [_]
                          (let [link1 (gdom/createElement "script")
                                link2 (gdom/createElement "script")
                                link3 (gdom/createElement "script")
                                css1  (gdom/createElement "link")
                                css2  (gdom/createElement "link")
                                css3  (gdom/createElement "link")
                                head (aget (gdom/getElementsByTagNameAndClass "head") 0)]
                            (doto link1
                              (.setAttribute "src" "/public/js/picker.js")
                              (.setAttribute "type" "text/javascript"))
                            (doto link2
                              (.setAttribute "src" "/public/js/picker.date.js")
                              (.setAttribute "type" "text/javascript"))
                            (doto link3
                              (.setAttribute "src" "/public/js/picker.time.js")
                              (.setAttribute "type" "text/javascript"))
                            (doto css1
                              (.setAttribute "href" "/public/css/datepicker/default.css")
                              (.setAttribute "type" "text/css")
                              (.setAttribute "rel" "stylesheet"))
                            (doto css2
                              (.setAttribute "href" "/public/css/datepicker/default.date.css")
                              (.setAttribute "type" "text/css")
                              (.setAttribute "rel" "stylesheet"))
                            (doto css3
                              (.setAttribute "href" "/public/css/datepicker/default.time.css")
                              (.setAttribute "type" "text/css")
                              (.setAttribute "rel" "stylesheet"))
                            (doto head
                              (.appendChild link1)
                              (.appendChild link2)
                              (.appendChild link3)
                              (.appendChild css1)
                              (.appendChild css2)
                              (.appendChild css3))))
              (render [_]
                      (dom/div {:class "row"}
                               (dom/h1 "Create Ride")
                               (dom/div {:class "col-md-6 col-md-offset-3"}
                                        (dom/div {:role "form" :class "jumbotron"}
                                                 (dom/div {:class "form-group"}
                                                          (dom/label "Recurrent ride?")
                                                          (dom/div {:class "btn-group btn-group-justified"}
                                                                   (dom/div {:class "btn-group"}
                                                                            (dom/button {:class     (str "btn btn-default "
                                                                                                         (util/active (om/get-state owner :recurrent?)))
                                                                                         :type      "button"
                                                                                         :on-click #(om/set-state! owner :recurrent? true)}
                                                                                        "Yes"))
                                                                   (dom/div {:class "btn-group"}
                                                                            (dom/button {:class     (str "btn btn-default "
                                                                                                         (util/active (not (om/get-state owner :recurrent?))))
                                                                                         :type      "button"
                                                                                         :on-click #(om/set-state! owner :recurrent? false)}
                                                                                        "No"))))
                                                 (dom/div {:class "form-group"
                                                           :style {:display (util/display (not (om/get-state owner :recurrent?)))}}
                                                          (dom/label "Pick a day: ")
                                                          (dom/input {:class       "datepicker pull-right"
                                                                      :type        "text"
                                                                      :placeholder "Date"
                                                                      :on-focus    pick-date
                                                                      :on-blur     #(get-date owner)}))
                                                 (dom/div {:class "form-group"}
                                                          (dom/label "Pick a time of day: ")
                                                          (dom/input {:class       "timepicker pull-right"
                                                                      :type        "text"
                                                                      :placeholder "Time"
                                                                      :on-focus    pick-time
                                                                      :on-blur     #(get-time owner)}))
                                                 (dom/div {:class "form-group"
                                                           :style {:display (util/display (om/get-state owner :recurrent?))}}
                                                          (dom/label "Which days?")
                                                          (dom/ul {:class "list-group"}
                                                                  (dom/li {:class "list-group-item"
                                                                           :on-click #(toggle-item owner :weekdays)}
                                                                          "Every Weekday"
                                                                          (dom/span {:class "glyphicon glyphicon-ok pull-right"
                                                                                     :style {:display (util/display (every-weekday? owner))}}))
                                                                  (dom/li {:class "list-group-item"
                                                                           :on-click #(toggle-item owner :monday)}
                                                                          "Monday"
                                                                          (dom/span {:class "glyphicon glyphicon-ok pull-right"
                                                                                     :style {:display (util/display
                                                                                                        (om/get-state owner [:weekdays :monday]))}}))
                                                                  (dom/li {:class "list-group-item"
                                                                           :on-click #(toggle-item owner :tuesday)}
                                                                          "Tuesday"
                                                                          (dom/span {:class "glyphicon glyphicon-ok pull-right"
                                                                                     :style {:display (util/display
                                                                                                        (om/get-state owner [:weekdays :tuesday]))}}))
                                                                  (dom/li {:class "list-group-item"
                                                                           :on-click #(toggle-item owner :wednesday)}
                                                                          "Wednesday"
                                                                          (dom/span {:class "glyphicon glyphicon-ok pull-right"
                                                                                     :style {:display (util/display
                                                                                                        (om/get-state owner [:weekdays :wednesday]))}}))
                                                                  (dom/li {:class "list-group-item"
                                                                           :on-click #(toggle-item owner :thursday)}
                                                                          "Thursday"
                                                                          (dom/span {:class "glyphicon glyphicon-ok pull-right"
                                                                                     :style {:display (util/display
                                                                                                        (om/get-state owner [:weekdays :thursday]))}}))
                                                                  (dom/li {:class "list-group-item"
                                                                           :on-click #(toggle-item owner :friday)}
                                                                          "Friday"
                                                                          (dom/span {:class "glyphicon glyphicon-ok pull-right"
                                                                                     :style {:display (util/display
                                                                                                        (om/get-state owner [:weekdays :friday]))}}))
                                                                  (dom/li {:class "list-group-item"
                                                                           :on-click #(toggle-item owner :saturday)}
                                                                          "Saturday"
                                                                          (dom/span {:class "glyphicon glyphicon-ok pull-right"
                                                                                     :style {:display (util/display
                                                                                                        (om/get-state owner :saturday))}}))
                                                                  (dom/li {:class "list-group-item"
                                                                           :on-click #(toggle-item owner :sunday)}
                                                                          "Sunday"
                                                                          (dom/span {:class "glyphicon glyphicon-ok pull-right"
                                                                                     :style {:display (util/display
                                                                                                        (om/get-state owner :sunday))}}))))
                                                 (dom/button {:class    "btn btn-primary pull-left"
                                                              :on-click #(secretary/dispatch! "/ride/create")}
                                                             "Previous")
                                                 (dom/button {:class    "btn btn-primary pull-right"
                                                              :on-click #(secretary/dispatch! "/ride/create/3")}
                                                             "Next"))))))

(defn- handle-notes-input
  "Handle notes/info text input"
  [owner event]
  (om/set-state! owner :notes (.. event -target -value)))

(defn- handle-numeric-input
  "Validate and handle seat input"
  [owner state {:keys [min max]} event]
  (let [val     (.. event -target -value)
        old     (om/get-state owner state)]
    (if (re-find #"^\d+$" val)
      (let [new (gint/fromString val)]
        (if (and (< new max) (>= new min))
          (om/set-state! owner state new)
          (om/set-state! owner state old)))
      (om/set-state! owner state old))))

(defcomponent final-step
              "Create ride final step: vacant seats and ride price"
              [data owner]
              (init-state [_]
                          {:cash? false
                           :seats 1
                           :price 10
                           :notes ""})
              (render-state [_ {:keys [cash? seats price notes]}]
                            (dom/div {:class "row"}
                                     (dom/h1 "Create Ride")
                                     (dom/div {:class "col-md-6 col-md-offset-3"}
                                              (dom/div {:role "form" :class "jumbotron"}
                                                       (dom/div {:class "form-group"}
                                                                (dom/label "Do you accept online payment?")
                                                                (dom/div {:class "btn-group btn-group-justified"}
                                                                         (dom/div {:class "btn-group"}
                                                                                  (dom/button {:class    (str "btn btn-default "
                                                                                                              (util/active cash?))
                                                                                               :type     "button"
                                                                                               :on-click #(om/set-state! owner :cash? true)}
                                                                                              "Cash only"))
                                                                         (dom/div {:class "btn-group"}
                                                                                  (dom/button {:class    (str "btn btn-default "
                                                                                                              (util/active (not cash?)))
                                                                                               :type     "button"
                                                                                               :on-click #(om/set-state! owner :cash? false)}
                                                                                              "Accept online"))))
                                                       (dom/div {:class "form-group"}
                                                                (dom/label "Contribution asked for: ")
                                                                (dom/div {:class "input-group"}
                                                                         (dom/span {:class "input-group-addon"} "€")
                                                                         (dom/input {:class       "form-control pull-right"
                                                                                     :type        "text"
                                                                                     :ref         "price"
                                                                                     :value       price
                                                                                     :on-change   #(handle-numeric-input owner :price {:min 1 :max 9999} %)
                                                                                     :on-focus    #(select-text owner "price")
                                                                                     :on-mouse-up #(.preventDefault %)
                                                                                     :style       {:text-align "right"}})
                                                                         (dom/span {:class "input-group-addon"} ".00")))
                                                       (dom/div {:class "form-group"}
                                                                (dom/label "Number of vacant seats: ")
                                                                (dom/input {:class       "pull-right"
                                                                            :type        "number"
                                                                            :min         "1"
                                                                            :max         "8"
                                                                            :ref         "seats"
                                                                            :value       seats
                                                                            :on-change   #(handle-numeric-input owner :seats {:min 1 :max 9} %)
                                                                            :on-focus    #(select-text owner "seats")
                                                                            :on-mouse-up #(.preventDefault %)
                                                                            :style       {:min-width "50px"}}))
                                                       (dom/div {:class "form-group"}
                                                                (dom/label "Additional notes/info : ")
                                                                (dom/textarea {:class "form-control"
                                                                               :rows  "5"
                                                                               :on-change #(handle-notes-input owner %)
                                                                               :value notes}))
                                                       (dom/button {:class    "btn btn-primary pull-left"
                                                                    :on-click #(secretary/dispatch! "/ride/create/2")}
                                                                   "Previous")
                                                       (dom/button {:class "btn btn-primary pull-right"
                                                                    :on-click #(secretary/dispatch! "/ride/create/done")}
                                                                   "Finish"))))))

(defcomponent ride-done
              "Ride created message"
              [data owner]
              (render [_]
                      (dom/div {:class "row"}
                               (dom/div {:class "col-md-10 col-md-offset-1"}
                                        (dom/div {:class "alert alert-success"
                                                  :role  "alert"
                                                  :style {:text-align "center"}}
                                                 (dom/h3 "Ride successfully created!"))))))
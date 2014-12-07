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
  ([data owner {:keys [lat lon] :as coords} type]
   (when coords
     (let [pos (google.maps.LatLng. lat lon)
           map (om/get-state owner :map)
           marker (get-in @data [:ride :first-step type :marker])]
       (if marker
         (do
           (.setPosition marker pos)
           marker)
         (let [options #js {:position pos}
               marker  (google.maps.Marker. options)]
           (.setMap marker map)
           marker))))))

(defn- display-route
  "Requests route and displays it, creates a DirectionsRender object if one doesn't exist already"
  [owner start end]
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
  [data owner]
  (let [origin (get-in @data [:ride :first-step :origin])
        dest   (get-in @data [:ride :first-step :destination])]
    (when (and (:marker origin) (:marker dest))
      (display-route owner origin dest))))

(defn- position-to-current
  "Set origin/destination to user's current location"
  [data owner type]
  (let [ctr (om/get-state owner :center)]
    (go
      (let [lat      (:lat ctr)
            lon      (:lon ctr)
            marker   (create-marker data owner ctr type)
            locality (<! (geo/geocode lat lon))
            point    {:lat lat :lon lon :marker marker :locality locality}]
        (om/update! data [:ride :first-step type] point)
        (try-route data owner)))))

(defn- position-by-input
  "Set origin/destination using the text box input"
  [data owner type]
  (let [locality (get-in @data [:ride :first-step type :locality])
        last-req (om/get-state owner :last-req)]
    (when (not (blank? locality))
      (when (not (= locality last-req))
        (go
          (let [{:keys [address lat lon] :as res} (<! (geo/geocode locality))
                marker (create-marker data owner res type)
                point {:lat lat :lon lon :marker marker :locality address}]
            (om/set-state! owner :last-req locality)
            (pan-map owner lat lon)
            (om/update! data [:ride :first-step type] point)
            (try-route data owner)))))))

(defn- handle-input
  "Handle text input"
  [data type event]
  (om/update! data
              [:ride :first-step type :locality]
              (.. event -target -value)))

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
                           :map         nil
                           :center      {:lat lat :lon lon}})
              (did-mount [_]
                         (let [map-node (om/get-node owner "map")
                               options #js {:center (google.maps.LatLng. lat lon)
                                            :zoom zoom
                                            :mapTypeId google.maps.MapTypeId.ROADMAP}]
                           (om/set-state! owner :map (google.maps.Map. map-node options))
                           (center-map owner)
                           (.log js/console )))
              (render [_]
                      (dom/div {:class "row"}
                               (dom/h1 "Create Ride")
                               (dom/div {:class "col-md-5 col-md-offset-1"}
                                        (dom/div {:role "form" :class "jumbotron"}
                                                 (dom/div {:class "form-group"}
                                                          (dom/label "Willing to drive?")
                                                          (dom/div {:class "btn-group btn-group-justified"}
                                                                   (dom/div {:class "btn-group"}
                                                                            (dom/button {:class     (str "btn btn-default "
                                                                                                         (util/active (get-in data [:ride :first-step :driving?])))
                                                                                         :type      "button"
                                                                                         :on-click #(om/update! data [:ride :first-step :driving?] true)}
                                                                                         "Yes"))
                                                                   (dom/div {:class "btn-group"}
                                                                            (dom/button {:class     (str "btn btn-default "
                                                                                                         (util/active (not (get-in data [:ride :first-step :driving?]))))
                                                                                         :type      "button"
                                                                                         :on-click #(om/update! data [:ride :first-step :driving?] false)}
                                                                                        "No"))))
                                                 (dom/div {:class "form-group"}
                                                          (dom/label "Starting from: ")
                                                          (dom/div {:class "input-group"}
                                                                   (dom/input {:class       "form-control"
                                                                               :type        "text"
                                                                               :placeholder "Origin"
                                                                               :value       (get-in data [:ride :first-step :origin :locality])
                                                                               :on-change   #(handle-input data :origin %)
                                                                               :on-blur     #(position-by-input data owner :origin)
                                                                               :on-focus    #(select-text owner "orig")
                                                                               :on-mouse-up #(.preventDefault %)
                                                                               :ref         "orig"}
                                                                              (dom/span {:class "input-group-btn"}
                                                                                        (dom/button {:class    "btn btn-default"
                                                                                                     :on-click #(position-to-current data owner :origin)}
                                                                                                    "Current")))))
                                                 (dom/div {:class "form-group"}
                                                          (dom/label "Going to: ")
                                                          (dom/div {:class "input-group"}
                                                                   (dom/input {:class       "form-control"
                                                                               :type        "text"
                                                                               :placeholder "Destination"
                                                                               :value       (get-in data [:ride :first-step :destination :locality])
                                                                               :on-change   #(handle-input data :destination %)
                                                                               :on-blur     #(position-by-input data owner :destination)
                                                                               :on-focus    #(select-text owner "dest")
                                                                               :on-mouse-up #(.preventDefault %)
                                                                               :ref         "dest"}
                                                                              (dom/span {:class "input-group-btn"}
                                                                                        (dom/button {:class    "btn btn-default"
                                                                                                     :on-click #(position-to-current data owner :destination)}
                                                                                                    "Current")))))
                                                 (dom/button {:class "btn btn-primary pull-right"
                                                              :on-click #(secretary/dispatch! "/ride/create/2")}
                                                             "Next")))
                               (dom/div {:class "col-md-5"}
                                        (dom/div {:style {:height "350px"} :ref "map"})))))

(defn- every-weekday?
  "True if every weekday is checked"
  [data]
  (let [days (get-in data [:ride :second-step :weekdays])]
    (every? (fn [checked?] checked?) (vals days))))

(defn- toggle-all
  "Turn all weekdays into the checked argument"
  [data checked]
  (let [days (keys (get-in @data [:ride :second-step :weekdays]))]
    (doseq [day days]
      (om/update! data [:ride :second-step :weekdays day] checked))))

(defn- toggle-weekdays
  "Toggle every weekday"
  [data]
  (if (every-weekday? @data)
    (toggle-all data false)
    (toggle-all data true)))

(defn- toggle-item
  "Toggle the selected day"
  [data day]
  (cond
    (= day :weekdays) (toggle-weekdays data)
    (= day :saturday) (om/transact! data [:ride :second-step :saturday] not)
    (= day :sunday)   (om/transact! data [:ride :second-step :sunday] not)
    :else             (om/transact! data [:ride :second-step :weekdays day] not)))

(defn- get-time
  "Set component state based on TimePicker value"
  [data]
  (let [picker (.pickatime (js/$ ".timepicker") "picker")
        time   (.get picker "value")]
    (om/update! data [:ride :second-step :time] time)))

(defn- pick-time
  "Opens de TimePicker so that an hour can be selected"
  []
  (let [picker (js/$ ".timepicker")]
    (.pickatime picker)))

(defn- get-date
  "Set component state based on DatePicker value"
  [data]
  (let [picker (.pickadate (js/$ ".datepicker") "picker")
        date   (.get picker "value")]
    (om/update! data [:ride :second-step :date] date)))

(defn- pick-date
  "Opens de DatePicker so that a date can be selected"
  []
  (let [picker (js/$ ".datepicker")]
    (.pickadate picker)))

(defcomponent second-step
              "Create ride second step: time and day"
              [data owner]
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
                                                                                                         (util/active (get-in data [:ride :second-step :recurrent?])))
                                                                                         :type      "button"
                                                                                         :on-click #(om/update! data [:ride :second-step :recurrent?] true)}
                                                                                        "Yes"))
                                                                   (dom/div {:class "btn-group"}
                                                                            (dom/button {:class     (str "btn btn-default "
                                                                                                         (util/active (not (get-in data [:ride :second-step :recurrent?]))))
                                                                                         :type      "button"
                                                                                         :on-click #(om/update! data [:ride :second-step :recurrent?] false)}
                                                                                        "No"))))
                                                 (dom/div {:class "form-group"
                                                           :style {:display (util/display (not (get-in data [:ride :second-step :recurrent?])))}}
                                                          (dom/label "Pick a day: ")
                                                          (dom/input {:class       "datepicker pull-right"
                                                                      :type        "text"
                                                                      :placeholder "Date"
                                                                      :on-focus    pick-date
                                                                      :on-blur     #(get-date data)}))
                                                 (dom/div {:class "form-group"}
                                                          (dom/label "Pick a time of day: ")
                                                          (dom/input {:class       "timepicker pull-right"
                                                                      :type        "text"
                                                                      :placeholder "Time"
                                                                      :on-focus    pick-time
                                                                      :on-blur     #(get-time data)}))
                                                 (dom/div {:class "form-group"
                                                           :style {:display (util/display (get-in data [:ride :second-step :recurrent?]))}}
                                                          (dom/label "Which days?")
                                                          (dom/ul {:class "list-group"}
                                                                  (dom/li {:class "list-group-item"
                                                                           :on-click #(toggle-item data :weekdays)}
                                                                          "Every Weekday"
                                                                          (dom/span {:class "glyphicon glyphicon-ok pull-right"
                                                                                     :style {:display (util/display (every-weekday? data))}}))
                                                                  (dom/li {:class "list-group-item"
                                                                           :on-click #(toggle-item data :monday)}
                                                                          "Monday"
                                                                          (dom/span {:class "glyphicon glyphicon-ok pull-right"
                                                                                     :style {:display (util/display
                                                                                                        (get-in data [:ride :second-step :weekdays :monday]))}}))
                                                                  (dom/li {:class "list-group-item"
                                                                           :on-click #(toggle-item data :tuesday)}
                                                                          "Tuesday"
                                                                          (dom/span {:class "glyphicon glyphicon-ok pull-right"
                                                                                     :style {:display (util/display
                                                                                                        (get-in data [:ride :second-step :weekdays :tuesday]))}}))
                                                                  (dom/li {:class "list-group-item"
                                                                           :on-click #(toggle-item data :wednesday)}
                                                                          "Wednesday"
                                                                          (dom/span {:class "glyphicon glyphicon-ok pull-right"
                                                                                     :style {:display (util/display
                                                                                                        (get-in data [:ride :second-step :weekdays :wednesday]))}}))
                                                                  (dom/li {:class "list-group-item"
                                                                           :on-click #(toggle-item data :thursday)}
                                                                          "Thursday"
                                                                          (dom/span {:class "glyphicon glyphicon-ok pull-right"
                                                                                     :style {:display (util/display
                                                                                                        (get-in data [:ride :second-step :weekdays :thursday]))}}))
                                                                  (dom/li {:class "list-group-item"
                                                                           :on-click #(toggle-item data :friday)}
                                                                          "Friday"
                                                                          (dom/span {:class "glyphicon glyphicon-ok pull-right"
                                                                                     :style {:display (util/display
                                                                                                        (get-in data [:ride :second-step :weekdays :friday]))}}))
                                                                  (dom/li {:class "list-group-item"
                                                                           :on-click #(toggle-item data :saturday)}
                                                                          "Saturday"
                                                                          (dom/span {:class "glyphicon glyphicon-ok pull-right"
                                                                                     :style {:display (util/display
                                                                                                        (get-in data [:ride :second-step :saturday]))}}))
                                                                  (dom/li {:class "list-group-item"
                                                                           :on-click #(toggle-item data :sunday)}
                                                                          "Sunday"
                                                                          (dom/span {:class "glyphicon glyphicon-ok pull-right"
                                                                                     :style {:display (util/display
                                                                                                        (get-in data [:ride :second-step :sunday]))}}))))
                                                 (dom/button {:class    "btn btn-primary pull-left"
                                                              :on-click #(secretary/dispatch! "/ride/create")}
                                                             "Previous")
                                                 (if (get-in data [:ride :first-step :driving?])
                                                   (dom/button {:class    "btn btn-primary pull-right"
                                                              :on-click #(secretary/dispatch! "/ride/create/3")}
                                                             "Next")
                                                   (dom/button {:class "btn btn-primary pull-right"
                                                                :on-click #(secretary/dispatch! "/ride/create/done")}
                                                               "Finish")))))))

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
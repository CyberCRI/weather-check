(ns weather-check.core
    (:require [reagent.core :as reagent :refer [atom create-class]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]
              [reagent-forms.core :refer [bind-fields]]
              [ajax.core :refer [GET POST]]
              [clojure.string :as string]))

;; -------------------------
;; Views

(defn home-page []
  [:div [:h2 "Welcome to weather check"]
   [:div [:a {:href "/form"} "go to form"]]
   [:div [:a {:href "/weather"} "see the weather"]]])


; Form page
(def phrases [
  "People don't talk to me"
  "I'm not invited to activities"
  "I am judged for what I am"
  "I've been denied an opportunity"
  "I've been verbally abused"
  "I've been looked down on"
  "I'm feeling left out"
  "I've been teased or mocked"
  "People give me a mean look"
  ])

(defn row [index label]
  (let [id (str "index-" index)]
    [:div 
      [:input {:field :checkbox :id id}]
      [:label {:for id} label]]))

(def form-template 
  [:div (map-indexed row phrases)])

(defn set-outstanding-request [outstanding-request bool]
  (swap! outstanding-request #(constantly bool)))

(defn send-clouds [clouds-state outstanding-request]
  (let [index-names (keys (filter #(second %) @clouds-state)) ; Get a list of the keys with true values in the state map 
        phrases (map #(nth phrases (js/parseInt (second (string/split % "-")))) index-names) ; Convert names like "index-1" into the corresponding phrase
        clouds {:clouds phrases} 
        clouds-value (clj->js clouds)]
    (.log js/console clouds-value)
    (set-outstanding-request outstanding-request true)
    (POST "/api/clouds" {:params clouds-value 
                         :format :json 
                         :handler #(accountant/navigate! "/")
                         :finally #(set-outstanding-request outstanding-request false)})))

(defn form-page []
  (let [clouds (atom {})
        outstanding-request (atom false)]
    (fn []
      [:div [:h2 "Tell us about the weather"]
       [:p "What have you experienced?"]
       [bind-fields form-template clouds]
       [:button {:on-click #(send-clouds clouds outstanding-request) :disabled @outstanding-request} "Send the Weather"]])))

;;;; Weather page
(defrecord Cloud 
  [phrase ; String
   importance ; Number in [0, 1]
   animating ; Bool
   position ; 2D vector
  ])

(defn draw-cloud [{:keys [animating position phrase]} cloud] 
  [:object {:type "image/svg+xml" 
            :data "/images/cloud.svg"
            :class (if animating "cloud animating-cloud" "cloud")
            :style {:transform (str "translate(" (first position) "px, " (second position) "px)") }} ])

(defn draw-clouds [clouds] [:div (for [cloud clouds] ^{:key (:phrase cloud)} [draw-cloud cloud])])

(defn centered-rand [magnitude] (- (rand magnitude) (/ magnitude 2)))

(defn update-clouds [clouds]
  (let [canvas (.getElementById js/document "weather-container")
        canvas-width (.-clientWidth canvas)
        canvas-height (.-clientHeight canvas)]
    (for [{[x y] :position :as cloud} clouds]
      ; TODO: get size of cloud
      (if (> x canvas-width)
        ; If out of canvas, restart on the left
        (-> cloud
          (assoc :position [-400 (rand canvas-height)])
          (assoc :animating false))
        ; Otherwise move towards the right with a random vertical 
        (-> cloud
          (assoc :position [(+ x 50) (+ y (centered-rand 50))])
          (assoc :animating true))))))

(defn weather-page []
  (let [clouds (atom [(Cloud. "Hi" 0.5 true [300 100]) (Cloud. "Bye" 0.5 true [100 300])])
        callback #(swap! clouds update-clouds)
        interval-id (atom nil)]
    (create-class 
      {:component-did-mount #(reset! interval-id (js/setInterval callback 1000))
       :component-will-unmount #(js/clearInterval @interval-id)
       :reagent-render 
         (fn [] [:div { :id "weather-container" } 
            [:h2 "Weather"]
            [draw-clouds @clouds]])})))

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/form" []
  (session/put! :current-page #'form-page))

(secretary/defroute "/weather" []
  (session/put! :current-page #'weather-page))

;; -------------------------
;; Initialize app

(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (accountant/configure-navigation!
    {:nav-handler
     (fn [path]
       (secretary/dispatch! path))
     :path-exists?
     (fn [path]
       (secretary/locate-route path))})
  (accountant/dispatch-current!)
  (mount-root))

(ns weather-check.core
    (:require [reagent.core :as reagent :refer [atom create-class]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]
              [reagent-forms.core :refer [bind-fields]]
              [ajax.core :refer [GET POST]]
              [clojure.string :as string]
              [clojure.walk :refer [keywordize-keys stringify-keys]]))


;; -------------------------
;; Views

(defn home-page []
  [:div [:h2 "Welcome to weather check"]])

(defn thanks-page []
  [:div [:h2 "Thanks for telling us how you feel"]])

(defn create-group-page []
  [:div [:h2 "Make a new group"]])

(defn group-page [group-id]
  [:div [:h2 "Group " group-id] 
   [:p [:a {:href (str "/group/" group-id "/form") } "Tell your feelings"]]
   [:p [:a {:href (str "/group/" group-id "/weather")} "See the weather"]]])


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
  "People give me a mean look"])

(defn row [index label]
  (let [id (str "index-" index)]
    ^{:key index} [:div 
      [:input {:field :checkbox :id id}]
      [:label {:for id} label]]))

(def form-template 
  [:div (map-indexed row phrases)])

(defn send-clouds [group-id clouds-state outstanding-request error-message]
  (let [index-names (keys (filter #(second %) @clouds-state)) ; Get a list of the keys with true values in the state map 
        phrases (map #(nth phrases (js/parseInt (second (string/split % "-")))) index-names) ; Convert names like "index-1" into the corresponding phrase
        clouds {:clouds phrases} 
        clouds-value (clj->js clouds)]
    (.log js/console clouds-value)
    (reset! outstanding-request true)
    (prn "group-id" group-id)
    (POST (str "/api/groups/" group-id) 
          {:params clouds-value 
           :format :json 
           :handler #(accountant/navigate! "/thanks")
           :error-handler #(reset! error-message "Error submitting. Please try again later.")
           :finally #(reset! outstanding-request false)})))

(defn form-page [group-id]
  (let [clouds (atom {})
        outstanding-request (atom false)
        error-message (atom nil)]
    (fn []
      [:div [:h2 "Tell us about the weather"]
       [:p "What have you experienced?"]
       [bind-fields form-template clouds]
       [:p.error-message @error-message]
       [:button {:on-click #(send-clouds group-id clouds outstanding-request error-message) :disabled @outstanding-request} "Send the Weather"]])))

;;;; Weather page
(defrecord Cloud 
  [phrase ; String
   importance ; Number in [0, 1]
   percent ; Number in [0, 1]
   position ; 2D vector
  ])

(def cloud-speed-x 25)
(def updates-between-clouds 15)

(defn draw-cloud [{:keys [position phrase importance percent]} cloud] 
  [:div.cloud {:style {:transform (str "translate(" (first position) "px, " (second position) "px)") 
                       :opacity importance}}
    [:object {:type "image/svg+xml" 
              :data "/images/cloud.svg"}]
    [:p.phrase phrase]
    [:p.count (str percent "%")]])

(defn draw-clouds [clouds] [:div (for [cloud clouds] ^{:key (:phrase cloud)} [draw-cloud cloud])])

(defn rand-in-range [x-min x-max] 
  (let [range (- x-max x-min)]
    (+ (rand range) x-min)))

; Takes off 300 from the height to avoid clouds moving out of screen
(defn canvas-size []
  (let [canvas (.getElementById js/document "weather-container")]
    [(.-clientWidth canvas) (- (.-clientHeight canvas) 300)]))

(defn clamp [x x-min x-max] (-> x (min x-max) (max x-min)))

(defn rand-starting-pos [canvas-width canvas-height]
  [-400 (rand-in-range (* 0.1 canvas-height) canvas-height)])

(defn make-clouds [{:keys [reply-count cloud-counts]}]
  (let [[canvas-width canvas-height] (canvas-size)
        indexed-phrases (map-indexed (fn [index [phrase count]] {:phrase (name phrase) :count count :index (+ index 1)}) cloud-counts)]
    (prn "indexed-phrases" indexed-phrases)
    (for [{:keys [phrase count index]} indexed-phrases]
      (Cloud. 
        phrase 
        ; Importance is the proportional to the % of people who thought it
        (-> count (/ reply-count) (* 2) (clamp 0.5 1))
        (int (* 100 (/ count reply-count)))
        nil))))

(defn add-clouds [counter clouds-off-screen clouds-on-screen]
  (if (>= @counter updates-between-clouds)
    (do 
      (when (>= (count @clouds-off-screen) 2)
        (let [[canvas-width canvas-height] (canvas-size)
              canvas-half-height (max 300 (/ 2 (+ canvas-height 300)))
              ; Take 2 clouds
              [cloud-top cloud-bottom & clouds-rest] @clouds-off-screen
               ; Give them initial positions
              cloud-top-positioned (assoc cloud-top :position [(rand-in-range -450 -500) 
                                                               (rand-in-range 0 50)])
              cloud-bottom-positioned (assoc cloud-bottom :position [(rand-in-range -450 -500) 
                                                                     (rand-in-range canvas-half-height (+ 50 canvas-half-height))])]
          (swap! clouds-on-screen concat [cloud-top-positioned cloud-bottom-positioned])
          (reset! clouds-off-screen clouds-rest)))
      (reset! counter 0))
    (swap! counter inc)))

(defn remove-clouds [clouds-off-screen clouds-on-screen]
  ; Separate on-screen into those that should stay and those should go
  (let [[canvas-width canvas-height] (canvas-size)
        is-on-screen (fn [{[x y] :position}] (< x (+ canvas-width 50)))
        { keep-on true take-off false } (group-by is-on-screen @clouds-on-screen)]
    (swap! clouds-off-screen concat take-off)
    (reset! clouds-on-screen keep-on)))
            
(defn move-clouds [clouds]
  (let [[canvas-width canvas-height] (canvas-size)]
    (for [{[x y] :position :as cloud} clouds]
      ; Move towards the right with a random vertical 
      (assoc cloud :position [(+ x cloud-speed-x) (clamp (+ y (rand-in-range -5 5)) 0 canvas-height)]))))

(defn update-clouds [counter clouds-off-screen clouds-on-screen]
  (add-clouds counter clouds-off-screen clouds-on-screen)
  (swap! clouds-on-screen move-clouds)
  (remove-clouds clouds-off-screen clouds-on-screen))

(defn weather-report [state]
  (if state
    (str "Weather from " (:reply-count state) " responses")
    "Loading weather")) 

(defn weather-page [group-id]
  (let [clouds-off-screen (atom [])
        clouds-on-screen (atom [])
        counter (atom updates-between-clouds)
        callback #(update-clouds counter clouds-off-screen clouds-on-screen)
        interval-id (atom nil)
        server-state (atom {})]
    (GET (str "/api/groups/" group-id) 
         {:handler (fn [state] 
            (js/console.log "ajax reply" (clj->js state))
            (reset! server-state (keywordize-keys state))
            (reset! clouds-off-screen (make-clouds @server-state)))}) 
    (create-class 
      {:component-did-mount #(reset! interval-id (js/setInterval callback 1000))
       :component-will-unmount #(js/clearInterval @interval-id)
       :reagent-render 
         (fn [] [:div { :id "weather-container" } 
                  [draw-clouds @clouds-on-screen]
                  [:p.summary (weather-report @server-state)]])})))

(defn current-page []
  (let [f (session/get :current-page)]
    [:div f]))

;; -------------------------
;; Routes

(secretary/defroute "/" []
  (session/put! :current-page [home-page]))

(secretary/defroute "/thanks" []
  (session/put! :current-page [thanks-page]))

(secretary/defroute "/group" []
  (session/put! :current-page [create-group-page]))

(secretary/defroute "/group/:group-id" [group-id]
  (session/put! :current-page [group-page group-id]))

(secretary/defroute group-form "/group/:group-id/form" [group-id]
  (session/put! :current-page [form-page group-id]))

(secretary/defroute group-weather "/group/:group-id/weather" [group-id]
  (session/put! :current-page [weather-page group-id]))

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

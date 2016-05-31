(ns weather-check.core
    (:require [reagent.core :as reagent :refer [atom]]
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
  ])

(defn row [index label]
  (let [id (str "index-" index)]
    [:div 
      [:input {:field :checkbox :id id}]
      [:label {:for id} label]]))

(def form-template 
  [:div (map-indexed row phrases)])

(defn send-clouds [clouds-state]
  (let [index-names (keys (filter #(second %) @clouds-state)) ; Get a list of the keys with true values in the state map 
        phrases (map #(nth phrases (js/parseInt (second (string/split % "-")))) index-names) ; Convert names like "index-1" into the corresponding phrase
        clouds {:clouds phrases} 
        clouds-value (clj->js clouds)]
    (.log js/console clouds-value)
    (POST "/api/clouds" {:params clouds-value :format :json})))

(defn form-page []
  (let [clouds (atom {})]
    (fn []
      [:div [:h2 "Tell us about the weather"]
       [:p "What have you experienced?"]
       [bind-fields form-template clouds]
       [:button {:on-click #(send-clouds clouds)} "Send the Weather"]])))


(defn weather-page []
  [:div [:h2 "Weather"]])


(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/form" []
  (session/put! :current-page #'form-page))

(secretary/defroute "/weather" []
  (session/put! :weather-page #'weather-page))

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

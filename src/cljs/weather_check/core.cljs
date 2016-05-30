(ns weather-check.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [accountant.core :as accountant]
              [reagent-forms.core :refer [bind-fields]]
              [ajax.core :refer [GET POST]]))

;; -------------------------
;; Views

(defn home-page []
  [:div [:h2 "Welcome to weather check"]
   [:div [:a {:href "/form"} "go to form"]]
   [:div [:a {:href "/weather"} "see the weather"]]])


; Form page
(defn row [label]
  [:div
    [:input {:field :checkbox :id label}]
    [:label {:for label} label]])

(def form-template 
  [:div
    (row "People don't talk to me")
    (row "I'm not invited to activities")])

(defn send-clouds [clouds-state]
  (let [clouds {:clouds (keys (filter #(second %) @clouds-state))} ; Get a list of the keys with true values in the state map 
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

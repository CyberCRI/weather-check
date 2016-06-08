(ns weather-check.handler
  (:require [compojure.core :refer [GET POST defroutes routes]]
            [compojure.route :refer [not-found resources]]
            [hiccup.page :refer [include-js include-css html5]]
            [weather-check.middleware :refer [wrap-middleware]]
            [config.core :refer [env]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.defaults :refer [site-defaults api-defaults wrap-defaults]]
            [ring.util.response :refer [response]]
            [clojure.java.jdbc :as jdbc]))

(def db {
  :dbtype "postgresql"
  :dbname "weather-check"
  ; :username ""
  ; :password ""
  })


(def state (atom {
                  ; Number of respondants
                  :reply-count 0
                  ; Map of phrases to the number of occurences
                  :cloud-counts {} })) 


;;; WEBSITE

(def mount-target
  [:div#app
      [:h3 "ClojureScript has not been compiled!"]
      [:p "please run "
       [:b "lein figwheel"]
       " in order to start the compiler"]])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))])

(def loading-page
  (html5
    (head)
    [:body {:class "body-container"}
     mount-target
     (include-js "/js/app.js")]))


;;; API

(defn create-group [request]
  (let [group-name (get-in request [:body "name"])
        group-id (apply str (repeatedly 7 #(rand-nth "ABCDEFGHIJKLMOPQRSTUVWXYZ")))]
    (jdbc/insert! db :groups { :group_id group-id :name group-name })
    (response {:id group-id})))

(defn get-counts [request]
  (response @state))

; ; If the value exists, calls inc on it, or starts with 1 
; (defn safe-inc [x] (inc (or x 0)))

; (defn update-state [old-state new-clouds]
;   (-> old-state
;     (update :reply-count inc) ; Increment reply count
;     ; Increment the value of each phrase in the map (will start from 0 if non-existant)
;     (update :cloud-counts 
;        #(reduce (fn [m phrase] (update m phrase safe-inc)) % new-clouds))))

(defn post-phrases [request]
  (let [group-id (get-in request [:params :group-id])
        phrases (get-in request [:body "clouds"])]
    (let [[{response-id :response_id}] (jdbc/insert! db :responses { :group_id group-id })]
      (jdbc/insert-multi! db :phrases (for [phrase phrases] { :response_id response-id :phrase phrase })))
    (response {})))


;;; ROUTES

(defroutes site-routes
  (GET "/" [] loading-page)
  (GET "/form" [] loading-page)
  (GET "/weather" [] loading-page))

(defroutes api-routes  
  (POST "/api/groups" [] (wrap-json-response (wrap-json-body create-group)))
  (GET "/api/groups/:group-id" [] (wrap-json-response (wrap-json-body get-counts)))
  (POST "/api/groups/:group-id" [] (wrap-json-response (wrap-json-body post-phrases))))

(defroutes other-routes
  (resources "/")
  (not-found "Not Found"))

(def app 
  (routes
    (wrap-middleware api-routes api-defaults) ; Not sure why this must go first in the list, but otherwise anti-forgery token required
    (wrap-middleware site-routes site-defaults)
    (wrap-middleware other-routes site-defaults)))

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
  (let [group-id (get-in request [:params :group-id])
        phrases (get-in request [:body "clouds"])
        rows (jdbc/query db ["select phrase, count(*) 
                            from phrases, responses 
                            where phrases.response_id = responses.response_id 
                            and group_id = ? group by phrase;" group-id])
        results-map (reduce (fn [m row] (assoc m (:phrase row) (:count row))) {} rows)]
    (response results-map)))

(defn post-phrases [request]
  (let [group-id (get-in request [:params :group-id])
        phrases (get-in request [:body "clouds"])
        ; Create response in DB
        [{response-id :response_id}] (jdbc/insert! db :responses { :group_id group-id })]
    ; Add phrases to DB
    (jdbc/insert-multi! db :phrases (for [phrase phrases] { :response_id response-id :phrase phrase })))
    (response {}))


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

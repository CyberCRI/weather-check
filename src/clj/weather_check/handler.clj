(ns weather-check.handler
  (:require [compojure.core :refer [GET POST defroutes routes]]
            [compojure.route :refer [not-found resources]]
            [hiccup.page :refer [include-js include-css html5]]
            [weather-check.middleware :refer [wrap-middleware]]
            [config.core :refer [env]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.defaults :refer [site-defaults api-defaults wrap-defaults]]
            [ring.util.response :refer [response]]
            [clojure.java.jdbc :as jdbc]
            [clojure.edn :as edn]))


(def db (edn/read-string (slurp "config.clj")))

;;; WEBSITE

(def mount-target
  [:div#app "Loading..."])

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
        [group-name] (jdbc/query db
                      ["select name from groups where group_id = ?" group-id]
                      {:row-fn :name})
        [reply-count] (jdbc/query db
                      ["select count(*) from responses where group_id = ?" group-id]
                      {:row-fn :count})
        rows (jdbc/query db ["select phrase, count(*) 
                            from phrases, responses 
                            where phrases.response_id = responses.response_id 
                            and group_id = ? group by phrase;" group-id])
        results-map (reduce (fn [m row] (assoc m (:phrase row) (:count row))) {} rows)]
    (response { :name group-name :reply-count reply-count :cloud-counts results-map })))

(defn post-phrases [request]
  (let [group-id (get-in request [:params :group-id])
        phrases (get-in request [:body "clouds"])
        ; Create response in DB
        [{response-id :response_id}] (jdbc/insert! db :responses { :group_id group-id })]
    ; Add phrases to DB
    (jdbc/insert-multi! db :phrases (for [phrase phrases] { :response_id response-id :phrase phrase })))
    (response {}))


;;; ROUTES

(def cljs-urls ["/" 
                "/thanks"
                "/group"
                "/group/:group-id"
                "/group/:group-id/form"
                "/group/:group-id/weather"])

(def site-routes (apply routes (for [url cljs-urls] (GET url [] loading-page))))

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

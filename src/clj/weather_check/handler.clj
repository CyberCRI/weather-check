(ns weather-check.handler
  (:require [compojure.core :refer [GET POST defroutes routes]]
            [compojure.route :refer [not-found resources]]
            [hiccup.page :refer [include-js include-css html5]]
            [weather-check.middleware :refer [wrap-middleware]]
            [config.core :refer [env]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.defaults :refer [site-defaults api-defaults wrap-defaults]]
            [ring.util.response :refer [response]]))

(def clouds (atom []))

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

(defn get-clouds [request]
  (response {:clouds @clouds}))

(defn post-clouds [request]
  (let [cloud (get-in request [:body "cloud"])]
    (swap! clouds conj cloud)
    (response {})))

(defroutes site-routes
  (GET "/" [] loading-page))

(defroutes api-routes
  (GET "/clouds" [] (wrap-json-response get-clouds))
  (POST "/clouds" [] (wrap-json-response (wrap-json-body post-clouds))))

(defroutes other-routes
  (resources "/")
  (not-found "Not Found"))

(def app 
  (routes
    (wrap-middleware api-routes api-defaults) ; Not sure why this must go first in the list, but otherwise anti-forgery token required
    (wrap-middleware site-routes site-defaults)
    (wrap-middleware other-routes site-defaults)))

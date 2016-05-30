(ns weather-check.middleware
  (:require [ring.middleware.defaults :refer [site-defaults wrap-defaults]]))

(defn wrap-middleware [handler defaults]
  (wrap-defaults handler defaults))

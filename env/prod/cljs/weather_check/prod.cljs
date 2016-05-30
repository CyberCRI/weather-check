(ns weather-check.prod
  (:require [weather-check.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)

(ns crap4clj.crap
  (:require [clojure.string :as str]))

(defn crap-score [complexity coverage-pct]
  (when (number? coverage-pct)
    (let [cc (double complexity)
          uncov (- 1.0 (/ coverage-pct 100.0))]
      (+ (* cc cc uncov uncov uncov) cc))))

(defn sort-by-crap [entries]
  (sort-by (juxt #(nil? (:crap %)) (comp - #(or % 0.0) :crap)) entries))

(defn- format-cov [cov]
  (if (number? cov)
    (format "%5.1f%%" cov)
    "  N/A "))

(defn- format-crap [score]
  (if (number? score)
    (format "%8.1f" score)
    "     N/A"))

(defn format-report [entries]
  (let [header (format "%-30s %-35s %4s %7s %8s"
                 "Function" "Namespace" "CC" "Cov%" "CRAP")
        sep (apply str (repeat (count header) "-"))
        lines (for [e entries]
                (format "%-30s %-35s %4d %7s %8s"
                        (:name e) (:namespace e) (:complexity e)
                        (format-cov (:coverage e))
                        (format-crap (:crap e))))]
    (str/join "\n"
      (concat ["CRAP Report" "===========" header sep]
              lines [""]))))

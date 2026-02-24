(ns crap4clj.crap
  (:require [clojure.string :as str]))

(defn crap-score [complexity coverage-pct]
  (let [cc (double complexity)
        uncov (- 1.0 (/ coverage-pct 100.0))]
    (+ (* cc cc uncov uncov uncov) cc)))

(defn sort-by-crap [entries]
  (sort-by :crap #(compare %2 %1) entries))

(defn format-report [entries]
  (let [header (format "%-30s %-35s %4s %6s %8s"
                 "Function" "Namespace" "CC" "Cov%" "CRAP")
        sep (apply str (repeat (count header) "-"))
        lines (for [e entries]
                (format "%-30s %-35s %4d %5.1f%% %8.1f"
                  (:name e) (:namespace e) (:complexity e)
                  (:coverage e) (:crap e)))]
    (str/join "\n"
      (concat ["CRAP Report" "===========" header sep]
              lines [""]))))

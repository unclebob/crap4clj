(ns crap4clj.coverage
  (:require [clojure.string :as str]))

(def ^:private span-pattern
  #"<span[^>]*title=\"(\d+) out of (\d+) forms covered\"[^>]*>\s*(\d+)&nbsp;")

(def ^:private detailed-span-pattern
  #"(?s)<span class=\"([^\"]+)\" title=\"(\d+) out of (\d+) forms covered\">\s*(\d+)&nbsp;&nbsp;(.*?)\s*</span><br/>")

(def ^:private defn-line-pattern
  #"\(defn-?&nbsp;([^\s&\(\)\[\]\{\}\"]+)")

(defn parse-line-coverage [html]
  (let [matches (re-seq span-pattern html)]
    (into {}
      (for [[_ covered total line-str] matches
            :let [c (Integer/parseInt covered)
                  t (Integer/parseInt total)]
            :when (pos? t)]
        [(Integer/parseInt line-str) {:covered c :total t}]))))

(defn coverage-for-range [line-cov start-line end-line]
  (let [entries (for [ln (range start-line (inc end-line))
                      :let [entry (get line-cov ln)]
                      :when entry]
                  entry)
        total-covered (reduce + 0 (map :covered entries))
        total-forms (reduce + 0 (map :total entries))]
    (if (zero? total-forms)
      0.0
      (* 100.0 (/ (double total-covered) (double total-forms))))))

(defn parse-detailed-line-coverage [html]
  (->> (re-seq detailed-span-pattern html)
       (mapv (fn [[_ klass covered total line-str code]]
               {:class klass
                :covered (Integer/parseInt covered)
                :total (Integer/parseInt total)
                :line (Integer/parseInt line-str)
                :code code}))))

(defn- coverage-for-entries [entries]
  (let [tracked (filter #(pos? (:total %)) entries)
        total-covered (reduce + 0 (map :covered tracked))
        total-forms (reduce + 0 (map :total tracked))]
    (if (zero? total-forms)
      0.0
      (* 100.0 (/ (double total-covered) (double total-forms))))))

(defn coverage-for-function-name [detailed-line-cov fn-name]
  (let [defs (keep-indexed (fn [idx entry]
                             (when-let [m (re-find defn-line-pattern (:code entry))]
                               {:idx idx :name (second m)}))
                           detailed-line-cov)
        start-idx (:idx (first (filter #(= fn-name (:name %)) defs)))]
    (when start-idx
      (let [end-idx (or (some (fn [{:keys [idx]}]
                                (when (> idx start-idx) (dec idx)))
                              defs)
                        (dec (count detailed-line-cov)))
            entries (subvec detailed-line-cov start-idx (inc end-idx))]
        (coverage-for-entries entries)))))

(defn source-to-coverage-path [source-path]
  (str "target/coverage/" (subs source-path 4) ".html"))

(defn namespace-to-coverage-paths [ns-name]
  (let [ns-path (-> ns-name
                    (str/replace "-" "_")
                    (str/replace "." "/"))]
    [(str "target/coverage/" ns-path ".clj.html")
     (str "target/coverage/" ns-path ".cljc.html")]))

(defn extract-declared-namespace [source]
  (or (some-> (re-find #"\(\s*ns\s+([A-Za-z0-9*+!_?.\-/]+)" source)
              second)
      (some-> (re-find #"\(\s*in-ns\s+'([A-Za-z0-9*+!_?.\-/]+)\s*\)" source)
              second)
      (some-> (re-find #"\(\s*in-ns\s+\(quote\s+([A-Za-z0-9*+!_?.\-/]+)\)\s*\)" source)
              second)))

(defn source-to-coverage-paths [source-path source]
  (let [declared-ns (extract-declared-namespace source)]
    (->> (concat [(source-to-coverage-path source-path)]
                 (when declared-ns
                   (namespace-to-coverage-paths declared-ns)))
         distinct
         vec)))

(defn source-to-namespace [source-path]
  (-> source-path
      (subs 4)
      (str/replace #"\.cljc?$" "")
      (str/replace "/" ".")
      (str/replace "_" "-")))

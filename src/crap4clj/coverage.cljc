;; mutation-tested: 2026-03-04
(ns crap4clj.coverage
  (:import (java.io File))
  (:import (java.net URLDecoder))
  (:require [clojure.string :as str]))

(def ^:private span-pattern
  #"<span[^>]*title=\"(\d+) out of (\d+) forms covered\"[^>]*>\s*(\d+)&nbsp;")

(def ^:private detailed-span-pattern
  #"(?s)<span class=\"([^\"]+)\" title=\"(\d+) out of (\d+) forms covered\">\s*(\d+)&nbsp;&nbsp;(.*?)\s*</span><br/>")

(def ^:private defn-line-pattern
  #"\(defn-?&nbsp;([^\s&\(\)\[\]\{\}\"]+)")

(def ^:private lcov-da-pattern #"DA:(\d+),(\d+)")

(def ^:private lcov-sf-prefix "SF:")

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

(defn- finalize-lcov-record [state]
  (if-let [f (:current-file state)]
    (-> state
        (update :out assoc f (:current-lines state))
        (assoc :current-file nil :current-lines {}))
    state))

(defn- start-lcov-record [state line]
  (-> state
      finalize-lcov-record
      (assoc :current-file (subs line (count lcov-sf-prefix))
             :current-lines {})))

(defn- add-lcov-da [state line]
  (if-let [[_ ln-str count-str] (re-find lcov-da-pattern line)]
    (let [ln (Integer/parseInt ln-str)
          count (Integer/parseInt count-str)]
      (update state :current-lines assoc ln {:covered (if (pos? count) 1 0)
                                             :total 1}))
    state))

(defn- parse-lcov-line [state line]
  (cond
    (str/starts-with? line lcov-sf-prefix) (start-lcov-record state line)
    (= line "end_of_record") (finalize-lcov-record state)
    (:current-file state) (add-lcov-da state line)
    :else state))

(defn parse-lcov [text]
  (let [lines (str/split-lines text)
        state (reduce parse-lcov-line {:current-file nil
                                       :current-lines {}
                                       :out {}}
                      lines)]
    (:out (finalize-lcov-record state))))

(defn load-lcov [path]
  (try
    (parse-lcov (slurp path))
    (catch java.io.FileNotFoundException _
      nil)))

(defn- normalize-path [path]
  (let [decoded (URLDecoder/decode path "UTF-8")]
    (-> decoded
        (str/replace "\\" "/")
        (str/replace #"^file:" "")
        (str/replace #"^\./" "")
        (str/replace #"/+" "/"))))

(defn- path-segments [path]
  (->> (str/split (normalize-path path) #"/")
       (remove str/blank?)
       vec))

(defn- suffix-segments-match? [path suffix]
  (let [p (path-segments path)
        s (path-segments suffix)
        n (count s)]
    (and (pos? n)
         (<= n (count p))
         (= s (subvec p (- (count p) n) (count p))))))

(defn- source-path-candidates [source-path]
  (let [relative (normalize-path source-path)
        absolute (normalize-path (.getAbsolutePath (File. source-path)))
        canonical (normalize-path (.getCanonicalPath (File. source-path)))
        relative-no-src (str/replace relative #"^src/" "")
        absolute-no-src (str/replace absolute #"/src/" "/")
        canonical-no-src (str/replace canonical #"/src/" "/")]
    (->> [relative absolute canonical
          relative-no-src absolute-no-src canonical-no-src]
         (remove str/blank?)
         distinct)))

(defn lcov-coverage-for-source [lcov-data source-path]
  (when lcov-data
    (let [candidates (source-path-candidates source-path)
          by-key (into {} (map (fn [[k v]] [(normalize-path k) v]) lcov-data))]
      (or (some #(get by-key %) candidates)
          (some (fn [[k v]]
                  (when (some #(suffix-segments-match? k %) candidates)
                    v))
                by-key)))))

(defn- common-suffix-length [a b]
  (loop [xs (reverse (path-segments a))
         ys (reverse (path-segments b))
         n 0]
    (if (and (seq xs) (seq ys) (= (first xs) (first ys)))
      (recur (rest xs) (rest ys) (inc n))
      n)))

(defn lcov-diagnostics [lcov-data source-path]
  (when lcov-data
    (let [candidates (source-path-candidates source-path)
          normalized-keys (->> (keys lcov-data)
                               (map normalize-path)
                               distinct)
          scored (for [k normalized-keys]
                   {:sf k
                    :score (apply max (map #(common-suffix-length k %) candidates))})
          closest (->> scored
                       (filter #(pos? (:score %)))
                       (sort-by (juxt (comp - :score) :sf))
                       (take 5)
                       vec)]
      {:source-path (normalize-path source-path)
       :source-candidates candidates
       :sf-count (count normalized-keys)
       :closest-sf closest})))

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

(ns crap4clj.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [crap4clj.complexity :as complexity]
            [crap4clj.coverage :as coverage]
            [crap4clj.crap :as crap]))

(defn filter-sources [files module-filters]
  (if (empty? module-filters)
    files
    (filter (fn [f]
              (some #(str/includes? f %) module-filters))
            files)))

(defn build-entries [fns line-cov ns-name]
  (mapv (fn [f]
          (let [cov (coverage/coverage-for-range line-cov (:start-line f) (:end-line f))
                score (crap/crap-score (:complexity f) cov)]
            {:name (:name f)
             :namespace ns-name
             :complexity (:complexity f)
             :coverage cov
             :crap score}))
        fns))

(defn analyze-file [source-path]
  (let [source (slurp source-path)
        fns (complexity/extract-functions source)
        cov-path (coverage/source-to-coverage-path source-path)
        ns-name (coverage/source-to-namespace source-path)]
    (if (.exists (io/file cov-path))
      (let [html (slurp cov-path)
            line-cov (coverage/parse-line-coverage html)]
        (build-entries fns line-cov ns-name))
      (build-entries fns {} ns-name))))

(defn find-source-files []
  (->> (file-seq (io/file "src"))
       (filter #(re-find #"\.cljc?$" (.getName %)))
       (map #(.getPath %))
       sort))

(defn -main [& args]
  (let [sources (find-source-files)
        filtered (filter-sources sources (vec args))
        all-entries (mapcat analyze-file filtered)
        sorted (crap/sort-by-crap all-entries)]
    (println (crap/format-report sorted))))

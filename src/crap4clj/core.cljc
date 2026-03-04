(ns crap4clj.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [crap4clj.complexity :as complexity]
            [crap4clj.coverage :as coverage]
            [crap4clj.crap :as crap]))

(defn delete-coverage-dir [dir-path]
  (let [dir (io/file dir-path)]
    (when (.exists dir)
      (run! io/delete-file (reverse (file-seq dir))))))

(defn run-coverage [command]
  (let [parts (.split command " ")
        pb (ProcessBuilder. (java.util.Arrays/asList parts))]
    (.inheritIO pb)
    (.waitFor (.start pb))))

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

(defn build-entries-by-name [fns detailed-line-cov line-cov ns-name]
  (mapv (fn [f]
          (let [cov (or (coverage/coverage-for-function-name detailed-line-cov (:name f))
                        (coverage/coverage-for-range line-cov (:start-line f) (:end-line f)))
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
        source-cov-path (coverage/source-to-coverage-path source-path)
        cov-path (some #(when (.exists (io/file %)) %)
                       (coverage/source-to-coverage-paths source-path source))
        ns-name (or (coverage/extract-declared-namespace source)
                    (coverage/source-to-namespace source-path))]
    (if cov-path
      (let [html (slurp cov-path)
            line-cov (coverage/parse-line-coverage html)]
        (if (= cov-path source-cov-path)
          (build-entries fns line-cov ns-name)
          (let [detailed-line-cov (coverage/parse-detailed-line-coverage html)]
            (build-entries-by-name fns detailed-line-cov line-cov ns-name))))
      (build-entries fns {} ns-name))))

(defn find-source-files []
  (->> (file-seq (io/file "src"))
       (filter #(re-find #"\.cljc?$" (.getName %)))
       (map #(.getPath %))
       sort))

(defn -main [& args]
  (delete-coverage-dir "target/coverage")
  (let [exit (run-coverage "clj -M:cov")]
    (when-not (zero? exit)
      (println (str "Coverage failed (exit " exit ")"))
      (System/exit 1)))
  (let [sources (find-source-files)
        filtered (filter-sources sources (vec args))
        all-entries (mapcat analyze-file filtered)
        sorted (crap/sort-by-crap all-entries)]
    (println (crap/format-report sorted))))

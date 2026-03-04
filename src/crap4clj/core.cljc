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

(defn build-entries-by-name [fns detailed-line-cov ns-name]
  (mapv (fn [f]
          (let [cov (or (coverage/coverage-for-function-name detailed-line-cov (:name f))
                        0.0)
                score (crap/crap-score (:complexity f) cov)]
            {:name (:name f)
             :namespace ns-name
             :complexity (:complexity f)
             :coverage cov
             :crap score}))
        fns))

(defn analyze-file
  ([source-path]
   (analyze-file source-path nil))
  ([source-path lcov-data]
  (let [source (slurp source-path)
        fns (complexity/extract-functions source)
        source-cov-path (coverage/source-to-coverage-path source-path)
        source-cov-exists? (.exists (io/file source-cov-path))
        ns-cov-path (some #(when (and (not= % source-cov-path)
                                      (.exists (io/file %)))
                             %)
                          (coverage/source-to-coverage-paths source-path source))
        lcov-line-cov (coverage/lcov-coverage-for-source lcov-data source-path)
        ns-name (or (coverage/extract-declared-namespace source)
                    (coverage/source-to-namespace source-path))]
    (cond
      source-cov-exists?
      (let [html (slurp source-cov-path)
            line-cov (coverage/parse-line-coverage html)]
        (build-entries fns line-cov ns-name))

      lcov-line-cov
      (build-entries fns lcov-line-cov ns-name)

      ns-cov-path
      (let [html (slurp ns-cov-path)
            detailed-line-cov (coverage/parse-detailed-line-coverage html)
            entries (build-entries-by-name fns detailed-line-cov ns-name)
            unresolved (count (filter #(zero? (:coverage %)) entries))]
        (when (pos? unresolved)
          (binding [*out* *err*]
            (println (format "Warning: namespace fallback coverage for %s via %s left %d/%d functions unresolved; enable LCOV for file-accurate coverage."
                             source-path ns-cov-path unresolved (count entries)))))
        entries)

      :else
      (build-entries fns {} ns-name)))))

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
        lcov-data (coverage/load-lcov "target/coverage/lcov.info")
        all-entries (mapcat #(analyze-file % lcov-data) filtered)
        sorted (crap/sort-by-crap all-entries)]
    (println (crap/format-report sorted))))

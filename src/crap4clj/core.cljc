;; mutation-tested: 2026-03-04
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
          (let [cov (coverage/coverage-for-function-name detailed-line-cov (:name f))
                score (crap/crap-score (:complexity f) cov)]
            {:name (:name f)
             :namespace ns-name
             :complexity (:complexity f)
             :coverage cov
             :crap score}))
        fns))

(defn- source-namespace [source source-path]
  (or (coverage/extract-declared-namespace source)
      (coverage/source-to-namespace source-path)))

(defn- source-cov-exists? [source-path]
  (.exists (io/file (coverage/source-to-coverage-path source-path))))

(defn- existing-namespace-cov-path [source-path source]
  (let [source-cov-path (coverage/source-to-coverage-path source-path)]
    (some #(when (and (not= % source-cov-path)
                      (.exists (io/file %)))
             %)
          (coverage/source-to-coverage-paths source-path source))))

(defn- entries-from-source-html [source-path fns ns-name]
  (let [html (slurp (coverage/source-to-coverage-path source-path))
        line-cov (coverage/parse-line-coverage html)]
    (build-entries fns line-cov ns-name)))

(defn- entries-from-lcov [fns lcov-line-cov ns-name]
  (build-entries fns lcov-line-cov ns-name))

(defn- warn-unresolved-namespace-fallback [source-path ns-cov-path entries]
  (let [unresolved (count (filter #(nil? (:coverage %)) entries))]
    (when (pos? unresolved)
      (binding [*out* *err*]
        (println (format "Warning: namespace fallback coverage for %s via %s left %d/%d functions unresolved; showing N/A (not 0.0%%). Enable LCOV (--lcov) for file-accurate coverage."
                         source-path ns-cov-path unresolved (count entries)))))))

(defn- entries-from-namespace-html [source-path ns-cov-path fns ns-name]
  (let [html (slurp ns-cov-path)
        detailed-line-cov (coverage/parse-detailed-line-coverage html)
        entries (build-entries-by-name fns detailed-line-cov ns-name)]
    (warn-unresolved-namespace-fallback source-path ns-cov-path entries)
    entries))

(declare debug-lcov-mismatch)

(defn- maybe-debug-lcov-mismatch [lcov-data lcov-line-cov source-path]
  (when (and lcov-data (nil? lcov-line-cov))
    (debug-lcov-mismatch source-path lcov-data)))

(defn- entries-for-source [source-path source fns ns-name lcov-line-cov]
  (cond
    (source-cov-exists? source-path) (entries-from-source-html source-path fns ns-name)
    lcov-line-cov (entries-from-lcov fns lcov-line-cov ns-name)
    :else (if-let [ns-cov-path (existing-namespace-cov-path source-path source)]
            (entries-from-namespace-html source-path ns-cov-path fns ns-name)
            (build-entries fns {} ns-name))))

(defn- env-true? [s]
  (contains? #{"1" "true" "yes" "on"}
             (str/lower-case (str (or s "")))))

(defn- debug-lcov-mismatch [source-path lcov-data]
  (when (and lcov-data (env-true? (System/getenv "CRAP4CLJ_DEBUG_LCOV")))
    (let [{:keys [source-path source-candidates sf-count closest-sf]}
          (coverage/lcov-diagnostics lcov-data source-path)]
      (binding [*out* *err*]
        (println (format "LCOV debug: no SF match for %s (SF entries: %d)" source-path sf-count))
        (println (str "LCOV debug: source candidates: "
                      (str/join ", " source-candidates)))
        (if (seq closest-sf)
          (do
            (println "LCOV debug: closest SF candidates:")
            (doseq [{:keys [sf score]} closest-sf]
              (println (format "  score=%d sf=%s" score sf))))
          (println "LCOV debug: no close SF candidates."))))))

(defn analyze-file
  ([source-path]
   (analyze-file source-path nil))
  ([source-path lcov-data]
   (let [source (slurp source-path)
         fns (complexity/extract-functions source)
         lcov-line-cov (coverage/lcov-coverage-for-source lcov-data source-path)
         ns-name (source-namespace source source-path)]
     (maybe-debug-lcov-mismatch lcov-data lcov-line-cov source-path)
     (entries-for-source source-path source fns ns-name lcov-line-cov))))

(defn find-source-files []
  (->> (file-seq (io/file "src"))
       (filter #(re-find #"\.cljc?$" (.getName %)))
       (map #(.getPath %))
       sort))

(defn- run-coverage-with-lcov []
  (let [exit-with-lcov (run-coverage "clj -M:cov --lcov")]
    (if (zero? exit-with-lcov)
      0
      (do
        (binding [*out* *err*]
          (println "Warning: clj -M:cov --lcov failed; retrying without --lcov."))
        (run-coverage "clj -M:cov")))))

(declare exit!)

(defn- ensure-coverage-success! [exit]
  (when-not (zero? exit)
    (println (str "Coverage failed (exit " exit ")"))
    (exit! 1)))

(defn- exit! [status]
  (System/exit status))

(defn- sorted-entries [args lcov-data]
  (let [sources (find-source-files)
        filtered (filter-sources sources (vec args))
        all-entries (mapcat #(analyze-file % lcov-data) filtered)]
    (crap/sort-by-crap all-entries)))

(defn -main [& args]
  (delete-coverage-dir "target/coverage")
  (ensure-coverage-success! (run-coverage-with-lcov))
  (let [lcov-data (coverage/load-lcov "target/coverage/lcov.info")
        sorted (sorted-entries args lcov-data)]
    (println (crap/format-report sorted))))

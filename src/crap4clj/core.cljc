;; mutation-tested: 2026-09-17
(ns crap4clj.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [crap4clj.cli :as cli]
            [crap4clj.complexity :as complexity]
            [crap4clj.coverage :as coverage]
            [crap4clj.crap :as crap]
            [crap4clj.doseq-double-count :as doseq-double-count]))

(defn delete-coverage-dir [dir-path]
  (let [dir (io/file dir-path)]
    (when (.exists dir)
      (run! io/delete-file (reverse (file-seq dir))))))

(defn run-coverage [command]
  (let [pb (ProcessBuilder. ["sh" "-c" command])]
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

(defn- source-namespace [source source-path source-root]
  (or (coverage/extract-declared-namespace source)
      (coverage/source-to-namespace source-path source-root)))

(defn- existing-namespace-cov-path [source-path source source-root]
  (let [source-cov-path
        (coverage/source-to-coverage-path source-path source-root)]
    (some #(when (and (not= % source-cov-path)
                      (.exists (io/file %)))
             %)
          (coverage/source-to-coverage-paths
            source-path source source-root))))

(defn- apply-doseq-double-count
  [source source-path line-cov]
  (let [result (doseq-double-count/correct-line-cov source line-cov)]
    {:line-cov (:line-cov result)
     :correction {:path source-path
                  :corrected-lines (:corrected-lines result)
                  :form-count (:form-count result)}}))

(defn- maybe-correct-line-cov
  [source source-path line-cov options]
  (if (:doseq-double-count? options)
    (apply-doseq-double-count source source-path line-cov)
    {:line-cov line-cov :correction nil}))

(defn- entries-from-source-html [coverage-path fns ns-name source source-path options]
  (let [html (slurp coverage-path)
        parsed (coverage/parse-line-coverage html)
        {:keys [line-cov correction]}
        (maybe-correct-line-cov source source-path parsed options)]
    {:entries (build-entries fns line-cov ns-name)
     :correction correction}))

(defn- entries-from-lcov [fns lcov-line-cov ns-name]
  {:entries (build-entries fns lcov-line-cov ns-name)
   :correction nil})

(defn- warn-unresolved-namespace-fallback [source-path ns-cov-path entries]
  (let [unresolved (count (filter #(nil? (:coverage %)) entries))]
    (when (pos? unresolved)
      (binding [*out* *err*]
        (println (format "Warning: namespace fallback coverage for %s via %s left %d/%d functions unresolved; showing N/A (not 0.0%%). Enable LCOV (--lcov) for file-accurate coverage."
                         source-path ns-cov-path unresolved (count entries)))))))

(defn- entries-from-namespace-html
  [source-path ns-cov-path fns ns-name source options]
  (let [html (slurp ns-cov-path)]
    (if (:doseq-double-count? options)
      (let [parsed (coverage/parse-line-coverage html)
            {:keys [line-cov correction]}
            (maybe-correct-line-cov source source-path parsed options)]
        {:entries (build-entries fns line-cov ns-name)
         :correction correction})
      (let [detailed-line-cov (coverage/parse-detailed-line-coverage html)
            entries (build-entries-by-name fns detailed-line-cov ns-name)]
        (warn-unresolved-namespace-fallback source-path ns-cov-path entries)
        {:entries entries :correction nil}))))

(declare debug-lcov-mismatch)

(defn- maybe-debug-lcov-mismatch [lcov-data lcov-line-cov source-path]
  (when (and lcov-data (nil? lcov-line-cov))
    (debug-lcov-mismatch source-path lcov-data)))

(defn- empty-analysis [fns ns-name]
  {:entries (build-entries fns {} ns-name)
   :correction nil})

(defn- entries-for-source
  [source-path source-root source fns ns-name lcov-line-cov options]
  (let [source-cov-path
        (coverage/source-to-coverage-path source-path source-root)]
    (cond
      (.exists (io/file source-cov-path))
      (entries-from-source-html source-cov-path fns ns-name source source-path options)

      lcov-line-cov
      (entries-from-lcov fns lcov-line-cov ns-name)

      :else
      (if-let [ns-cov-path
               (existing-namespace-cov-path source-path source source-root)]
        (entries-from-namespace-html source-path ns-cov-path fns ns-name source options)
        (empty-analysis fns ns-name)))))

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

(defn- analyze-source
  [source-path lcov-data source-root options]
  (let [source (slurp source-path)
        fns (complexity/extract-functions source)
        lcov-line-cov (coverage/lcov-coverage-for-source lcov-data source-path)
        ns-name (source-namespace source source-path source-root)]
    (maybe-debug-lcov-mismatch lcov-data lcov-line-cov source-path)
    (entries-for-source
      source-path source-root source fns ns-name lcov-line-cov options)))

(defn analyze-file
  ([source-path]
   (analyze-file source-path nil nil))
  ([source-path lcov-data]
   (analyze-file source-path lcov-data nil))
  ([source-path lcov-data source-root]
   (analyze-file source-path lcov-data source-root nil))
  ([source-path lcov-data source-root options]
   (let [{:keys [entries correction]}
         (analyze-source source-path lcov-data source-root options)]
     (with-meta (vec entries) {::correction correction}))))

(defn source-files-in-root [source-root]
  (let [root (io/file source-root)]
    (if (.exists root)
      (->> (file-seq root)
           (filter #(.isFile %))
           (filter #(re-find #"\.(?:clj[cs]?|bb)$" (.getName %)))
           (map #(.getPath %)))
      [])))

(defn find-source-files
  ([] (find-source-files ["src"]))
  ([source-roots]
   (->> source-roots
        (mapcat source-files-in-root)
        distinct
        sort)))

(defn- run-coverage-with-lcov
  ([] (run-coverage-with-lcov nil))
  ([coverage-command]
   (if coverage-command
     (run-coverage coverage-command)
     (let [exit-with-lcov (run-coverage "clj -M:cov --lcov")]
       (if (zero? exit-with-lcov)
         0
         (do
           (binding [*out* *err*]
             (println "Warning: clj -M:cov --lcov failed; retrying without --lcov."))
           (run-coverage "clj -M:cov")))))))

(declare exit!)

(defn- ensure-coverage-success! [exit]
  (when-not (zero? exit)
    (println (str "Coverage failed (exit " exit ")"))
    (exit! 1)))

(defn- exit! [status]
  (System/exit status))

(defn- source-root-map [options]
  (into (sorted-map)
        (for [root (:source-roots options)
              source (source-files-in-root root)]
          [source root])))

(defn- analyze-all [options lcov-data]
  (let [root-by-source (source-root-map options)
        filtered (filter-sources (keys root-by-source) (:module-filters options))
        chunks (mapv #(analyze-file % lcov-data (get root-by-source %) options)
                     filtered)]
    {:entries (crap/sort-by-crap (mapcat identity chunks))
     :corrections (keep #(::correction (meta %)) chunks)}))

(defn- sorted-entries [options lcov-data]
  (:entries (analyze-all options lcov-data)))

(defn- prepare-coverage! [options]
  (when-not (:use-existing-coverage? options)
    (delete-coverage-dir "target/coverage")
    (ensure-coverage-success! (run-coverage-with-lcov (:coverage-command options)))))

(defn metrics-path
  ([] (metrics-path (System/getProperty "user.dir")))
  ([root] (.getPath (io/file root ".metrics" "crap.edn"))))

(defn write-metrics-snapshot!
  ([entries] (write-metrics-snapshot! entries (System/getProperty "user.dir")))
  ([entries root]
   (let [f (io/file (metrics-path root))]
     (io/make-parents f)
     (spit f (str (pr-str {:entries
                           (mapv #(select-keys % [:name :namespace :complexity :coverage :crap])
                                 entries)})
                  "\n"))
     (.getPath f))))

(defn run [options]
  (case (:action options)
    :help (println (:message options))
    :analyze (do
               (prepare-coverage! options)
               (let [lcov-data (coverage/load-lcov (:lcov-path options))
                     {:keys [entries corrections]} (analyze-all options lcov-data)]
                 (when (:doseq-double-count? options)
                   (println (doseq-double-count/format-summary corrections))
                   (println))
                 (write-metrics-snapshot! entries)
                 (println (crap/format-report entries))))))

(defn -main [& args]
  (run (cli/parse-args args)))

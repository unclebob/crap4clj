;; mutation-tested: 2026-09-17
(ns crap4clj.cli
  (:require [clojure.string :as str]))

(def help-message
  (str "Usage: clj -M:crap [module-filter ...]\n"
       "   or: bb crap [module-filter ...]\n"
       "\n"
       "Runs Cloverage, computes CRAP scores, and prints a report sorted worst first.\n"
       "\n"
       "Options:\n"
       "  -h, --help                    Print this help message and exit.\n"
       "  -s, --source-root <path>      Root containing .clj, .cljc, .cljs, or .bb files. May be repeated. Default: src.\n"
       "      --lcov <path>             LCOV file to read. Default: target/coverage/lcov.info.\n"
       "      --use-existing-coverage   Do not delete or regenerate target/coverage.\n"
       "      --coverage-command <cmd>  Coverage command to run instead of clj -M:cov --lcov.\n"
       "      --doseq-double-count      Opt-in recount when a tested doseq/for body looks\n"
       "                                half-covered. Cloverage instruments both copies of\n"
       "                                the body (chunked and unchunked), so a fully run\n"
       "                                line can show as 50% forms (e.g. 15/30). That is a\n"
       "                                rewriter bug, not missing tests. Run a normal crap\n"
       "                                report first; if HTML titles on those body lines\n"
       "                                are N out of 2N, re-run with this flag to confirm\n"
       "                                the fingerprint and treat the live copy as covered.\n"
       "                                Leave off unless you suspect this. Needs Cloverage\n"
       "                                HTML form counts; ignored for LCOV-only runs.\n"
       "\n"
       "Arguments:\n"
       "  module-filter    Optional source path fragment. When present, only matching\n"
       "                   source files under the configured source roots are analyzed."))

(defn- help-requested? [args]
  (some #{"--help" "-h"} args))

(defn- take-option-value [args option]
  (let [value (second args)]
    (if value
      value
      (throw (ex-info (str option " requires a value") {:option option})))))

(defn- option-like? [arg]
  (str/starts-with? arg "-"))

(defn- validate-options [options]
  (when (and (not= "target/coverage/lcov.info" (:lcov-path options))
             (not (:use-existing-coverage? options))
             (nil? (:coverage-command options)))
    (throw (ex-info "--lcov requires --use-existing-coverage or --coverage-command"
                    {:option "--lcov"})))
  options)

(defn- parse-analyze-args [args]
  (loop [args args
         options {:action :analyze
                  :source-roots []
                  :lcov-path "target/coverage/lcov.info"
                  :use-existing-coverage? false
                  :coverage-command nil
                  :doseq-double-count? false
                  :module-filters []}]
    (if-let [arg (first args)]
      (case arg
        ("-s" "--source-root")
        (recur (drop 2 args)
               (update options :source-roots conj (take-option-value args arg)))

        "--lcov"
        (recur (drop 2 args)
               (assoc options :lcov-path (take-option-value args arg)))

        "--coverage-command"
        (recur (drop 2 args)
               (assoc options :coverage-command (take-option-value args arg)))

        "--use-existing-coverage"
        (recur (rest args)
               (assoc options :use-existing-coverage? true))

        "--doseq-double-count"
        (recur (rest args)
               (assoc options :doseq-double-count? true))

        (if (option-like? arg)
          (throw (ex-info (str "Unknown option: " arg) {:option arg}))
          (recur (rest args)
                 (update options :module-filters conj arg))))
      (-> options
          (update :source-roots #(if (seq %) (vec %) ["src"]))
          validate-options))))

(defn parse-args [args]
  (try
    (if (help-requested? args)
      {:action :help
       :message help-message}
      (parse-analyze-args args))
    (catch clojure.lang.ExceptionInfo e
      {:action :help
       :message (str (ex-message e) "\n\n" help-message)})))

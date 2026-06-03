(ns crap4clj.cli)

(def help-message
  (str "Usage: clj -M:crap [module-filter ...]\n"
       "   or: bb crap [module-filter ...]\n"
       "\n"
       "Runs Cloverage, computes CRAP scores, and prints a report sorted worst first.\n"
       "\n"
       "Options:\n"
       "  -h, --help    Print this help message and exit.\n"
       "\n"
       "Arguments:\n"
       "  module-filter    Optional source path fragment. When present, only matching\n"
       "                   source files under src/ are analyzed."))

(defn- help-requested? [args]
  (some #{"--help" "-h"} args))

(defn parse-args [args]
  (if (help-requested? args)
    {:action :help
     :message help-message}
    {:action :analyze
     :module-filters (vec args)}))

(ns crap4clj.cli-spec
  (:require [clojure.string :as str]
            [speclj.core :refer :all]
            [crap4clj.cli :refer :all]))

(describe "crap4clj cli"
  (context "parse-args"
    (it "returns an analyze command with module filters"
      (should= {:action :analyze
                :source-roots ["src"]
                :lcov-path "target/coverage/lcov.info"
                :use-existing-coverage? false
                :coverage-command nil
                :module-filters ["combat" "movement"]}
        (parse-args ["combat" "movement"])))

    (it "parses source roots, lcov path, existing coverage, and coverage command"
      (should= {:action :analyze
                :source-roots ["src" "swarmforge/scripts"]
                :lcov-path "tmp/lcov.info"
                :use-existing-coverage? true
                :coverage-command "bb coverage --out \"tmp/lcov file.info\""
                :module-filters ["squad"]}
        (parse-args ["--source-root" "src"
                     "-s" "swarmforge/scripts"
                     "--lcov" "tmp/lcov.info"
                     "--use-existing-coverage"
                     "--coverage-command" "bb coverage --out \"tmp/lcov file.info\""
                     "squad"])))

    (it "rejects unknown options"
      (let [command (parse-args ["--sorce-root" "src"])]
        (should= :help (:action command))
        (should (str/includes? (:message command) "Unknown option: --sorce-root"))))

    (it "rejects missing option values"
      (let [command (parse-args ["--source-root"])]
        (should= :help (:action command))
        (should (str/includes? (:message command) "--source-root requires a value"))))

    (it "rejects custom lcov paths without an existing or custom coverage source"
      (let [command (parse-args ["--lcov" "custom/lcov.info"])]
        (should= :help (:action command))
        (should (str/includes? (:message command)
                               "--lcov requires --use-existing-coverage or --coverage-command"))))

    (it "returns a help command for --help"
      (let [command (parse-args ["--help"])]
        (should= :help (:action command))
        (should (str/includes? (:message command) "Usage: clj -M:crap"))
        (should (str/includes? (:message command) "bb crap"))
        (should (str/includes? (:message command) ".cljs, or .bb files"))))

    (it "returns a help command for -h"
      (let [command (parse-args ["-h"])]
        (should= :help (:action command))
        (should (str/includes? (:message command) "--help"))))))

(ns crap4clj.cli-spec
  (:require [clojure.string :as str]
            [speclj.core :refer :all]
            [crap4clj.cli :refer :all]))

(describe "crap4clj cli"
  (context "parse-args"
    (it "returns an analyze command with module filters"
      (should= {:action :analyze
                :module-filters ["combat" "movement"]}
        (parse-args ["combat" "movement"])))

    (it "returns a help command for --help"
      (let [command (parse-args ["--help"])]
        (should= :help (:action command))
        (should (str/includes? (:message command) "Usage: clj -M:crap"))))

    (it "returns a help command for -h"
      (let [command (parse-args ["-h"])]
        (should= :help (:action command))
        (should (str/includes? (:message command) "--help"))))))

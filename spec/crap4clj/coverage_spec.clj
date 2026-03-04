(ns crap4clj.coverage-spec
  (:require [speclj.core :refer :all]
            [crap4clj.coverage :refer :all]))

(describe "coverage HTML parsing"
  (context "parse-line-coverage"
    (it "parses a covered line"
      (let [html "<span class=\"covered\" title=\"3 out of 5 forms covered\">\n                013&nbsp;&nbsp;(defn&nbsp;foo\n                </span><br/>"
            result (parse-line-coverage html)]
        (should= {13 {:covered 3 :total 5}} result)))

    (it "parses a not-covered line"
      (let [html "<span class=\"not-covered\" title=\"0 out of 3 forms covered\">\n                007&nbsp;&nbsp;(bar&nbsp;x)\n                </span><br/>"
            result (parse-line-coverage html)]
        (should= {7 {:covered 0 :total 3}} result)))

    (it "skips not-tracked lines (0 out of 0)"
      (let [html "<span class=\"not-tracked\" title=\"0 out of 0 forms covered\">\n                001&nbsp;&nbsp;;;comment\n                </span><br/>"
            result (parse-line-coverage html)]
        (should= {} result)))

    (it "skips blank lines"
      (let [html "<span class=\"blank\" title=\"0 out of 0 forms covered\">\n                008&nbsp;&nbsp;\n                </span><br/>"
            result (parse-line-coverage html)]
        (should= {} result))))

  (context "coverage-for-function-name"
    (it "finds coverage for a function by defn name within namespace html"
      (let [html (str "<span class=\"covered\" title=\"1 out of 1 forms covered\">1&nbsp;&nbsp;(defn&nbsp;foo&nbsp;[]</span><br/>"
                      "<span class=\"covered\" title=\"1 out of 1 forms covered\">2&nbsp;&nbsp;&nbsp;&nbsp;1)</span><br/>"
                      "<span class=\"not-covered\" title=\"0 out of 1 forms covered\">1&nbsp;&nbsp;(defn&nbsp;bar&nbsp;[]</span><br/>"
                      "<span class=\"not-covered\" title=\"0 out of 1 forms covered\">2&nbsp;&nbsp;&nbsp;&nbsp;0)</span><br/>")
            detailed (parse-detailed-line-coverage html)]
        (should= 100.0 (coverage-for-function-name detailed "foo"))
        (should= 0.0 (coverage-for-function-name detailed "bar"))
        (should= nil (coverage-for-function-name detailed "baz")))))

  (context "lcov parsing"
    (it "parses line coverage from lcov text"
      (let [lcov (str "SF:src/foo/bar.clj\n"
                      "DA:10,3\n"
                      "DA:11,0\n"
                      "end_of_record\n")
            parsed (parse-lcov lcov)]
        (should= {10 {:covered 1 :total 1}
                  11 {:covered 0 :total 1}}
          (get parsed "src/foo/bar.clj"))))

    (it "finds lcov coverage by source path suffix"
      (let [parsed {"/tmp/work/src/foo/bar.clj" {10 {:covered 1 :total 1}}}]
        (should= {10 {:covered 1 :total 1}}
          (lcov-coverage-for-source parsed "src/foo/bar.clj"))))

    (it "matches when SF path omits src prefix"
      (let [parsed {"empire/architecture/dependency_checker/core_base.clj"
                    {12 {:covered 1 :total 1}
                     13 {:covered 0 :total 1}}}]
        (should= {12 {:covered 1 :total 1}
                  13 {:covered 0 :total 1}}
          (lcov-coverage-for-source
            parsed
            "src/empire/architecture/dependency_checker/core_base.clj"))))

    (it "matches file URI style SF paths"
      (let [parsed {"file:/Users/me/project/src/foo/bar.clj"
                    {7 {:covered 1 :total 1}}}]
        (should= {7 {:covered 1 :total 1}}
          (lcov-coverage-for-source parsed "src/foo/bar.clj"))))

    (it "reports closest SF candidates for diagnostics"
      (let [parsed {"src/alpha/beta.clj" {}
                    "/tmp/empire/architecture/dependency_checker/core_base.clj" {}
                    "/tmp/empire/architecture/dependency_checker/cli.clj" {}}
            d (lcov-diagnostics parsed "src/empire/architecture/dependency_checker/core_base.clj")]
        (should= "src/empire/architecture/dependency_checker/core_base.clj"
          (:source-path d))
        (should (pos? (:sf-count d)))
        (should (seq (:closest-sf d)))
        (should= "/tmp/empire/architecture/dependency_checker/core_base.clj"
          (:sf (first (:closest-sf d)))))))

  (context "coverage-for-range"
    (it "computes coverage percentage for a line range"
      (let [line-cov {3 {:covered 3 :total 5}
                      4 {:covered 2 :total 2}
                      5 {:covered 0 :total 3}}]
        (should-be-a Double (coverage-for-range line-cov 3 5))
        (should= 50.0 (coverage-for-range line-cov 3 5))))

    (it "returns 0.0 when no instrumented lines in range"
      (should= 0.0 (coverage-for-range {} 1 5))))

  (context "source-to-coverage-path"
    (it "maps source path to coverage HTML path"
      (should= "target/coverage/foo/combat.cljc.html"
        (source-to-coverage-path "src/foo/combat.cljc")))

    (it "maps nested source path"
      (should= "target/coverage/foo/bar/army.clj.html"
        (source-to-coverage-path "src/foo/bar/army.clj"))))

  (context "namespace-to-coverage-paths"
    (it "maps namespace to Cloverage-style HTML paths"
      (should= ["target/coverage/foo/bar_baz.clj.html"
                "target/coverage/foo/bar_baz.cljc.html"]
        (namespace-to-coverage-paths "foo.bar-baz"))))

  (context "extract-declared-namespace"
    (it "extracts namespace from ns form"
      (should= "foo.bar"
        (extract-declared-namespace "(ns foo.bar)\\n(defn x [] 1)")))

    (it "extracts namespace from in-ns quote form"
      (should= "foo.bar"
        (extract-declared-namespace "(in-ns 'foo.bar)\\n(defn x [] 1)")))

    (it "extracts namespace from in-ns (quote ...) form"
      (should= "foo.bar"
        (extract-declared-namespace "(in-ns (quote foo.bar))\\n(defn x [] 1)"))))

  (context "source-to-coverage-paths"
    (it "adds namespace fallback paths after per-file path"
      (let [paths (source-to-coverage-paths
                    "src/empire/architecture/dependency_checker/core_base_config.clj"
                    "(in-ns 'empire.architecture.dependency-checker)")]
        (should= ["target/coverage/empire/architecture/dependency_checker/core_base_config.clj.html"
                  "target/coverage/empire/architecture/dependency_checker.clj.html"
                  "target/coverage/empire/architecture/dependency_checker.cljc.html"]
          paths))))

  (context "source-to-namespace"
    (it "converts .cljc source path to namespace string"
      (should= "foo.combat"
        (source-to-namespace "src/foo/combat.cljc")))

    (it "converts .clj source path to namespace string"
      (should= "foo.combat"
        (source-to-namespace "src/foo/combat.clj")))

    (it "converts nested path with underscores"
      (should= "foo.game-loop"
        (source-to-namespace "src/foo/game_loop.cljc")))))

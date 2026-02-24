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

  (context "coverage-for-range"
    (it "computes coverage percentage for a line range"
      (let [line-cov {3 {:covered 3 :total 5}
                      4 {:covered 2 :total 2}
                      5 {:covered 0 :total 3}}]
        (should-be-a Double (coverage-for-range line-cov 3 5))
        (should= 50.0 (coverage-for-range line-cov 3 5))))

    (it "returns 100.0 when no instrumented lines in range"
      (should= 100.0 (coverage-for-range {} 1 5))))

  (context "source-to-coverage-path"
    (it "maps source path to coverage HTML path"
      (should= "target/coverage/foo/combat.cljc.html"
        (source-to-coverage-path "src/foo/combat.cljc")))

    (it "maps nested source path"
      (should= "target/coverage/foo/bar/army.clj.html"
        (source-to-coverage-path "src/foo/bar/army.clj"))))

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

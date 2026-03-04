(ns crap4clj.core-spec
  (:require [clojure.java.io :as io]
            [speclj.core :refer :all]
            [crap4clj.core :refer :all]))

(describe "crap4clj core"
  (context "filter-sources"
    (it "returns all files when no filter"
      (let [files ["src/foo/combat.cljc" "src/foo/config.clj"]]
        (should= files (filter-sources files []))))

    (it "filters to matching modules"
      (let [files ["src/foo/combat.cljc"
                   "src/foo/config.clj"
                   "src/foo/bar/army.cljc"]]
        (should= ["src/foo/combat.cljc"]
          (filter-sources files ["combat"]))))

    (it "matches nested module paths"
      (let [files ["src/foo/combat.cljc"
                   "src/foo/bar/army.cljc"]]
        (should= ["src/foo/bar/army.cljc"]
          (filter-sources files ["bar/army"])))))

  (context "build-entries"
    (it "returns function entries with crap scores"
      (let [fns [{:name "foo" :start-line 1 :end-line 3 :complexity 2}]
            line-cov {1 {:covered 1 :total 1}
                      2 {:covered 1 :total 2}
                      3 {:covered 0 :total 1}}
            ns-name "test.ns"
            entries (build-entries fns line-cov ns-name)]
        (should= 1 (count entries))
        (should= "foo" (:name (first entries)))
        (should= "test.ns" (:namespace (first entries)))
        (should= 2 (:complexity (first entries)))
        (should-be-a Double (:coverage (first entries)))
        (should-be-a Double (:crap (first entries))))))

  (context "find-source-files"
    (it "finds .cljc and .clj files under src"
      (let [files (find-source-files)]
        (should (seq files))
        (should (every? #(re-find #"\.cljc?$" %) files)))))

  (context "full pipeline integration"
    (it "analyzes a real source file"
      (let [entries (analyze-file "src/crap4clj/core.cljc")]
        (should (seq entries))
        (doseq [e entries]
          (should-be-a String (:name e))
          (should-be-a String (:namespace e))
          (should-be-a Number (:complexity e))
          (should (<= 0 (:coverage e) 100))
          (should (pos? (:crap e)))))))

  (context "analyze-file namespace coverage fallback"
    (it "uses namespace coverage report when split-file coverage report is missing"
      (let [source-path "src/test/split_ns_demo/part_a.clj"
            coverage-path "target/coverage/test/split_ns_demo.clj.html"
            source "(in-ns 'test.split-ns-demo)\n\n(defn split-fn []\n  (if true\n    1\n    0))\n"
            ;; Duplicate line numbers simulate multiple loaded files merged into one namespace page.
            ;; Name-based fallback should still attribute coverage to split-fn correctly.
            html (str "<span class=\"covered\" title=\"1 out of 1 forms covered\">3&nbsp;&nbsp;(defn&nbsp;split-fn&nbsp;[]</span><br/>"
                      "<span class=\"covered\" title=\"1 out of 1 forms covered\">4&nbsp;&nbsp;&nbsp;&nbsp;(if&nbsp;true</span><br/>"
                      "<span class=\"covered\" title=\"1 out of 1 forms covered\">5&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;1</span><br/>"
                      "<span class=\"not-covered\" title=\"0 out of 1 forms covered\">3&nbsp;&nbsp;(defn&nbsp;other-fn&nbsp;[]</span><br/>"
                      "<span class=\"not-covered\" title=\"0 out of 1 forms covered\">4&nbsp;&nbsp;&nbsp;&nbsp;0)</span><br/>")]
        (.mkdirs (java.io.File. "src/test/split_ns_demo"))
        (.mkdirs (java.io.File. "target/coverage/test"))
        (spit source-path source)
        (spit coverage-path html)
        (try
          (let [entries (analyze-file source-path)
                entry (first entries)]
            (should (seq entries))
            (should= "split-fn" (:name entry))
            (should= "test.split-ns-demo" (:namespace entry))
            (should= 100.0 (:coverage entry)))
          (finally
            (io/delete-file coverage-path true)
            (io/delete-file source-path true)
            (io/delete-file "src/test/split_ns_demo" true)))))))

  (context "analyze-file lcov fallback"
    (it "uses lcov line data when per-file html is missing"
      (let [source-path "src/test/lcov_demo.clj"
            source "(ns test.lcov-demo)\n\n(defn only-fn []\n  42)\n"
            lcov-data {"src/test/lcov_demo.clj" {3 {:covered 1 :total 1}
                                                 4 {:covered 1 :total 1}}}]
        (.mkdirs (java.io.File. "src/test"))
        (spit source-path source)
        (try
          (let [entry (first (analyze-file source-path lcov-data))]
            (should= "only-fn" (:name entry))
            (should= 100.0 (:coverage entry)))
          (finally
            (io/delete-file source-path true))))))

  (context "analyze-file namespace fallback without matching defn"
    (it "marks coverage as indeterminate when namespace html does not include the function"
      (let [source-path "src/test/no_match/part_a.clj"
            coverage-path "target/coverage/test/no_match.clj.html"
            source "(in-ns 'test.no-match)\n\n(defn part-fn []\n  1)\n"
            html "<span class=\"covered\" title=\"1 out of 1 forms covered\">1&nbsp;&nbsp;(ns&nbsp;test.no-match)</span><br/>"]
        (.mkdirs (java.io.File. "src/test/no_match"))
        (.mkdirs (java.io.File. "target/coverage/test"))
        (spit source-path source)
        (spit coverage-path html)
        (try
          (let [entry (first (analyze-file source-path))]
            (should= "part-fn" (:name entry))
            (should= nil (:coverage entry))
            (should= nil (:crap entry)))
          (finally
            (io/delete-file coverage-path true)
            (io/delete-file source-path true)
            (io/delete-file "src/test/no_match" true))))))

  (context "delete-coverage-dir"
    (it "deletes an existing coverage directory"
      (let [dir (java.io.File. "target/test-coverage-dir/sub")]
        (.mkdirs dir)
        (spit (java.io.File. dir "file.html") "data")
        (delete-coverage-dir "target/test-coverage-dir")
        (should-not (.exists (java.io.File. "target/test-coverage-dir")))))

    (it "is a no-op when directory does not exist"
      (delete-coverage-dir "target/nonexistent-coverage-dir")
      (should-not (.exists (java.io.File. "target/nonexistent-coverage-dir")))))

  (context "run-coverage"
    (it "returns 0 for a successful command"
      (should= 0 (run-coverage "true")))

    (it "returns non-zero for a failing command"
      (should-not= 0 (run-coverage "false"))))

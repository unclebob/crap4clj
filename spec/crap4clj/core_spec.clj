(ns crap4clj.core-spec
  (:require [speclj.core :refer :all]
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
      (should-not= 0 (run-coverage "false")))))

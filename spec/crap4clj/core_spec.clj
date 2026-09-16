(ns crap4clj.core-spec
  (:require [clojure.java.io :as io]
            [speclj.core :refer :all]
            [clojure.string :as str]
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
    (it "finds Clojure and Babashka source files under src"
      (let [files (find-source-files)]
        (should (seq files))
        (should (every? #(re-find #"\.(?:clj[cs]?|bb)$" %) files))))

    (it "finds files under configured source roots"
      (let [dir (java.io.File. "target/source-root-demo/scripts")]
        (.mkdirs dir)
        (spit (java.io.File. dir "demo.clj") "(ns demo)\n")
        (spit (java.io.File. dir "client.cljs") "(ns client)\n")
        (spit (java.io.File. dir "task.bb") "(defn task [] :done)\n")
        (spit (java.io.File. dir "ignore.txt") "nope")
        (try
          (should= ["target/source-root-demo/scripts/client.cljs"
                    "target/source-root-demo/scripts/demo.clj"
                    "target/source-root-demo/scripts/task.bb"]
            (find-source-files ["target/source-root-demo/scripts"]))
          (finally
            (io/delete-file "target/source-root-demo/scripts/demo.clj" true)
            (io/delete-file "target/source-root-demo/scripts/client.cljs" true)
            (io/delete-file "target/source-root-demo/scripts/task.bb" true)
            (io/delete-file "target/source-root-demo/scripts/ignore.txt" true)
            (io/delete-file "target/source-root-demo/scripts" true)
            (io/delete-file "target/source-root-demo" true))))))

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

  (context "write-metrics-snapshot!"
    (it "writes function entries to .metrics/crap.edn"
      (let [root (.getCanonicalPath (io/file "target" "crap-metrics-demo"))
            entries [{:name "foo" :namespace "demo.ns" :complexity 2 :coverage 80.0 :crap 2.16}]]
        (.mkdirs (io/file root))
        (try
          (write-metrics-snapshot! entries root)
          (let [data (read-string (slurp (metrics-path root)))]
            (should= "foo" (get-in data [:entries 0 :name]))
            (should= 2 (get-in data [:entries 0 :complexity])))
          (finally
            (io/delete-file (metrics-path root) true)
            (io/delete-file (io/file root ".metrics") true)
            (io/delete-file root true))))))

  (context "sorted-entries"
    (it "passes each configured source root into file analysis"
      (let [calls (atom [])]
        (with-redefs [crap4clj.core/source-files-in-root
                      (fn [root]
                        (case root
                          "scripts" ["scripts/task.bb"]
                          "src" ["src/app/core.clj"]))
                      crap4clj.core/analyze-file
                      (fn [source _ root]
                        (swap! calls conj [source root])
                        [])]
          (#'crap4clj.core/sorted-entries
            {:source-roots ["scripts" "src"]
             :module-filters []}
            nil))
        (should= [["scripts/task.bb" "scripts"]
                  ["src/app/core.clj" "src"]]
                 @calls))))

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
            (io/delete-file source-path true)))))

    (it "analyzes a shebang-style .bb file with lcov coverage"
      (let [source-path "target/bb-source-demo/scripts/report.bb"
            source (str "#!/usr/bin/env bb\n"
                        "(defn report [ready?]\n"
                        "  (if ready? :ready :waiting))\n")
            lcov-data {source-path {2 {:covered 1 :total 1}
                                    3 {:covered 1 :total 1}}}]
        (.mkdirs (java.io.File. "target/bb-source-demo/scripts"))
        (spit source-path source)
        (try
          (let [entry (first (analyze-file source-path lcov-data))]
            (should= "report" (:name entry))
            (should= "target.bb-source-demo.scripts.report" (:namespace entry))
            (should= 2 (:complexity entry))
            (should= 100.0 (:coverage entry))
            (should= 2.0 (:crap entry)))
          (finally
            (io/delete-file source-path true)
            (io/delete-file "target/bb-source-demo/scripts" true)
            (io/delete-file "target/bb-source-demo" true))))))

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
          (let [err (java.io.StringWriter.)]
            (binding [*err* err]
              (let [entry (first (analyze-file source-path))]
                (should= "part-fn" (:name entry))
                (should= nil (:coverage entry))
                (should= nil (:crap entry)))))
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
      (should-not= 0 (run-coverage "false")))

    (it "runs commands through the shell so quoted args stay intact"
      (should= 0 (run-coverage "test \"two words\" = \"two words\""))))

  (context "debug-lcov-mismatch"
    (it "prints diagnostics when debug mode is enabled"
      (with-redefs [crap4clj.core/env-true? (constantly true)
                    crap4clj.coverage/lcov-diagnostics
                    (fn [_ _]
                      {:source-path "src/foo.clj"
                       :source-candidates ["src/foo.clj"]
                       :sf-count 2
                       :closest-sf [{:sf "src/bar.clj" :score 2}]})]
        (let [err (java.io.StringWriter.)]
          (binding [*err* err]
            (#'crap4clj.core/debug-lcov-mismatch "src/foo.clj" {:any :data}))
          (should (str/includes? (str err) "LCOV debug: no SF match"))
          (should (str/includes? (str err) "closest SF candidates")))))

    (it "does not print diagnostics when debug mode is disabled"
      (with-redefs [crap4clj.core/env-true? (constantly false)]
        (let [err (java.io.StringWriter.)]
          (binding [*err* err]
            (#'crap4clj.core/debug-lcov-mismatch "src/foo.clj" {:any :data}))
          (should= "" (str err))))))

  (context "run-coverage-with-lcov"
    (it "runs a custom coverage command when supplied"
      (let [calls (atom [])]
        (with-redefs [crap4clj.core/run-coverage
                      (fn [cmd]
                        (swap! calls conj cmd)
                        0)]
          (should= 0 (#'crap4clj.core/run-coverage-with-lcov "bb coverage")))
        (should= ["bb coverage"] @calls)))

    (it "returns 0 immediately when --lcov run succeeds"
      (let [calls (atom [])]
        (with-redefs [crap4clj.core/run-coverage
                      (fn [cmd]
                        (swap! calls conj cmd)
                        0)]
          (should= 0 (#'crap4clj.core/run-coverage-with-lcov)))
        (should= ["clj -M:cov --lcov"] @calls)))

    (it "retries without --lcov when first command fails"
      (let [calls (atom [])
            err (java.io.StringWriter.)]
        (with-redefs [crap4clj.core/run-coverage
                      (fn [cmd]
                        (swap! calls conj cmd)
                        (if (= cmd "clj -M:cov --lcov") 1 0))]
          (binding [*err* err]
            (should= 0 (#'crap4clj.core/run-coverage-with-lcov))))
        (should= ["clj -M:cov --lcov" "clj -M:cov"] @calls)
        (should (str/includes? (str err) "retrying without --lcov")))))

  (context "ensure-coverage-success!"
    (it "is a no-op for zero exit code"
      (let [out (java.io.StringWriter.)]
        (binding [*out* out]
          (#'crap4clj.core/ensure-coverage-success! 0)
          (should= "" (str out)))))

    (it "prints failure and calls exit hook for non-zero status"
      (let [out (java.io.StringWriter.)
            status* (atom nil)]
        (with-redefs [crap4clj.core/exit! (fn [s] (reset! status* s))]
          (binding [*out* out]
            (#'crap4clj.core/ensure-coverage-success! 2)
            (should (str/includes? (str out) "Coverage failed (exit 2)"))
            (should= 1 @status*))))))

  (context "maybe-debug-lcov-mismatch"
    (it "delegates when lcov exists but source lookup misses"
      (let [called? (atom false)]
        (with-redefs [crap4clj.core/debug-lcov-mismatch (fn [_ _] (reset! called? true))]
          (#'crap4clj.core/maybe-debug-lcov-mismatch {:x 1} nil "src/foo.clj")
          (should @called?))))

    (it "does nothing when lcov lookup succeeds"
      (let [called? (atom false)]
        (with-redefs [crap4clj.core/debug-lcov-mismatch (fn [_ _] (reset! called? true))]
          (#'crap4clj.core/maybe-debug-lcov-mismatch {:x 1} {1 {:covered 1 :total 1}} "src/foo.clj")
          (should-not @called?)))))

  (context "-main"
    (it "runs pipeline and prints report"
      (let [out (java.io.StringWriter.)]
        (with-redefs [crap4clj.core/delete-coverage-dir (fn [_] nil)
                      crap4clj.core/run-coverage-with-lcov (fn [_] 0)
                      crap4clj.coverage/load-lcov (fn [_] {:lcov true})
                      crap4clj.core/sorted-entries
                      (fn [options _]
                        (should= ["foo"] (:module-filters options))
                        [{:name "foo"}])
                      crap4clj.crap/format-report (fn [_] "CRAP REPORT")]
          (binding [*out* out]
            (-main "foo")
            (should (str/includes? (str out) "CRAP REPORT"))))))

    (it "runs through parsed command maps"
      (let [out (java.io.StringWriter.)]
        (with-redefs [crap4clj.core/run (fn [options]
                                          (println (:action options)))]
          (binding [*out* out]
            (-main "foo")
            (should (str/includes? (str out) ":analyze")))))))

  (context "run"
    (it "prints help without running coverage or analysis"
      (let [out (java.io.StringWriter.)
            forbidden (fn [& _]
                        (throw (ex-info "help must not run analysis" {})))]
        (with-redefs [crap4clj.core/delete-coverage-dir forbidden
                      crap4clj.core/run-coverage-with-lcov forbidden
                      crap4clj.coverage/load-lcov forbidden
                      crap4clj.core/sorted-entries forbidden]
          (binding [*out* out]
            (run {:action :help
                  :message "Usage: clj -M:crap\n--help"})
            (should (str/includes? (str out) "Usage: clj -M:crap"))
            (should (str/includes? (str out) "--help"))))))

    (it "uses existing coverage without deleting or running coverage"
      (let [out (java.io.StringWriter.)
            forbidden (fn [& _]
                        (throw (ex-info "existing coverage must not run coverage setup" {})))]
        (with-redefs [crap4clj.core/delete-coverage-dir forbidden
                      crap4clj.core/run-coverage-with-lcov forbidden
                      crap4clj.coverage/load-lcov (fn [path]
                                                    (should= "custom/lcov.info" path)
                                                    {:lcov true})
                      crap4clj.core/sorted-entries
                      (fn [options _]
                        (should= ["custom/src"] (:source-roots options))
                        [])
                      crap4clj.crap/format-report (fn [_] "CRAP REPORT")]
          (binding [*out* out]
            (run {:action :analyze
                  :source-roots ["custom/src"]
                  :lcov-path "custom/lcov.info"
                  :use-existing-coverage? true
                  :coverage-command nil
                  :module-filters []})
            (should (str/includes? (str out) "CRAP REPORT")))))))

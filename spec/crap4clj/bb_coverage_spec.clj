(ns crap4clj.bb-coverage-spec
  (:require [clojure.tools.reader.reader-types :as reader-types]
            [speclj.core :refer :all]
            [crap4clj.bb-coverage :refer :all]))

(def test-file-extensions '(".clj" ".cljc"))
(def test-clj-platform {:extensions '(".clj" ".cljc")
                        :read-opts {:features #{:clj}}})
(def test-clj-read-opts {:features #{:clj}})
(defn test-source-forms [_] :clj-forms)
(def delegated-args (atom nil))

(defn record-main [& args]
  (reset! delegated-args args)
  :covered)

(describe "Babashka Cloverage adapter"
  (context "enable-bb-sources!"
    (it "adds .bb to source discovery and resource lookup"
      (with-redefs [test-file-extensions '(".clj" ".cljc")
                    test-clj-platform {:extensions '(".clj" ".cljc")
                                       :read-opts {:features #{:clj}}}
                    test-clj-read-opts {:features #{:clj}}
                    test-source-forms (fn [_] :clj-forms)
                    crap4clj.bb-coverage/resolve-required-var
                    (fn [sym]
                      (case sym
                        clojure.tools.namespace.file/clojure-extensions
                        #'test-file-extensions

                        clojure.tools.namespace.find/clj
                        #'test-clj-platform

                        clojure.tools.namespace.parse/clj-read-opts
                        #'test-clj-read-opts

                        cloverage.source/forms
                        #'test-source-forms))]
        (enable-bb-sources!)
        (should-contain ".bb" test-file-extensions)
        (should-contain ".bb" (:extensions test-clj-platform))
        (should-contain :bb (get-in test-clj-platform [:read-opts :features]))
        (should-contain :bb (:features test-clj-read-opts))
        (should= @#'crap4clj.bb-coverage/read-bb-forms
                 @#'test-source-forms)))

    (it "does not add duplicate extensions"
      (should= '(".bb" ".clj" ".cljc")
        (#'crap4clj.bb-coverage/add-bb-extension
          '(".bb" ".clj" ".cljc"))))

    (it "adds the :bb reader feature without removing :clj"
      (should= #{:bb :clj}
        (:features
          (#'crap4clj.bb-coverage/add-bb-reader-feature
            {:read-cond :allow :features #{:clj}}))))

    (it "reads the :bb branch of reader conditionals"
      (with-open [reader
                  (reader-types/indexing-push-back-reader
                    "#?(:bb :babashka :clj :clojure)\n(+ 1 2)")]
        (should= [:babashka '(+ 1 2)]
          (vec (#'crap4clj.bb-coverage/read-bb-forms reader))))))

  (context "-main"
    (it "enables .bb files before delegating to Cloverage"
      (let [enabled? (atom false)]
        (reset! delegated-args nil)
        (with-redefs [crap4clj.bb-coverage/enable-bb-sources!
                      (fn [] (reset! enabled? true))
                      crap4clj.bb-coverage/resolve-required-var
                      (fn [_] #'record-main)]
          (should= :covered (-main "-p" "scripts" "--lcov")))
        (should @enabled?)
        (should= '("-p" "scripts" "--lcov") @delegated-args)))))

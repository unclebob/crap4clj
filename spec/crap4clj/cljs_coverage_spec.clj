(ns crap4clj.cljs-coverage-spec
  (:require [speclj.core :refer :all]
            [crap4clj.cljs-coverage :refer :all]))

(def test-file-extensions '(".clj" ".cljc"))
(def test-clj-platform {:extensions '(".clj" ".cljc")
                        :read-opts {:features #{:clj}}})
(def delegated-args (atom nil))

(defn record-main [& args]
  (reset! delegated-args args)
  :covered)

(describe "ClojureScript Cloverage adapter"
  (context "enable-cljs-sources!"
    (it "adds .cljs to source discovery and resource lookup"
      (with-redefs [test-file-extensions '(".clj" ".cljc")
                    test-clj-platform {:extensions '(".clj" ".cljc")
                                       :read-opts {:features #{:clj}}}
                    crap4clj.cljs-coverage/resolve-required-var
                    (fn [sym]
                      (case sym
                        clojure.tools.namespace.file/clojure-extensions
                        #'test-file-extensions

                        clojure.tools.namespace.find/clj
                        #'test-clj-platform))]
        (enable-cljs-sources!)
        (should-contain ".cljs" test-file-extensions)
        (should-contain ".cljs" (:extensions test-clj-platform))
        (should= #{:clj} (get-in test-clj-platform [:read-opts :features]))))

    (it "does not add duplicate extensions"
      (should= '(".cljs" ".clj" ".cljc")
        (#'crap4clj.cljs-coverage/add-cljs-extension
          '(".cljs" ".clj" ".cljc")))))

  (context "-main"
    (it "enables .cljs files before delegating to Cloverage"
      (let [enabled? (atom false)]
        (reset! delegated-args nil)
        (with-redefs [crap4clj.cljs-coverage/enable-cljs-sources!
                      (fn [] (reset! enabled? true))
                      crap4clj.cljs-coverage/resolve-required-var
                      (fn [_] #'record-main)]
          (should= :covered (-main "-p" "src" "--lcov")))
        (should @enabled?)
        (should= '("-p" "src" "--lcov") @delegated-args)))))

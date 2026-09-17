(ns crap4clj.doseq-double-count-spec
  (:require [clojure.string :as str]
            [speclj.core :refer :all]
            [crap4clj.doseq-double-count :as ddc]
            [crap4clj.core :as core]))

(def enemies-like
  (str "(ns demo.render)\n"
       "(defn enemies! [state]\n"
       "  (doseq [e es]\n"
       "    (let [x 1]\n"
       "      (foo x)\n"
       "      (bar x)))\n"
       "  (no-stroke))\n"))

(def enemies-cov
  {2 {:covered 1 :total 1}
   3 {:covered 28 :total 33}
   4 {:covered 12 :total 24}
   5 {:covered 15 :total 30}
   6 {:covered 15 :total 30}
   7 {:covered 2 :total 2}})

(describe "doseq double-count correction"
  (context "form-ranges"
    (it "finds a doseq form's line range"
      (should= [{:start-line 3 :end-line 6}]
               (ddc/form-ranges enemies-like)))

    (it "finds for and namespaced doseq"
      (let [source (str "(defn a []\n"
                        "  (for [x xs] x))\n"
                        "(defn b []\n"
                        "  (clojure.core/doseq [y ys]\n"
                        "    y))\n")]
        (should= [{:start-line 2 :end-line 2}
                  {:start-line 4 :end-line 5}]
                 (ddc/form-ranges source))))

    (it "ignores doseq in comments and strings"
      (let [source (str "; (doseq [e es] e)\n"
                        "\"(doseq [e es] e)\"\n"
                        "(defn a [] 1)\n")]
        (should= [] (ddc/form-ranges source))))

    (it "does not treat format as for"
      (should= [] (ddc/form-ranges "(defn a [] (format \"%s\" x))\n"))))

  (context "doubled-form-count?"
    (it "matches the 50% fingerprint"
      (should (ddc/doubled-form-count? {:covered 15 :total 30}))
      (should (ddc/doubled-form-count? {:covered 1 :total 2})))

    (it "rejects uncovered, full, and uneven counts"
      (should-not (ddc/doubled-form-count? {:covered 0 :total 30}))
      (should-not (ddc/doubled-form-count? {:covered 15 :total 15}))
      (should-not (ddc/doubled-form-count? {:covered 28 :total 33}))
      (should-not (ddc/doubled-form-count? {:covered 5 :total 20}))))

  (context "correct-line-cov"
    (it "covers the live copy of doubled doseq body lines"
      (let [result (ddc/correct-line-cov enemies-like enemies-cov)]
        (should= [4 5 6] (:corrected-lines result))
        (should= 1 (:form-count result))
        (should= {:covered 12 :total 12} (get-in result [:line-cov 4]))
        (should= {:covered 15 :total 15} (get-in result [:line-cov 5]))
        (should= {:covered 28 :total 33} (get-in result [:line-cov 3]))
        (should= {:covered 2 :total 2} (get-in result [:line-cov 7]))))

    (it "does not rewrite 50% lines outside doseq/for"
      (let [source "(defn a []\n  (if x 1 0))\n"
            line-cov {1 {:covered 1 :total 1}
                      2 {:covered 5 :total 10}}
            result (ddc/correct-line-cov source line-cov)]
        (should= [] (:corrected-lines result))
        (should= {:covered 5 :total 10} (get-in result [:line-cov 2]))))

    (it "raises function coverage when the body was doubled"
      (let [fns [{:name "enemies!" :start-line 2 :end-line 7 :complexity 9}]
            before (core/build-entries fns enemies-cov "demo.render")
            after (core/build-entries
                   fns
                   (:line-cov (ddc/correct-line-cov enemies-like enemies-cov))
                   "demo.render")]
        (should (< (:coverage (first after)) 100.0))
        (should (< (:coverage (first before)) (:coverage (first after)))))))

  (context "format-summary"
    (it "reports no match"
      (should= "doseq double-count: no doubled doseq/for bodies found"
               (ddc/format-summary [{:path "a.clj" :corrected-lines [] :form-count 0}])))

    (it "confirms corrected files"
      (let [text (ddc/format-summary
                  [{:path "src/foo.cljc"
                    :corrected-lines [4 5 6]
                    :form-count 1}])]
        (should (str/includes? text "src/foo.cljc: confirmed 3 lines in 1 doseq/for form"))))))

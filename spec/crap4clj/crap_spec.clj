(ns crap4clj.crap-spec
  (:require [speclj.core :refer :all]
            [crap4clj.crap :refer :all]))

(describe "CRAP metric"
  (context "crap-score"
    (it "returns CC for 100% covered function"
      ;; CRAP = CC^2 * (1-1)^3 + CC = 0 + CC = CC
      (should= 5.0 (crap-score 5 100.0)))

    (it "returns CC^2 + CC for 0% covered function"
      ;; CRAP = CC^2 * 1 + CC = CC^2 + CC
      (should= 30.0 (crap-score 5 0.0)))

    (it "computes correctly for partial coverage"
      ;; CC=8, cov=45 => 64 * (1-0.45)^3 + 8 = 64 * 0.166375 + 8 = 10.648 + 8 = 18.648
      (should (< (Math/abs (- 18.648 (crap-score 8 45.0))) 0.01)))

    (it "returns 1.0 for trivial fully-covered function"
      (should= 1.0 (crap-score 1 100.0))))

  (context "sort-by-crap"
    (it "sorts entries by crap score descending"
      (let [entries [{:name "a" :crap 10.0}
                     {:name "b" :crap 50.0}
                     {:name "c" :crap 1.0}]
            sorted (sort-by-crap entries)]
        (should= ["b" "a" "c"] (map :name sorted)))))

  (context "format-report"
    (it "produces a text table"
      (let [entries [{:name "foo" :namespace "test.bar" :complexity 3 :coverage 85.0 :crap 4.5}]
            report (format-report entries)]
        (should-contain "foo" report)
        (should-contain "test.bar" report)
        (should-contain "CRAP" report)))))

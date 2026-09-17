;; mutation-tested: 2026-09-17
;; Opt-in Cloverage doseq/for double-count correction.
(ns crap4clj.doseq-double-count
  "Cloverage instruments both copies of a doseq/for body. Executed body lines
  then show exactly 50% form coverage. This ns detects that fingerprint inside
  doseq/for forms and treats the live copy as fully covered."
  (:require [clojure.string :as str]
            [crap4clj.complexity :as complexity]))

(def ^:private form-pattern
  #"\((?:clojure\.core/)?(?:doseq|for)[\s\n]*\[")

(defn- line-of
  [text idx]
  (inc (count (re-seq #"\n" (subs text 0 idx)))))

(defn- matching-close-idx
  [text open-idx]
  (loop [i (inc open-idx)
         depth 1]
    (cond
      (zero? depth) (dec i)
      (>= i (count text)) nil
      (= \( (nth text i)) (recur (inc i) (inc depth))
      (= \) (nth text i)) (recur (inc i) (dec depth))
      :else (recur (inc i) depth))))

(defn form-ranges
  "Inclusive line ranges of doseq/for forms in source."
  [source]
  (let [text (complexity/without-strings-and-comments source)]
    (loop [from 0
           ranges []]
      (let [remaining (subs text from)
            matched (re-find form-pattern remaining)]
        (if matched
          (let [open (+ from (str/index-of remaining matched))
                close (matching-close-idx text open)]
            (if close
              (recur (inc close)
                     (conj ranges {:start-line (line-of text open)
                                   :end-line (line-of text close)}))
              (recur (inc open) ranges)))
          ranges)))))

(defn doubled-form-count?
  "True when a line's form counts are the 50% doseq/for fingerprint."
  [{:keys [covered total]}]
  (and (number? covered)
       (number? total)
       (pos? covered)
       (= total (* 2 covered))))

(defn- lines-in-range
  [line-cov {:keys [start-line end-line]}]
  (filter (fn [ln]
            (and (>= ln start-line)
                 (<= ln end-line)
                 (doubled-form-count? (get line-cov ln))))
          (keys line-cov)))

(defn doubled-lines
  [source line-cov]
  (->> (form-ranges source)
       (mapcat #(lines-in-range line-cov %))
       distinct
       sort
       vec))

(defn- fully-cover
  [entry]
  (let [covered (:covered entry)]
    {:covered covered :total covered}))

(defn correct-line-cov
  "Return a map with corrected line coverage and the lines that changed."
  [source line-cov]
  (let [lines (doubled-lines source line-cov)
        ranges (form-ranges source)
        corrected (reduce (fn [m ln]
                            (if (contains? m ln)
                              (assoc m ln (fully-cover (get m ln)))
                              m))
                          line-cov
                          lines)]
    {:line-cov corrected
     :corrected-lines lines
     :form-count (count ranges)}))

(defn format-summary
  "Human confirmation of detections, or a no-match note."
  [file-results]
  (let [hits (filter #(seq (:corrected-lines %)) file-results)]
    (if (empty? hits)
      "doseq double-count: no doubled doseq/for bodies found"
      (str "doseq double-count\n"
           "==================\n"
           (str/join "\n"
                     (map (fn [{:keys [path corrected-lines form-count]}]
                            (format "%s: confirmed %d line%s in %d doseq/for form%s"
                                    path
                                    (count corrected-lines)
                                    (if (= 1 (count corrected-lines)) "" "s")
                                    form-count
                                    (if (= 1 form-count) "" "s")))
                          hits))))))

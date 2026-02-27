(ns crap4clj.complexity
  (:require [clojure.string :as str]))

(def ^:private decision-point-pattern
  #"\((if-not|if-let|if-some|when-not|when-let|when-some|when-first|if|when|and|or|loop|catch)[\s\)]")

(def ^:private cond-form-pattern
  #"\((cond->>|cond->|cond|condp|case)[\s\)]")

(defn- strip-strings [text]
  (str/replace text #"\"(?:[^\"\\]|\\.)*\"" "\"\""))

(defn- strip-comments [text]
  (->> (str/split-lines text)
       (remove #(re-matches #"\s*;.*" %))
       (str/join "\n")))

(defn- open-bracket? [ch]
  (or (= ch \() (= ch \{) (= ch \[)))

(defn- close-bracket? [ch]
  (or (= ch \)) (= ch \}) (= ch \])))

(defn- count-top-level-forms
  "Counts top-level forms in text starting at idx (inside an open paren).
   Returns the count of forms at depth 1 before the matching close paren."
  [text start-idx]
  (loop [i start-idx, depth 1, forms 0, in-form false]
    (if (or (>= i (count text)) (zero? depth))
      forms
      (let [ch (nth text i)]
        (cond
          (open-bracket? ch)
          (recur (inc i) (inc depth)
                 (if (and (= depth 1) (not in-form)) (inc forms) forms)
                 true)

          (close-bracket? ch)
          (recur (inc i) (dec depth) forms
                 (if (= depth 1) false in-form))

          (Character/isWhitespace ch)
          (recur (inc i) depth forms
                 (if (= depth 1) false in-form))

          :else
          (recur (inc i) depth
                 (if (and (= depth 1) (not in-form)) (inc forms) forms)
                 true))))))

(defn- skip-to-body
  "Returns index just past the form keyword (e.g., past 'cond' in '(cond ...')."
  [text match-start]
  (let [after-paren (inc match-start)]
    (loop [i after-paren]
      (if (or (>= i (count text))
              (Character/isWhitespace (nth text i))
              (= (nth text i) \)))
        i
        (recur (inc i))))))

(defn- count-clauses [text form-type match-start]
  (let [body-start (skip-to-body text match-start)
        total-forms (count-top-level-forms text body-start)
        skip (case form-type "condp" 2 "case" 1 "cond->" 1 "cond->>" 1 0)
        remaining (- total-forms skip)
        has-default? (and (= form-type "case") (odd? remaining))]
    (if has-default?
      (inc (quot remaining 2))
      (quot remaining 2))))

(defn- count-cond-decisions [clean]
  (let [matcher (re-matcher cond-form-pattern clean)]
    (loop [total 0]
      (if-not (.find matcher)
        total
        (let [form-type (second (re-find cond-form-pattern (.group matcher)))
              match-start (.start matcher)]
          (recur (+ total (count-clauses clean form-type match-start))))))))

(defn cyclomatic-complexity [fn-text]
  (let [clean (-> fn-text strip-strings strip-comments)
        simple (count (re-seq decision-point-pattern clean))
        cond-decisions (count-cond-decisions clean)]
    (+ 1 simple cond-decisions)))

(def ^:private defn-pattern #"^\(defn-?\s+(\S+)")

(defn- find-defn-starts [lines]
  (keep-indexed
    (fn [idx line]
      (when-let [m (re-find defn-pattern line)]
        {:name (second m) :start-line (inc idx)}))
    lines))

(defn- last-non-blank-before [lines line-idx]
  (loop [i (dec line-idx)]
    (cond
      (neg? i) line-idx
      (not (str/blank? (nth lines i))) (inc i)
      :else (recur (dec i)))))

(defn- assign-end-lines [starts lines]
  (let [total (count lines)]
    (map-indexed
      (fn [i start]
        (let [end (if (< i (dec (count starts)))
                    (last-non-blank-before lines (dec (:start-line (nth starts (inc i)))))
                    total)]
          (assoc start :end-line end)))
      starts)))

(defn- fn-text-from-lines [lines {:keys [start-line end-line]}]
  (str/join "\n" (subvec lines (dec start-line) end-line)))

(defn extract-functions [source]
  (let [lines (vec (str/split-lines source))
        starts (find-defn-starts lines)
        with-ends (assign-end-lines starts lines)]
    (mapv (fn [finfo]
            (let [text (fn-text-from-lines lines finfo)]
              (assoc finfo :complexity (cyclomatic-complexity text))))
          with-ends)))

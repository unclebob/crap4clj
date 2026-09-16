(ns crap4clj.cljs-coverage
  "Make Cloverage discover and instrument .cljs sources.

  Cloverage still reads forms with :features #{:clj}, so JavaScript interop
  must sit behind #?(:cljs ...) with a #?(:clj ...) branch that can eval
  on the JVM.")

(def ^:private cljs-extension ".cljs")

(defn- add-cljs-extension [extensions]
  (if (some #{cljs-extension} extensions)
    extensions
    (conj extensions cljs-extension)))

(defn- resolve-required-var [sym]
  (or (requiring-resolve sym)
      (throw (ex-info (str "Cannot resolve " sym) {:symbol sym}))))

(defn enable-cljs-sources!
  "Add .cljs to Cloverage / tools.namespace JVM source discovery.
  Form reading stays #{:clj} so reader conditionals can supply JVM stubs."
  []
  (let [file-extensions-var
        (resolve-required-var 'clojure.tools.namespace.file/clojure-extensions)
        clj-platform-var
        (resolve-required-var 'clojure.tools.namespace.find/clj)]
    (alter-var-root file-extensions-var add-cljs-extension)
    (alter-var-root clj-platform-var
                    #(update % :extensions add-cljs-extension))))

(defn- coverage-main
  []
  (or (try (resolve-required-var 'speclj.cloverage/-main)
           (catch Exception _ nil))
      (resolve-required-var 'cloverage.coverage/-main)))

(defn -main [& args]
  (enable-cljs-sources!)
  (apply (coverage-main) args))

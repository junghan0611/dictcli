(ns dictcli.stem-main
  "dictcli stem CLI 엔트리포인트 — Kiwi JNI 포함 (JVM 전용)
   
   run.sh stem에서 호출. gen-class 없이 -m으로 직접 실행."
  (:require [dictcli.stem :as stem]
            [dictcli.graph :as g]
            [dictcli.core :as core]
            [clojure.string :as str]))

(defn -main [& args]
  (let [text (first args)
        tokens? (some #{"--tokens"} args)]
    (if (nil? text)
      (do
        (println "Usage: dictcli stem <\"문장\"> [--tokens]")
        (println "  예: dictcli stem \"설계했다\"")
        (println "  예: dictcli stem \"검색증강생성을 구현했다\" --tokens"))
      (do
        (when tokens?
          (println "🔍 토큰:")
          (doseq [t (stem/tokenize text)]
            (println (str "  " (:form t) "\t" (name (:tag t))))))
        (let [noun-stems (stem/stems text)]
          (println (str "🌱 stem: " (str/join ", " noun-stems)))
          ;; expand 연결
          (let [{:keys [index]} (core/load-graph)]
            (doseq [s noun-stems]
              (let [expanded (g/expand index s)]
                (when (seq expanded)
                  (println (str "  🔍 " s " → " (str/join ", " expanded)))))))
          (println (str "📊 어간 " (count noun-stems) "개")))))))

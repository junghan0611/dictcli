(ns dictcli.stem-main
  "dictcli stem CLI 엔트리포인트 — Kiwi JNI 포함 (JVM 전용)
   
   run.sh stem에서 호출. gen-class 없이 -m으로 직접 실행.
   시작 시 graph.edn 한글 entity를 Kiwi 사용자 사전에 자동 주입."
  (:require [dictcli.stem :as stem]
            [dictcli.graph :as g]
            [dictcli.core :as core]
            [clojure.string :as str]))

;; ── 사용자 사전 주입 ──────────────────────────────

(defn- korean-entities
  "graph.edn에서 한글 entity만 추출 (Kiwi 사전 주입 대상)"
  [triples]
  (->> triples
       (map first)
       distinct
       (filter #(re-find #"[\uAC00-\uD7AF]" %))
       vec))

(defn- inject-graph-words!
  "graph.edn 한글 entity → Kiwi NNP 사용자 사전 주입.
   시작 시 1회 호출. 반환: 주입된 단어 수."
  []
  (let [triples (g/load-graph (core/graph-path))
        words (korean-entities triples)
        cnt (stem/build-with-words! words)]
    cnt))

;; ── 메인 ──────────────────────────────────────────

(defn -main [& args]
  (let [subcmd (first args)
        rest-args (rest args)]
    (case subcmd
      ;; inject 서브커맨드: 사전 주입만 테스트
      "inject"
      (let [cnt (inject-graph-words!)]
        (println (str "✅ graph.edn → Kiwi 사전 주입: " cnt "개 한글 entity")))

      ;; 기본: stem 분석 (사전 주입 + tokenize + expand)
      (let [text subcmd
            tokens? (some #{"--tokens"} rest-args)
            json? (some #{"--json"} rest-args)]
        (if (nil? text)
          (do
            (println "Usage: dictcli stem <\"문장\"> [--tokens] [--json]")
            (println "       dictcli stem inject          사전 주입 테스트")
            (println "  예: dictcli stem \"설계했다\"")
            (println "  예: dictcli stem \"검색증강생성을 구현했다\" --tokens"))
          (do
            ;; 사전 주입 (매 실행 시, ~1초)
            (let [cnt (inject-graph-words!)]
              (when-not json?
                (binding [*out* *err*]
                  (println (str "📖 사전 주입: " cnt "개")))))
            (when tokens?
              (println "🔍 토큰:")
              (doseq [t (stem/tokenize text)]
                (println (str "  " (:form t) "\t" (name (:tag t))))))
            (let [noun-stems (stem/stems text)]
              (if json?
                ;; JSON 출력 (에이전트 연동)
                (println (str "[" (str/join "," (map #(str "\"" % "\"") noun-stems)) "]"))
                ;; 사람 읽기용
                (do
                  (println (str "🌱 stem: " (str/join ", " noun-stems)))
                  ;; expand 연결
                  (let [{:keys [index]} (core/load-graph)]
                    (doseq [s noun-stems]
                      (let [expanded (g/expand index s)]
                        (when (seq expanded)
                          (println (str "  🔍 " s " → " (str/join ", " expanded)))))))
                  (println (str "📊 어간 " (count noun-stems) "개")))))))))))

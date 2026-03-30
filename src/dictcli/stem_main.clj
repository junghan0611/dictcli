(ns dictcli.stem-main
  "dictcli stem CLI 엔트리포인트 — Kiwi JNI 포함 (JVM 전용)
   
   run.sh stem에서 호출. gen-class 없이 -m으로 직접 실행.
   시작 시 graph.edn 한글 entity를 Kiwi 사용자 사전에 자동 주입.
   
   모드:
   - 단건: dictcli stem \"문장\" [--tokens] [--json]
   - 배치: dictcli stem --batch < input.txt  (줄 단위 stdin → JSON stdout)
   - 주입: dictcli stem inject"
  (:require [dictcli.stem :as stem]
            [dictcli.graph :as g]
            [dictcli.core :as core]
            [clojure.string :as str]
            [clojure.java.io :as io]))

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

;; ── 배치 모드 ─────────────────────────────────────

(defn- run-batch!
  "stdin에서 줄 단위로 읽어 stem 결과를 JSON으로 stdout에 출력.
   JVM 1회 시작 → 수천 줄 처리. 인덱싱 파이프라인용.
   
   입력: 한 줄에 하나의 텍스트 (청크)
   출력: 한 줄에 하나의 JSON 배열 [\"stem1\",\"stem2\",...]
   빈 줄 → []
   
   stderr에 진행 상황 출력."
  []
  (let [cnt (inject-graph-words!)
        start (System/currentTimeMillis)]
    (binding [*out* *err*]
      (println (str "📖 사전 주입: " cnt "개, 배치 모드 시작 (stdin → stdout)")))
    (let [rdr (io/reader *in*)
          lines (line-seq rdr)
          processed (atom 0)]
      (doseq [line lines]
        (let [text (str/trim line)
              result (if (str/blank? text)
                       []
                       (stem/stems text))
              json (str "[" (str/join "," (map #(str "\"" % "\"") result)) "]")]
          (println json)
          (flush)
          (swap! processed inc)
          (when (zero? (mod @processed 100))
            (binding [*out* *err*]
              (println (str "  ... " @processed "줄 처리"))))))
      (let [elapsed (/ (- (System/currentTimeMillis) start) 1000.0)]
        (binding [*out* *err*]
          (println (str "✅ 배치 완료: " @processed "줄, " (format "%.1f" elapsed) "초"
                        (when (pos? @processed)
                          (str " (" (format "%.0f" (/ @processed elapsed)) "줄/초)")))))))))

;; ── 메인 ──────────────────────────────────────────

(defn -main [& args]
  (let [subcmd (first args)
        rest-args (rest args)]
    (case subcmd
      ;; inject: 사전 주입만 테스트
      "inject"
      (let [cnt (inject-graph-words!)]
        (println (str "✅ graph.edn → Kiwi 사전 주입: " cnt "개 한글 entity")))

      ;; batch: stdin → stdout 스트리밍
      "--batch"
      (run-batch!)

      ;; 기본: 단건 stem 분석
      (let [text subcmd
            tokens? (some #{"--tokens"} rest-args)
            json? (some #{"--json"} rest-args)]
        (if (nil? text)
          (do
            (println "Usage: dictcli stem <\"문장\"> [--tokens] [--json]")
            (println "       dictcli stem --batch          stdin 배치 (인덱싱용)")
            (println "       dictcli stem inject           사전 주입 테스트")
            (println "  예: dictcli stem \"설계했다\"")
            (println "  예: dictcli stem \"검색증강생성을 구현했다\" --tokens")
            (println "  예: echo -e \"문장1\\n문장2\" | dictcli stem --batch"))
          (do
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
                  (let [{:keys [index]} (core/load-graph)]
                    (doseq [s noun-stems]
                      (let [expanded (g/expand index s)]
                        (when (seq expanded)
                          (println (str "  🔍 " s " → " (str/join ", " expanded)))))))
                  (println (str "📊 어간 " (count noun-stems) "개")))))))))))

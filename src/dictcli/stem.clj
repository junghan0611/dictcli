(ns dictcli.stem
  "Kiwi 형태소 분석기 통합 — 한국어 어간 추출
   
   KiwiJava JNI 바인딩을 Clojure interop으로 호출.
   jar: lib/kiwi-java-*.jar (OS별 네이티브 .so 내장)
   모델: models/models/cong/base/ (105MB)
   
   v0.23.0 API: tokenize(String, AnalyzeOption)"
  (:import [kr.pe.bab2min Kiwi Kiwi$Token Kiwi$POSTag Kiwi$Match
            Kiwi$AnalyzeOption KiwiBuilder]))

;; ── 상수 ──────────────────────────────────────────

(def ^:private default-model-path
  "Kiwi 모델 경로. KIWI_MODEL 환경변수로 오버라이드."
  (or (System/getenv "KIWI_MODEL")
      "models/models/cong/base/"))

;; NNG(일반명사), NNP(고유명사) — 어간으로 추출할 품사
(def ^:private noun-tags
  #{Kiwi$POSTag/nng Kiwi$POSTag/nnp})

;; ── AnalyzeOption 싱글턴 ──────────────────────────

(def ^:private default-option
  "allWithNormalizing 매치 옵션"
  (Kiwi$AnalyzeOption. (int Kiwi$Match/allWithNormalizing)
                       nil                     ;; blocklist
                       (short 0)               ;; allowedDialects (standard)
                       (float 0.0)             ;; dialectCost
                       nil                     ;; typoTransformer
                       (float 0.0)))           ;; typoThreshold

;; ── Kiwi 인스턴스 (lazy atom) ──────────────────────

(defonce ^:private kiwi-atom (atom nil))

(defn- ensure-kiwi
  "Kiwi 인스턴스 반환. 없으면 초기화."
  []
  (or @kiwi-atom
      (let [kiwi (Kiwi/init default-model-path)]
        (reset! kiwi-atom kiwi)
        kiwi)))

;; ── 토크나이즈 ────────────────────────────────────

(defn tokenize
  "문장을 Kiwi로 토크나이즈. Token 배열 → Clojure 맵 시퀀스.
   
   (tokenize \"설계했다\")
   ;=> [{:form \"설계\" :tag :nng :pos 0 :len 2}
   ;    {:form \"하\" :tag :xsv :pos 2 :len 1} ...]"
  [text]
  (let [kiwi (ensure-kiwi)
        tokens (.tokenize kiwi (str text) default-option)]
    (mapv (fn [^Kiwi$Token t]
            {:form     (.-form t)
             :tag-byte (.-tag t)
             :tag      (keyword (.toLowerCase (Kiwi$POSTag/toString (.-tag t))))
             :pos      (.-position t)
             :len      (.-length t)})
          tokens)))

;; ── 어간 추출 ─────────────────────────────────────

(defn stems
  "문장에서 명사 어간만 추출.
   
   (stems \"설계했다\")       ;=> [\"설계\"]
   (stems \"검색증강생성을 구현했다\") ;=> [\"검색\" \"증강\" \"생성\" \"구현\"]"
  [text]
  (->> (tokenize text)
       (filter #(noun-tags (:tag-byte %)))
       (mapv :form)
       distinct
       vec))

;; ── 사용자 사전 주입 ──────────────────────────────

(defn build-with-words!
  "graph.edn 엔티티를 Kiwi 사용자 사전에 주입하고 새 인스턴스로 교체.
   addWord(form, tag, score) — NNP 고유명사로 등록.
   
   인덱싱 시점에 한 번 호출. 이후 stems/tokenize가 새 사전 사용."
  [words]
  (with-open [builder (KiwiBuilder. (str default-model-path))]
    (doseq [w words]
      (.addWord builder (str w) Kiwi$POSTag/nnp (float 0.0)))
    (let [new-kiwi (.build builder)]
      (reset! kiwi-atom new-kiwi)
      (count words))))

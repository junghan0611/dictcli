(ns dictcli.graph
  "EDN 트리플 그래프 — 코드가 곧 데이터
   
   트리플: [entity relation value]
   관계 타입: :trans :opposite :related :synonym :broader :narrower :domain :source"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]))

;; ── 관계 타입 ──────────────────────────────────────

(def relation-types
  "허용된 관계 타입과 설명"
  {:trans    "번역/대응 (한↔영)"
   :opposite "대극/반대"
   :related  "의미 연결"
   :synonym  "동의/유사 (같은 언어 내)"
   :broader  "상위 개념"
   :narrower "하위 개념"
   :domain   "소속 영역"
   :source   "출처 메타노트 ID"})

(def inverse-relations
  "대칭 관계 — a→b이면 b→a도 성립"
  {:opposite :opposite
   :synonym  :synonym
   :related  :related})

;; ── 로드/세이브 ────────────────────────────────────

(defn load-graph
  "EDN 파일에서 트리플 벡터 로드. 없으면 빈 벡터."
  [path]
  (if (.exists (io/file path))
    (edn/read-string (slurp path))
    []))

(defn save-graph
  "트리플 벡터를 EDN 파일로 저장. 읽기 좋게 포맷팅."
  [path triples]
  (let [header (str ";; graph.edn — 힣의 어휘 연결체\n"
                    ";; 형식: [entity relation value]\n"
                    ";; 생성: " (java.time.LocalDateTime/now) "\n"
                    ";; 트리플: " (count triples) "개\n\n")]
    (spit path
          (str header
               "[\n"
               (str/join "\n" (map #(str " " (pr-str %)) triples))
               "\n]\n"))))

;; ── 인메모리 인덱스 ───────────────────────────────

(defn build-index
  "트리플 벡터 → {:by-entity {word [triples...]} :by-value {word [triples...]}}"
  [triples]
  {:by-entity (group-by first triples)
   :by-value  (group-by #(nth % 2) triples)
   :by-rel    (group-by second triples)
   :count     (count triples)})

;; ── 쿼리 ──────────────────────────────────────────

(defn query-entity
  "단어를 entity로 가진 모든 트리플"
  [index word]
  (get (:by-entity index) word []))

(defn query-value
  "단어를 value로 가진 모든 트리플 (역방향)"
  [index word]
  (get (:by-value index) word []))

(defn query-word
  "단어가 entity든 value든 연결된 모든 트리플"
  [index word]
  (into (query-entity index word)
        (query-value index word)))

(defn query-relation
  "특정 관계 타입의 모든 트리플"
  [index rel]
  (get (:by-rel index) rel []))

;; ── 고수준 함수 ───────────────────────────────────

(defn translations
  "단어의 번역어 목록. 양방향 탐색."
  [index word]
  (let [forward  (->> (query-entity index word)
                      (filter #(= (second %) :trans))
                      (map #(nth % 2)))
        backward (->> (query-value index word)
                      (filter #(= (second %) :trans))
                      (map first))]
    (distinct (concat forward backward))))

(defn opposites
  "단어의 대극어. 양방향."
  [index word]
  (let [forward  (->> (query-entity index word)
                      (filter #(= (second %) :opposite))
                      (map #(nth % 2)))
        backward (->> (query-value index word)
                      (filter #(= (second %) :opposite))
                      (map first))]
    (distinct (concat forward backward))))

(defn cluster-members
  "같은 :source 메타노트를 공유하는 단어 + 그 단어에서 :synonym/:related/:opposite로 연결된 단어들"
  [index meta-id]
  (let [;; 직접 :source를 가진 단어들
        seeds (->> (query-value index meta-id)
                   (filter #(= (second %) :source))
                   (map first)
                   set)
        ;; seeds에서 :synonym, :related, :opposite로 1홉 확장
        expand-one (fn [word]
                     (->> (query-entity index word)
                          (filter #(#{:synonym :related :opposite} (second %)))
                          (map #(nth % 2))))
        expanded (into seeds (mapcat expand-one seeds))]
    (sort expanded)))

(defn expand
  "쿼리 확장 — 단어에서 도달 가능한 모든 영어 키워드.
   1. 직접 번역 (:trans)
   2. 대극의 번역
   3. 관련어의 번역
   knowledge_search 연동용."
  [index word]
  (let [;; 1단계: 직접 번역
        direct (translations index word)
        ;; 2단계: 대극어의 번역
        opps (opposites index word)
        opp-trans (mapcat #(translations index %) opps)
        ;; 3단계: 관련어의 번역
        related (->> (query-entity index word)
                     (filter #(#{:related :synonym} (second %)))
                     (map #(nth % 2)))
        rel-trans (mapcat #(translations index %) related)
        ;; 합치기 (원래 단어와 영어가 아닌 것 제외하지 않음 — 확장이 목적)
        all (distinct (concat direct opp-trans rel-trans))]
    all))

;; ── 트리플 추가 ───────────────────────────────────

(defn valid-triple?
  "트리플 유효성 검사"
  [[e r v :as triple]]
  (and (vector? triple)
       (= 3 (count triple))
       (string? e)
       (keyword? r)
       (contains? relation-types r)
       (string? v)))

(defn add-triple
  "트리플 추가. 중복 방지. 대칭 관계는 역방향도 자동 추가."
  [triples [e r v :as triple]]
  (if-not (valid-triple? triple)
    (do (println (str "⚠️  잘못된 트리플: " (pr-str triple)
                      " (관계 타입: " (str/join ", " (map name (keys relation-types))) ")"))
        triples)
    (let [existing (set triples)
          new-triples (cond-> [triple]
                        ;; 대칭 관계면 역방향도 추가
                        (contains? inverse-relations r)
                        (conj [v r e]))]
      (into triples (remove existing new-triples)))))

(defn add-triples
  "여러 트리플 한번에 추가"
  [triples new-triples]
  (reduce add-triple triples new-triples))

;; ── 통계 ──────────────────────────────────────────

(defn stats
  "그래프 통계"
  [triples]
  (let [entities (set (map first triples))
        values   (set (map #(nth % 2) triples))
        words    (set/union entities values)
        by-rel   (frequencies (map second triples))
        sources  (->> triples
                      (filter #(= (second %) :source))
                      (map #(nth % 2))
                      distinct)]
    {:triples    (count triples)
     :words      (count words)
     :entities   (count entities)
     :relations  by-rel
     :clusters   (count sources)
     :sources    sources}))

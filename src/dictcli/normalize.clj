(ns dictcli.normalize
  "graph.edn 정규화 — Denote 태그 호환 [a-z0-9]
   
   1. 영어 trans 값 소문자화
   2. 공백 제거 (붙여쓰기)
   3. 복합 한글 분리 (X와 Y, X 및 Y → X, Y + :synonym)
   4. 신토피콘 대극 쌍 분해 (Good and Evil → good, evil + 한글 분리)
   5. 잘못된 값 정리 (긴 설명, 한글 섞인 영어 등)"
  (:require [clojure.string :as str]
            [dictcli.graph :as g]))

;; ── 신토피콘 대극/복합 쌍 매핑 ──────────────────

(def syntopicon-pairs
  "X and Y → [X, Y] 분해 + 한글 분리"
  {"Good and Evil"              {:ko ["선" "악"]       :en ["good" "evil"]}
   "Life and Death"             {:ko ["삶" "죽음"]     :en ["life" "death"]}
   "Memory and Imagination"     {:ko ["기억" "상상"]   :en ["memory" "imagination"]}
   "Necessity and Contingency"  {:ko ["필연성" "우연성"] :en ["necessity" "contingency"]}
   "One and Many"               {:ko ["하나" "다수"]   :en ["one" "many"]}
   "Pleasure and Pain"          {:ko ["쾌락" "고통"]   :en ["pleasure" "pain"]}
   "Same and Other"             {:ko ["동일" "다름"]   :en ["same" "other"]}
   "Sign and Symbol"            {:ko ["기호" "상징"]   :en ["sign" "symbol"]}
   "Tyranny and Despotism"      {:ko ["폭정" "독재"]   :en ["tyranny" "despotism"]}
   "Universal and Particular"   {:ko ["보편" "특수"]   :en ["universal" "particular"]}
   "Virtue and Vice"            {:ko ["미덕" "악덕"]   :en ["virtue" "vice"]}
   "War and Peace"              {:ko ["전쟁" "평화"]   :en ["war" "peace"]}
   "Custom and Convention"      {:ko ["관습" "관례"]   :en ["custom" "convention"]}})

;; ── 복합어 → 붙여쓰기 매핑 ──────────────────────

(def compound-mappings
  "공백 있는 영어 복합어 → 붙여쓰기 + 개별 단어"
  {"virtual reality"           {:joined "virtualreality"   :parts ["virtual" "reality"]}
   "computational thinking"    {:joined "computationalthinking" :parts ["computational" "thinking"]}
   "reinforcement learning"    {:joined "reinforcementlearning" :parts ["reinforcement" "learning"]}
   "scientific revolution"     {:joined "scientificrevolution" :parts ["scientific" "revolution"]}
   "paradigm shift"            {:joined "paradigmshift"    :parts ["paradigm" "shift"]}
   "normal science"            {:joined "normalscience"    :parts ["normal" "science"]}
   "double-entry accounting"   {:joined "doubleentryaccounting" :parts ["accounting"]}
   "digital brain"             {:joined "digitalbrain"     :parts ["digital" "brain"]}
   "paideia proposal"          {:joined "paideiaproposal"  :parts ["paideia" "proposal"]}
   "nonviolent communication"  {:joined "nonviolentcommunication" :parts ["nonviolent" "communication"]}
   "great books"               {:joined "greatbooks"       :parts ["greatbooks"]}
   "grey rock technique"       {:joined "greyrocktechnique" :parts []}})

;; ── 인명 매핑 ──────────────────────────────────

(def person-mappings
  {"eckhart tolle"    "eckharttolle"
   "walter benjamin"  "walterbenjamin"})

;; ── 한글 복합어 분리 ────────────────────────────

(def korean-compound-patterns
  "한글 복합어를 분리할 패턴"
  {#"(.+)와 (.+)"  :synonym
   #"(.+)과 (.+)"  :synonym
   #"(.+) 및 (.+)" :synonym})

;; ── 정규화 로직 ─────────────────────────────────

(defn normalize-english
  "영어 값 정규화: 소문자 + 공백 제거"
  [s]
  (-> s
      str/lower-case
      (str/replace #"[^a-z0-9]" "")))

(defn is-garbage?
  "잘못된 trans 값인지 (긴 설명, 한글 섞임 등)"
  [v]
  (or (> (count v) 80)
      (and (re-find #"[가-힣]" v) (re-find #"[a-zA-Z]" v) (> (count v) 30))))

(defn normalize-triples
  "전체 트리플 정규화"
  [triples]
  (let [result (atom [])
        add! (fn [t] (swap! result conj t))]

    (doseq [[e r v :as triple] triples]
      (cond
        ;; :trans가 아닌 것은 그대로 유지
        (not= r :trans)
        (add! triple)

        ;; 가비지 값 제거
        (is-garbage? v)
        (println (str "  🗑️  제거: " (pr-str triple)))

        ;; 신토피콘 대극 쌍 분해
        (contains? syntopicon-pairs v)
        (let [{:keys [ko en]} (syntopicon-pairs v)]
          (println (str "  🔀 분해: " v " → " (str/join ", " en)))
          ;; 원래 복합 한글 엔트리에는 붙여쓰기 태그
          (add! [e :trans (normalize-english v)])
          ;; 개별 한글→영어 매핑
          (doseq [[k eng] (map vector ko en)]
            (add! [k :trans eng])))

        ;; 인명
        (contains? person-mappings (str/lower-case v))
        (do (add! [e :trans (person-mappings (str/lower-case v))]))

        ;; 복합어 (공백 포함)
        (contains? compound-mappings (str/lower-case v))
        (let [{:keys [joined parts]} (compound-mappings (str/lower-case v))]
          (add! [e :trans joined])
          (doseq [p parts]
            (add! [e :trans p])))

        ;; 일반 공백 포함 → 붙여쓰기
        (re-find #" " v)
        (do (println (str "  📝 붙여쓰기: \"" v "\" → \"" (normalize-english v) "\""))
            (add! [e :trans (normalize-english v)]))

        ;; 대문자만 → 소문자
        (re-find #"[A-Z]" v)
        (add! [e :trans (str/lower-case v)])

        ;; 나머지 그대로
        :else
        (add! triple)))

    ;; 한글 복합어 분리 (entity 쪽)
    (let [ko-compounds (filter (fn [[e _ _]]
                                 (some #(re-find % e) (keys korean-compound-patterns)))
                               @result)]
      (doseq [[e r v] ko-compounds]
        (doseq [[pattern rel-type] korean-compound-patterns]
          (when-let [m (re-find pattern e)]
            (let [w1 (nth m 1)
                  w2 (nth m 2)]
              (println (str "  🔀 한글분리: \"" e "\" → \"" w1 "\", \"" w2 "\""))
              (add! [w1 :trans v])
              (add! [w2 :trans v])
              (add! [w1 :synonym w2])
              (add! [w2 :synonym w1]))))))

    ;; 중복 제거
    (vec (distinct @result))))

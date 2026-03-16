(ns dictcli.parser
  "ten 형식 glossary 파서
   
   형식: <<용어>> :: 정의
   
   파일 구조:
   - 프론트매터 (Denote txt 형식, --- 구분)
   - # 섹션 헤딩
   - <<용어>> :: 정의 (한 줄에 하나)")

(defn parse-term-line
  "한 줄에서 <<용어>> :: 정의 추출. 없으면 nil."
  [line]
  (when-let [m (re-find #"<<(.+?)>>\s*::\s*(.*)" line)]
    {:word (nth m 1)
     :definition (nth m 2)}))

(defn detect-lang
  "단어의 언어 감지. 한글이면 :ko, 라틴이면 :en, 독일어 특수문자면 :de"
  [word]
  (cond
    (re-find #"[가-힣]" word) :ko
    (re-find #"[äöüÄÖÜß]" word) :de
    (re-find #"[a-zA-Z]" word) :en
    :else :unknown))

(defn parse-frontmatter
  "Denote txt 프론트매터 파싱. --- 로 구분된 헤더에서 key-value 추출."
  [lines]
  (let [in-front? (atom false)
        result (atom {})]
    (doseq [line lines]
      (cond
        (= (clojure.string/trim line) "---")
        (if @in-front?
          (reset! in-front? false)  ; 닫는 ---
          (reset! in-front? true))  ; 여는 ---
        
        @in-front?
        (when-let [m (re-find #"(\w+):\s+(.*)" line)]
          (swap! result assoc (keyword (nth m 1)) (nth m 2)))))
    @result))

(defn parse-section
  "# 헤딩에서 섹션명 추출. nil이면 섹션 아님."
  [line]
  (when-let [m (re-find #"^#+\s+(.*)" line)]
    (nth m 1)))

(defn parse-glossary-file
  "glossary 파일 전체 파싱. {:meta {...} :terms [{:word :definition :lang :section :line-no}...]}"
  [filepath]
  (let [lines (clojure.string/split-lines (slurp filepath))
        meta (parse-frontmatter lines)
        current-section (atom nil)
        terms (atom [])]
    (doseq [[idx line] (map-indexed vector lines)]
      (when-let [section (parse-section line)]
        (reset! current-section section))
      (when-let [{:keys [word definition]} (parse-term-line line)]
        (swap! terms conj
               {:word word
                :definition definition
                :lang (detect-lang word)
                :section @current-section
                :line-no (inc idx)
                :source filepath})))
    {:meta meta
     :terms @terms
     :filepath filepath}))

(defn parse-directory
  "디렉토리 내 모든 glossary 파일 파싱."
  [dir-path & {:keys [extensions] :or {extensions #{"txt" "org" "md"}}}]
  (let [dir (clojure.java.io/file dir-path)
        files (->> (file-seq dir)
                   (filter (fn [^java.io.File f] (.isFile f)))
                   (filter (fn [^java.io.File f]
                             (extensions (last (clojure.string/split (.getName f) #"\."))))))]
    (mapv (fn [^java.io.File f] (parse-glossary-file (.getPath f))) files)))

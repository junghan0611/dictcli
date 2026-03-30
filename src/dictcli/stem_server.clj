(ns dictcli.stem-server
  "dictcli stem-server — 상주 소켓 서버 (JVM 1회 시작, 쿼리당 ~1ms)
   
   JVM 시작 2.7초를 1회만 부담. 이후 요청은 TCP 소켓으로 즉시 응답.
   
   서버: dictcli stem --serve [port]    (기본 18230)
   클라이언트: echo '설계했다' | nc localhost 18230
   
   프로토콜:
   - 요청: 한 줄 텍스트 (UTF-8)
   - 응답: JSON 배열 + 개행 [\"stem1\",\"stem2\"]
   - 빈 줄 또는 EOF → 연결 종료"
  (:require [dictcli.stem :as stem]
            [dictcli.graph :as g]
            [dictcli.core :as core]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [java.net ServerSocket Socket]
           [java.io BufferedReader InputStreamReader PrintWriter]))

(def DEFAULT_PORT 18230)

(defn- korean-entities [triples]
  (->> triples (map first) distinct
       (filter #(re-find #"[\uAC00-\uD7AF]" %)) vec))

(defn- inject-graph-words! []
  (let [triples (g/load-graph (core/graph-path))
        words (korean-entities triples)]
    (stem/build-with-words! words)
    (count words)))

(defn- handle-client [^Socket client]
  (try
    (let [in (BufferedReader. (InputStreamReader. (.getInputStream client) "UTF-8"))
          out (PrintWriter. (.getOutputStream client) true)]
      (loop []
        (when-let [line (.readLine in)]
          (let [text (str/trim line)]
            (if (str/blank? text)
              (.println out "[]")
              (let [noun-stems (stem/stems text)
                    json (str "[" (str/join "," (map #(str "\"" % "\"") noun-stems)) "]")]
                (.println out json)))
            (recur)))))
    (catch Exception e
      (binding [*out* *err*]
        (println (str "⚠ client error: " (.getMessage e)))))
    (finally
      (.close client))))

(defn serve!
  "소켓 서버 시작. 블로킹."
  ([] (serve! DEFAULT_PORT))
  ([port]
   (let [cnt (inject-graph-words!)]
     (println (str "📖 사전 주입: " cnt "개"))
     (println (str "🚀 stem-server 시작: localhost:" port))
     (println (str "   echo '설계했다' | nc localhost " port))
     (println "   종료: Ctrl+C")
     (flush)
     (with-open [server (ServerSocket. port)]
       (.setReuseAddress server true)
       (loop []
         (let [client (.accept server)]
           (future (handle-client client))
           (recur)))))))

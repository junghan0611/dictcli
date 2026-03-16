(ns dictcli.wordmap
  "saiculture wordmap.json 파서"
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]))

(defn parse-wordmap
  "wordmap.json 파싱. {:frequency {word count} :cooccurrence {pair count}}"
  [filepath]
  (with-open [rdr (io/reader filepath)]
    (let [data (json/read rdr :key-fn keyword)]
      {:frequency (:frequency data)
       :cooccurrence (:cooccurrence_top100 data)
       :total-files (:total_files data)
       :total-unique-tags (:total_unique_tags data)})))

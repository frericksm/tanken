(ns tanken-daten.core
  (:use [net.cgrand.enlive-html]
        [clojure.pprint])
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]))

(def db-file "U:/Userdaten/Ablage/Tankstellenpreise/preise.db")
(def tank-stellen ["-1262786956" "-878843771" "-415369586"])
(def url-format "http://www.adac.de/infotestrat/tanken-kraftstoffe-und-antrieb/kraftstoffpreise/detail.aspx?ComponentId=185101,32494&ItpId=%s")

(defn fetch-page
  [url]
  (html-resource (io/reader url)))

(defn extract-tankstelle [url]
  (->> (fetch-page url)
     ((fn [p] (select p #{[:#wucKraftstoffpreiseDeDetailAdresse-6 ] [:#wucKraftstoffpreiseDeDetailAdresse-9]} )))
     (map :content)
     (map first)
     (map clojure.string/trim)
     (interpose ", ")
     (apply str)
     ))

(defn extract-preis [content]
  (try
    (->> content
       (map #(get-in % [:attrs :src] ))
       (filter #(not (nil? %)))
       (map #(->> (re-find #"/((\d|\w)+)\.gif" %) (second)))
       (map #(if (= % "punkt") "." %))
       (apply str)
       (java.math.BigDecimal.))
     (catch Exception e)))

(defn extract-val [content]
  (cond (= 1 (count content)) (.trim (first content))
        (< 1 (count content)) (extract-preis content)
        true "?")
  )

(defn parse-date [datetime]
   (try
      (.parse (java.text.SimpleDateFormat. "dd.MM.yyyy HH:mm:ss") datetime)
    (catch Exception e)))

(defn extract-preise [url]
  (->> (select (fetch-page url) [:#wucKraftstoffpreiseDeDetail-2 :table  :tr :td] )
       (map :content)
       (map extract-val)
       (partition 3 3)
       (map (fn [[treibstoff preis datum-zeit]]
              {:treibstoff treibstoff :preis preis :datum-zeit (parse-date datum-zeit)}))
       (filter (fn [{:keys [datum-zeit]}]
                 (not (nil? datum-zeit))))
       ))


(defn tank-preise [id]
  (let [daten (->> id
                   (format url-format)
                   ((juxt extract-tankstelle extract-preise)))]
    (->> (second daten)
         (map #(assoc % :tankstelle (first daten))))))

(defn collect-data []
   (let [db (->> db-file (io/reader) (java.io.PushbackReader.) (edn/read))
         new-values   (->> tank-stellen
                           (map tank-preise)
                           (apply concat)
                           (filter #(not= "Diesel" (:treibstoff %)))
                           (set))
         new-db (set/union db new-values)]
     (pprint new-db (io/writer db-file))))


(defn -main [& args]
  (while true
     (println "Collect at: " (java.util.Date.))
     (collect-data)
     (Thread/sleep (* 1000 60 17))
    ))

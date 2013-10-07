(ns tanken-daten.core
  (:use [clojure.pprint])
  (:require [net.cgrand.enlive-html :as e]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]))


;; The string to format a url with one of the tank-stellen
(def url-format "http://www.adac.de/infotestrat/tanken-kraftstoffe-und-antrieb/kraftstoffpreise/detail.aspx?ComponentId=185101,32494&ItpId=%s")

;; List of ids of petrol stations  (as required by http://www.adac.de/infotestrat...)
(def tank-stellen ["-1262786956" ;; Westfalen
                   "-878843771"  ;; Shell
                   "-415369586"  ;; Ratio
                   ])

(defn fetch-page
  "fetch url with enlive"
  [url]
  (e/html-resource (io/reader url)))

(defn extract-tankstelle
  "Extract address details of the petrol station"
  [url]
  (->> (fetch-page url)
     ((fn [p] (e/select p #{[:#wucKraftstoffpreiseDeDetailAdresse-6 ] [:#wucKraftstoffpreiseDeDetailAdresse-9]} )))
     (map :content)
     (map first)
     (map clojure.string/trim)
     (interpose ", ")
     (apply str)
     ))

(defn extract-price
  "Extract the current petrol price from a list of html image elements"
  [content]
  (try
    (->> content
       (map #(get-in % [:attrs :src] ))
       (filter #(not (nil? %)))
       (map #(->> (re-find #"/((\d|\w)+)\.gif" %) (second)))
       (map #(if (= % "punkt") "." %))
       (apply str)
       (java.math.BigDecimal.))
     (catch Exception e)))

(defn extract-val
  "Extract price data from the content of a td element"
  [content]
  (cond (= 1 (count content)) (.trim (first content)) ;; which is the name of the petrol like "Super", "Super 10" or "Diesel"
        (< 1 (count content)) (extract-price content) ;; which is the current price 'encoded' in images; decoded with extract-price
        true "?"))

(defn parse-date
  "Parse the date in format provided in the page"
  [datetime]
   (try
      (.parse (java.text.SimpleDateFormat. "dd.MM.yyyy HH:mm:ss") datetime)
    (catch Exception e)))

(defn extract-prices
  "Extract the current petrol prices"
  [url]
  (->> (e/select (fetch-page url) [:#wucKraftstoffpreiseDeDetail-2 :table  :tr :td] )
       (map :content)
       (map extract-val)
       (partition 3 3)
       (map (fn [[treibstoff preis datum-zeit]]
              {:treibstoff treibstoff :preis preis :datum-zeit (parse-date datum-zeit)}))
       (filter (fn [{:keys [datum-zeit]}]
                 (not (nil? datum-zeit))))
       ))


(defn tank-preise
  "Returns the current prices of the petrol station with the 'id' as map with keys :tankstelle :treibstoff :preis :datum-zeit"
  [id]
  (let [daten (->> id
                   (format url-format)
                   ((juxt extract-tankstelle extract-prices)))]
    (->> (second daten)
         (map #(assoc % :tankstelle (first daten))))))

(defn collect-data
  "Reads the current petrol prices and adds the new data to the file denoted by db-file.
  db-file : File to store collected data. The content of the file is a set of maps. Each map with keys :tankstelle :treibstoff :preis :datum-zeit"
  [db-file]
   (let [db (->> db-file (io/reader) (java.io.PushbackReader.) (edn/read))
         new-values   (->> tank-stellen
                           (map tank-preise)
                           (apply concat)
                           (filter #(not= "Diesel" (:treibstoff %)))
                           (set))
         new-db (set/union db new-values)]
     (pprint new-db (io/writer db-file))))

(defn -main
  "Loops over collect-data. Waits 17 min between the calls of collect-data.
  args[0] is the path to the database file"
  [& args]
  (println args)
  (let [db-file (first args)]
    (while true
       (println "Collect at: " (java.util.Date.))
       (collect-data db-file)
       (Thread/sleep (* 1000 60 17)))))


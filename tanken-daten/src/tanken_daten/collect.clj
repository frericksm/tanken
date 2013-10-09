(ns tanken-daten.collect
  (:use [clojure.pprint])
  (:require [net.cgrand.enlive-html :as e]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]))


;; The string to format a url with one of the stations
(def url-format "http://www.adac.de/infotestrat/tanken-kraftstoffe-und-antrieb/kraftstoffpreise/detail.aspx?ItpId=%s")

;; List of ids of petrol stations  (as required by http://www.adac.de/infotestrat...)
(def stations ["-1262786956" ;; Westfalen
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


(defn petrol-prices
  "Returns the current prices of the petrol station with the 'id' as map with keys :tankstelle :treibstoff :preis :datum-zeit"
  [id]
  (let [daten (->> id
                   (format url-format)
                   ((juxt extract-tankstelle extract-prices)))]
    (->> (second daten)
         (map #(assoc % :tankstelle (first daten))))))

(defn read-db-file [db-file]
  (->> db-file (io/reader) (java.io.PushbackReader.) (edn/read)))

(defn collect-data
  "Reads the current petrol prices and adds the new data to the file denoted by db-file.
  db-file : File to store collected data. The content of the file is a set of maps. Each map with keys :tankstelle :treibstoff :preis :datum-zeit"
  [db-file]
   (let [db (read-db-file db-file)
         new-values   (->> stations
                           (map petrol-prices)
                           (apply concat)
                           (filter #(not= "Diesel" (:treibstoff %)))
                           (set))
         new-db (set/union db new-values)]
     (pprint new-db (io/writer db-file))))


(defn select [station treibstoff db-file]
  (->> db-file
       read-db-file
       (filter #(= station (:tankstelle %)))
       (filter #(= treibstoff (:treibstoff %)))
       (map #(update-in % [:datum-zeit] (fn [d] (.getTime d))))
       (map (juxt :datum-zeit :preis))
       ))

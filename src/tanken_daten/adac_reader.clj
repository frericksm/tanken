(ns tanken-daten.adac-reader
  "Liest und representiert  Daten zu den Tankstellen "
  (:require [net.cgrand.enlive-html :as e]
            [clojure.java.io :as io]
            [datomic.api :as api]))


;; The string to format a url with one of the adac-ids
(def url-format "https://www.adac.de/infotestrat/tanken-kraftstoffe-und-antrieb/kraftstoffpreise/detail.aspx?ItpId=%s")

;; List of ids of petrol adac-ids  (as required by http://www.adac.de/infotestrat...)
(def adac-ids (set  [ "-1262786956"  ;; "Westfalen"
                      "-878843771"   ;; "Shell"
                      "-415369586"   ;; "Ratio"
                      "-1482640195"  ;; "Mr. Wash"
                     "-1520029345"  ;; "Raiffaisen"
                     "699438462"    ;; Freie  Hafenstrasse
                     
                     ]))

  (defn adac-url "Die URL  der Tankstelle zur 'adac-id'"
    [adac-id]
    (format url-format adac-id))

(defn fetch-page
  "mit enlive geparste  Representation der 'url'"
  [url]
  (e/html-resource (io/reader url)))

(defn extract-tankstelle
  "Extract address details of the petrol station"
  [page-content adac-id]
  (->> page-content
       ((fn [p] (e/select p #{[:#wucKraftstoffpreiseDeDetailAdresse-3 ]
                             [:#wucKraftstoffpreiseDeDetailAdresse-6 ]
                             [:#wucKraftstoffpreiseDeDetailAdresse-9]
                             [:#wucKraftstoffpreiseDeDetailAdresse-12 ]} )))
       (map :content)
       (map first)
       (map clojure.string/trim)
       ((fn [[name betreiber strasse plz-ort]]
          (let [plz (re-find #"\d+" plz-ort)
                ort (->> (subs plz-ort (count plz)) (re-find #"\W*(.*)") last )]
            (hash-map :tanken.station/adac-id adac-id
                      :tanken.station/name name
                      :tanken.station/betreiber betreiber
                      :tanken.station/strasse strasse
                      :tanken.station/plz plz
                      :tanken.station/ort ort))))
       ))

(def day-to-attr
  {"Montag" :tanken.station.opening-time/montag
   "Dienstag" :tanken.station.opening-time/dienstag
   "Mittwoch" :tanken.station.opening-time/mittwoch
   "Donnerstag" :tanken.station.opening-time/donnerstag
   "Freitag" :tanken.station.opening-time/freitag
   "Samstag" :tanken.station.opening-time/samstag
   "Sonntag" :tanken.station.opening-time/sonntag})

(defn extract-zeiten
  "Extrahiert die Öffnungzeiten der Tankstelle"
  [page-content]
  (->> (e/select page-content
                 #{[:#wucKraftstoffpreiseDeDetail-29  :.box-col1]
                   [:#wucKraftstoffpreiseDeDetail-29  :.box-col2]})
       (map :content)
       (map first)
       (map clojure.string/trim)
       (partition 2 2)
       (map (fn [[d time]] {(get day-to-attr d) time}))))

(defn extract-price
  "Extract the current petrol price from a list of html image elements.

The html fragment:
        <img src=\"/_common/img/info-test-rat/tanken/1.gif\"><img src=\"/_common/img/info-test-rat/tanken/punkt.gif\"><img src=\"/_common/img/info-test-rat/tanken/5.gif\"><img src=\"/_common/img/info-test-rat/tanken/4.gif\"><img src=\"/_common/img/info-test-rat/tanken/9.gif\">

represents the number: 1.549"
  [content]
  (try
    (->> content
       (map #(get-in % [:attrs :src] ))
       (filter #(not (nil? %)))
       (map #(->> (re-find #"/((\d|\w)+)\.gif" %) (second)))
       (map #(if (= % "punkt") "." %))
       (apply str)
       (bigdec))
     (catch Exception e)))

(defn extract-val
  "Extract price data from the content of a td element"
  [content]
  (cond (= 1 (count content)) (.trim (first content)) ;; which is the name of the gasolines like "Super", "Super E10" or "Diesel"
        (< 1 (count content)) (extract-price content) ;; which is the current price 'encoded' in images; decoded with extract-price
        true "?"))

(defn parse-date
  "Parse the date in format provided in the page"
  [datetime]
   (try
      (.parse (java.text.SimpleDateFormat. "dd.MM.yyyy HH:mm:ss") datetime)
    (catch Exception e)))

(defn extract-prices
  "Returns the current prices of the petrol station with the 'id' as map with keys :tankstelle :tanken.preismeldung/sorte :tanken.preismeldung/preis :tanken.preismeldung/zeitpunkt"
  [page-content]
  (->> (e/select page-content [:#wucKraftstoffpreiseDeDetail-2 :table  :tr :td] )
       (map :content)
       (map extract-val)
       (partition 3 3)
       (filter (fn [[ _ _ zeitpunkt]] (not (nil? (parse-date zeitpunkt)))))
       (map (fn [[treibstoff preis zeitpunkt]]
              {:tanken.preismeldung/sorte treibstoff
               :tanken.preismeldung/preis preis
               :tanken.preismeldung/zeitpunkt (parse-date zeitpunkt)}))
       ))

(defn tankstelle-tx 
"datomic Transaction für die  Tankstelle mit der 'adac-id'"
  [adac-id]
  (as-> (adac-url adac-id) x
(fetch-page x)
(extract-tankstelle x adac-id)
  (assoc x :db/id (api/tempid :db.part/user))))

(defn stations-tx 
  "Liefert eine Transaction für alle Tanstellen"
  []
  (as-> adac-ids x
(map tankstelle-tx x)))








(defn collect-data
  "Liefert eine Liste von Maps. Jede Map hat die Keys
:adac-id, :preismeldungen, :tankstelle und :opening-times"
  [adac-ids]
  (->> adac-ids
       (map (fn [adac-id]
              (let [page-content (->> adac-id adac-url fetch-page)]
                (hash-map :adac-id adac-id
                          :preismeldungen (extract-prices page-content)
                          :tankstelle (extract-tankstelle page-content adac-id)
                          :opening-times (extract-zeiten page-content)
                          ))))))

(ns tanken-daten.collect
  (:use [clojure.pprint])
  (:require [net.cgrand.enlive-html :as e]
            [tanken-daten.storage :as storage]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]))


;; The string to format a url with one of the adac-ids
(def url-format "http://www.adac.de/infotestrat/tanken-kraftstoffe-und-antrieb/kraftstoffpreise/detail.aspx?ItpId=%s")

;; List of ids of petrol adac-ids  (as required by http://www.adac.de/infotestrat...)
(def adac-ids ["-1262786956"  ;; "Westfalen"
               "-878843771"   ;; "Shell"
               "-415369586"   ;; "Ration"
               "-1482640195"  ;; "Mr. Wash"
               "-1520029345"  ;; "Raiffaisen"
               ])

(defn adac-url [adac-id]
  (format url-format adac-id))

(defn fetch-page
  "fetch url with enlive"
  [url]
  (e/html-resource (io/reader url)))

(defn extract-tankstelle
  "Extract address details of the petrol station"
  [page-content]
  (->> page-content
       ((fn [p] (e/select p #{[:#wucKraftstoffpreiseDeDetailAdresse-3 ]
                             [:#wucKraftstoffpreiseDeDetailAdresse-6 ]
                             [:#wucKraftstoffpreiseDeDetailAdresse-9]
                             [:#wucKraftstoffpreiseDeDetailAdresse-12 ]} )))
     (map :content)
     (map first)
     (map clojure.string/trim)
     ((fn [[ name betreiber strasse plz-ort]]
        (let [plz (re-find #"\d+" plz-ort)
              ort (->> (subs plz-ort (count plz)) (re-find #"\W*(.*)") last )]
          (hash-map :name name
                    :betreiber betreiber
                    :strasse strasse
                    :plz plz
                    :ort ort))))
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
  "Extract the current petrol prices"
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



(defn petrol-prices
  "Returns the current prices of the petrol station with the 'id' as map with keys :tankstelle :tanken.preismeldung/sorte :tanken.preismeldung/preis :tanken.preismeldung/zeitpunkt"
  [station-entities]
  (->> station-entities
       (map :tanken.station/adac-id)
       (map (juxt identity (fn [id] (->> id adac-url fetch-page extract-prices))))))

(defn read-db-file [db-file]
  (->> db-file (io/reader) (java.io.PushbackReader.) (edn/read)))

(defn select [station treibstoff db-file]
  (->> db-file
       read-db-file
       (filter #(= station (:tankstelle %)))
       (filter #(= treibstoff (:tanken.preismeldung/sorte %)))
       (map #(update-in % [:tanken.preismeldung/zeitpunkt] (fn [d] (.getTime d))))
       (map (juxt :tanken.preismeldung/zeitpunkt :tanken.preismeldung/preis))
       ))

(defn collect-data
  "Liefert die Liste der aktuellen Spritpreise als Liste von Maps. Jede Map hat die Keys :tankstelle :tanken.preismeldung/sorte :tanken.preismeldung/preis :tanken.preismeldung/zeitpunkt"
  [station-entities]
  (->> station-entities
       (petrol-prices)))

(defn preload-stations [conn]
  (doseq [id adac-ids]
    (let [{:keys [name
                  betreiber
                  strasse
                  plz
                  ort]} (->> id adac-url fetch-page extract-tankstelle)]
      (storage/create-tankstelle conn
                                 id
                                 name
                                 betreiber
                                 strasse
                                 plz
                                 ort))))

(defn extract-transform-load [conn load-fn]
  (let [alle-tankstellen (storage/alle-tankstellen (datomic.api/db conn))
        data   (->> alle-tankstellen collect-data)]
    (println "data: " data)
    (doseq [[adac-id preismeldungen] data]
      (println "adac-id: " adac-id)
      (doseq [pm  preismeldungen]
        (println "pm:" pm)
        (load-fn conn adac-id pm)))))

(defn store-preismeldung [conn adac-id pm]
  (storage/create-preismeldung conn
                               adac-id
                               (:tanken.preismeldung/zeitpunkt pm)
                               (:tanken.preismeldung/sorte pm)
                               (:tanken.preismeldung/preis pm)))

(defn print-preismeldung [conn adac-id pm]
  (println (format "%s : %s, %s, %s"
                   adac-id
                   (:tanken.preismeldung/zeitpunkt pm)
                   (:tanken.preismeldung/sorte pm)
                   (:tanken.preismeldung/preis pm))))

(defn start [system]
  (let [scheduler (->> system :scheduler)
        conn      (->> system :db :connection)
        task      (fn [] (do (println "\nRunning ...")
                            (try 
                              (extract-transform-load conn store-preismeldung)
                              ;(extract-transform-load conn print-preismeldung)
                              (catch Exception e
                                (println "caught exception: " (.getMessage e)))
                              )))]
    (preload-stations conn)
    (.scheduleWithFixedDelay scheduler task 0 17 java.util.concurrent.TimeUnit/SECONDS))
  system)

(defn stop [system]
  system)



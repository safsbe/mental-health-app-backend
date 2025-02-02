(ns sg.com.itserv.lumin.api.main
  (:import [java.sql Array])
  (:require [clojure.java.io :as io]
            [clojure.set :refer [rename-keys]]
            [muuntaja.core :as m]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [reitit.coercion.malli]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.parameters :as parameters]
            [reitit.ring.middleware.multipart :as multipart]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.openapi :as openapi]
            [reitit.ring :as ring]
            [reitit.swagger-ui :as swagger-ui]
            [ring.adapter.jetty :as jetty]
            [honey.sql :as sql]
            [honey.sql.helpers :refer [select from where right-join insert-into columns values]]
            [buddy.auth.backends :as backends]
            [buddy.auth.middleware :refer [wrap-authentication]]))


(extend-protocol rs/ReadableColumn
  Array
  (rs/read-column-by-label [^Array v _]    (vec (.getArray v)))
  (rs/read-column-by-index [^Array v _ _]  (vec (.getArray v))))

(defn- jwt-middleware [handler]
  (fn [request]
    (assoc request :jwt (get-in request [:headers ""]))))

(def ^:private datasource-config {:dbtype "postgresql"
                                  :user "postgres"
                                  :password "Password123"})

(def ^:private datasource (jdbc/get-datasource datasource-config))

(defn- drop-schema [connection]
  (-> "db/migration/0000-drop.sql"
      (io/resource)
      (slurp)
      (as-> x (jdbc/execute! connection [x]))))

(defn- migrate [connection]
;  (println (slurp (io/resource "db/migration/0001.sql")))
  (-> "db/migration/0001.sql"
      (io/resource)
      (slurp)
      (as-> x (jdbc/execute! connection [x])))
  (-> "db/migration/0001-populate.sql.sql"
      (io/resource)
      (slurp)
      (as-> x (jdbc/execute! connection [x])))
  (println (jdbc/execute! connection ["
SELECT
    table_schema || '.' || table_name
FROM
    information_schema.tables
WHERE
    table_type = 'BASE TABLE'
AND
    table_schema NOT IN ('pg_catalog', 'information_schema');"]))
  (println "SHOW TALBES END ----"))

(defn- create-handler [connection]
  (ring/ring-handler
       (ring/router
        [["/ping"
          {:get
           {:handler (fn [_]
                       {:status 200
                        :body {:reply "Pong6!"
                               :foo "bar"
                               :tables (jdbc/execute! connection ["
SELECT
    table_schema || '.' || table_name
FROM
    information_schema.tables
WHERE
    table_type = 'BASE TABLE'
AND
    table_schema NOT IN ('pg_catalog', 'information_schema');"])}})
            :responses {200 {:body [:map [:reply :string]]}}}}]
         ["/openapi.json"
          {:get
           {:handler (openapi/create-openapi-handler)
            :openapi {:info {:title "Lumin API" :version "0.0.1"}
                      :components {:securitySchemes {"auth" {:type :openIdConnect
                                                             :openIdConnectUrl "https://auth.dev.lumin.itserv.com.sg"}}}}
            :no-doc true}}]
         ["/public"
          {:openapi {:security [{"auth" []}]}}
          ["/profile"
           {:get
            {:handler (fn [_]
                        (->> (-> (select :preferred_name :service_type)
                                 (from :users)
                                 (where [:= :idp_id "0001"])
                                 (sql/format))
                             (jdbc/execute-one! connection)
                             (#(rename-keys % {:users/preferred_name :preferredName
                                               :users/service_type :serviceType}))
                             ((fn [x]
                                (if (nil? x)
                                  {:status 400}
                                  {:status 200
                                   :body (assoc x :email "An email")})))))
             :responses {200 {:body [:map
                                     [:preferredName :string]
                                     [:serviceType :string]
                                     [:email :string]]}
                         400 nil}}}]
          [""
           {:openapi {:tags ["support"]}}
           ["/self-help"
            {:get
             {:handler (fn [_] {})
              :responses {200 {:body [:map
                                      [:hotlines
                                       [:vector [:map
                                                 [:displayName :string]
                                                 [:telUrl :string]]]]
                                      [:outreachChannels
                                       [:vector [:map
                                                 [:displayName :string]
                                                 [:url :string]]]]]}}}}]
           ["/feedback"
            [""
             {:post
              {:handler (fn [_] {})
               :parameters {:body [:map
                                   [:category :string]
                                   [:feedback :string]]}}}]
            ["/categories"
             {:get
              {:handler (fn [_]
                          (->> (-> (select :public_id :title :icon_download_url)
                                   (from :feedbacK_categories)
                                   (sql/format))
                               (jdbc/execute! datasource)
                               (map #(rename-keys % {:categories/public_id :id
                                                     :categories/title :title
                                                     :categories/icon_download_url :iconDownloadUrl}))
                               ((fn [x]
                                 {:status 200
                                  :body x}))))
               :responses {200 {:body [:vector :string]}}}}]]]
          ["/categories"
           {:get
            {:handler (fn [_]
                        (->> (-> (select :public_id :title :icon_download_url)
                                 (from :categories)
                                 (sql/format))
                             (jdbc/execute! connection)
                             (map #(rename-keys % {:categories/public_id :id
                                                   :categories/title :title
                                                   :categories/icon_download_url :iconDownloadUrl}))
                             ((fn [x]
                                {:status 200
                                 :body x}))))
             :responses {200 {:body [:vector
                                     [:map
                                      [:id :uuid]
                                      [:title :string]
                                      [:iconDownloadUrl :string]]]}}}}]
          ["/articles"
           {:openapi {:tags ["article"]}}
           [""
            {:get
             {:handler (fn [_]
                         (->> (-> (select :public_id :title)
                                  (from :articles)
                                  (sql/format))
                              (jdbc/execute! connection)
                              (map #(rename-keys
                                     %
                                     {:articles/public_id :id
                                      :articles/title :title}))
                              ((fn [x]
                                   {:status 200
                                    :body x}))))}
              :responses {200 {:body [:vector
                                      [:map
                                       [:id :uuid]
                                       [:title :string]]]}}}]
           ["/:articleId"
            ["/pages/:pageIndex"
             [[""
               {:get
                {:handler (fn [{:keys [path-params] :as _req}]
                            (->> (-> (select :p.title :p.body)
                                     (from [:article_pages :p])
                                     (right-join [:articles :a] [:= :p.article_id :a.id])
                                     (where [:and
                                             [:= :a.public_id [:cast (:articleId path-params) :uuid]]
                                             [:= :p.page_index [:cast (:pageIndex path-params) :integer]]]))
                                 (sql/format)
                                 (jdbc/execute! connection)
                                 (map  #(rename-keys %
                                         {:article_pages/title :title
                                          :article_pages/body :body}))
                                 ((fn [x]
                                    (if (> (count x) 0)
                                      {:status 200
                                       :body x}
                                      {:status 404})))))
                 :parameters {:path [:map
                                     [:articleId :uuid]
                                     [:pageIndex :int]]}
                 :responses {200 {:body [:vector
                                         [:map
                                          [:title :string]
                                          [:body :string]]]}
                             404 nil}}}]
              
              ["/read-indicator"
               {:post
                {:handler (fn [_] {})
                 :parameters {:body [:map [:readAt :string]]}
                 :responses {200 nil}}}]]]]]
          ["/diary/entries"
           {:openapi {:tags ["diary"]}}
           [""
            {:get
             {:handler (fn [_]
                         (->> (-> (select :entry_date)
                                  (from :diary_entries)
                                  (where [:= :user_id 1])
                                  (sql/format))
                              (jdbc/execute! connection)
                              (mapv #(:diary_entries/entry_date %))
                              (mapv #(.format (java.text.SimpleDateFormat. "yyyy/MM/dd") %))
                              ((fn [x]
                                {:status 200
                                 :body x}))))
              :responses {200 {:body [:vector :string]}}}}]
           ["/:date"
            {:get
             {:handler (fn [_]
                         (->> (-> (select :mood
                                          :significant_events
                                          :moment_best
                                          :moment_worst
                                          :what_helped
                                          :medicine_taken
                                          :nap_count
                                          :nap_duration_total_hrs
                                          :sleep_start_at
                                          :sleep_end_at
                                          :tags)
                                  (from :diary_entries)
                                  (where [:= :user_id 1])
                                  (sql/format))
                              (jdbc/execute! connection)
                              (map #(rename-keys % {:diary_entries/mood :mood
                                                    :diary_entries/significant_events :significantEvents
                                                    :diary_entries/moment_best :momentBest
                                                    :diary_entries/moment_worst :momentWorst
                                                    :diary_entries/what_happened :whatHappened
                                                    :diary_entries/medicine_taken :medicineTaken
                                                    :diary_entries/nap_count :napCount
                                                    :diary_entries/nap_duration_total_hrs :napDurationTotalHrs
                                                    :diary_entries/sleep_start_at :sleepStartAt
                                                    :diary_entries/sleep_end_at :sleepEndAt
                                                    :diary_entries/tags :tags}))
                              ((fn [x]
                                 (println x)
                                 (if (> (count x) 0)
                                   {:status 200
                                    :body x}
                                   {:status 404})))))
                         
              :parameters {:path [:map [:date :string]]
                           }
              :responses {200 nil
                          400 nil}}
             :post
             {:handler (fn [{:keys [body-params path-params] :as _req}]
                         (-> (rename-keys {:mood :mood
                                                :significantEvents :significant_events
                                                :momentBest :moment_best
                                                :momentWorst :moment_worst
                                                :whatHappened :what_happened
                                                :medicineTaken :medicine_taken
                                                :napCount :nap_count
                                                :napDurationTotalHrs :nap_duration_total_hrs
                                                :sleepStartAt :sleep_start_at
                                                :sleepEndAt :sleep_end_at
                                                :tags :tags})
                              (as-> x
                                (insert-into :diary_entries)
                                (columns :mood
                                         :significant_events
                                         :moment_best
                                         :moment_worst
                                         :what_helped
                                         :medicine_taken
                                         :nap_count
                                         :nap_duration_total_hrs
                                         :sleep_start_at
                                         :sleep_end_at
                                         :tags)
                                (values x)
                                (where [:and
                                        [:user_id [:= (-> (select :id)
                                                          (from :users)
                                                          (where [:= :idp_id "0001"]))]]
                                        [:= :entry_date (:date path-params)]])
                                (sql/format))
                              (#(jdbc/execute! connection %))))
              :parameters {:path [:map [:date :string]]}
              :responses {200 nil}}}]]
          ["/meditations"
           {:openapi {:tags ["meditation"]}}
           ["/tracks"
            [""
             {:get
              {:responses {200 {:body [:vector
                                       [:map
                                        [:id :uuid]
                                        [:title :string]
                                        [:downloadUrl :string]]]}}
               :handler (fn [_]
                          (->> (-> (select :public_id :title :download_url)
                                   (from :meditation_tracks)
                                   (sql/format))
                               (jdbc/execute! connection)
                               (map
                                #(rename-keys %
                                  {:meditation_tracks/public_id :id
                                   :meditation_tracks/title :title
                                   :meditation_tracks/download_url :downloadUrl}))
                               ((fn [x]
                                  {:status 200
                                   :body x}))))}}]
            ["/:id"
             ["/listen-indicator"
              {:post
               {:handler (fn [{:keys [path-params] :as _req}]
                           (-> (insert-into :users_meditation_track_listens)
                               (columns :user_id :meditation_track_id :listened_at)
                               (values (-> (select :id)
                                           (from :users)
                                           (where [:= "0001" :idp_id]))
                                       (:id path-params)
                                       (java.util.Date.))
                               (sql/format)))
                :parameters {:body [:map
                                    [:listenedAt :string]
                                    [:timestamp :int]]}}}]]]]
          
          ["/quotes"
           {:get
            {:handler (fn [_]
                        (->> (-> (select :quote)
                                 (from :quotes)
                                 (sql/format))
                             (jdbc/execute! connection)
                             (mapv #(:quotes/quote %))
                             ((fn [x]
                                {:status 200
                                 :body x}))))
             :responses {200 {:body [:vector :string]}}}}]]]
        {:data {:coercion (reitit.coercion.malli/create)
                :muuntaja m/instance
                :middleware [openapi/openapi-feature
                             parameters/parameters-middleware
                             muuntaja/format-negotiate-middleware
                             muuntaja/format-response-middleware
;                             exception/exception-middleware
                             muuntaja/format-request-middleware
                             coercion/coerce-response-middleware
                             coercion/coerce-request-middleware
                             multipart/multipart-middleware
;                             (wrap-authentication (backends/jws {:secret "my-secret"}))
                             ]}})
       (ring/routes
        (swagger-ui/create-swagger-ui-handler
         {:path "/swagger-ui"
          :url ["/openapi.json"]
          :config {:validatorUrl nil}})
        (ring/create-default-handler))))

(defn- main [_]
  (let [handler (create-handler datasource)]
  ; (migrate datasource)
    (jetty/run-jetty handler {:port 8080 :join? true})))


(defn- dev [_]
  (drop-schema datasource)
  (migrate datasource)
  (main nil))

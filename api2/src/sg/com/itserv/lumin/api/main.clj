(ns sg.com.itserv.lumin.api.main
  (:require [clojure.java.io :as io]
            [clojure.set :refer [rename-keys]]
            [muuntaja.core :as m]
            [next.jdbc :as jdbc]
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
            [honey.sql.helpers :refer [select from where]]))

(defn- migrate [connection]
  (println (slurp (io/resource "db/migration/0001.sql")))
  (-> "db/migration/0001.sql"
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
            {:handler (fn [_] {})
             :responses {200 {:body [:map
                                     [:preferredName :string]
                                     [:serviceType :string]
                                     [:email :string]]}}}}]
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
              {:handler (fn [_] {})
               :responses {200 {:body [:vector :string]}}}}]]]
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
                                  :body x}))))
              :responses {200 {:body [:vector
                                      [:map
                                       [:id :uuid]
                                       [:title :string]]]}}}}]
           ["/pages/:index"
            [[""
              {:get
               {:handler (fn [_] {})
                :parameters {:path [:map [:index :int]]}
                :responses {200 {:body [:map
                                        [:title :string]
                                        [:body :string]]}}}}]
             
             ["/read-indicator"
              {:post
               {:handler (fn [_] {})
                :parameters {:body [:map [:readAt :string]]}
                :responses {200 nil}}}]]]]
          ["/diary/entries"
           {:openapi {:tags ["diary"]}}
           ["/"
            {:get
             {:handler (fn [_]
                         {:status 200
                          :body {}})
              :responses {200 {:body [:vector [:map
                                               [:moodRating :int]
                                               [:significantEvents [:vector :string]]
                                               [:momentBest [:vector :string]]
                                               [:momentWorst [:vector :string]]
                                               [:whatHelped [:vector :string]]
                                               [:medicationTaken [:vector :string]]
                                               [:sleepStart :string]
                                               [:sleepEnd :string]
                                               [:tags [:vector :string]]]]}}}}]
           ["/:date"
            {:post
             {:handler (fn [_]
                         {:status 200
                          :body {}})
              :parameters {:path [:map [:date :string]]
                           :body [:vector [:map
                                           [:moodRating :int]
                                           [:significantEvents [:vector :string]]
                                           [:momentBest [:vector :string]]
                                           [:momentWorst [:vector :string]]
                                           [:whatHelped [:vector :string]]
                                           [:medicationTaken [:vector :string]]
                                           [:sleepStart :string]
                                           [:sleepEnd :string]
                                           [:tags [:vector :string]]]]}
              :responses {200 nil}}}]]
          ["/meditations"
           {:openapi {:tags ["meditation"]}}
           ["/tracks"
            [""
             {:get
              {:responses {200 {:body [:vector
                                       [:map
                                        [:id :uuid]
                                        [:title :string]]]}}
               :handler (fn [_]
                          (->> (-> (select :public_id :title)
                                   (from :meditation_tracks)
                                   (sql/format))
                               (jdbc/execute! connection)
                               (map
                                #(rename-keys %
                                  {:meditation_tracks/public_id :id
                                   :meditation_tracks/title :title}))
                               ((fn [x]
                                  {:status 200
                                   :body x}))))}}]
            ["/:id"
             [""
              {:get
               {:handler (fn [_] {})
                :parameters {:path [:map [:id :string]]}
                :responses {200 {:body [:map [:downloadUrl :string]]}}}}]
             ["/listen-indicator"
              {:post
               {:handler (fn [_] {})
                :parameters {:body [:map
                                    [:listenedAt :string]
                                    [:timestamp :int]]}}}]]]]
          
          ["/quotes"
           {:get
            {:handler (fn [_]
                        {:status 200
                         :body [{:title "Foo"}
                                {:title "Bar"}]})
             :responses {200 {:body [:vector [:map [:title :string]]]}}}}]]]
        {:data {:coercion (reitit.coercion.malli/create)
                :muuntaja m/instance
                :middleware [openapi/openapi-feature
                             parameters/parameters-middleware
                             muuntaja/format-negotiate-middleware
                             muuntaja/format-response-middleware
                             exception/exception-middleware
                             muuntaja/format-request-middleware
                             coercion/coerce-response-middleware
                             coercion/coerce-request-middleware
                             multipart/multipart-middleware]}})
       (ring/routes
        (swagger-ui/create-swagger-ui-handler
         {:path "/swagger-ui"
          :url ["/openapi.json"]
          :config {:validatorUrl nil}})
        (ring/create-default-handler))))

(defn- main [_]
  (let [datasource (jdbc/get-datasource {:dbtype "postgresql"
                                         :user "postgres"
                                         :password "Password123"})]
      (let [handler (create-handler datasource)]
;        (migrate datasource)
        (jetty/run-jetty handler {:port 8080 :join? true}))))

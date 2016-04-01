(require
 '[datomic.api :as d]
 '[clojure.pprint :refer [pprint]]
 '[datomic.samples.repl :as repl])

;; helper function
(defn tempid [] (d/tempid :db.part/user))

(def uri "datomic:dev://datomicdb:4334/meetup")

#_(d/delete-database uri)
(d/create-database uri)

(def conn (d/connect uri))

;; define the schema as data
(def schema {:db/id (d/tempid :db.part/db)
             :db/ident :email
             :db/valueType :db.type/string
             :db/cardinality :db.cardinality/one
             :db/unique :db.unique/identity
             :db.install/_attribute :db.part/db})

;; install the schema via a transaction
(def schema-ret @(d/transact conn [schema]))
(keys schema-ret)

(def newdb (:db-after schema-ret))

(= newdb (:db-before schema-ret))
(= newdb (d/db conn))

;; define query as data
(def query '[:find ?e ?email
             :where [?e :email ?email]])

(d/q query (d/db conn)) ;; nothing yet

;; add user and email
(def fred-ret @(d/transact conn [{:db/id (tempid)
                                  :email "fred@gmail.com"}]))

(keys fred-ret)

;; get fred's id
(:tempids fred-ret)
(def fred-id (-> fred-ret :tempids first val))
fred-id

(d/q query newdb) ;; ???

(def fred-db (:db-after fred-ret))
(d/q query fred-db)


;; add emily
(def emily-ret
  @(d/transact conn
               [{:db/id (tempid)
                 :email "emily@gmail.com"}]))

(def emily-db (:db-after emily-ret))



;; speculative changes - fred changes name to freddy
(def freddy-tx [{:db/id (tempid)
                 :email "freddy@gmail.com"}])

(def freddy-db (-> (d/db conn) ;; get the current value of the db
                   (d/with freddy-tx) ;; with dbval tx -> dbval
                   :db-after))

(d/q query freddy-db) ;; oops
(d/q query (d/db conn)) ;; no harm done



;; better, use fred's entity id, not tempid

(def freddy-tx [{:db/id fred-id
                 :email "freddy@gmail.com"}])

(def freddy-db (-> (d/db conn) (d/with freddy-tx) :db-after))

(d/q query freddy-db)

;; now for real
(def freddy-db (-> conn (d/transact freddy-tx) deref :db-after))

(d/q query freddy-db)
(d/q query (d/db conn)) ;; it happened




;; an information system retains history
(def latest-db (d/db conn))
(d/q query latest-db)
(d/q query (d/history latest-db))


;; enhance query to grab txes
(def tquery '[:find ?e ?email ?tx
              :where [?e :email ?email ?tx]])

(d/q tquery (d/history latest-db)) ;; hmm, more results, why?



;; get assertion/retraction status as well
(def full-query '[:find ?e ?email ?tx ?added
                  :where [?e :email ?email ?tx ?added]])

(d/q full-query (d/history latest-db))


;; stable, conveyable basis
(def fred-t (d/basis-t fred-db))
fred-t
(d/t->tx fred-t)


;; even the latest db can give us any prior point in time
(d/q query (d/as-of latest-db fred-t))
(d/q query (d/since latest-db fred-t))




;; query 2 dbs
(def query2 '[:find ?e ?email
              :in $db1 $db2
              :where
              [$db1 ?e :email ?email]
              [$db2 ?e :email ?email]])

;; who had the same email then and now?
(d/q query2 emily-db latest-db)




;; data is as good as db
(d/q query2
     '[[lucy :email "lucy@hotmail.com"]]
     '[[lucy :email "lucy@hotmail.com"]
       [rich :email "rich@hickey.com"]])



;; entity navigation

(def music-uri "datomic:dev://datomicdb:4334/mbrainz-1968-1973")

(def mconn (d/connect music-uri))
(def db (d/db mconn))

;; let's see hungarian artists

(def hungarian-artists
  (d/q '[:find ?artist ?name
        :where
        [?country :country/name "Hungary"]
        [?artist :artist/country ?country]
        [?artist :artist/name ?name]]
      db))
hungarian-artists



(def liszt-id (ffirst (d/q '[:find ?e
                             :where [?e :artist/name "Franz Liszt"]]
                           db)))
liszt-id

(def franz-liszt (d/entity db liszt-id))
(keys franz-liszt)
(:artist/startYear franz-liszt)


;; relational attributes, so can navigate in either direction
;; releases have artists, via :release/artists
;; what releases have Franz Liszt as an artist?

(pprint (:release/_artists franz-liszt))

(-> franz-liszt :release/_artists first keys)

(->> franz-liszt :release/_artists
     (map (juxt :release/name :release/year :release/country))
     pprint)

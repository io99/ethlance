(ns ethlance.server.db
  "Represents the ethlance in-memory sqlite database. Contains a mount
  component for creating the in-memory database upon initial load."
  (:require
   [clojure.pprint :as pprint]
   [com.rpl.specter :as $ :include-macros true]
   [cuerdas.core :as str]
   [district.server.config :refer [config]]
   [district.server.db :as db]
   [district.server.db.column-types :refer [address not-nil default-nil default-zero default-false sha3-hash primary-key]]
   [district.server.db.honeysql-extensions]
   [honeysql.core :as sql]
   [honeysql.helpers :as sqlh :refer [merge-where merge-order-by merge-left-join defhelper]]
   [medley.core :as medley]
   [mount.core :as mount :refer [defstate]]
   [taoensso.timbre :as log]))


(declare start stop)


(def mount-state-key
  "Key defining our mount component within the district configuration"
  :ethlance/db)


(defstate ^{:on-reload :noop} ethlance-db
  :start (start (merge (get @config mount-state-key)
                       (mount-state-key (mount/args))))
  :stop (stop))


(def database-schema
  "Represents the database schema, consisting of tables, and their
  column descriptions.

  Notes:

  - Table Entry order matters for creation and deletion.

  - :id-keys is a listing which makes up a table's compound key

  - :list-keys is a listing which makes up a table's key for producing
  a proper listing.
  "
  ;;
  ;; Ethlance Tables
  ;;
  [{:table-name :User
    :table-columns
    [[:user/address address]
     [:user/country-code :varchar #_not-nil]
     [:user/user-name :varchar]
     [:user/full-name :varchar]
     [:user/email :varchar not-nil]
     [:user/profile-image :varchar]
     [:user/date-registered :unsigned :integer]
     [:user/date-updated :unsigned :integer]
     [:user/github-username :varchar]
     [:user/linkedin-username :varchar]
     [:user/status :varchar]
     ;; PK
     [(sql/call :primary-key :user/address)]]
    :id-keys []
    :list-keys []}

   {:table-name :Candidate
    :table-columns
    [[:user/address address]
     [:candidate/bio :varchar]
     [:candidate/professional-title :varchar]
     [:candidate/rate :integer not-nil]
     [:candidate/rate-currency-id :varchar not-nil]
     ;; PK
     [(sql/call :primary-key :user/address)]
     ;; FKs
     [(sql/call :foreign-key :user/address) (sql/call :references :User :user/address) (sql/raw "ON DELETE CASCADE")]]
    :id-keys []
    :list-keys []}

   {:table-name :Employer
    :table-columns
    [[:user/address address not-nil]
     [:employer/bio :varchar]
     [:employer/professional-title :varchar]
     ;; PK
     [(sql/call :primary-key :user/address)]
     ;; FKs
     [(sql/call :foreign-key :user/address) (sql/call :references :User :user/address) (sql/raw "ON DELETE CASCADE")]]
    :id-keys []
    :list-keys []}

   {:table-name :Arbiter
    :table-columns
    [[:user/address address not-nil]
     [:arbiter/bio :varchar]
     [:arbiter/professional-title :varchar]
     [:arbiter/fee :integer not-nil]
     [:arbiter/fee-currency-id :varchar not-nil]
     ;; PK
     [(sql/call :primary-key :user/address)]
     ;; FKs
     [(sql/call :foreign-key :user/address) (sql/call :references :User :user/address) (sql/raw "ON DELETE CASCADE")]]
    :id-keys []
    :list-keys []}

   {:table-name :UserLanguage
    :table-columns
    [[:user/address address not-nil]
     [:language/id :varchar not-nil]
     ;; PK
     [(sql/call :primary-key :user/address :language/id)]
     ;; FKs
     [(sql/call :foreign-key :user/address) (sql/call :references :User :user/address) (sql/raw "ON DELETE CASCADE")]]
    :id-keys []
    :list-keys []}

   {:table-name :Category
    :table-columns
    [[:category/id :varchar not-nil]
     ;; PK
     [(sql/call :primary-key :category/id)]]
    :id-keys []
    :list-keys []}

   {:table-name :Skill
    :table-columns
    [[:skill/id :varchar not-nil]
     ;; PK
     [(sql/call :primary-key :skill/id)]]
    :id-keys []
    :list-keys []}

   {:table-name :ArbiterCategory
    :table-columns
    [[:user/address address not-nil]
     [:category/id :varchar not-nil]
     ;; PK
     [(sql/call :primary-key :user/address :category/id)]
     ;; FKs
     [(sql/call :foreign-key :user/address) (sql/call :references :User :user/address) (sql/raw "ON DELETE CASCADE")]
     [(sql/call :foreign-key :category/id) (sql/call :references :Category :category/id) (sql/raw "ON DELETE CASCADE")]]
    :id-keys []
    :list-keys []}

   {:table-name :ArbiterSkill
    :table-columns
    [[:user/address address not-nil]
     [:skill/id :varchar not-nil]
     ;; PK
     [(sql/call :primary-key :user/address :skill/id)]
     ;; FKs
     [(sql/call :foreign-key :user/address) (sql/call :references :User :user/address) (sql/raw "ON DELETE CASCADE")]
     [(sql/call :foreign-key :skill/id) (sql/call :references :Skill :skill/id) (sql/raw "ON DELETE CASCADE")]]
    :id-keys []
    :list-keys []}

   {:table-name :CandidateCategory
    :table-columns
    [[:user/address address not-nil]
     [:category/id :varchar not-nil]
     ;; PK
     [(sql/call :primary-key :user/address :category/id)]
     ;; FKs
     [(sql/call :foreign-key :user/address) (sql/call :references :User :user/address) (sql/raw "ON DELETE CASCADE")]
     [(sql/call :foreign-key :category/id) (sql/call :references :Category :category/id) (sql/raw "ON DELETE CASCADE")]]
    :id-keys []
    :list-keys []}

   {:table-name :CandidateSkill
    :table-columns
    [[:user/address address not-nil]
     [:skill/id :varchar not-nil]
     ;; PK
     [(sql/call :primary-key :user/address :skill/id)]
     ;; FKs
     [(sql/call :foreign-key :user/address) (sql/call :references :User :user/address) (sql/raw "ON DELETE CASCADE")]
     [(sql/call :foreign-key :skill/id) (sql/call :references :Skill :skill/id) (sql/raw "ON DELETE CASCADE")]]
    :id-keys []
    :list-keys []}

   {:table-name :Job
    :table-columns
    [[:job/id :integer]
     [:job/title :varchar not-nil]
     [:job/description :varchar not-nil]
     [:job/category :varchar]
     [:job/status :varchar]
     [:job/date-created :unsigned :integer]
     [:job/date-published :unsigned :integer]
     [:job/date-updated :unsigned :integer]
     [:job/estimated-length :unsigned :integer]
     [:job/required-availability :integer]
     [:job/bid-option :integer]
     [:job/expertise-level :integer]
     [:job/number-of-candidates :integer]
     [:job/invitation-only? :integer]
     [:job/token address]
     [:job/token-version :integer]
     [:job/reward :unsigned :integer]
     [:job/date-deadline :unsigned :integer]
     [:job/platform :varchar]
     [:job/web-reference-url :varchar]
     [:job/language-id :varchar]
     ;; PK
     [(sql/call :primary-key :job/id)]]
    :id-keys []
    :list-keys []}

   {:table-name :EthlanceJob
    :table-columns
    [[:job/id :integer]
     [:ethlance-job/id :integer]
     [:ethlance-job/estimated-lenght :integer]
     [:ethlance-job/max-number-of-candidates :integer]
     [:ethlance-job/invitation-only? :integer]
     [:ethlance-job/required-availability :integer]
     [:ethlance-job/hire-address :varchar]
     [:ethlance-job/bid-option :integer]

     ;; PK
     [(sql/call :primary-key :job/id)]

     ;; FK
     [(sql/call :foreign-key :job/id) (sql/call :references :Job :job/id) (sql/raw "ON DELETE CASCADE")]
     ]}

   {:table-name :StandardBounty
    :table-columns
    [[:job/id :integer]
     [:standard-bounty/id :integer]
     [:standard-bounty/platform :varchar]
     [:standard-bounty/deadline :integer]
     ;; PK
     [(sql/call :primary-key :job/id)]

     ;; FK
     [(sql/call :foreign-key :job/id) (sql/call :references :Job :job/id) (sql/raw "ON DELETE CASCADE")]
     ]}

   {:table-name :JobCreator
    :table-columns
    [[:job/id :integer]
     [:user/address address]
     ;; PK
     [(sql/call :primary-key :job/id :user/address)]
     ;; FKs
     [(sql/call :foreign-key :user/address) (sql/call :references :User :user/address) (sql/raw "ON DELETE CASCADE")]
     [(sql/call :foreign-key :job/id) (sql/call :references :Job :job/id) (sql/raw "ON DELETE CASCADE")]]
    :id-keys []
    :list-keys []}

   {:table-name :JobSkill
    :table-columns
    [[:job/id :integer]
     [:skill/id :varchar]
     ;; PK
     [(sql/call :primary-key :job/id :skill/id)]
     ;; FKs
     [(sql/call :foreign-key :job/id) (sql/call :references :Job :job/id) (sql/raw "ON DELETE CASCADE")]
     [(sql/call :foreign-key :skill/id) (sql/call :references :Skill :skill/id) (sql/raw "ON DELETE CASCADE")]]
    :id-keys []
    :list-keys []}

   {:table-name :JobArbiter
    :table-columns
    [[:job/id :integer]
     [:user/address address]
     [:job-arbiter/fee :unsigned :integer]
     [:job-arbiter/fee-currency-id :varchar]
     [:job-arbiter/status :varchar]
     ;; PK
     [(sql/call :primary-key :job/id :user/address)]
     ;; FKs
     [(sql/call :foreign-key :user/address) (sql/call :references :User :user/address) (sql/raw "ON DELETE CASCADE")]
     [(sql/call :foreign-key :job/id) (sql/call :references :Job :job/id) (sql/raw "ON DELETE CASCADE")]]
    :id-keys []
    :list-keys []}

   {:table-name :JobFile
    :table-columns
    [[:job/id :integer]
     [:job/file-id :integer]

     ;; FKs
     [(sql/call :foreign-key :job/id) (sql/call :references :Job :job/id) (sql/raw "ON DELETE CASCADE")]
     ]
    :id-keys [:job/id :job/file-id]
    :list-keys []}

   {:table-name :JobStory
    :table-columns
    [[:job-story/id :integer]
     [:job/id :integer]
     [:job-story/status :varchar]
     [:job-story/date-created :unsigned :integer]
     [:job-story/date-updated :unsigned :integer]
     [:job-story/invitation-message-id :integer]
     [:job-story/proposal-message-id :integer]
     [:job-story/proposal-rate :integer]
     [:job-story/proposal-rate-currency-id :varchar]
     ;; PK
     [(sql/call :primary-key :job-story/id)]

     ;; FKs
     [(sql/call :foreign-key :job/id) (sql/call :references :Job :job/id) (sql/raw "ON DELETE CASCADE")]
     [(sql/call :foreign-key :job-story/invitation-message-id) (sql/call :references :Message :message/id) (sql/raw "ON DELETE CASCADE")]
     [(sql/call :foreign-key :job-story/proposal-message-id) (sql/call :references :Message :message/id) (sql/raw "ON DELETE CASCADE")]]
    :id-keys []
    :list-keys []}

   {:table-name :JobStoryCandidate
    :table-columns
    [[:job-story/id :integer]
     [:user/address address]
     ;; PK
     [(sql/call :primary-key :job-story/id :user/address)]
     ;; FKs
     [(sql/call :foreign-key :job-story/id) (sql/call :references :JobStory :job-story/id) (sql/raw "ON DELETE CASCADE")]
     [(sql/call :foreign-key :user/address) (sql/call :references :User :user/address) (sql/raw "ON DELETE CASCADE")]]
    :id-keys []
    :list-keys []}

   {:table-name :JobStoryMessage
    :table-columns
    [[:job-story/id :integer]
     [:message/id :integer]
     ;; PK
     [(sql/call :primary-key :job-story/id :message/id)]
     ;; FKs
     [(sql/call :foreign-key :job-story/id) (sql/call :references :JobStory :job-story/id) (sql/raw "ON DELETE CASCADE")]
     [(sql/call :foreign-key :message/id) (sql/call :references :Message :message/id) (sql/raw "ON DELETE CASCADE")]]
    :id-keys []
    :list-keys []}

   {:table-name :Feedback
    :table-columns
    [[:job-story/id :integer]
     [:message/id :integer]
     [:feedback/rating :integer]
     ;; PK
     [(sql/call :primary-key :job-story/id :message/id)]
     ;; FKs
     [(sql/call :foreign-key :job-story/id) (sql/call :references :JobStory :job-story/id) (sql/raw "ON DELETE CASCADE")]

     [(sql/call :foreign-key :message/id) (sql/call :references :Message :message/id) (sql/raw "ON DELETE CASCADE")]]
    :id-keys []
    :list-keys []}

   {:table-name :JobStoryInvoice
    :table-columns
    [
     [:job-story/id :integer]
     [:message/id :integer]
     [:job-story-invoice/status :varchar]
     [:job-story-invoice/amount-requested :unsigned :integer]
     [:job-story-invoice/amount-paid :unsigned :integer]
     [:job-story-invoice/date-paid :unsigned :integer]
     [:job-story-invoice/date-work-started :unsigned :integer]
     [:job-story-invoice/date-work-ended :unsigned :integer]
     [:job-story-invoice/work-duration :unsigned :integer]
     [:invoice/ref-id :integer]
     ;; PK
     [(sql/call :primary-key :job-story/id :message/id)]
     ;; FKs
     [(sql/call :foreign-key :job-story/id) (sql/call :references :JobStory :job-story/id) (sql/raw "ON DELETE CASCADE")]
     [(sql/call :foreign-key :message/id) (sql/call :references :Message :message/id) (sql/raw "ON DELETE CASCADE")]]
    :id-keys []
    :list-keys []}

   {:table-name :Dispute
    :table-columns
    [[:job/id :integer]
     [:job-story/id :integer]
     [:dispute/raised-message-id :integer]
     [:dispute/resolved-message-id :integer]

     ;; PK
     [(sql/call :primary-key :job/id :job-story/id)]

     ;; FKs
     [(sql/call :foreign-key :job-story/id) (sql/call :references :JobStory :job-story/id) (sql/raw "ON DELETE CASCADE")]
     [(sql/call :foreign-key :job/id) (sql/call :references :Job :job/id) (sql/raw "ON DELETE CASCADE")]
     [(sql/call :foreign-key :dispute/raised-message-id) (sql/call :references :Message :message/id) (sql/raw "ON DELETE CASCADE")]
     [(sql/call :foreign-key :dispute/resolved-message-id) (sql/call :references :Message :message/id) (sql/raw "ON DELETE CASCADE")]
     ]
    :id-keys []
    :list-keys []}

   {:table-name :Message
    :table-columns
    [[:message/id :integer]
     [:message/creator address]
     [:message/text :varchar]
     [:message/date-created :unsigned :integer]
     ;; proposal, invitation, raised dispute, resolved dispute, feedback, invoice, direct message, job story message
     [:message/type :varchar not-nil]
     ;; PK
     [(sql/call :primary-key :message/id)]
     ;; FKs
     [(sql/call :foreign-key :message/id) (sql/call :references :Message :message/id) (sql/raw "ON DELETE CASCADE")]]
    :id-keys [:message/id]
    :list-keys []}

   {:table-name :DirectMessage
    :table-columns
    [[:message/id :integer]
     [:direct-message/receiver address]
     [:direct-message/read? :integer]
     ;; PK
     [(sql/call :primary-key :message/id)]
     ;; FKs
     [(sql/call :foreign-key :message/id) (sql/call :references :Message :message/id) (sql/raw "ON DELETE CASCADE")]]
    :id-keys [:message/id]
    :list-keys []}

   {:table-name :MessageFile
    :table-columns
    [[:message/id :integer]
     [:file/id :integer]

     ;; PK
     [(sql/call :primary-key :message/id :file/id)]
     ;; FKs
     [(sql/call :foreign-key :message/id) (sql/call :references :Message :message/id) (sql/raw "ON DELETE CASCADE")]
     [(sql/call :foreign-key :file/id) (sql/call :references :File :file/id) (sql/raw "ON DELETE CASCADE")]]
    :id-keys [:message/id :file/id]
    :list-keys []}

   {:table-name :File
    :table-columns
    [[:file/id :integer]
     [:file/hash :varchar]
     [:file/name :varchar]
     [:file/directory-hash :varchar]

     ;; PK
     [(sql/call :primary-key :file/id)]
     ]
    :id-keys []
    :list-keys []}])


(defn list-tables
  "Lists all of the tables currently in the sqlite3 database."
  []
  (let [results
        (db/all {:select [:name]
                 :from [:sqlite_master]
                 :where [:= :type "table"]})]
    (mapv :name results)))

(defn print-db
  "(print-db) prints all db tables to the repl
   (print-db :users) prints only users table"
  ([] (print-db nil))
  ([table]
   (let [select (fn [& [select-fields & r]]
                  (pprint/print-table (db/all (->> (partition 2 r)
                                                   (map vec)
                                                   (into {:select select-fields})))))
         all-tables (if table
                      [(name table)]
                      (->> (db/all {:select [:name] :from [:sqlite-master] :where [:= :type "table"]})
                           (map :name)))]
     (doseq [t all-tables]
       (println "#######" (str/upper t) "#######")
       (select [:*] :from [(keyword t)])))))

(defn table-exists?
  [name]
  (contains? (set (list-tables)) name))


(defn- get-table-schema
  "Retrieve the given table schema defined by `table-name` from the
  database schema, or nil."
  [table-name]
  (-> ($/select [$/ALL #(= (:table-name %) table-name)] database-schema)
      first))


(defn- get-table-column-names
  "Retrieves the table column names for a given table schema defined by their `table-name`."
  [table-name]
  (let [table-schema (get-table-schema table-name)]
    (->> (:table-columns table-schema)
         (map first)
         (filter keyword?))))


(defn create-db!
  "Creates the database with tables defined in the `database-schema`."
  []
  (log/info "Creating Sqlite Database...")
  (doseq [{:keys [table-name table-columns]} database-schema]
    (log/debug (str/format "  - Creating Database Table '%s' ..." table-name))
    (db/run! {:create-table [table-name :if-not-exists] :with-columns [table-columns]}))
  (log/debug "Tables Created: " (list-tables)))


(defn drop-db!
  "Drops all of the database tables defined in the `database-schema`."
  []
  (log/info "Dropping Sqlite Database...")
  (doseq [{:keys [table-name]} (reverse database-schema)]
    (log/debug (str/format "  - Dropping Database Table '%s' ..." table-name))
    (db/run! {:drop-table [:if-exists table-name]})))

(defn insert-row!
  "Inserts into the given `table-name` with the given `item`. The
  table-name and item structure are defined in the `database-schema`."
  [table-name item]
  (if-let [table-schema (get-table-schema table-name)]
    (let [table-column-names (get-table-column-names table-name)
          item (select-keys item table-column-names)]
      (not-empty (db/run! {:insert-into table-name
                           :columns (keys item)
                           :values [(vals item)]})))
    (log/error (str/format "Unable to find table schema for '%s'" table-name))))


(defn update-row!
  "Updates the given `table-name` with the given `item`. The table-name
  and item structure are defined in the `database-schema`.

  Notes:

  - The :id-keys within the table-schema needs to be defined with at
  least one id-key in order to correctly update a row.
  "
  [table-name item]
  (if-let [table-schema (get-table-schema table-name)]
    (do
      (assert (not (empty? (:id-keys table-schema)))
              (str/format ":id-keys for table schema '%s' is required for updating rows." table-name))
      (let [table-column-names (get-table-column-names table-name)
            item (select-keys item table-column-names)]
        (not-empty (db/run! {:update table-name
                             :set item
                             :where (concat
                                     [:and]
                                     (for [id-key (:id-keys table-schema)]
                                       [:= id-key (get item id-key)]))}))))
    (log/error (str/format "Unable to find table schema for '%s'" table-name))))


(defn get-row
  "Get the given `fields` for the data row described by the `table-name`
  and `item` containing appropriate `item-keys` to identify the item.

  Notes:

  - The :id-keys within the table-schema need to be defined.

  - If `fields` are not supplied, all of the table columns are returned.
  "
  [table-name item & fields]
  (if-let [table-schema (get-table-schema table-name)]
    (do
      (assert (not (empty? (:id-keys table-schema)))
              (str/format ":id-keys for table schema '%s' is required for getting rows." table-name))
      (let [table-column-names (get-table-column-names table-name)
            item (select-keys item table-column-names)
            fields (or fields table-column-names)]
        (not-empty (db/get {:select fields
                            :from [table-name]
                            :where (concat
                                    [:and]
                                    (for [id-key (:id-keys table-schema)]
                                      [:= id-key (get item id-key)]))}))))
    (log/error (str/format "Unable to find table schema for '%s'" table-name))))


(defn get-list
  "Get a list of rows with the given `fields` for the given `table-name`.
  The `item` should contain appropriate `list-keys` to identify the
  row listing. If no fields are supplied, it is assumed that you want
  *all* row columns for each row.

  Notes:

  - The :list-keys within the table-schema need to be defined.

  - If the :list-keys sequence is empty, assumed that every row is
  returned.
  "
  [table-name item & fields]
  (if-let [table-schema (get-table-schema table-name)]
    (do
      (assert (sequential? (:list-keys table-schema))
              (str/format ":list-keys for table schema '%s' is required for getting rows." table-name))
      (let [table-column-names (get-table-column-names table-name)
            item (select-keys item table-column-names)
            fields (or fields table-column-names)
            list-keys (:list-keys table-schema)
            where-clause (if (> (count list-keys) 0)
                           (concat
                            [:and]
                            (for [list-key list-keys]
                              [:= list-key (get item list-key)]))
                           [:= 1 1])]
        (not-empty (db/all {:select fields
                            :from [table-name]
                            :where where-clause}))))
    (log/error (str/format "Unable to find table schema for '%s'" table-name))))

(defn upsert-user! [args]
  (let [values (select-keys args (get-table-column-names :User))]
    (db/run! {:insert-into :User
              :values [values]
              :upsert {:on-conflict [:user/address]
                       :do-update-set (keys values)}})))

(defn get-last-insert-id []
  (:id (db/get {:select [[(sql/call :last_insert_rowid) :id]] })))

(defn add-bounty [bounty-job]
  (insert-row! :Job bounty-job)
  (let [job-id (get-last-insert-id)]
    (insert-row! :StandardBounty (assoc bounty-job :job/id job-id))))

(defn add-ethlance-job [ethlance-job]
  (insert-row! :Job ethlance-job)
  (let [job-id (get-last-insert-id)]
    (insert-row! :EthlanceJob (assoc ethlance-job :job/id job-id))))

(defn get-job-id-for-bounty [bounty-id]
  (db/get {:select [:job/id]
           :from [[:standard-bounty :sb]]
           :where [:= :sb.standard-bounty/id bounty-id]}))

(defn update-bounty [bounty-id job-data]
  (let [job-id (get-job-id-for-bounty bounty-id)]
    (update-row! :StandardBounty job-data)
    (update-row! :Job (assoc job-data :job/id job-id))))

(defn get-job-id-for-ethlance-job [ethlance-job-id]
  (db/get {:select [:job/id]
           :from [[:ethlance-job :ej]]
           :where [:= :ej.ethlance-job/id ethlance-job-id]}))

(defn update-ethlance-job [ethlance-job-id job-data]
  (let [job-id (get-job-id-for-ethlance-job ethlance-job-id)]
    (update-row! :EthlanceJob job-data)
    (update-row! :Job (assoc job-data :job/id job-id))))

(defn add-job-story-invoice [invoice]
(prn "INVOICE " invoice)

  (insert-row! :JobStoryInvoice invoice))

(defn update-job-story-invoice [invoice]
  (insert-row! :JobStoryInvoice invoice))

(defn add-message
  "Inserts a Message. Returns autoincrement id"
  [message]
  (insert-row! :Message message)
  (get-last-insert-id))

(defn add-job-story
  "Inserts a JobStory. Returns autoincrement id"
  [job-story]
  (insert-row! :JobStory job-story)
  (get-last-insert-id))

(defn add-job-story-message [job-story-message]
  (insert-row! :JobStoryMessage job-story-message))

(defn add-message-file [message-id {:keys [:file/name :file/hash :file/directory-hash] :as file}]
  (let [file-id (do
                  (insert-row! :File file)
                  (get-last-insert-id))]
    (insert-row! :MessageFile {:message/id message-id
                               :file/id file-id})))

(defn get-job-story-id-by-standard-bounty-id [bounty-id]
  (:id (db/get {:select [[:js.job-story/id :id]]
                :from [[:job-story :js]]
                :join [[:job :j] [:= :js.job/id :j.job/id]
                       [:standard-bounty :sb] [:= :j.job/id :sb.job/id]]
                :where [:= :sb.standard-bounty/id bounty-id]})))

(defn set-job-story-invoice-status-for-bounties [bounty-id invoice-ref-id status]
  (let [job-story-id (get-job-story-id-by-standard-bounty-id bounty-id)]
    (db/run! {:update :JobStoryInvoice
              :set {:invoice/status status}
              :where [:and
                      [:= :job-story/id job-story-id]
                      [:= :invoice/ref-id invoice-ref-id]]})))

(defn get-job-story-id-by-ethlance-job-id [ethlance-job-id]
  (:id (db/get {:select [[:js.job-story/id :id]]
                :from [[:job-story :js]]
                :join [[:job :j] [:= :js.job/id :j.job/id]
                       [:ethlance-job :ej] [:= :j.job/id :ej.job/id]]
                :where [:= :ej.ethlance-job/id ethlance-job-id]})))

(defn set-job-story-invoice-status-for-ethlance-job [ethlance-job-id invoice-id status]
  (let [job-story-id (get-job-story-id-by-ethlance-job-id ethlance-job-id)]
    (db/run! {:update :JobStoryInvoice
              :set {:invoice/status status}
              :where [:and
                      [:= :job-story/id job-story-id]
                      [:= :invoice/ref-id invoice-id]]})))

(defn start
  "Start the ethlance-db mount component."
  [{:keys [:resync?] :as opts}]
  (when resync?
    (log/info "Database module called with a resync flag.")
    (drop-db!))
  (create-db!))

(defn stop
  "Stop the ethlance-db mount component."
  []
  ::stopped)

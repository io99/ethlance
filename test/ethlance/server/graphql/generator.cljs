(ns ethlance.server.graphql.generator
  (:require

   [district.server.db :as db]
   [ethlance.server.db :as ethlance-db]
   [district.shared.async-helpers :refer [promise->]]
   [taoensso.timbre :as log]

   [clojure.string :as string]
   [cljs-time.core :as time]
   [cljs-time.coerce :as time-coerce]

   [ethlance.server.contract.ethlance-issuer :as ethlance-issuer]
   ))

(def lorem "Lorem ipsum dolor sit amet, consectetur adipiscing elit. In blandit auctor neque ut pharetra. Vivamus mollis ligula at ultrices cursus. Sed suscipit hendrerit nulla. Maecenas eleifend facilisis enim, eget imperdiet ipsum vestibulum id. Maecenas at dui ut purus tempor porttitor vitae vel mauris. In accumsan mattis est, eget sollicitudin nibh bibendum nec. Mauris posuere nisi pulvinar nibh dapibus varius. Nunc elementum arcu eu ex ullamcorper mattis. Proin porttitor viverra nisi, eu venenatis magna feugiat ultrices. Vestibulum justo justo, ullamcorper sit amet ultrices in, tempor non turpis.")

(def job-categories
  {0 "All Categories"
   1 "Web, Mobile & Software Dev"
   2 "IT & Networking"
   3 "Data Science & Analytics"
   4 "Design & Creative"
   5 "Writing"
   6 "Translation"
   7 "Legal"
   8 "Admin Support"
   9 "Customer Service"
   10 "Sales & Marketing"
   11 "Accounting & Consulting"
   12 "Other"})

(def languages ["en" "nl" "pl" "de" "es" "fr"])

(defn generate-user-languages [user-addresses]
  (js/Promise.
   (fn [resolve reject]
     (try
       (doall (for [address user-addresses language languages]
                (let [[speaks? _] (shuffle [true false])]
                  (when speaks?
                    (ethlance-db/insert-row! :UserLanguage {:user/address address
                                                            :language/id language})))))
       (resolve true)
       (catch :default e
         (log/error "Error" {:error e})
         (reject e))))))

(defn generate-categories [categories [_ candidate arbiter]]
  (js/Promise.
   (fn [resolve reject]
     (try
       (doall (for [category categories]
                (do
                  (ethlance-db/insert-row! :Category {:category/id category})

                  (ethlance-db/insert-row! :CandidateCategory {:user/address candidate
                                                               :category/id category})

                  (ethlance-db/insert-row! :ArbiterCategory {:user/address arbiter
                                                             :category/id category}))))
       (resolve true)
       (catch :default e
         (log/error "Error" {:error e})
         (reject e))))))

(defn generate-skills [skills [_ candidate arbiter]]
  (js/Promise.
   (fn [resolve reject]
     (try
       (doall (for [skill skills]
                (do
                  (ethlance-db/insert-row! :Skill {:skill/id skill})

                  (ethlance-db/insert-row! :CandidateSkill {:user/address candidate
                                                            :skill/id skill})

                  (ethlance-db/insert-row! :ArbiterSkill {:user/address arbiter
                                                          :skill/id skill}))))
       (resolve true)
       (catch :default e
         (log/error "Error" {:error e})
         (reject e))))))

(defn generate-users [user-addresses]
  (js/Promise.
   (fn [resolve reject]
     (try
       (doall (for [address user-addresses]
                (let [[country-code _] (shuffle ["US" "BE" "UA" "CA" "SLO" "PL"])
                      [first-name _] (shuffle ["Filip" "Juan" "Ben" "Matus"])
                      [second-name _] (shuffle ["Fu" "Bar" "Smith" "Doe" "Hoe"])
                      [extension _] (shuffle ["io" "com" "gov"])
                      [profile-id _] (shuffle (range 0 10))
                      [currency _] (shuffle ["EUR" "USD"])
                      date-registered (time-coerce/to-long (time/minus (time/now) (time/days (rand-int 60))))
                      from (rand-int 100)
                      bio (subs lorem from (+ 100 from))
                      [professional-title _] (shuffle ["Dr" "Md" "PhD" "Mgr" "Master of Wine and Whisky"])]
                  (ethlance-db/insert-row! :User {:user/address address
                                                  :user/country-code country-code
                                                  :user/user-name (str "@" first-name)
                                                  :user/full-name (str first-name " " second-name)
                                                  :user/email (string/lower-case (str first-name "@" second-name "." extension))
                                                  :user/profile-image (str "https://randomuser.me/api/portraits/lego/" profile-id ".jpg")
                                                  :user/date-registered date-registered
                                                  :user/date-updated date-registered})

                  (when (= "EMPLOYER" address)
                    (ethlance-db/insert-row! :Employer {:user/address address
                                                        :employer/bio bio
                                                        :employer/professional-title professional-title}))

                  (when (= "CANDIDATE" address)
                    (ethlance-db/insert-row! :Candidate {:user/address address
                                                         :candidate/rate (rand-int 200)
                                                         :candidate/rate-currency-id currency
                                                         :candidate/bio bio
                                                         :candidate/professional-title professional-title}))
                  (when (= "ARBITER" address)
                    (ethlance-db/insert-row! :Arbiter {:user/address address
                                                       :arbiter/bio bio
                                                       :arbiter/professional-title professional-title
                                                       :arbiter/fee (rand-int 200)
                                                       :arbiter/fee-currency-id currency})))))
       (resolve true)
       (catch :default e
         (log/error "Error" {:error e})
         (reject e))))))

(defn generate-jobs [jobs [employer & _]]
  (js/Promise.
   (fn [resolve reject]
     (try
       (doall (for [{:keys [job-id job-type]} jobs]

                (let [title (str (-> ["marmot" "deer" "mammut" "tiger" "lion" "elephant" "bobcat"] shuffle first) " "
                                 (-> ["control" "design" "programming" "aministartion" "development"] shuffle first))
                      description (let [from (rand-int 100)] (subs lorem from (+ 20 from)))
                      category (get job-categories (rand-int 13))
                      status  (rand-nth ["hiring" "hiring done"])
                      date-created (time/minus (time/now) (time/days (rand-int 60)))
                      date-published (time/plus date-created (time/days (rand-int 5)))
                      date-updated (time/plus date-published (time/days (rand-int 7)))
                      expertise-level (rand-nth ["beginner" "intermediate" "expert"])
                      token "0x8f389F672Ef0586628689f9227e1d0e09f9A3245"
                      token-version (ethlance-issuer/token-version (rand-nth [:eth :erc20 :erc721]))
                      reward (rand-int 300)
                      web-reference-url (str "http://ethlance.com/" job-id)


                      estimated-length (case (-> (rand-nth [:hours :days :weeks]))
                                         :hours (time/hours (rand-int 24))
                                         :days (time/days (rand-int 30))
                                         :weeks (time/weeks (rand-int 100)))
                      availability (rand-nth ["Part Time" "Full Time"])

                      bid-option (rand-nth ["Hourly Rate" "Bounty"])
                      number-of-candidates (rand-int 5)
                      invitation-only? (rand-nth [true false])




                      language (rand-nth languages)
                      job {:job/id job-id
                           :job/title title
                           :job/description description
                           :job/category category
                           :job/status status
                           :job/date-created (time-coerce/to-long date-created)
                           :job/date-published (time-coerce/to-long date-published)
                           :job/date-updated (time-coerce/to-long date-updated)
                           :job/expertise-level expertise-level
                           :job/token token
                           :job/token-version token-version
                           :job/reward reward
                           :job/web-reference-url web-reference-url
                           :job/language-id language}]

                  (case job-type
                    :standard-bounties
                    (let [bounty-id job-id ;; this is not real but will work for generator
                          platform (rand-nth ["mobile" "web" "embedded"])
                          date-deadline (time/plus date-updated estimated-length)
                          bounty {:standard-bounty/id bounty-id
                                  :standard-bounty/platform platform
                                  :standard-bounty/deadline (time-coerce/to-long date-deadline)}]
                      (ethlance-db/add-bounty (merge job bounty)))


                    :ethlance-job
                    (let [ethlance-job-id job-id
                          ethlance-job {:ethlance-job/id ethlance-job-id
                                        :ethlance-job/estimated-lenght (time/in-millis estimated-length)
                                        :ethlance-job/max-number-of-candidates (rand-int 10)
                                        :ethlance-job/invitation-only? (rand-nth [true false])
                                        :ethlance-job/required-availability (rand-nth [true false])
                                        :ethlance-job/hire-address nil
                                        :ethlance-job/bid-option 1}]
                      (ethlance-db/add-ethlance-job (merge job ethlance-job))))

                  (ethlance-db/insert-row! :JobCreator {:job/id job-id
                                                        :user/address employer}))))
       (resolve true)
       (catch :default e
         (log/error "Error" {:error e})
         (reject e))))))

(defn generate-job-arbiters [job-ids [employer candidate arbiter]]
  (js/Promise.
   (fn [resolve reject]
     (try
       (doall (for [job-id job-ids]
                (let [status (rand-nth ["invited" "accepted" ])
                      fee (rand-int 200)
                      fee-currency-id (rand-nth ["EUR" "USD"])
                      arbiter {:job/id job-id
                               :user/address arbiter
                               :job-arbiter/fee fee
                               :job-arbiter/fee-currency-id fee-currency-id
                               :job-arbiter/status status}]
                  (ethlance-db/insert-row! :JobArbiter arbiter))))
       (resolve true)
       (catch :default e
         (log/error "Error" {:error e})
         (reject e))))))

(defn generate-message [{:keys [:message/creator :message/id :message/text] :as message}]
  (-> message
      (merge {:message/text (or text
                                (let [from (rand-int 200)]
                                  (subs lorem from (+ 20 from))))
              :message/date-created (time-coerce/to-long (time/minus (time/now)
                                                                     (time/days (rand-int 60))))})
      (merge (case (:message/type message)
               :job-story-message (case (:job-story-message/type message)
                                    :raise-dispute {}
                                    :resolve-dispute {}
                                    :proposal {}
                                    :invitation {}
                                    :invoice {:invoice/status (rand-nth ["waiting" "paid"])
                                              :invoice/amount-requested (rand-int 10)}
                                    :feedback {:feedback/rating (rand-int 5)}
                                    nil)
               :direct-message {}))))

(defn generate-job-stories [stories-ids jobs [employer candidate arbiter]]
  (js/Promise.
   (fn [resolve reject]
     (try
       (doall (for [story-id stories-ids]
                (let [_ (println "Generating story" story-id)
                      {:keys [job-id job-type]} (rand-nth jobs)
                      status  (rand-nth ["proposal pending" "active" "finished" "cancelled"])
                      date-created (time/minus (time/now) (time/days (rand-int 60)))
                      job-story {:job-story/id story-id
                                 :job/id job-id
                                 :job-story/status status
                                 :job-story/date-created (time-coerce/to-long date-created)
                                 :job-story/creator candidate}]

                  (case job-type
                    :standard-bounties
                    (ethlance-db/add-job-story job-story)

                    :ethlance-job
                    (do
                      (ethlance-db/add-ethlance-job-story job-story)
                      (ethlance-db/add-message (generate-message {:message/creator employer
                                                                  :message/text "Do you want to work with us?"
                                                                  :message/type :job-story-message
                                                                  :job-story-message/type :invitation
                                                                  :job-story/id story-id})))))))
       (resolve true)
       (catch :default e
         (log/error "Error" {:error e})
         (reject e))))))

(defn generate-disputes [contract-ids job-ids [employer candidate arbiter]]
  (js/Promise.
   (fn [resolve reject]
     (try
       (doall (for [job-id job-ids
                    contract-id contract-ids]
                (when (-> [true false] shuffle first)
                  (let [last-message-index (:count (db/get {:select [[:%count.* :count]]
                                                            :from [:Message]}))
                        raised-dispute-message (generate-message {:message/creator employer
                                                                  :message/id (+ last-message-index 1)})
                        resolved-dispute-message (generate-message {:message/creator candidate
                                                                    :message/id (+ last-message-index 2)})]
                    (ethlance-db/insert-row! :Message (merge raised-dispute-message
                                                             {:message/type "RAISED-DISPUTE"}))
                    (ethlance-db/insert-row! :Message (merge resolved-dispute-message
                                                             {:message/type "RESOLVED-DISPUTE"}))
                    (ethlance-db/insert-row! :Dispute {:job/id job-id
                                                       :contract/id contract-id
                                                       :dispute/raised-message-id (:message/id raised-dispute-message)
                                                       :dispute/resolved-message-id (:message/id resolved-dispute-message)})))))
       (resolve true)
       (catch :default e
         (log/error "Error" {:error e})
         (reject e))))))

(defn generate-invoices [invoice-ids contract-ids [employer candidate arbiter]]
  (js/Promise.
   (fn [resolve reject]
     (try
       (doall (for [invoice-id invoice-ids]
                (let [[contract-id _] (shuffle contract-ids)
                      last-message-index  (:count (db/get {:select [[:%count.* :count]]
                                                           :from [:Message]}))
                      invoice-message (generate-message {:message/creator candidate
                                                         :message/id (+ last-message-index 1)})
                      [status _] (shuffle ["PAID" "PENDING"])
                      date-work-started (time/minus (time/now) (time/days (rand-int 60)))
                      work-duration (case (-> [:hours :days :weeks] shuffle first)
                                      :hours (time/hours (rand-int 24))
                                      :days (time/days (rand-int 30))
                                      :weeks (time/weeks (rand-int 100)))
                      date-work-ended (time/plus date-work-started work-duration)
                      date-paid (when (= "PAID" status) (time-coerce/to-long (time/plus date-work-ended (time/days (rand-int 7)))))]
                  (ethlance-db/insert-row! :Message (merge invoice-message
                                                           {:message/type "INVOICE"}))
                  (ethlance-db/insert-row! :Invoice {:invoice/id invoice-id
                                                     :contract/id contract-id
                                                     :message/id (:message/id invoice-message)
                                                     :invoice/status status
                                                     :invoice/amount-requested (rand-int 12000)
                                                     :invoice/amount-paid (rand-int 12000)
                                                     :invoice/date-work-started (time-coerce/to-long date-work-started)
                                                     :invoice/work-duration (time/in-millis work-duration)
                                                     :invoice/date-work-ended (time-coerce/to-long date-work-ended)
                                                     :invoice/date-paid date-paid}))))
       (resolve true)
       (catch :default e
         (log/error "Error" {:error e})
         (reject e))))))

(defn generate-feedback [contract-ids [employer candidate arbiter]]
  (js/Promise.
   (fn [resolve reject]
     (try
       (doall (for [contract-id contract-ids]
                (let [last-message-index (:count (db/get {:select [[:%count.* :count]]
                                                          :from [:Message]}))

                      candidate-feedback-message-id (inc last-message-index)
                      candidate-feedback (generate-message {:message/creator employer
                                                            :message/id candidate-feedback-message-id})
                      candidate-rating (rand-int 5)

                      employer-feedback-message-id (+ last-message-index 2)
                      employer-feedback (generate-message {:message/creator candidate
                                                           :message/id employer-feedback-message-id})
                      employer-rating (rand-int 5)

                      arbiter-feedback-from-candidate-message-id (+ last-message-index 3)
                      arbiter-feedback-from-candidate (generate-message {:message/creator candidate
                                                                         :message/id arbiter-feedback-from-candidate-message-id})
                      arbiter-from-candidate-rating (rand-int 5)

                      arbiter-feedback-from-employer-message-id (+ last-message-index 4)
                      arbiter-feedback-from-employer (generate-message {:message/creator employer
                                                                        :message/id arbiter-feedback-from-employer-message-id})
                      arbiter-from-employer-rating (rand-int 5)                      ]
                  ;; feedback for the candidate
                  (ethlance-db/insert-row! :Message (merge candidate-feedback
                                                           {:message/type "CANDIDATE FEEDBACK"}))

                  (ethlance-db/insert-row! :Feedback {:contract/id contract-id
                                                      :message/id candidate-feedback-message-id
                                                      :feedback/rating candidate-rating})
                  ;; feedback for the employer
                  (ethlance-db/insert-row! :Message (merge employer-feedback
                                                           {:message/type "EMPLOYER FEEDBACK"}))

                  (ethlance-db/insert-row! :Feedback {:contract/id contract-id
                                                      :message/id employer-feedback-message-id
                                                      :feedback/rating employer-rating})

                  ;; feedback for the arbiter from candidate
                  (ethlance-db/insert-row! :Message (merge arbiter-feedback-from-candidate
                                                           {:message/type "ARBITER FEEDBACK"}))

                  (ethlance-db/insert-row! :Feedback {:contract/id contract-id
                                                      :message/id arbiter-feedback-from-candidate-message-id
                                                      :feedback/rating arbiter-from-candidate-rating})

                  ;; feedback for the arbiter from employer
                  (ethlance-db/insert-row! :Message (merge arbiter-feedback-from-employer
                                                           {:message/type "ARBITER FEEDBACK"}))

                  (ethlance-db/insert-row! :Feedback {:contract/id contract-id
                                                      :message/id arbiter-feedback-from-employer-message-id
                                                      :feedback/rating arbiter-from-employer-rating}))))
       (resolve true)
       (catch :default e
         (log/error "Error" {:error e})
         (reject e))))))

(defn generate-dev-data []
  (let [user-addresses ["EMPLOYER" "CANDIDATE" "ARBITER"]
        categories ["Web" "Mobile" "Embedded"]
        skills ["Solidity" "Clojure"]
        jobs (map (fn [jid jtype] {:job-id jid :job-type jtype})
                 (range 0 3)
                 (cycle [:standard-bounties :ethlance-job]))
        stories-ids (range 0 5)
        invoice-ids (range 0 10)]
    (promise->
     (generate-users user-addresses)
     #(generate-categories categories user-addresses)
     #(generate-skills skills user-addresses)
     #(generate-user-languages user-addresses)
     #(generate-jobs jobs user-addresses)
     #(generate-job-arbiters (map :job-id jobs) user-addresses)
     #(generate-job-stories stories-ids jobs user-addresses)
     ;;#(generate-disputes contract-ids job-ids user-addresses)
     ;;#(generate-invoices invoice-ids contract-ids user-addresses)
     ;;#(generate-feedback contract-ids user-addresses)
     #(log/debug "Done"))))

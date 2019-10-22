(ns ethlance.shared.routes)


(def routes [["/" :route/home]

             ;; Users
             ["/arbiters" :route.user/arbiters]
             ["/candidates" :route.user/candidates]
             ["/employers" :route.user/employers]
             ["/user/" :route.user/profile]
             ["/user/:address" :route.user/profile]

             ;; Jobs
             ["/jobs" :route.job/jobs]
             ["/jobs/new" :route.job/new] ;; general & bounty
             ["/jobs/contract/" :route.job/contract]
             ["/jobs/contract/:index" :route.job/contract]
             ["/jobs/detail/" :route.job/detail]
             ["/jobs/detail/:index" :route.job/detail]
             
             ;; Invoices
             ["/invoices/new" :route.invoice/new]
             ["/invoices/" :route.invoice/index]
             ["/invoices/:index" :route.invoice/index]

             ;; Me
             ["/me" :route.me/index]
             ["/me/sign-up" :route.me/sign-up]])


(def dev-routes
  (conj routes ["/devcard/index" :route.devcard/index]))

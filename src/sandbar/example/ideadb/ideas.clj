;; Copyright (c) Brenton Ashworth. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file COPYING at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns sandbar.example.ideadb.ideas
  (:require [sandbar.example.ideadb.data :as data])
  (:use (hiccup core)
        (ring.util [response :only (redirect)])
        (sandbar core
                 [auth :only (current-username
                              current-user-roles
                              any-role-granted?)]
                 stateful-session
                 util
                 validation
                 forms)
        (sandbar.dev tables standard-pages)
        (sandbar.example.ideadb properties layouts)))

;;
;; Idea List
;; =========
;;

(def idea-table-columns
     [{:column :id :actions #{:sort}}
      :name
      :description
      :customer_need
      {:column :originator :actions #{:sort :filter}} 
      {:column :date_entered :actions #{:sort :filter} :attr {:align :center}} 
      {:column :category :actions #{:sort :filter}} 
      {:column :idea_type :actions #{:sort :filter}} 
      {:column :business_unit :actions #{:sort :filter}} 
      {:column :status :actions #{:sort :filter}}])

(defn generate-welcome-message [request]
  (if (not (any-role-granted? :admin))
    [:div {:id "welcome-message"}
     (str "Welcome " (current-username)
          "! The table below displays all of the ideas you have submitted.")]))

(defmethod display-table-cell [:idea :name] [type k data]
           (if (any-role-granted? :admin)
             (clink-to (str "/idea/edit?id=" (:id data))
                       (:name data))
             (:name data)))

(defmethod display-table-cell [:idea :empty] [type k data]
           (clink-to (str "/idea/delete?id=" (:id data)) "Delete"))

;; TODO - Add a feature to Carte that will allow you to ensure that a
;; criteria is met no matter what previous criteria have been set.
;; Carte should also be able to deal with an empty criteria list.

(defrecord IdeaTable [type props page-size]

  ResourceList
  
  (find-resources
   [this filters page-and-sort]
   (data/idea-table-records-function type filters page-and-sort))

  (fields [this] [])

  PagedResources

  (page-size [this] page-size)
  
  (total-resource-count [this filters]
                        (data/count-records type filters))

  Labels

  (label [this key] (get props key (name key))))

(def idea-table-adapter (IdeaTable. :idea properties 2))

(defn idea-table [request]
  (filter-and-sort-table idea-table-adapter
                         (if (any-role-granted? :admin)
                           (conj idea-table-columns :empty)
                           idea-table-columns)
                         (:params request)))

(defn idea-list-view [request]
  (html
   (generate-welcome-message request)
   (idea-table request)))

(defn user-has-ideas? [request]
  (< 0 (count (data/idea-table-records-function :idea {} {}))))

(defn idea-list [request]
  (if (or (any-role-granted? :admin)
          (user-has-ideas? request))
    (list-layout "Idea List"
                 request
                 (idea-list-view request))
    (redirect (cpath "/idea/new"))))

(defn idea-list-post [request]
  (table-as-json (html (idea-table request))))

(defn idea-download-view []
  (let [data (data/fetch :idea)
        fields [:id :name :description :customer_need :originator
                :date_entered :category :idea_type :business_unit :status]
        data (concat [(map #(% properties) fields)]
                     (map #(map (fn [field] (% field)) fields) data))]
    (format-csv data)))

;; This should be included in the above function. It is the same view
;; but a differnet content type.
(defn idea-download [request]
  {:status 200
   :headers {"Content-Type" "application/vnd.ms-excel"
             "Content-disposition"
             "attachment;filename=\"ideadb.csv\""}
   :body (idea-download-view)})

;;
;; Create Idea
;; ===========
;;

(defn public-idea-fields []
  [(textarea "What is your idea?  Describe your new idea in 100 words
                   or less?"
                  :description {:cols 75 :rows 10} :required)
   (textfield "Do you have a name for your idea?"
                   :name {:size 70} :required)
   (textarea "What consumer need would be met by this new idea?"
                  :customer_need {:cols 75 :rows 5} :required)
   (textfield "Who can we thank for this idea? (Optional)"
                   :originator {:size 70})])

(defn admin-idea-fields []
  [(select "Category"
               :category :name :name
               (data/fetch :idea_category :order-by :name)
               {}
               {"" "Select a Category..."})
   (select "Type"
                :idea_type :name :name
                (data/fetch :idea_type :order-by :name)
                {}
                {"" "Select a Type..."})
   (select "Business Unit"
                :business_unit :name :name
                (data/fetch :business_unit :order-by :name)
                {}
                {"" "Select a Business Unit..."})
   (select "Status"
                :status :name :name
                (data/fetch :idea_status :order-by :name)
                {}
                {"" "Select a Status..."})])

(defn new-idea-form [request]
  (let [admin (any-role-granted? :admin)]
    (template :over-under
              "/idea/new"
              {:title "Submit an Idea"
               :buttons
               (if admin
                 [[:submit "Save and Close" ]
                  [:submit-and-new "Save and New"]
                  [:cancel]]
                 [[:submit "Submit My Idea"] [:cancel]])}
              (form-layout-grid [1 1 1 1 4]
                                :idea
                                (if admin
                                  (concat
                                   (public-idea-fields)
                                   (admin-idea-fields))
                                  (public-idea-fields))
                                request))))

(defn new-idea [request]
  (form-layout "New Idea Form"
               request
               (new-idea-form request)))

(defn create-idea-from-params [params]
  (let [idea
        (get-params [:id :description :name :customer_need :originator
                     :category :idea_type :business_unit :status
                     :date_entered :user_id]
                    params)
        date (if-let [de (:date_entered idea)]
               (if (empty? de) (date-string) de)
               (date-string))
        user (if-let [u (:user_id idea)]
               (if (empty? u) (current-username) u)
               (current-username))
        idea (-> idea
                 (assoc :date_entered date)
                 (assoc :user_id user))]
    (clean-form-input idea)))

(defn save-idea-success-fn [action success]
  (fn [form-data]
    (do
      (data/save :idea form-data)
      (set-flash-value! :user-message (if (= action "new")
                                        "Your idea has been successfully
                                         submitted."
                                        "The idea has been updated."))
      success)))

(def idea-validator
     (build-validator (non-empty-string :description
                                        "Please enter a description.")
                      (non-empty-string :name
                                        "Please enter a name.")
                      (non-empty-string :customer_need
                                        "Please enter a customer need.")))
(defn save-idea! [params action]
  (redirect
   (let [submit (get-param params :sumbit)
         success (if (= submit "Save and New")
                   (cpath "/idea/new")
                   (cpath "/ideas"))]
     (if (form-cancelled? params)
       success
       (let [form-data (create-idea-from-params params)
             failure (cpath (str "/idea/" action))]
         (if-valid idea-validator form-data
                   (save-idea-success-fn action success)
                   (store-errors-and-redirect :idea failure)))))))

(defn new-idea-post [{params :params}]
  (save-idea! params "new"))

;;
;; Edit Idea
;; =========
;;

(defn edit-idea-form [request params]
  (let [form-data (data/fetch-id :idea (get-param params :id))]
    (template :over-under
              "/idea/edit"
              {:title "Administrator Form"
               :buttons [[:submit "Save Changes"] [:cancel]]}
              (form-layout-grid [1 1 1 1 4]
                                :idea
                                (conj
                                 (concat (public-idea-fields)
                                         (admin-idea-fields))
                                 (hidden :id)
                                 (hidden :date_entered)
                                 (hidden :user_id))
                                request
                                form-data))))

(defn edit-idea [request]
  (form-layout "Edit Idea Form"
               request
               (edit-idea-form request
                               (:params request))))

(defn edit-idea-post [{params :params}]
  (save-idea! params (str "edit?id=" (get-param params :id))))

;;
;; Delete Idea
;; ===========
;;

(defn delete-idea [request]
  (form-layout "Confirm Delete Idea"
               request
               (confirm-delete data/fetch-id
                               :idea
                               properties
                               (get-param (:params request) :id))))

(defn delete-idea-post [{params :params}]
  (do
    (if (not (form-cancelled? params))
      (data/delete-id :idea (get-param params :id)))
    (redirect "list")))

(ns pinhead.api
  "!"
  (:require [clj-http.client :as client]
            [hiccup.util :as hurl]
            [pinhead.user :refer [apitoken ntoken!]]
            [pinhead.util :refer [base-url col->query-str timestamp]]))
;;;; API Maps
(def ^:private posts-api
  {:update (base-url "posts/update")
   :add (base-url "posts/add")
   :delete (base-url "posts/delete")
   :get (base-url "posts/get")
   :recent (base-url "posts/recent")
   :dates (base-url "posts/dates")
   :all (base-url "posts/all")
   :suggest (base-url "posts/suggest")})
(def ^:private tags-api
  {:get (base-url "tags/get")
   :delete (base-url "tags/delete")
   :rename (base-url "tags/rename")})
(def ^:private user-api
  {:secret (base-url "user/secret")
   :auth_token (base-url "user/auth_token")})
(def ^:private notes-api
  {:list (base-url "notes/list")
   :ID (base-url "notes/")})
(defn- query-pinboard
  "I used GET requests (when I probably
should use proper RESTful verbs) out of
solidarity with the pinboard documentation.
Premptively embarrassed if I don't need to."
  [& query-string]
  (client/get (apply str query-string)))
;;;; API Functions: POSTS
(defn posts-update
  "Returns the most recent time a bookmark
was added, updated, or deleted.
Token is an atom which must be derefed."
  [token & {:keys [format]
            :or {format "json"}}]
  (query-pinboard (:update posts-api)
                  "?auth_token=" token
                  "&format=" format))
(defn posts-update2
  "perhaps use the clj-http library properly."
  [token & {keys [format]
            :or {format "json"}}]
  (client/get (:update posts-api) {:query-params {:auth_token token
                                                  :format format}}))
;; DEV Note: this fn doesn't yet check if arguments
;; are valid pinboard data types -TOADD.
(defn posts-add
  "Corresponds to the posts/add pinboard (v1) api function.
Token, url, and description are required.
Token is an atom and must be derefed when passed to
the fn.
argument | pb type | comment
---------------------------------------------------------------------------------------------------------
url | url | the url of the item
description | title | title of the item. Unfortunate historical name.
extended | text | description of the item. Unfortunate historical name.
tags | tag | vector (of strings), up to 100 tags, e.g. [\"pinhead\" \"clojure\" \"docs\"]
dt | datetime | creation time for bookmark, default current time, no datestamps >10 min in future
replace | yes/no | replace any existing bookmark with this URL. Default is yes.
shared | yes/no | make bookmark public, default is yes, unless privacy lock enabled
toread | yes/no | marks the bookmark as unread. default is no
When optional fields are not declared, the pinboard api defaults are applied.
example repl usage: (posts-add @apitoken \"https://mycool.website\" \"my website with redundant TLD\")"
  [token url description
   & {:keys [extended tags dt replace shared toread]
      :or {extended "", tags [], dt (timestamp),
           replace "yes", shared "yes", toread "no"}}]
  (query-pinboard (:add posts-api)
                  "?url=" (hurl/url-encode url)
                  "&description=" (hurl/url-encode description)
                  "&extended=" (hurl/url-encode extended)
                  "&tags=" (col->query-str tags)
                  "&dt=" (hurl/url-encode dt)
                  "&replace=" replace
                  "&shared=" shared
                  "&toread=" toread
                  "&auth_token=" token))
(defn posts-delete
  "Corresponds to the posts/delete
pb.v1 api.
Both arguments are required.
token is an atom and must be derefed when passed.
argument | pb type | comment
----------------------------------------
url | url | the url of the item
example repl usage: (posts-delete @apitoken \"https://mycool.website\")"
  [token url]
  (query-pinboard (:delete posts-api)
                  "?url=" (hurl/url-encode url)
                  "&auth_token=" token))
(defn posts-get
  "Corresponds to the posts/get method in
pb.v1 api.
Returns one or more posts on a single day matching arguments.
If no date or url is given, date of most recent bookmark used.
Only the auth token is required. token is an atom and must be
derefed when passed.
argument | pb type | comment
------------------------------------------------------------------
tag | tag | filter by up to three tags, vector of strings
dt | date | return results bookmarked on this day
url | url | return bookmark for this URL
meta | yes/no | include a change detection signature
example repl usage 1: (posts-get @authtoken)
example repl usage 2: (posts-get @authtoken :tag [\"pinhead\" \"clojure\"])
example repl usage 3: (posts-get @authtoken :url \"https://mycool.website\" :meta \"yes\")"
  [token & {:keys [tag dt url meta]
            :or {tag [], dt "", url "", meta "yes"}}]
  (query-pinboard (:get posts-api)
                  "?tag=" (col->query-str tag)
                  "&dt=" dt
                  "&url=" (hurl/url-encode url)
                  "&meta=" meta
                  "&auth_token=" token))
(defn posts-recent
  "RATE LIMIT: the pinboard API documentation has
politely asked us to call this AT MOST ONCE PER
MINUTE
Corresponds to the posts/recent pb.v1 api method.
Returns a list of the user's most recent posts,
filtered by tag.
Only the auth token is required. token is an atom
which must be derefed when passed.
argument | pb type | comment
---------------------------------------------------------------------------
tag | tag | filter by up to three tags, vector of strings
count | int | number of results to return. Default is 15, max is 100
format | json/xml | the literal string \"json\" or \"xml\"
example repl usage 1: (posts-recent @apitoken)
example repl usage 2: (posts-recent @apitoken :count 3)
example repl usage 3 (posts-recent @apitoken :count 5 :format \"xml\")"
  [token & {:keys [count tag format]
            :or {count "", tag "", format "json"}}]
  (query-pinboard (:recent posts-api)
                  "?tag=" (col->query-str tag)
                  "&count=" count
                  "&format=" format
                  "&auth_token=" token))
(defn posts-dates
  "Corresponds to the posts/dates pb.v1 api method.
Returns a list of dates with the number of posts
at each date. Token is an atom which must be derefed."
  [token & {:keys [tag1 tag2 tag3 format]
            :or {tag1 "", tag2 "", tag3 "", format "json"}}]
  (query-pinboard (:dates posts-api)
                  "?tag=" (col->query-str (vector tag1 tag2 tag3))
                  "&format=" format
                  "&auth_token=" token))
(defn posts-all
  "RATE LIMIT: The pinboard API documentation has
politely asked us to call this at most ONCE EVERY
FIVE MINUTES.
Also, the documentation has requested that we
call posts-update to see if the content has been
updated so that we may conditionally reduce the
number of posts-all calls..
Corresponds to the posts/all method. Returns all
bookmarks in the user's account.
tag - filter by up to three tags
start - offset value (default 0)
results - number of results to return. Default is all.
fromdt - return only bookmarks created after this time
todt - return only bookmarks created after this time
meta - include a change detection signature for each bookmark.
token - the user's auth token."
  [token & {:keys [tag start results fromdt todt meta format]
            :or {tag "", start "", results "", fromdt "",
                 todt "", meta "yes", format "json"}}]
  (query-pinboard (:all posts-api)
                  "?tag=" (col->query-str tag)
                  "&start=" start
                  "&results=" results
                  "&fromdt=" fromdt
                  "&todt=" todt
                  "&meta=" meta
                  "&format=" format
                  "&auth_token=" token))
(defn posts-suggest
  "Corresponds to the posts/suggest method. Returns
a list of popular tags and recommended tags for
a given URL. Popular tags are tags used site-wide
for the url; recommended tags are drawn from the
user's own tags."
  [token url & {:keys [format]
                :or {format "json"}}]
  (query-pinboard (:suggest posts-api)
                  "?url=" (hurl/url-encode url)
                  "&format=" format
                  "&auth_token=" token))
;;;; API Functions: TAGS
(defn tags-get
  "Corresponds to the tags/get method. Returns a
full list of the user's tags along with the
number of times they were used."
  [token & {:keys [format]
            :or {format "json"}}]
  (query-pinboard (:get tags-api)
                  "?auth_token=" token
                  "&format=" format))
(defn tags-delete
  "Corresponds with the tags/delete method.
Delete an existing tag."
  [token tag]
  (query-pinboard (:delete tags-api)
                  "?tag=" (hurl/url-encode tag)
                  "&auth_token=" token))
(defn tags-rename
  "Corresponds with the tags/rename method.
Rename a tag, or fold it in to an existing tag."
  [token old new]
  (query-pinboard (:rename tags-api)
                  "?old=" (hurl/url-encode old)
                  "&new=" (hurl/url-encode new)
                  "&auth_token=" token))
;;;; API Functions: USER
(defn user-secret
  "Corresponds with the user/secret method.
Returns the user's secret RSS key (for viewing
private feeds)"
  [token]
  (query-pinboard (:secret user-api)
                  "?auth_token=" token))
(defn user-api-token
  "Corresponds with the user/api_token method.
   Returns the user's API token (for making
   API calls without a password)"
  [token]
  (query-pinboard (:auth_token user-api)
                  "?auth_token=" token))
;;;; API Functions: NOTES
(defn notes-list
  "Corresponds to the notes/list method.
   Returns a list of the user's notes."
  [token]
  (query-pinboard (:list notes-api)
                  "?auth_token=" token))
(defn notes-ID
  "Corresponds to the notes/ID method.
   Returns an individual user note."
  [token hash]
  (query-pinboard (:ID notes-api)
                  hash "/"
                  "?auth_token=" token))

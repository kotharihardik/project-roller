# Apache Roller - Weblog and Content Management Subsystem Documentation

## Overview

The **Weblog and Content Management Subsystem** is the core component of Apache Roller that handles:

- **Weblog lifecycle management**: Creation, modification, deletion, and retrieval of weblogs
- **Content management**: Blog entries, categories, comments, tags, and attributes
- **Media management**: Files, directories, and content storage
- **Template management**: Weblog themes and custom templates
- **Bookmark management**: Folders and bookmarks organization
- **Rendering**: Page generation, caching, and URL strategies
- **Comment handling**: Validation, authentication, and moderation

This subsystem follows a layered architecture pattern with clear separation between domain entities, business services, persistence, rendering, and presentation layers.

---

## Package Structure

| Package | Purpose |
|---------|---------|
| `org.apache.roller.weblogger.pojos` | Domain entities, enums, and data transfer objects |
| `org.apache.roller.weblogger.business` | Business service interfaces |
| `org.apache.roller.weblogger.business.jpa` | JPA implementations of business services |
| `org.apache.roller.weblogger.ui.rendering` | Rendering engine, validators, and model loaders |
| `org.apache.roller.weblogger.ui.rendering.servlets` | HTTP servlets for page, feed, and comment handling |
| `org.apache.roller.weblogger.ui.rendering.util` | Request parsing, caching, and URL mapping utilities |

---

## Architecture Diagram

```

                              PRESENTATION LAYER                              

               
  PageServlet   FeedServlet   SearchServlet   CommentServlet        
               
                                                                         
                                                                         
         
                      Request Parsing & Caching                            
    WeblogPageRequest | WeblogFeedRequest | WeblogCommentRequest          
    WeblogPageCache   | WeblogFeedCache   | SiteWideCache                 
         

                              RENDERING LAYER                                 

                   
  RendererManager >VelocityRenderer        ModelLoader             
                   
                                                                             
                                                                             
          
    CommentValidationManager | CommentAuthenticator | RollerMessages       
          

                              BUSINESS LAYER                                  

        
                            Weblogger                                      
                  
    WeblogManager WeblogEntryManager MediaFileManager               
                  
                     
    FileContentManager  ThemeManager  BookmarkManager               
                     
                                                            
     URLStrategy                                                         
                                                            
        

                              PERSISTENCE LAYER                               

          
                      JPAPersistenceStrategy                               
                          
    JPAWeblogManagerImpl JPAWeblogEntryManagerImpl                    
                          
                            
    JPAMediaFileManagerImpl FileContentManagerImpl                    
                            
          

                              DOMAIN LAYER                                    

                    
   Weblog >WeblogEntry >WeblogEntryTag     WeblogCategory     
                    
                                                                            
                                                                            
                                
               WeblogEntryComment    WeblogEntryAttribute              
                                
                                                                             
                                                                             
                           
  WeblogBookmarkFolder>WeblogBookmark  WeblogTemplate                
                           
                                                                            
                                                                            
                
  MediaFileDirectory> MediaFile     CustomTemplateRendition        
                

```

---

## Domain/Entity Layer (pojos)

### 1. Weblog

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Core entity representing a blog/weblog in the system.

**Description:**
The `Weblog` class is the central entity for blog management in Apache Roller. It stores all weblog configuration including display settings, comment policies, locale preferences, and theme selection. Each weblog has an owner (creator) and can contain multiple entries, categories, bookmarks, media files, and templates. This class is persisted to the database via JPA.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique identifier |
| `handle` | String | URL-friendly unique name |
| `name` | String | Display name |
| `tagline` | String | Blog description/subtitle |
| `enableBloggerApi` | Boolean | Enable Blogger API access |
| `editorPage` | String | Default editor page |
| `bannedwordslist` | String | List of banned words for comments |
| `allowComments` | Boolean | Global comment permission |
| `emailComments` | Boolean | Email notification for comments |
| `emailAddress` | String | Contact email |
| `editorTheme` | String | Current theme name |
| `locale` | String | Blog's locale |
| `timeZone` | String | Blog's timezone |
| `defaultPlugins` | String | Default plugins list |
| `visible` | Boolean | Public visibility |
| `active` | Boolean | Active status |
| `dateCreated` | Date | Creation timestamp |
| `defaultAllowComments` | Boolean | Default comment setting for entries |
| `defaultCommentDays` | int | Days comments are allowed |
| `moderateComments` | Boolean | Require comment moderation |
| `entryDisplayCount` | int | Entries per page |
| `lastModified` | Date | Last update timestamp |
| `enableMultiLang` | boolean | Enable multi-language support |
| `showAllLangs` | boolean | Show all language entries |
| `iconPath` | String | Path to blog icon |
| `about` | String | About section content |
| `creator` | String | Creator username |
| `analyticsCode` | String | Analytics tracking code |
| `bloggerCategory` | WeblogCategory | Default category for Blogger API |
| `weblogCategories` | List&lt;WeblogCategory&gt; | All categories |
| `bookmarkFolders` | List&lt;WeblogBookmarkFolder&gt; | All bookmark folders |
| `mediaFileDirectories` | List&lt;MediaFileDirectory&gt; | All media directories |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `Weblog()` | Default constructor |
| `Weblog(handle, creator, name, desc, email, editorTheme, locale, timeZone)` | Full specification constructor |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `hasCategory(name)` | Checks if category exists |
| `hasBookmarkFolder(name)` | Checks if bookmark folder exists |
| `hasMediaFileDirectory(name)` | Checks if media directory exists |
| `getURL()` | Returns relative URL |
| `getAbsoluteURL()` | Returns absolute URL |
| `hasUserPermission(user, action)` | Checks if user has specific permission |
| `hasUserPermissions(user, actions)` | Checks if user has any of the permissions |

**Interactions:**
- **User**: Many-to-one as creator/owner
- **WeblogCategory**: One-to-many (owns categories)
- **WeblogEntry**: One-to-many (owns entries)
- **WeblogBookmarkFolder**: One-to-many (owns bookmark folders)
- **MediaFileDirectory**: One-to-many (owns media directories)
- **WeblogTemplate**: One-to-many (owns templates)
- **WeblogPermission**: One-to-many for member permissions
- **WeblogManager**: Managed by this service for CRUD operations

---

### 2. WeblogEntry

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Represents a blog post/entry in a weblog.

**Description:**
The `WeblogEntry` class represents individual blog posts. It contains the entry content, metadata, publication status, and relationships to tags, comments, and categories. Entries support multiple publication states (DRAFT, PUBLISHED, PENDING, SCHEDULED) and can be scheduled for future publication.

**Publication Status (PubStatus enum):**

| Constant | Description |
|----------|-------------|
| `DRAFT` | Entry is a draft, not visible |
| `PUBLISHED` | Entry is published and visible |
| `PENDING` | Entry awaiting approval |
| `SCHEDULED` | Entry scheduled for future publication |

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique identifier |
| `title` | String | Entry title |
| `link` | String | External link |
| `summary` | String | Entry summary/excerpt |
| `text` | String | Full entry content |
| `contentType` | String | Content MIME type |
| `contentSrc` | String | External content source |
| `anchor` | String | URL-friendly anchor |
| `pubTime` | Timestamp | Publication timestamp |
| `updateTime` | Timestamp | Last update timestamp |
| `plugins` | String | Applied plugins |
| `allowComments` | Boolean | Comments allowed |
| `commentDays` | Integer | Days comments open |
| `rightToLeft` | Boolean | RTL text direction |
| `pinnedToMain` | Boolean | Pinned to main page |
| `status` | PubStatus | Publication status |
| `locale` | String | Entry locale |
| `creatorUserName` | String | Author username |
| `searchDescription` | String | SEO description |
| `website` | Weblog | Parent weblog |
| `category` | WeblogCategory | Entry category |
| `tagSet` | Set&lt;WeblogEntryTag&gt; | Associated tags |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `WeblogEntry()` | Default constructor |
| `WeblogEntry(id, category, website, creator, title, link, text, anchor, pubTime, updateTime, status)` | Full specification constructor |
| `WeblogEntry(other)` | Copy constructor |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `addTag(name)` | Adds a new tag |
| `getTagsAsString()` | Returns tags as comma-separated string |
| `setTagsAsString(tags)` | Sets tags from string |
| `getPermalink()` | Returns permanent URL |
| `getDisplayTitle()` | Returns formatted title |
| `getDisplayContent()` | Returns formatted content |
| `hasWritePermissions(user)` | Checks if user can edit |
| `getCommentsStillAllowed()` | Checks if comments are still open |

**Interactions:**
- **Weblog**: Many-to-one (belongs to)
- **WeblogCategory**: Many-to-one (categorized in)
- **WeblogEntryTag**: One-to-many (has tags)
- **WeblogEntryComment**: One-to-many (has comments)
- **WeblogEntryAttribute**: One-to-many (has attributes)
- **User**: Many-to-one (created by)
- **WeblogEntryManager**: Managed by this service

---

### 3. WeblogCategory

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Represents a category for organizing weblog entries.

**Description:**
`WeblogCategory` provides a way to organize blog entries into logical groupings. Each category belongs to a single weblog and can contain multiple entries. Categories have a position for ordering and can include optional descriptions and images.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique identifier |
| `name` | String | Category name |
| `description` | String | Category description |
| `image` | String | Category image path |
| `position` | int | Display order position |
| `weblog` | Weblog | Parent weblog |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `WeblogCategory()` | Default constructor |
| `WeblogCategory(weblog, name, description, image)` | Full specification constructor |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `calculatePosition()` | Calculates position based on siblings |
| `retrieveWeblogEntries(publishedOnly)` | Gets entries in this category |
| `isInUse()` | Checks if category has entries |
| `compareTo(other)` | Compares by position |

**Interactions:**
- **Weblog**: Many-to-one (belongs to)
- **WeblogEntry**: One-to-many (categorizes entries)
- **WeblogEntryManager**: Manages category CRUD

---

### 4. WeblogEntryComment

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Represents a comment on a weblog entry.

**Description:**
`WeblogEntryComment` stores user comments on blog entries. Comments go through an approval workflow with multiple states (APPROVED, PENDING, DISAPPROVED, SPAM). The class tracks commenter information, content, and metadata for spam prevention.

**Approval Status (ApprovalStatus enum):**

| Constant | Description |
|----------|-------------|
| `APPROVED` | Comment is approved and visible |
| `PENDING` | Comment awaiting moderation |
| `DISAPPROVED` | Comment rejected |
| `SPAM` | Comment marked as spam |

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique identifier |
| `name` | String | Commenter name |
| `email` | String | Commenter email |
| `url` | String | Commenter website |
| `content` | String | Comment content |
| `postTime` | Timestamp | Comment timestamp |
| `status` | ApprovalStatus | Approval status |
| `notify` | Boolean | Email notification preference |
| `remoteHost` | String | Commenter IP/host |
| `referrer` | String | HTTP referrer |
| `userAgent` | String | Browser user agent |
| `plugins` | String | Applied plugins |
| `contentType` | String | Content MIME type |
| `weblogEntry` | WeblogEntry | Parent entry |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `WeblogEntryComment()` | Default constructor |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `getSpam()` | Returns true if marked as spam |
| `getPending()` | Returns true if pending |
| `getApproved()` | Returns true if approved |
| `getTimestamp()` | Returns formatted timestamp |

**Interactions:**
- **WeblogEntry**: Many-to-one (belongs to)
- **WeblogEntryManager**: Manages comment CRUD
- **CommentSearchCriteria**: Filters comments
- **CommentValidationManager**: Validates comments

---

### 5. WeblogEntryTag

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Represents a tag associated with a weblog entry.

**Description:**
`WeblogEntryTag` provides tagging functionality for blog entries. Tags enable categorization beyond traditional categories, supporting folksonomy-style organization. Each tag tracks who created it and when.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique identifier |
| `website` | Weblog | Associated weblog |
| `weblogEntry` | WeblogEntry | Tagged entry |
| `userName` | String | Creator username |
| `name` | String | Tag name |
| `time` | Timestamp | Creation timestamp |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `WeblogEntryTag()` | Default constructor |
| `WeblogEntryTag(id, website, weblogEntry, user, name, time)` | Full specification constructor |

**Interactions:**
- **WeblogEntry**: Many-to-one (tags entry)
- **Weblog**: Many-to-one (belongs to)
- **User**: Many-to-one (created by)

---

### 6. WeblogEntryAttribute

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Represents a custom attribute on a weblog entry.

**Description:**
`WeblogEntryAttribute` provides extensible metadata for blog entries through key-value pairs. This allows plugins and customizations to store additional data with entries without modifying the core schema.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique identifier |
| `entry` | WeblogEntry | Parent entry |
| `name` | String | Attribute name/key |
| `value` | String | Attribute value |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `WeblogEntryAttribute()` | Default constructor |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `compareTo(att)` | Compares by name |

**Interactions:**
- **WeblogEntry**: Many-to-one (belongs to)

---

### 7. WeblogBookmarkFolder

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Represents a folder for organizing bookmarks in a weblog.

**Description:**
`WeblogBookmarkFolder` provides hierarchical organization for bookmarks (blogroll links). Each weblog can have multiple folders to categorize external links.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique identifier |
| `name` | String | Folder name |
| `weblog` | Weblog | Parent weblog |
| `bookmarks` | List&lt;WeblogBookmark&gt; | Contained bookmarks |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `WeblogBookmarkFolder()` | Default constructor |
| `WeblogBookmarkFolder(name, weblog)` | Constructor with name and weblog |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `addBookmark(bookmark)` | Adds a bookmark |
| `hasBookmarkOfName(bookmarkName)` | Checks if bookmark exists |
| `retrieveBookmarks()` | Retrieves all bookmarks |
| `compareTo(other)` | Compares by name |

**Interactions:**
- **Weblog**: Many-to-one (belongs to)
- **WeblogBookmark**: One-to-many (contains)
- **BookmarkManager**: Manages folder CRUD

---

### 8. WeblogBookmark

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Represents a bookmark/link in a weblog.

**Description:**
`WeblogBookmark` stores external links (blogroll entries) within bookmark folders. Each bookmark has a URL, optional feed URL for syndication, and display metadata.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique identifier |
| `name` | String | Bookmark name |
| `description` | String | Bookmark description |
| `url` | String | Target URL |
| `feedUrl` | String | RSS/Atom feed URL |
| `image` | String | Icon/image path |
| `priority` | Integer | Display priority |
| `folder` | WeblogBookmarkFolder | Parent folder |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `WeblogBookmark()` | Default constructor |
| `WeblogBookmark(parent, name, desc, url, feedUrl, image)` | Full specification constructor |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `calculatePriority()` | Calculates priority based on siblings |
| `compareTo(o)` | Compares by priority |

**Interactions:**
- **WeblogBookmarkFolder**: Many-to-one (contained in)
- **Weblog**: Indirect through folder
- **BookmarkManager**: Manages bookmark CRUD

---

### 9. MediaFileDirectory

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Represents a directory for organizing media files.

**Description:**
`MediaFileDirectory` provides folder organization for uploaded media files. Each weblog has a default media directory and can create additional directories for organization.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique identifier |
| `name` | String | Directory name |
| `description` | String | Directory description |
| `weblog` | Weblog | Parent weblog |
| `mediaFiles` | Set&lt;MediaFile&gt; | Contained files |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `MediaFileDirectory()` | Default constructor |
| `MediaFileDirectory(weblog, name, desc)` | Constructor with weblog and name |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `isEmpty()` | Checks if directory is empty |
| `hasMediaFile(name)` | Checks if file exists |
| `getMediaFile(name)` | Gets file by name |

**Interactions:**
- **Weblog**: Many-to-one (belongs to)
- **MediaFile**: One-to-many (contains)
- **MediaFileManager**: Manages directory CRUD

---

### 10. MediaFile

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Represents an uploaded media file.

**Description:**
`MediaFile` stores metadata for uploaded files including images, videos, audio, and documents. It tracks file properties, dimensions for images, thumbnails, and supports tagging for organization.

**Media File Type (MediaFileType enum):**

| Constant | Description |
|----------|-------------|
| `AUDIO` | Audio files |
| `VIDEO` | Video files |
| `IMAGE` | Image files |
| `OTHERS` | Other file types |

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique identifier |
| `name` | String | File name |
| `description` | String | File description |
| `copyrightText` | String | Copyright notice |
| `isSharedForGallery` | Boolean | Shared in gallery |
| `length` | long | File size in bytes |
| `width` | int | Image width |
| `height` | int | Image height |
| `thumbnailWidth` | int | Thumbnail width |
| `thumbnailHeight` | int | Thumbnail height |
| `contentType` | String | MIME type |
| `originalPath` | String | Original file path |
| `dateUploaded` | Timestamp | Upload timestamp |
| `lastUpdated` | Timestamp | Last update time |
| `creatorUserName` | String | Uploader username |
| `weblog` | Weblog | Associated weblog |
| `directory` | MediaFileDirectory | Parent directory |
| `tagSet` | Set&lt;MediaFileTag&gt; | File tags |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `MediaFile()` | Default constructor |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `getInputStream()` | Returns file input stream |
| `isImageFile()` | Checks if file is an image |
| `getPermalink()` | Returns permanent URL |
| `getThumbnailURL()` | Returns thumbnail URL |
| `getThumbnailInputStream()` | Returns thumbnail stream |
| `addTag(name)` | Adds a tag |
| `getTagsAsString()` | Returns tags as string |

**Interactions:**
- **MediaFileDirectory**: Many-to-one (contained in)
- **Weblog**: Many-to-one (belongs to)
- **MediaFileTag**: One-to-many (has tags)
- **FileContent**: Uses for content storage
- **MediaFileManager**: Manages file CRUD

---

### 11. MediaFileTag

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Represents a tag associated with a media file.

**Description:**
`MediaFileTag` provides tagging functionality for media files, similar to entry tags. This enables organization and searching of media content.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique identifier |
| `name` | String | Tag name |
| `mediaFile` | MediaFile | Tagged file |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `MediaFileTag()` | Default constructor |
| `MediaFileTag(name, mediaFile)` | Constructor with name and file |

**Interactions:**
- **MediaFile**: Many-to-one (tags file)

---

### 12. FileContent

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Represents the actual content of an uploaded file.

**Description:**
`FileContent` wraps the physical file on disk, providing access to file metadata and content stream. It bridges the domain model to the file system.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `resourceFile` | File | Physical file reference |
| `fileId` | String | File identifier |
| `weblog` | Weblog | Associated weblog |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `FileContent(weblog, fileId, file)` | Constructor with weblog, ID, and file |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `getInputStream()` | Returns file input stream |

**Interactions:**
- **Weblog**: Many-to-one (belongs to)
- **MediaFile**: Used for content access
- **FileContentManager**: Manages storage

---

### 13. WeblogTemplate

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Represents a custom template for weblog rendering.

**Description:**
`WeblogTemplate` stores custom templates that override or extend theme templates. Each template has a specific action (page type) and can have multiple renditions for different devices.

**Component Type (ComponentType enum):**

| Constant | Description |
|----------|-------------|
| `WEBLOG` | Main weblog page |
| `PERMALINK` | Individual entry page |
| `SEARCH` | Search results page |
| `TAGSINDEX` | Tags index page |
| `STYLESHEET` | CSS stylesheet |
| `CUSTOM` | Custom page |

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique identifier |
| `action` | ComponentType | Template action/type |
| `name` | String | Template name |
| `description` | String | Template description |
| `link` | String | Template link/path |
| `lastModified` | Date | Last modification time |
| `hidden` | boolean | Hidden from navigation |
| `navbar` | boolean | Include in navigation |
| `outputContentType` | String | Output MIME type |
| `weblog` | Weblog | Parent weblog |
| `templateRenditions` | List&lt;CustomTemplateRendition&gt; | Template renditions |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `WeblogTemplate()` | Default constructor |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `isNavbar()` | Returns navbar inclusion |
| `isHidden()` | Returns hidden status |
| `isRequired()` | Checks if template is required |
| `isCustom()` | Checks if custom template |
| `getTemplateRendition(type)` | Gets specific rendition |
| `addTemplateRendition(rendition)` | Adds a rendition |

**Interactions:**
- **Weblog**: Many-to-one (belongs to)
- **CustomTemplateRendition**: One-to-many (has renditions)
- **ThemeTemplate**: Implements interface
- **WeblogManager**: Manages template CRUD

---

### 14. CustomTemplateRendition

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Represents a specific rendition of a template.

**Description:**
`CustomTemplateRendition` stores the actual template code for different rendition types (standard, mobile). It supports different template languages (Velocity).

**Rendition Type (RenditionType enum):**

| Constant | Description |
|----------|-------------|
| `STANDARD` | Standard desktop rendition |
| `MOBILE` | Mobile device rendition |

**Template Language (TemplateLanguage enum):**

| Constant | Description |
|----------|-------------|
| `VELOCITY` | Apache Velocity template |

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique identifier |
| `weblogTemplate` | WeblogTemplate | Parent template |
| `template` | String | Template code |
| `type` | RenditionType | Rendition type |
| `templateLanguage` | TemplateLanguage | Template language |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `CustomTemplateRendition()` | Default constructor |
| `CustomTemplateRendition(template, type)` | Constructor with template and type |

**Interactions:**
- **WeblogTemplate**: Many-to-one (belongs to)
- **TemplateRendition**: Implements interface
- **WeblogManager**: Manages storage

---

### 15. WeblogEntrySearchCriteria

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Data transfer object for entry search parameters.

**Description:**
`WeblogEntrySearchCriteria` encapsulates search parameters for querying weblog entries. It supports filtering by weblog, user, dates, category, tags, status, and text with pagination and sorting options.

**Sort By (SortBy enum):**

| Constant | Description |
|----------|-------------|
| `PUBLICATION_TIME` | Sort by publication time |
| `UPDATE_TIME` | Sort by update time |

**Sort Order (SortOrder enum):**

| Constant | Description |
|----------|-------------|
| `ASCENDING` | Ascending order |
| `DESCENDING` | Descending order |

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `weblog` | Weblog | Filter by weblog |
| `user` | User | Filter by author |
| `startDate` | Date | Start date filter |
| `endDate` | Date | End date filter |
| `catName` | String | Category name filter |
| `tags` | List&lt;String&gt; | Tags filter |
| `status` | PubStatus | Publication status filter |
| `text` | String | Text search filter |
| `sortBy` | SortBy | Sort field |
| `sortOrder` | SortOrder | Sort direction |
| `locale` | String | Locale filter |
| `offset` | int | Result offset |
| `maxResults` | int | Maximum results |

**Interactions:**
- **WeblogEntryManager**: Used for entry queries
- **WeblogEntry**: Filters entries
- **Weblog**: References for filtering
- **PubStatus**: Uses for status filtering

---

### 16. CommentSearchCriteria

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Data transfer object for comment search parameters.

**Description:**
`CommentSearchCriteria` encapsulates search parameters for querying comments. It supports filtering by weblog, entry, dates, status, and text with pagination.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `weblog` | Weblog | Filter by weblog |
| `entry` | WeblogEntry | Filter by entry |
| `searchText` | String | Text search filter |
| `startDate` | Date | Start date filter |
| `endDate` | Date | End date filter |
| `status` | ApprovalStatus | Approval status filter |
| `reverseChrono` | boolean | Reverse chronological order |
| `offset` | int | Result offset |
| `maxResults` | int | Maximum results |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `CommentSearchCriteria()` | Default constructor |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `isReverseChrono()` | Returns order direction |

**Interactions:**
- **WeblogEntryManager**: Used for comment queries
- **WeblogEntryComment**: Filters comments
- **Weblog**: References for filtering
- **WeblogEntry**: References for filtering
- **ApprovalStatus**: Uses for status filtering

---

### 17. MediaFileFilter

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Data transfer object for media file search parameters.

**Description:**
`MediaFileFilter` encapsulates search parameters for querying media files. It supports filtering by name, type, size, and tags with pagination and ordering options.

**Size Filter Type (SizeFilterType enum):**

| Constant | Description |
|----------|-------------|
| `GT` | Greater than |
| `GTE` | Greater than or equal |
| `EQ` | Equal |
| `LT` | Less than |
| `LTE` | Less than or equal |

**Media File Order (MediaFileOrder enum):**

| Constant | Description |
|----------|-------------|
| `NAME` | Order by name |
| `DATE_UPLOADED` | Order by upload date |
| `TYPE` | Order by file type |

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `name` | String | Name filter |
| `type` | MediaFileType | File type filter |
| `size` | long | Size filter value |
| `sizeFilterType` | SizeFilterType | Size comparison type |
| `tags` | List&lt;String&gt; | Tags filter |
| `order` | MediaFileOrder | Result ordering |
| `startIndex` | int | Start index |
| `length` | int | Result length |

**Interactions:**
- **MediaFileManager**: Used for file queries
- **MediaFile**: Filters files

---

### 18. TagStat

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Statistics object for tag usage.

**Description:**
`TagStat` represents tag usage statistics, typically used for tag clouds. It contains the tag name, usage count, and calculated intensity for display weighting.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `name` | String | Tag name |
| `count` | int | Usage count |
| `intensity` | int | Display intensity (1-10) |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `TagStat()` | Default constructor |

**Interactions:**
- **WeblogEntryManager**: Returns tag statistics

---

### 19. WeblogHitCount

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Tracks daily hit counts for weblogs.

**Description:**
`WeblogHitCount` stores daily page view statistics for weblogs. This is used for popularity tracking and analytics.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique identifier |
| `weblog` | Weblog | Associated weblog |
| `dailyHits` | int | Daily hit count |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `WeblogHitCount()` | Default constructor |

**Interactions:**
- **Weblog**: Many-to-one (tracks hits for)
- **WeblogEntryManager**: Manages hit counts

---

### 20. StatCount

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Generic statistics count object.

**Description:**
`StatCount` is a generic statistics object used for various counts like most commented entries, most commented weblogs, etc.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `subjectId` | String | Subject identifier |
| `subjectNameShort` | String | Short display name |
| `subjectNameLong` | String | Long display name |
| `typeKey` | String | Statistics type key |
| `count` | long | Count value |
| `weblogHandle` | String | Associated weblog handle |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `StatCount(subjectId, subjectNameShort, subjectNameLong, typeKey, count)` | Full specification constructor |

**Interactions:**
- **WeblogManager**: Returns weblog statistics
- **WeblogEntryManager**: Returns entry statistics

---

## Business Layer - Interfaces

### 21. Weblogger (Interface)

**Package:** `org.apache.roller.weblogger.business`

**Role:** Main facade interface providing access to all business managers.

**Description:**
`Weblogger` is the central entry point to the Roller business layer. It provides factory methods for all business service managers and handles application lifecycle (initialization, shutdown).

**Key Methods:**

| Method | Description |
|--------|-------------|
| `getUserManager()` | Returns UserManager instance |
| `getBookmarkManager()` | Returns BookmarkManager instance |
| `getWeblogManager()` | Returns WeblogManager instance |
| `getWeblogEntryManager()` | Returns WeblogEntryManager instance |
| `getThemeManager()` | Returns ThemeManager instance |
| `getMediaFileManager()` | Returns MediaFileManager instance |
| `getFileContentManager()` | Returns FileContentManager instance |
| `getUrlStrategy()` | Returns URLStrategy instance |
| `flush()` | Flushes pending changes |
| `initialize()` | Initializes the system |
| `release()` | Releases resources |
| `shutdown()` | Shuts down the system |
| `getVersion()` | Returns Roller version |
| `getRevision()` | Returns revision number |
| `getBuildTime()` | Returns build timestamp |
| `getBuildUser()` | Returns build user |

**Interactions:**
- **JPAWebloggerImpl**: Implementation class
- **All managers**: Provides access to
- **All servlets and actions**: Use for business operations

---

### 22. WeblogManager (Interface)

**Package:** `org.apache.roller.weblogger.business`

**Role:** Business service interface for weblog and template management.

**Description:**
`WeblogManager` defines the contract for all weblog-related operations including CRUD, searching, and template management.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `addWeblog(newWebsite)` | Creates a new weblog |
| `saveWeblog(data)` | Updates existing weblog |
| `removeWeblog(website)` | Deletes weblog and all content |
| `getWeblog(id)` | Retrieves weblog by ID |
| `getWeblogByHandle(handle)` | Retrieves weblog by handle |
| `getWeblogByHandle(handle, enabled)` | Retrieves with enabled filter |
| `getWeblogs(enabled, active, startDate, endDate, offset, length)` | Paginated weblog list |
| `getUserWeblogs(user, enabledOnly)` | Gets user's weblogs |
| `getWeblogUsers(weblog, enabledOnly)` | Gets weblog's users |
| `getMostCommentedWeblogs(startDate, endDate, offset, length)` | Gets most commented weblogs |
| `getWeblogHandleLetterMap()` | Returns count per first letter |
| `getWeblogsByLetter(letter, offset, length)` | Weblogs starting with letter |
| `saveTemplate(data)` | Saves a template |
| `removeTemplate(template)` | Removes a template |
| `getTemplate(id)` | Gets template by ID |
| `getTemplateByAction(weblog, action)` | Gets template by action type |
| `getTemplateByName(weblog, name)` | Gets template by name |
| `getTemplateByLink(weblog, link)` | Gets template by link |
| `saveTemplateRendition(templateCode)` | Saves template rendition |
| `getTemplates(weblog)` | Gets all weblog templates |
| `getWeblogCount()` | Returns total weblog count |
| `release()` | Releases resources |

**Interactions:**
- **JPAWeblogManagerImpl**: Implementation class
- **Weblog**: Manages CRUD operations
- **WeblogTemplate**: Manages templates
- **User**: Queries ownership

---

### 23. WeblogEntryManager (Interface)

**Package:** `org.apache.roller.weblogger.business`

**Role:** Business service interface for entry, category, comment, and tag management.

**Description:**
`WeblogEntryManager` defines the contract for all entry-related operations including entries, categories, comments, tags, and statistics.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `saveWeblogEntry(entry)` | Saves an entry |
| `removeWeblogEntry(entry)` | Removes an entry |
| `getWeblogEntry(id)` | Gets entry by ID |
| `getWeblogEntryByAnchor(weblog, anchor)` | Gets entry by anchor |
| `getWeblogEntries(criteria)` | Gets entries by criteria |
| `getWeblogEntryObjectMap(criteria)` | Gets entries grouped by date |
| `getWeblogEntryStringMap(criteria)` | Gets entry dates as strings |
| `getMostCommentedWeblogEntries(weblog, startDate, endDate, offset, length)` | Gets most commented entries |
| `getNextEntry(current, category, locale)` | Gets next entry |
| `getPreviousEntry(current, category, locale)` | Gets previous entry |
| `saveWeblogCategory(category)` | Saves a category |
| `removeWeblogCategory(category)` | Removes a category |
| `getWeblogCategory(id)` | Gets category by ID |
| `getWeblogCategoryByName(weblog, name)` | Gets category by name |
| `getWeblogCategories(weblog)` | Gets all categories |
| `saveComment(comment)` | Saves a comment |
| `removeComment(comment)` | Removes a comment |
| `getComment(id)` | Gets comment by ID |
| `getComments(criteria)` | Gets comments by criteria |
| `getPopularTags(weblog, startDate, offset, limit)` | Gets popular tags |
| `getTags(weblog, sortBy, startsWith, offset, limit)` | Gets tag list |
| `getHitCount(id)` | Gets hit count by ID |
| `getHitCountByWeblog(weblog)` | Gets hit count for weblog |
| `incrementHitCount(weblog, amount)` | Increments hit count |
| `getCommentCount()` | Gets total comment count |
| `getCommentCount(weblog)` | Gets weblog comment count |
| `getEntryCount()` | Gets total entry count |
| `getEntryCount(weblog)` | Gets weblog entry count |
| `release()` | Releases resources |

**Interactions:**
- **JPAWeblogEntryManagerImpl**: Implementation class
- **WeblogEntry**: Manages entry CRUD
- **WeblogCategory**: Manages category CRUD
- **WeblogEntryComment**: Manages comment CRUD
- **TagStat**: Returns tag statistics
- **WeblogHitCount**: Tracks hit counts

---

### 24. MediaFileManager (Interface)

**Package:** `org.apache.roller.weblogger.business`

**Role:** Business service interface for media file and directory management.

**Description:**
`MediaFileManager` defines the contract for all media file operations including file CRUD, directory management, and searching.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `initialize()` | Initializes the manager |
| `release()` | Releases resources |
| `createMediaFile(weblog, mediaFile, errors)` | Creates a media file |
| `createThemeMediaFile(weblog, mediaFile, errors)` | Creates theme media file |
| `updateMediaFile(weblog, mediaFile)` | Updates media file metadata |
| `updateMediaFile(weblog, mediaFile, content)` | Updates file with new content |
| `getMediaFile(id)` | Gets file by ID |
| `getMediaFile(id, includeContent)` | Gets file with optional content |
| `removeMediaFile(weblog, mediaFile)` | Removes a file |
| `searchMediaFiles(weblog, filter)` | Searches files |
| `createDefaultMediaFileDirectory(weblog)` | Creates default directory |
| `createMediaFileDirectory(directory)` | Creates a directory |
| `createMediaFileDirectory(weblog, name)` | Creates named directory |
| `getMediaFileDirectory(id)` | Gets directory by ID |
| `getMediaFileDirectoryByName(weblog, name)` | Gets directory by name |
| `getMediaFileByPath(weblog, path)` | Gets file by path |
| `getMediaFileByOriginalPath(weblog, path)` | Gets file by original path |
| `getMediaFileDirectories(weblog)` | Gets all directories |
| `getDefaultMediaFileDirectory(weblog)` | Gets default directory |
| `moveMediaFiles(files, directory)` | Moves files to directory |
| `moveMediaFile(mediaFile, directory)` | Moves single file |
| `fetchRecentPublicMediaFiles(length)` | Gets recent public files |
| `removeAllFiles(weblog)` | Removes all weblog files |
| `removeMediaFileDirectory(directory)` | Removes a directory |
| `removeMediaFileTag(name, mediaFile)` | Removes a file tag |

**Interactions:**
- **JPAMediaFileManagerImpl**: Implementation class
- **MediaFile**: Manages file CRUD
- **MediaFileDirectory**: Manages directory CRUD
- **MediaFileFilter**: Uses for searching
- **RollerMessages**: Reports validation errors

---

### 25. FileContentManager (Interface)

**Package:** `org.apache.roller.weblogger.business`

**Role:** Business service interface for file storage operations.

**Description:**
`FileContentManager` defines the contract for physical file storage operations including saving, retrieving, and deleting file content.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `saveFileContent(weblog, path, contentStream)` | Saves file content |
| `getFileContent(weblog, path)` | Gets file content stream |
| `deleteFile(weblog, path)` | Deletes a file |
| `canSave(weblog, path, contentType, size)` | Checks if file can be saved |
| `overQuota(weblog)` | Checks if weblog is over quota |
| `delete(weblog)` | Deletes all weblog files |
| `getFileContent(weblog, fileId)` | Gets FileContent by ID |
| `saveFileContent(weblog, fileId, content)` | Saves content by ID |
| `deleteFile(weblog, fileId)` | Deletes file by ID |
| `deleteAllFiles(weblog)` | Deletes all files |
| `canSave(weblog, fileName, contentType, size, messages)` | Validates save with messages |
| `release()` | Releases resources |

**Interactions:**
- **FileContentManagerImpl**: Implementation class
- **Weblog**: Stores files for
- **FileContent**: Manages content
- **RollerMessages**: Reports validation errors

---

### 26. ThemeManager (Interface)

**Package:** `org.apache.roller.weblogger.business`

**Role:** Business service interface for theme management.

**Description:**
`ThemeManager` defines the contract for theme operations including listing available themes, importing themes to weblogs, and reloading themes from disk.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `initialize()` | Initializes the manager |
| `getTheme(id)` | Gets SharedTheme by ID |
| `getTheme(weblog)` | Gets WeblogTheme for weblog |
| `getEnabledThemesList()` | Gets list of enabled themes |
| `importTheme(weblog, theme, skipStylesheet)` | Imports theme to weblog |
| `reLoadThemeFromDisk(themeName)` | Reloads theme from disk |

**Interactions:**
- **ThemeManagerImpl**: Implementation class
- **Weblog**: Manages themes for
- **SharedTheme**: Returns available themes
- **WeblogTheme**: Returns weblog-specific themes

---

### 27. BookmarkManager (Interface)

**Package:** `org.apache.roller.weblogger.business`

**Role:** Business service interface for bookmark and folder management.

**Description:**
`BookmarkManager` defines the contract for bookmark operations including folder and bookmark CRUD, and OPML import.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `saveFolder(folder)` | Saves a folder |
| `removeFolder(folder)` | Removes a folder |
| `getFolder(id)` | Gets folder by ID |
| `getAllFolders(weblog)` | Gets all folders |
| `getDefaultFolder(weblog)` | Gets default folder |
| `getFolder(weblog, name)` | Gets folder by name |
| `saveBookmark(bookmark)` | Saves a bookmark |
| `removeBookmark(bookmark)` | Removes a bookmark |
| `getBookmark(id)` | Gets bookmark by ID |
| `getBookmarks(folder)` | Gets folder's bookmarks |
| `importBookmarks(weblog, folder, opml)` | Imports OPML bookmarks |
| `release()` | Releases resources |

**Interactions:**
- **Weblog**: Manages bookmarks for
- **WeblogBookmarkFolder**: Manages folder CRUD
- **WeblogBookmark**: Manages bookmark CRUD

---

### 28. URLStrategy (Interface)

**Package:** `org.apache.roller.weblogger.business`

**Role:** Strategy interface for URL generation.

**Description:**
`URLStrategy` defines the contract for generating all URLs in the application. This includes weblog URLs, entry URLs, feed URLs, media URLs, and system URLs.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `getPreviewURLStrategy(previewTheme)` | Gets preview URL strategy |
| `getLoginURL(absolute)` | Gets login URL |
| `getLogoutURL(absolute)` | Gets logout URL |
| `getRegisterURL(absolute)` | Gets registration URL |
| `getActionURL(action, namespace, weblogHandle, parameters, absolute)` | Gets action URL |
| `getEntryAddURL(weblogHandle, absolute)` | Gets entry add URL |
| `getEntryEditURL(weblogHandle, entryId, absolute)` | Gets entry edit URL |
| `getWeblogConfigURL(weblogHandle, absolute)` | Gets weblog config URL |
| `getWeblogURL(weblog, locale, absolute)` | Gets weblog URL |
| `getWeblogEntryURL(weblog, locale, entryAnchor, absolute)` | Gets entry URL |
| `getWeblogCommentsURL(weblog, locale, entryAnchor, absolute)` | Gets comments URL |
| `getWeblogCommentURL(weblog, locale, entryAnchor, timeStamp, absolute)` | Gets comment URL |
| `getMediaFileURL(weblog, fileAnchor, absolute)` | Gets media file URL |
| `getMediaFileThumbnailURL(weblog, fileAnchor, absolute)` | Gets thumbnail URL |
| `getWeblogCollectionURL(weblog, locale, category, dateString, tags, pageNum, absolute)` | Gets collection URL |
| `getWeblogPageURL(weblog, locale, pageLink, entryAnchor, category, dateString, tags, pageNum, absolute)` | Gets page URL |
| `getWeblogFeedURL(weblog, locale, type, format, category, term, tags, excerpts, absolute)` | Gets feed URL |
| `getWeblogSearchURL(weblog, locale, query, category, pageNum, absolute)` | Gets search URL |
| `getWeblogResourceURL(weblog, filePath, absolute)` | Gets resource URL |
| `getWeblogRsdURL(weblog, absolute)` | Gets RSD URL |
| `getWeblogTagsJsonURL(weblog, absolute, pageNum)` | Gets tags JSON URL |
| `getOAuthRequestTokenURL()` | Gets OAuth request token URL |
| `getOAuthAuthorizationURL()` | Gets OAuth authorization URL |
| `getOAuthAccessTokenURL()` | Gets OAuth access token URL |

**Interactions:**
- **AbstractURLStrategy**: Abstract implementation
- **MultiWeblogURLStrategy**: Concrete implementation
- **PreviewURLStrategy**: Preview implementation
- **All servlets**: Use for URL generation

---

### 29. Template (Interface)

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Base interface for all templates.

**Description:**
`Template` defines the minimal contract for template objects, providing access to the template identifier and language.

**Interactions:**
- **ThemeTemplate**: Extends this interface
- **Renderer**: Renders templates

---

### 30. ThemeTemplate (Interface)

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Interface for theme-based templates.

**Description:**
`ThemeTemplate` extends `Template` to add theme-specific properties including action type, link, and visibility settings.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `isHidden()` | Returns hidden status |
| `isNavbar()` | Returns navbar inclusion |

**Interactions:**
- **Template**: Extends interface
- **WeblogTemplate**: Implements interface
- **ComponentType**: Uses for action

---

### 31. TemplateRendition (Interface)

**Package:** `org.apache.roller.weblogger.pojos`

**Role:** Interface for template renditions.

**Description:**
`TemplateRendition` defines the contract for template rendition objects, providing access to template code, language, and type.

**Interactions:**
- **CustomTemplateRendition**: Implements interface
- **RenditionType**: Uses for type
- **TemplateLanguage**: Uses for language

---

## Persistence Layer (JPA)

### 32. JPAWebloggerImpl

**Package:** `org.apache.roller.weblogger.business.jpa`

**Role:** JPA-based implementation of Weblogger interface.

**Description:**
`JPAWebloggerImpl` provides the concrete implementation of the `Weblogger` facade, instantiating and managing all business service implementations.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `userManager` | UserManager | User manager instance |
| `weblogManager` | WeblogManager | Weblog manager instance |
| `weblogEntryManager` | WeblogEntryManager | Entry manager instance |
| `mediaFileManager` | MediaFileManager | Media file manager instance |
| `fileContentManager` | FileContentManager | File content manager instance |
| `themeManager` | ThemeManager | Theme manager instance |
| `urlStrategy` | URLStrategy | URL strategy instance |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `flush()` | Flushes pending changes |

**Interactions:**
- **Weblogger**: Implements interface
- **All JPA managers**: Creates and manages

---

### 33. JPAWeblogManagerImpl

**Package:** `org.apache.roller.weblogger.business.jpa`

**Role:** JPA-based implementation of WeblogManager interface.

**Description:**
`JPAWeblogManagerImpl` provides the concrete implementation of `WeblogManager` using JPA for database operations.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `strategy` | JPAPersistenceStrategy | Database access strategy |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `addWeblog(weblog)` | Persists new weblog |
| `saveWeblog(weblog)` | Updates weblog |
| `getWeblog(id)` | Loads weblog by ID |
| `getWeblogByHandle(handle)` | Loads weblog by handle |

**Interactions:**
- **WeblogManager**: Implements interface
- **JPAPersistenceStrategy**: Uses for database operations
- **Weblog**: Manages persistence

---

### 34. JPAWeblogEntryManagerImpl

**Package:** `org.apache.roller.weblogger.business.jpa`

**Role:** JPA-based implementation of WeblogEntryManager interface.

**Description:**
`JPAWeblogEntryManagerImpl` provides the concrete implementation of `WeblogEntryManager` using JPA for database operations.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `strategy` | JPAPersistenceStrategy | Database access strategy |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `saveWeblogEntry(entry)` | Persists entry |
| `removeWeblogEntry(entry)` | Removes entry |
| `getWeblogEntry(id)` | Loads entry by ID |
| `getWeblogEntries(criteria)` | Queries entries |

**Interactions:**
- **WeblogEntryManager**: Implements interface
- **JPAPersistenceStrategy**: Uses for database operations
- **WeblogEntry**: Manages persistence

---

### 35. JPAMediaFileManagerImpl

**Package:** `org.apache.roller.weblogger.business.jpa`

**Role:** JPA-based implementation of MediaFileManager interface.

**Description:**
`JPAMediaFileManagerImpl` provides the concrete implementation of `MediaFileManager` using JPA for database operations.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `strategy` | JPAPersistenceStrategy | Database access strategy |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `createMediaFile(weblog, mediaFile, contentStream)` | Creates file |
| `updateMediaFile(weblog, mediaFile)` | Updates file |
| `getMediaFile(id)` | Loads file by ID |
| `searchMediaFiles(weblog, filter)` | Searches files |
| `deleteMediaFile(weblog, mediaFile)` | Deletes file |

**Interactions:**
- **MediaFileManager**: Implements interface
- **JPAPersistenceStrategy**: Uses for database operations
- **MediaFile**: Manages persistence

---

### 36. FileContentManagerImpl

**Package:** `org.apache.roller.weblogger.business.jpa`

**Role:** File system implementation of FileContentManager interface.

**Description:**
`FileContentManagerImpl` provides the concrete implementation of `FileContentManager` for storing files on the file system.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `storageDir` | String | Base storage directory path |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `saveFileContent(weblog, path, contentStream)` | Saves file to disk |
| `getFileContent(weblog, path)` | Reads file from disk |
| `deleteFile(weblog, path)` | Deletes file from disk |
| `canSave(weblog, path, contentType, size)` | Validates save operation |

**Interactions:**
- **FileContentManager**: Implements interface
- **Weblog**: Organizes storage by weblog

---

### 37. ThemeManagerImpl

**Package:** `org.apache.roller.weblogger.business.jpa`

**Role:** Implementation of ThemeManager interface.

**Description:**
`ThemeManagerImpl` provides the concrete implementation of `ThemeManager` for managing themes loaded from disk.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `themeMap` | Map&lt;String, SharedTheme&gt; | Cached themes |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `getTheme(themeId)` | Returns SharedTheme |
| `getEnabledThemesList()` | Returns enabled themes |
| `importTheme(weblog, themeId)` | Imports theme to weblog |
| `getWeblogTheme(weblog)` | Returns weblog's theme |

**Interactions:**
- **ThemeManager**: Implements interface
- **Weblog**: Manages themes for
- **SharedTheme**: Caches themes

---

### 38. JPAPersistenceStrategy

**Package:** `org.apache.roller.weblogger.business.jpa`

**Role:** Low-level JPA database operations wrapper.

**Description:**
`JPAPersistenceStrategy` provides a clean abstraction over JPA EntityManager operations, used by all JPA manager implementations.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `store(obj)` | Persists or merges entity |
| `remove(obj)` | Removes entity |
| `load(clazz, id)` | Loads entity by ID |
| `getNamedQuery(name, clazz)` | Returns typed named query |
| `flush()` | Flushes pending changes |

**Interactions:**
- **All JPA managers**: Use for database operations
- **EntityManager**: Wraps JPA operations

---

### 39. AbstractURLStrategy

**Package:** `org.apache.roller.weblogger.business`

**Role:** Abstract base class for URL strategies.

**Description:**
`AbstractURLStrategy` provides common URL generation logic shared by all URL strategy implementations.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `URL_BUFFER_SIZE` | int | Buffer size for URL building |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `getLoginURL(absolute)` | Builds login URL |
| `getLogoutURL(absolute)` | Builds logout URL |
| `getRegisterURL(absolute)` | Builds registration URL |
| `getActionURL(action, namespace, weblogHandle, parameters, absolute)` | Builds action URL |
| `getEntryAddURL(weblogHandle, absolute)` | Builds entry add URL |
| `getEntryEditURL(weblogHandle, entryId, absolute)` | Builds entry edit URL |
| `getWeblogConfigURL(weblogHandle, absolute)` | Builds config URL |
| `getXmlrpcURL(absolute)` | Builds XML-RPC URL |
| `getAtomProtocolURL(absolute)` | Builds Atom URL |

**Interactions:**
- **URLStrategy**: Implements interface
- **MultiWeblogURLStrategy**: Extends class
- **URLUtilities**: Uses for encoding

---

### 40. MultiWeblogURLStrategy

**Package:** `org.apache.roller.weblogger.business`

**Role:** URL strategy for multi-weblog installations.

**Description:**
`MultiWeblogURLStrategy` generates URLs for installations with multiple weblogs, including weblog handles in URL paths.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `getPreviewURLStrategy(previewTheme)` | Returns preview strategy |
| `getWeblogURL(weblog, locale, absolute)` | Builds weblog URL |
| `getWeblogEntryURL(weblog, locale, entryAnchor, absolute)` | Builds entry URL |
| `getMediaFileURL(weblog, fileAnchor, absolute)` | Builds media URL |
| `getMediaFileThumbnailURL(weblog, fileAnchor, absolute)` | Builds thumbnail URL |
| `getWeblogCommentsURL(weblog, locale, entryAnchor, absolute)` | Builds comments URL |
| `getWeblogCollectionURL(weblog, locale, category, dateString, tags, pageNum, absolute)` | Builds collection URL |
| `getWeblogPageURL(...)` | Builds page URL |
| `getWeblogFeedURL(...)` | Builds feed URL |
| `getWeblogSearchURL(weblog, locale, query, category, pageNum, absolute)` | Builds search URL |
| `getWeblogResourceURL(weblog, filePath, absolute)` | Builds resource URL |

**Interactions:**
- **AbstractURLStrategy**: Extends class
- **URLStrategy**: Implements interface
- **Weblog**: Builds URLs for
- **URLUtilities**: Uses for encoding

---

### 41. PreviewURLStrategy

**Package:** `org.apache.roller.weblogger.business`

**Role:** URL strategy for theme preview mode.

**Description:**
`PreviewURLStrategy` extends `MultiWeblogURLStrategy` to generate URLs for theme preview, adding preview-specific path segments.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `previewTheme` | String | Theme being previewed |
| `PREVIEW_URL_SEGMENT` | String | Preview path segment |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `getWeblogURL(weblog, locale, absolute)` | Builds preview weblog URL |
| `getWeblogEntryURL(weblog, locale, previewAnchor, absolute)` | Builds preview entry URL |
| `getWeblogCollectionURL(...)` | Builds preview collection URL |
| `getWeblogPageURL(...)` | Builds preview page URL |
| `getWeblogResourceURL(weblog, filePath, absolute)` | Builds preview resource URL |

**Interactions:**
- **MultiWeblogURLStrategy**: Extends class
- **Weblog**: Builds preview URLs for
- **WeblogTheme**: Checks theme type

---

## Rendering Layer

### 42. RendererManager

**Package:** `org.apache.roller.weblogger.ui.rendering`

**Role:** Manager for template rendering operations.

**Description:**
`RendererManager` coordinates template rendering by selecting appropriate renderers based on template type and managing the rendering process.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `rendererFactories` | Map&lt;String, RendererFactory&gt; | Registered factories |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `render(template, model)` | Renders template to string |
| `render(template, model, output)` | Renders template to stream |
| `getRenderer(template)` | Gets renderer for template |

**Interactions:**
- **RendererFactory**: Uses to create renderers
- **Renderer**: Creates instances
- **Template**: Renders templates
- **PageServlet**: Uses for page rendering

---

### 43. Renderer (Interface)

**Package:** `org.apache.roller.weblogger.ui.rendering`

**Role:** Interface for template renderers.

**Description:**
`Renderer` defines the contract for template rendering implementations.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `render(template, output)` | Renders template to output stream |

**Interactions:**
- **VelocityRenderer**: Implementation
- **RendererManager**: Creates instances
- **Template**: Renders templates

---

### 44. RendererFactory (Interface)

**Package:** `org.apache.roller.weblogger.ui.rendering`

**Role:** Factory interface for creating renderers.

**Description:**
`RendererFactory` defines the contract for factories that create appropriate renderers based on template type and device.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `getRenderer(template, deviceType)` | Creates renderer for template |

**Interactions:**
- **VelocityRendererFactory**: Implementation
- **RendererManager**: Uses to create renderers
- **Renderer**: Creates instances

---

### 45. VelocityRenderer

**Package:** `org.apache.roller.weblogger.ui.rendering`

**Role:** Apache Velocity template renderer.

**Description:**
`VelocityRenderer` implements the `Renderer` interface using Apache Velocity for template processing.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `velocityEngine` | VelocityEngine | Velocity engine instance |
| `context` | Map&lt;String, Object&gt; | Template context |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `render(template, output)` | Renders Velocity template |

**Interactions:**
- **Renderer**: Implements interface
- **VelocityEngine**: Uses for rendering
- **Template**: Renders templates

---

### 46. VelocityRendererFactory

**Package:** `org.apache.roller.weblogger.ui.rendering`

**Role:** Factory for creating Velocity renderers.

**Description:**
`VelocityRendererFactory` creates `VelocityRenderer` instances for Velocity templates.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `velocityEngine` | VelocityEngine | Shared engine instance |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `getRenderer(template, deviceType)` | Creates VelocityRenderer |

**Interactions:**
- **RendererFactory**: Implements interface
- **VelocityRenderer**: Creates instances

---

### 47. ModelLoader

**Package:** `org.apache.roller.weblogger.ui.rendering`

**Role:** Loads model data for template rendering.

**Description:**
`ModelLoader` prepares the data model used by templates, loading weblog data, entries, and other content into the template context.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `loadModels(request, model, preview)` | Loads models for page request |
| `loadSearchModels(query, weblog, urlStrategy, model, hits)` | Loads models for search |

**Interactions:**
- **Weblogger**: Uses for data access
- **WeblogPageRequest**: Gets request parameters
- **PageServlet**: Uses for model loading

---

### 48. CommentAuthenticator (Interface)

**Package:** `org.apache.roller.weblogger.ui.rendering`

**Role:** Interface for comment authentication mechanisms.

**Description:**
`CommentAuthenticator` defines the contract for CAPTCHA and other anti-spam authentication methods for comments.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `getHtml(request)` | Gets HTML for authentication challenge |
| `authenticate(request)` | Validates authentication response |

**Interactions:**
- **HttpServletRequest**: Uses for request data
- **CommentServlet**: Uses for comment validation

---

### 49. CommentValidationManager

**Package:** `org.apache.roller.weblogger.ui.rendering`

**Role:** Manages comment validation pipeline.

**Description:**
`CommentValidationManager` coordinates multiple comment validators to determine if a comment should be accepted, moderated, or rejected as spam.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `validators` | List&lt;CommentValidator&gt; | Registered validators |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `CommentValidationManager()` | Default constructor |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `addCommentValidator(val)` | Registers a validator |
| `validateComment(comment, messages)` | Validates comment with all validators |

**Interactions:**
- **CommentValidator**: Uses validators
- **WeblogEntryComment**: Validates comments
- **RollerMessages**: Collects validation messages
- **CommentServlet**: Uses for validation

---

### 50. CommentValidator (Interface)

**Package:** `org.apache.roller.weblogger.ui.rendering`

**Role:** Interface for comment validation implementations.

**Description:**
`CommentValidator` defines the contract for individual comment validators that check for spam, banned words, and other criteria.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `getName()` | Returns validator name |
| `validate(comment, messages)` | Validates comment, returns score |

**Interactions:**
- **WeblogEntryComment**: Validates comments
- **RollerMessages**: Collects messages
- **CommentValidationManager**: Uses validators

---

### 51. RollerMessages

**Package:** `org.apache.roller.weblogger.ui.rendering`

**Role:** Container for validation messages.

**Description:**
`RollerMessages` collects error and informational messages during operations like validation, file upload, etc.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `mErrors` | List&lt;RollerMessage&gt; | Error messages |
| `mMessages` | List&lt;RollerMessage&gt; | Info messages |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `RollerMessages()` | Default constructor |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `addError(key)` | Adds error by key |
| `addError(key, arg)` | Adds error with argument |
| `addError(key, args)` | Adds error with multiple arguments |
| `addMessage(key)` | Adds info message |
| `addMessage(key, arg)` | Adds message with argument |
| `addMessage(key, args)` | Adds message with arguments |
| `getErrors()` | Returns error iterator |
| `getMessages()` | Returns message iterator |
| `getErrorCount()` | Returns error count |
| `getMessageCount()` | Returns message count |

**Interactions:**
- **CommentValidationManager**: Uses for messages
- **MediaFileManager**: Uses for validation errors
- **FileContentManager**: Uses for validation errors

---

## Servlet Layer

### 52. PageServlet

**Package:** `org.apache.roller.weblogger.ui.rendering.servlets`

**Role:** Main servlet for rendering weblog pages.

**Description:**
`PageServlet` handles HTTP requests for weblog pages, coordinates caching, model loading, and template rendering.

**Extends:** `HttpServlet`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `weblogPageCache` | WeblogPageCache | Page cache instance |
| `siteWideCache` | SiteWideCache | Site-wide cache instance |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `doGet(request, response)` | Handles GET requests |
| `handleRequest(request, response)` | Processes page request |
| `generateKey(pageRequest, user)` | Generates cache key |
| `respondIfNotModified(request, response, lastModified)` | Checks modification |

**Interactions:**
- **WeblogPageRequest**: Parses requests
- **WeblogPageCache**: Caches pages
- **SiteWideCache**: Site-level caching
- **RendererManager**: Renders templates
- **ModelLoader**: Loads model data

---

### 53. FeedServlet

**Package:** `org.apache.roller.weblogger.ui.rendering.servlets`

**Role:** Servlet for generating RSS/Atom feeds.

**Description:**
`FeedServlet` handles HTTP requests for weblog feeds, generating RSS and Atom syndication formats.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `weblogFeedCache` | WeblogFeedCache | Feed cache instance |
| `siteWideCache` | SiteWideCache | Site-wide cache instance |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `init(servletConfig)` | Initializes servlet |
| `doGet(request, response)` | Handles feed requests |

**Interactions:**
- **WeblogFeedCache**: Caches feeds
- **SiteWideCache**: Site-level caching
- **WeblogFeedRequest**: Parses requests
- **HttpServletRequest**: Request handling
- **HttpServletResponse**: Response generation

---

### 54. SearchServlet

**Package:** `org.apache.roller.weblogger.ui.rendering.servlets`

**Role:** Servlet for handling search requests.

**Description:**
`SearchServlet` handles HTTP requests for weblog searches, coordinating with the search system and rendering results.

**Extends:** `HttpServlet`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `themeReload` | Boolean | Theme reload flag |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `init(servletConfig)` | Initializes servlet |
| `doGet(request, response)` | Handles search requests |

**Interactions:**
- **HttpServletRequest**: Request handling
- **HttpServletResponse**: Response generation
- **IndexManager**: Uses for search (from Search subsystem)

---

### 55. MediaResourceServlet

**Package:** `org.apache.roller.weblogger.ui.rendering.servlets`

**Role:** Servlet for serving media files.

**Description:**
`MediaResourceServlet` handles HTTP requests for media files, streaming file content with appropriate headers.

**Extends:** `HttpServlet`

**Key Methods:**

| Method | Description |
|--------|-------------|
| `doGet(request, response)` | Handles media requests |
| `serveMediaFile(id, response)` | Streams media file |

**Interactions:**
- **MediaFileManager**: Gets media files
- **HttpServletRequest**: Request handling
- **HttpServletResponse**: Response generation

---

### 56. CommentServlet

**Package:** `org.apache.roller.weblogger.ui.rendering.servlets`

**Role:** Servlet for handling comment submissions.

**Description:**
`CommentServlet` handles HTTP requests for comment submission, coordinating authentication, validation, and persistence.

**Extends:** `HttpServlet`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `authenticator` | CommentAuthenticator | Authentication handler |
| `commentValidationManager` | CommentValidationManager | Validation coordinator |
| `commentThrottle` | GenericThrottle | Rate limiting |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `init(servletConfig)` | Initializes servlet |
| `doGet(request, response)` | Handles GET (displays form) |
| `doPost(request, response)` | Handles POST (submits comment) |

**Interactions:**
- **CommentAuthenticator**: Authenticates submissions
- **CommentValidationManager**: Validates comments
- **WeblogEntryComment**: Creates comments
- **HttpServletRequest**: Request handling
- **HttpServletResponse**: Response generation

---

## Utility Layer

### 57. WeblogRequest (Abstract)

**Package:** `org.apache.roller.weblogger.ui.rendering.util`

**Role:** Base class for weblog-related request parsing.

**Description:**
`WeblogRequest` provides common request parsing functionality for all weblog-related servlets, extracting weblog handle, locale, and authenticated user.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `weblog` | Weblog | Resolved weblog |
| `weblogHandle` | String | Weblog handle from URL |
| `authenticUser` | User | Authenticated user |
| `locale` | String | Request locale |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `isValidDestination()` | Abstract - validates request |

**Interactions:**
- **WeblogPageRequest**: Extends class
- **WeblogFeedRequest**: Extends class
- **WeblogCommentRequest**: Extends class
- **Weblog**: Resolves weblog

---

### 58. WeblogPageRequest

**Package:** `org.apache.roller.weblogger.ui.rendering.util`

**Role:** Parses page rendering requests.

**Description:**
`WeblogPageRequest` extends `WeblogRequest` to parse parameters specific to page rendering including entry anchors, categories, dates, and tags.

**Extends:** `WeblogRequest`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `context` | String | Request context |
| `weblogAnchor` | String | Entry anchor |
| `weblogPageName` | String | Page name |
| `weblogCategoryName` | String | Category name |
| `weblogDate` | String | Date string |
| `tags` | List&lt;String&gt; | Tag filters |
| `pageNum` | int | Page number |
| `customParams` | Map&lt;String, String[]&gt; | Custom parameters |
| `weblogEntry` | WeblogEntry | Resolved entry |
| `weblogPage` | ThemeTemplate | Resolved template |
| `weblogCategory` | WeblogCategory | Resolved category |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `WeblogPageRequest()` | Default constructor |
| `WeblogPageRequest(request)` | Parses from HTTP request |

**Interactions:**
- **WeblogRequest**: Extends class
- **WeblogEntry**: Resolves entry
- **WeblogCategory**: Resolves category
- **ThemeTemplate**: Resolves template
- **PageServlet**: Uses for request parsing

---

### 59. WeblogFeedRequest

**Package:** `org.apache.roller.weblogger.ui.rendering.util`

**Role:** Parses feed generation requests.

**Description:**
`WeblogFeedRequest` extends `WeblogRequest` to parse parameters specific to feed generation including feed type, format, and filters.

**Extends:** `WeblogRequest`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `type` | String | Feed type |
| `format` | String | Feed format (rss, atom) |
| `weblogCategoryName` | String | Category filter |
| `tags` | List&lt;String&gt; | Tag filters |
| `page` | int | Page number |
| `excerpts` | boolean | Use excerpts |
| `term` | String | Search term |
| `weblogCategory` | WeblogCategory | Resolved category |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `WeblogFeedRequest()` | Default constructor |
| `WeblogFeedRequest(request)` | Parses from HTTP request |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `isExcerpts()` | Returns excerpts flag |

**Interactions:**
- **WeblogRequest**: Extends class
- **WeblogCategory**: Resolves category
- **FeedServlet**: Uses for request parsing
- **WeblogFeedCache**: Generates cache keys

---

### 60. WeblogCommentRequest

**Package:** `org.apache.roller.weblogger.ui.rendering.util`

**Role:** Parses comment submission requests.

**Description:**
`WeblogCommentRequest` extends `WeblogRequest` to parse parameters for comment submission including commenter info and content.

**Extends:** `WeblogRequest`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `name` | String | Commenter name |
| `email` | String | Commenter email |
| `url` | String | Commenter URL |
| `content` | String | Comment content |
| `notify` | boolean | Notification preference |
| `weblogAnchor` | String | Entry anchor |
| `weblogEntry` | WeblogEntry | Target entry |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `WeblogCommentRequest()` | Default constructor |
| `WeblogCommentRequest(request)` | Parses from HTTP request |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `isNotify()` | Returns notification preference |

**Interactions:**
- **WeblogRequest**: Extends class
- **WeblogEntry**: References target entry
- **CommentServlet**: Uses for request parsing

---

### 61. WeblogPageCache

**Package:** `org.apache.roller.weblogger.ui.rendering.util`

**Role:** Cache for rendered weblog pages.

**Description:**
`WeblogPageCache` provides caching for rendered page content, reducing database and rendering load for frequently accessed pages.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `cache` | Map&lt;String, CachedContent&gt; | Cached content map |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `get(key, lastModified)` | Gets cached content if fresh |
| `put(key, content)` | Caches content |
| `remove(key)` | Removes cached content |
| `clear()` | Clears all cached content |

**Interactions:**
- **PageServlet**: Uses for caching
- **CachedContent**: Stores cached data

---

### 62. WeblogFeedCache

**Package:** `org.apache.roller.weblogger.ui.rendering.util`

**Role:** Cache for generated feeds.

**Description:**
`WeblogFeedCache` provides caching for generated RSS/Atom feeds, implemented as a singleton.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `CACHE_ID` | String | Cache identifier |
| `singletonInstance` | WeblogFeedCache | Singleton instance |
| `cacheEnabled` | boolean | Cache enabled flag |
| `contentCache` | Cache | Underlying cache |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `getInstance()` | Returns singleton instance |
| `get(key, lastModified)` | Gets cached feed |
| `put(key, value)` | Caches feed |
| `remove(key)` | Removes cached feed |
| `clear()` | Clears all cached feeds |
| `generateKey(feedRequest)` | Generates cache key |

**Interactions:**
- **FeedServlet**: Uses for caching
- **WeblogFeedRequest**: Generates keys from

---

### 63. SiteWideCache

**Package:** `org.apache.roller.weblogger.ui.rendering.util`

**Role:** Site-wide cache with invalidation support.

**Description:**
`SiteWideCache` provides site-level caching with automatic invalidation when content changes. It implements `CacheHandler` to receive invalidation events.

**Implements:** `CacheHandler`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `CACHE_ID` | String | Cache identifier |
| `singletonInstance` | SiteWideCache | Singleton instance |
| `cacheEnabled` | boolean | Cache enabled flag |
| `contentCache` | Cache | Underlying cache |
| `lastUpdateTime` | ExpiringCacheEntry | Last update tracker |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `getInstance()` | Returns singleton instance |
| `get(key)` | Gets cached object |
| `put(key, value)` | Caches object |
| `remove(key)` | Removes cached object |
| `clear()` | Clears all cached objects |
| `getLastModified()` | Returns last modified date |
| `generateKey(pageRequest)` | Generates key from page request |
| `generateKey(feedRequest)` | Generates key from feed request |
| `invalidate(entry)` | Invalidates for entry change |
| `invalidate(website)` | Invalidates for weblog change |
| `invalidate(bookmark)` | Invalidates for bookmark change |
| `invalidate(folder)` | Invalidates for folder change |
| `invalidate(comment)` | Invalidates for comment change |
| `invalidate(user)` | Invalidates for user change |
| `invalidate(category)` | Invalidates for category change |
| `invalidate(template)` | Invalidates for template change |

**Interactions:**
- **CacheHandler**: Implements interface
- **PageServlet**: Uses for caching
- **FeedServlet**: Uses for caching
- **All entity types**: Receives invalidation events

---

### 64. CacheHandler (Interface)

**Package:** `org.apache.roller.weblogger.ui.rendering.util`

**Role:** Interface for cache invalidation handlers.

**Description:**
`CacheHandler` defines the contract for caches that need to be invalidated when content changes.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `invalidate(entry)` | Invalidates for WeblogEntry |
| `invalidate(website)` | Invalidates for Weblog |
| `invalidate(bookmark)` | Invalidates for WeblogBookmark |
| `invalidate(folder)` | Invalidates for WeblogBookmarkFolder |
| `invalidate(comment)` | Invalidates for WeblogEntryComment |
| `invalidate(user)` | Invalidates for User |
| `invalidate(category)` | Invalidates for WeblogCategory |
| `invalidate(template)` | Invalidates for WeblogTemplate |

**Interactions:**
- **SiteWideCache**: Implements interface
- **All entity types**: Triggers invalidation

---

### 65. RequestMapper (Interface)

**Package:** `org.apache.roller.weblogger.ui.rendering.util`

**Role:** Interface for URL request mapping.

**Description:**
`RequestMapper` defines the contract for mapping incoming URLs to appropriate servlets.

**Key Methods:**

| Method | Description |
|--------|-------------|
| `handleRequest(req, res)` | Maps and forwards request |

**Interactions:**
- **WeblogRequestMapper**: Implements interface
- **HttpServletRequest**: Maps requests
- **HttpServletResponse**: Forwards responses

---

### 66. WeblogRequestMapper

**Package:** `org.apache.roller.weblogger.ui.rendering.util`

**Role:** Maps weblog URLs to servlets.

**Description:**
`WeblogRequestMapper` implements `RequestMapper` to parse weblog URLs and forward to appropriate servlets (page, feed, media, search, comment).

**Implements:** `RequestMapper`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `PAGE_SERVLET` | String | Page servlet path |
| `FEED_SERVLET` | String | Feed servlet path |
| `RESOURCE_SERVLET` | String | Resource servlet path |
| `MEDIA_SERVLET` | String | Media servlet path |
| `SEARCH_SERVLET` | String | Search servlet path |
| `RSD_SERVLET` | String | RSD servlet path |
| `COMMENT_SERVLET` | String | Comment servlet path |
| `TRACKBACK_SERVLET` | String | Trackback servlet path |
| `restricted` | Set&lt;String&gt; | Restricted handles |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `WeblogRequestMapper()` | Default constructor |

**Key Methods:**

| Method | Description |
|--------|-------------|
| `handleRequest(request, response)` | Maps and forwards request |
| `calculateForwardUrl(request, handle, locale, context, data)` | Calculates forward URL |
| `isWeblog(potentialHandle)` | Checks if handle is a weblog |
| `isLocale(potentialLocale)` | Validates locale string |

**Interactions:**
- **RequestMapper**: Implements interface
- **All servlets**: Forwards to
- **Weblog**: Validates handles

---

## Relationship Diagrams

### Inheritance Relationships

| Child Class | Parent Class/Interface | Relationship |
|-------------|------------------------|--------------|
| JPAWebloggerImpl | Weblogger | implements |
| JPAWeblogManagerImpl | WeblogManager | implements |
| JPAWeblogEntryManagerImpl | WeblogEntryManager | implements |
| JPAMediaFileManagerImpl | MediaFileManager | implements |
| FileContentManagerImpl | FileContentManager | implements |
| ThemeManagerImpl | ThemeManager | implements |
| VelocityRenderer | Renderer | implements |
| VelocityRendererFactory | RendererFactory | implements |
| AbstractURLStrategy | URLStrategy | implements |
| MultiWeblogURLStrategy | AbstractURLStrategy | extends |
| PreviewURLStrategy | MultiWeblogURLStrategy | extends |
| WeblogTemplate | ThemeTemplate | implements |
| ThemeTemplate | Template | extends |
| CustomTemplateRendition | TemplateRendition | implements |
| SiteWideCache | CacheHandler | implements |
| WeblogRequestMapper | RequestMapper | implements |
| WeblogPageRequest | WeblogRequest | extends |
| WeblogFeedRequest | WeblogRequest | extends |
| WeblogCommentRequest | WeblogRequest | extends |
| PageServlet | HttpServlet | extends |
| FeedServlet | HttpServlet | extends |
| SearchServlet | HttpServlet | extends |
| MediaResourceServlet | HttpServlet | extends |
| CommentServlet | HttpServlet | extends |

### Composition Relationships

| Container | Component | Relationship |
|-----------|-----------|--------------|
| Weblog | WeblogCategory | owns (1 to many) |
| Weblog | WeblogEntry | owns (1 to many) |
| Weblog | WeblogBookmarkFolder | owns (1 to many) |
| Weblog | MediaFileDirectory | owns (1 to many) |
| Weblog | WeblogTemplate | owns (1 to many) |
| WeblogEntry | WeblogEntryTag | owns (1 to many) |
| WeblogEntry | WeblogEntryComment | owns (1 to many) |
| WeblogEntry | WeblogEntryAttribute | owns (1 to many) |
| WeblogBookmarkFolder | WeblogBookmark | contains (1 to many) |
| MediaFileDirectory | MediaFile | contains (1 to many) |
| MediaFile | MediaFileTag | has (1 to many) |
| WeblogTemplate | CustomTemplateRendition | has (1 to many) |

### Dependency Relationships

| Source | Target | Relationship |
|--------|--------|--------------|
| Weblogger | All Managers | provides access |
| WeblogManager | Weblog | manages CRUD |
| WeblogEntryManager | WeblogEntry | manages CRUD |
| WeblogEntryManager | WeblogCategory | manages |
| WeblogEntryManager | WeblogEntryComment | manages |
| MediaFileManager | MediaFile | manages |
| MediaFileManager | MediaFileDirectory | manages |
| FileContentManager | FileContent | manages |
| BookmarkManager | WeblogBookmarkFolder | manages |
| BookmarkManager | WeblogBookmark | manages |
| PageServlet | WeblogPageRequest | parses |
| PageServlet | RendererManager | uses |
| PageServlet | ModelLoader | uses |
| FeedServlet | WeblogFeedRequest | parses |
| CommentServlet | CommentValidationManager | uses |
| CommentServlet | CommentAuthenticator | uses |
| RendererManager | RendererFactory | uses |
| SiteWideCache | All Entities | invalidates for |

---

## Class Summary Table

| # | Class/Interface | Package | Type | Description |
|---|-----------------|---------|------|-------------|
| 1 | Weblog | pojos | Class | Core weblog entity |
| 2 | WeblogEntry | pojos | Class | Blog post entity |
| 3 | WeblogCategory | pojos | Class | Entry category |
| 4 | WeblogEntryComment | pojos | Class | Comment on entry |
| 5 | WeblogEntryTag | pojos | Class | Tag on entry |
| 6 | WeblogEntryAttribute | pojos | Class | Custom entry attribute |
| 7 | WeblogBookmarkFolder | pojos | Class | Bookmark folder |
| 8 | WeblogBookmark | pojos | Class | Bookmark/link |
| 9 | MediaFileDirectory | pojos | Class | Media file directory |
| 10 | MediaFile | pojos | Class | Uploaded media file |
| 11 | MediaFileTag | pojos | Class | Tag on media file |
| 12 | FileContent | pojos | Class | File content wrapper |
| 13 | WeblogTemplate | pojos | Class | Custom template |
| 14 | CustomTemplateRendition | pojos | Class | Template rendition |
| 15 | WeblogEntrySearchCriteria | pojos | Class | Entry search criteria |
| 16 | CommentSearchCriteria | pojos | Class | Comment search criteria |
| 17 | MediaFileFilter | pojos | Class | Media file search filter |
| 18 | TagStat | pojos | Class | Tag statistics |
| 19 | WeblogHitCount | pojos | Class | Hit count tracker |
| 20 | StatCount | pojos | Class | Generic statistics |
| 21 | Weblogger | business | Interface | Main business facade |
| 22 | WeblogManager | business | Interface | Weblog management service |
| 23 | WeblogEntryManager | business | Interface | Entry management service |
| 24 | MediaFileManager | business | Interface | Media file service |
| 25 | FileContentManager | business | Interface | File storage service |
| 26 | ThemeManager | business | Interface | Theme management service |
| 27 | BookmarkManager | business | Interface | Bookmark service |
| 28 | URLStrategy | business | Interface | URL generation strategy |
| 29 | Template | pojos | Interface | Base template interface |
| 30 | ThemeTemplate | pojos | Interface | Theme template interface |
| 31 | TemplateRendition | pojos | Interface | Template rendition interface |
| 32 | JPAWebloggerImpl | business.jpa | Class | Weblogger implementation |
| 33 | JPAWeblogManagerImpl | business.jpa | Class | WeblogManager implementation |
| 34 | JPAWeblogEntryManagerImpl | business.jpa | Class | WeblogEntryManager implementation |
| 35 | JPAMediaFileManagerImpl | business.jpa | Class | MediaFileManager implementation |
| 36 | FileContentManagerImpl | business.jpa | Class | FileContentManager implementation |
| 37 | ThemeManagerImpl | business.jpa | Class | ThemeManager implementation |
| 38 | JPAPersistenceStrategy | business.jpa | Class | JPA operations wrapper |
| 39 | AbstractURLStrategy | business | Abstract Class | Base URL strategy |
| 40 | MultiWeblogURLStrategy | business | Class | Multi-weblog URL strategy |
| 41 | PreviewURLStrategy | business | Class | Preview URL strategy |
| 42 | RendererManager | ui.rendering | Class | Rendering coordinator |
| 43 | Renderer | ui.rendering | Interface | Template renderer |
| 44 | RendererFactory | ui.rendering | Interface | Renderer factory |
| 45 | VelocityRenderer | ui.rendering | Class | Velocity renderer |
| 46 | VelocityRendererFactory | ui.rendering | Class | Velocity renderer factory |
| 47 | ModelLoader | ui.rendering | Class | Model data loader |
| 48 | CommentAuthenticator | ui.rendering | Interface | Comment authentication |
| 49 | CommentValidationManager | ui.rendering | Class | Comment validation coordinator |
| 50 | CommentValidator | ui.rendering | Interface | Comment validator |
| 51 | RollerMessages | ui.rendering | Class | Validation messages container |
| 52 | PageServlet | ui.rendering.servlets | Class | Page rendering servlet |
| 53 | FeedServlet | ui.rendering.servlets | Class | Feed generation servlet |
| 54 | SearchServlet | ui.rendering.servlets | Class | Search handling servlet |
| 55 | MediaResourceServlet | ui.rendering.servlets | Class | Media serving servlet |
| 56 | CommentServlet | ui.rendering.servlets | Class | Comment handling servlet |
| 57 | WeblogRequest | ui.rendering.util | Abstract Class | Base request parser |
| 58 | WeblogPageRequest | ui.rendering.util | Class | Page request parser |
| 59 | WeblogFeedRequest | ui.rendering.util | Class | Feed request parser |
| 60 | WeblogCommentRequest | ui.rendering.util | Class | Comment request parser |
| 61 | WeblogPageCache | ui.rendering.util | Class | Page cache |
| 62 | WeblogFeedCache | ui.rendering.util | Class | Feed cache |
| 63 | SiteWideCache | ui.rendering.util | Class | Site-wide cache |
| 64 | CacheHandler | ui.rendering.util | Interface | Cache invalidation handler |
| 65 | RequestMapper | ui.rendering.util | Interface | URL request mapper |
| 66 | WeblogRequestMapper | ui.rendering.util | Class | Weblog URL mapper |


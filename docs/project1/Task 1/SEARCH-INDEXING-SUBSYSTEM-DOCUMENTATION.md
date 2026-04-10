# Apache Roller - Search and Indexing Subsystem Documentation

## Overview

The **Search and Indexing Subsystem** is a core component of Apache Roller that provides full-text search capabilities for weblog content. Built on Apache Lucene, this subsystem handles:

- **Index management**: Creation, maintenance, and optimization of search indices
- **Content indexing**: Automatic indexing of weblog entries and comments
- **Search execution**: Full-text search across weblog content with filtering options
- **Concurrency control**: Thread-safe read/write operations on the index
- **Search UI**: Request handling, result rendering, and pagination

This subsystem follows a layered architecture pattern with clear separation between business logic (indexing operations), presentation (search servlet and models) and utility components.

---

## Package Structure

| Package | Purpose |
|---------|---------|
| `org.apache.roller.weblogger.business.search` | Business service interface and result containers |
| `org.apache.roller.weblogger.business.search.lucene` | Lucene-based implementation of search functionality |
| `org.apache.roller.weblogger.ui.rendering.servlets` | Search request handling servlet |
| `org.apache.roller.weblogger.ui.rendering.util` | Request parsing and validation utilities |
| `org.apache.roller.weblogger.ui.rendering.model` | Search results rendering model |
| `org.apache.roller.weblogger.ui.rendering.pagers` | Pagination support for search results |

---

## Architecture Diagram

```

                              UI LAYER                                        

                                                                              
               
  SearchServlet >WeblogSearchRequest      SearchResultsModel       
               
                                                                            
                                                                            
                                                    
                                               SearchResultsPager         
                                                    

                                BUSINESS LAYER                               

                                                                             
                                                          
    IndexManager     Interface                                      
     (Interface)                                                            
                                                          
                                                                             
                                                                             
                               
  LuceneIndexManager  >   IndexOperation                        
                 (Abstract)                          
                                                    
                                                                            
                                                    
                                                     
   SearchResultList                   
   SearchResultMap       WriteToIndex     ReadFromIndex               
       Operation        Operation                 
                                        
                                                                           
                                               
                                                                         
                                        
                 Add        Remove    ReIndex                        
                Entry       Entry      Entry                         
                                        
                                                                          
                                       
               Rebuild     Remove               Search                
               Website     Website              Operation             
                Index       Index                          
                                                     
                                                                              

```

---

## Business Layer - Interface

### 1. IndexManager (Interface)

**Package:** `org.apache.roller.weblogger.business.search`

**Role:** Primary business service interface for search and indexing operations.

**Description:**
The `IndexManager` interface defines the contract for all search and indexing operations in Apache Roller. It follows the Service Layer pattern, providing a clean API that abstracts away the underlying search engine implementation (Lucene). This interface is the main entry point for all search-related functionality throughout the application.

**Methods:**

| Method | Description |
|--------|-------------|
| `initialize()` | Initializes the search system, creates index if needed |
| `shutdown()` | Gracefully shuts down the search system |
| `release()` | Releases all resources associated with the Roller session |
| `isInconsistentAtStartup()` | Returns true if index needs to be rebuilt |
| `addEntryIndexOperation(entry: WeblogEntry)` | Adds a new entry to the index (background) |
| `addEntryReIndexOperation(entry: WeblogEntry)` | Re-indexes an existing entry (background) |
| `rebuildWeblogIndex(weblog: Weblog)` | Rebuilds index for a specific weblog (background) |
| `rebuildWeblogIndex()` | Rebuilds the entire search index (background) |
| `removeWeblogIndex(weblog: Weblog)` | Removes all entries for a weblog from index |
| `removeEntryIndexOperation(entry: WeblogEntry)` | Removes an entry from the index |
| `search(term, weblogHandle, category, locale, pageNum, entryCount, urlStrategy)` | Executes a search query and returns results |

**Interactions:**
- **LuceneIndexManager**: Implementation class
- **SearchServlet**: Uses for handling search requests (via WebloggerFactory)
- **SearchResultsModel**: Uses for retrieving search results
- **WeblogEntryManager**: Calls indexing methods when entries are modified

---

## Business Layer - Data Transfer Objects

### 2. SearchResultList

**Package:** `org.apache.roller.weblogger.business.search`

**Role:** Data transfer object containing search results as a list.

**Description:**
`SearchResultList` encapsulates the results of a search operation along with pagination metadata. It provides a clean way to transfer search results from the business layer to the presentation layer. Results are returned as a flat list of `WeblogEntryWrapper` objects.

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `limit` | int | Maximum number of results returned |
| `offset` | int | Starting position in result set |
| `categories` | Set&lt;String&gt; | Categories found in results |
| `results` | List&lt;WeblogEntryWrapper&gt; | List of matching weblog entries |

**Constructor:**

| Constructor | Description |
|-------------|-------------|
| `SearchResultList(results: List, categories: Set, limit: int, offset: int)` | Creates a new SearchResultList with the specified results and metadata |

**Methods:**

| Method | Description |
|--------|-------------|
| `getLimit()` | Returns the result limit |
| `getOffset()` | Returns the result offset |
| `getResults()` | Returns the list of matching entries |
| `getCategories()` | Returns categories found in results |

**Interactions:**
- **LuceneIndexManager**: Creates instances when returning search results
- **SearchResultsModel**: Consumes to populate the rendering model

---

## Business Layer - Lucene Implementation

### 3. LuceneIndexManager

**Package:** `org.apache.roller.weblogger.business.search.lucene`

**Role:** Lucene-based implementation of the IndexManager interface.

**Description:**
`LuceneIndexManager` is the central entry point into the Lucene searching API. It manages the search index lifecycle, schedules index operations and provides thread-safe access to the index through read-write locks.

**Implements:** `IndexManager`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `reader` | IndexReader | Shared Lucene index reader |
| `roller` | Weblogger | Reference to the main Weblogger instance |
| `searchEnabled` | boolean | Flag indicating if search is enabled |
| `indexDir` | String | Path to the index directory |
| `indexConsistencyMarker` | File | Marker file for index consistency |
| `inconsistentAtStartup` | boolean | Flag indicating if rebuild is needed |
| `rwl` | ReadWriteLock | Lock for thread-safe index access |

**Constructor:**

| Constructor | Description |
|-------------|-------------|
| `LuceneIndexManager(roller: Weblogger)` | Creates a new LuceneIndexManager with the given Weblogger instance |

**Public Methods:**

| Method | Description |
|--------|-------------|
| `initialize()` | Sets up index, checks consistency, triggers rebuild if needed |
| `shutdown()` | Closes reader, removes consistency marker |
| `release()` | Releases all resources associated with the Roller session |
| `isInconsistentAtStartup()` | Returns true if index needs to be rebuilt |
| `rebuildWeblogIndex()` | Schedules full index rebuild in background |
| `rebuildWeblogIndex(weblog)` | Schedules weblog-specific rebuild |
| `removeWeblogIndex(weblog)` | Schedules removal of weblog entries from index |
| `addEntryIndexOperation(entry)` | Schedules addition of entry to index |
| `addEntryReIndexOperation(entry)` | Schedules re-indexing of entry |
| `removeEntryIndexOperation(entry)` | Immediately removes entry from index |
| `search(term, weblogHandle, category, locale, pageNum, entryCount, urlStrategy)` | Executes search and returns results |
| `getIndexDirectory()` | Returns the Lucene directory instance |
| `getReadWriteLock()` | Returns the read-write lock for operations |
| `getSharedIndexReader()` | Returns shared IndexReader, creating if needed |
| `resetSharedReader()` | Invalidates the cached IndexReader |
| `getAnalyzer()` | Returns the configured Lucene analyzer (static) |

**Private Methods:**

| Method | Description |
|--------|-------------|
| `scheduleIndexOperation(op)` | Schedules an index operation for background execution |
| `indexExists()` | Checks if the Lucene index exists at the configured directory |
| `createIndex(dir)` | Creates a new empty Lucene index at the specified directory |
| `deleteIndex()` | Deletes all files in the index directory |

**Index Lifecycle:**

```

                        INITIALIZATION                               

  1. Check if search is enabled in configuration                     
  2. Determine index directory path                                  
  3. Check for inconsistency marker file                             
     - If exists: delete index, mark inconsistent                    
  4. Create index directory if not exists                            
  5. Try to open existing index                                      
     - If fails: mark inconsistent, delete, recreate                 
  6. If inconsistent: schedule background rebuild                    
  7. Create consistency marker file                                  



                          SHUTDOWN                                   

  1. Delete consistency marker (indicates clean shutdown)            
  2. Close IndexReader if open                                       

```

**Configuration Properties:**

| Property | Description |
|----------|-------------|
| `search.enabled` | Enable/disable search functionality |
| `search.index.dir` | Directory path for storing index |
| `lucene.analyzer.class` | Custom analyzer class name |
| `lucene.analyzer.maxTokenCount` | Maximum tokens per document |
| `search.index.comments` | Whether to index comment content |

**Interactions:**
- **IndexManager**: Implements this interface
- **IndexOperation**: Schedules various operations
- **SearchResultList**: Returns search results
- **FieldConstants**: Uses for field names
- **Weblogger**: Uses for accessing other managers
- **WebloggerConfig**: Reads configuration properties
- **ThreadManager**: Executes operations in background/foreground

---

### 4. IndexOperation (Abstract)

**Package:** `org.apache.roller.weblogger.business.search.lucene`

**Role:** Base class for all index operations.

**Description:**
`IndexOperation` is the abstract base class for all indexing operations. It implements `Runnable` for execution by the thread manager and provides common functionality for document creation and index writing.

**Implements:** `Runnable`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `manager` | LuceneIndexManager | Reference to the index manager |
| `writer` | IndexWriter | Lucene IndexWriter for modifications |

**Constructor:**

| Constructor | Description |
|-------------|-------------|
| `IndexOperation(manager: LuceneIndexManager)` | Creates a new IndexOperation with the given manager |

**Methods:**

| Method | Description |
|--------|-------------|
| `getDocument(data)` | Creates a Lucene Document from a WeblogEntry |
| `beginWriting()` | Opens an IndexWriter for modifications |
| `endWriting()` | Closes the IndexWriter |
| `run()` | Implements Runnable, calls doRun() |
| `doRun()` | Abstract method for operation-specific logic |

**Document Structure:**
The `getDocument()` method creates a Lucene Document with these fields:

| Field | Type | Stored | Description |
|-------|------|--------|-------------|
| `id` | StringField | Yes | Entry unique identifier |
| `handle` | StringField | Yes | Weblog handle |
| `username` | TextField | Yes | Creator's username |
| `title` | TextField | Yes | Entry title |
| `locale` | StringField | Yes | Entry locale |
| `content` | TextField | No | Entry text content |
| `updated` | StringField | Yes | Last update timestamp |
| `published` | SortedDocValuesField | - | Publication date (for sorting) |
| `cat` | StringField | Yes | Category name |
| `comment` | TextField | No | Comment content (if enabled) |
| `email` | StringField | Yes | Commenter emails |
| `name` | StringField | Yes | Commenter names |

**Inheritance Hierarchy:**
```
IndexOperation (abstract) implements Runnable
    
     WriteToIndexOperation (abstract)
           
            AddEntryOperation
            RemoveEntryOperation
            ReIndexEntryOperation
            RebuildWebsiteIndexOperation
            RemoveWebsiteIndexOperation
    
     ReadFromIndexOperation (abstract)
            
             SearchOperation
```

**Interactions:**
- **LuceneIndexManager**: References for index access
- **WriteToIndexOperation**: Extends for write operations
- **ReadFromIndexOperation**: Extends for read operations
- **WeblogEntry**: Creates documents from entries

---

### 5. WriteToIndexOperation (Abstract)

**Package:** `org.apache.roller.weblogger.business.search.lucene`

**Role:** Base class for operations that modify the index.

**Description:**
`WriteToIndexOperation` extends `IndexOperation` to provide write-specific behavior. It acquires an exclusive write lock before executing the operation and resets the shared reader afterward to ensure subsequent reads see the changes.

**Extends:** `IndexOperation`

**Constructor:**

| Constructor | Description |
|-------------|-------------|
| `WriteToIndexOperation(mgr: LuceneIndexManager)` | Creates a new WriteToIndexOperation with the given manager |

**Methods:**

| Method | Description |
|--------|-------------|
| `run()` | Acquires write lock, calls doRun(), releases lock, resets reader |

**Interactions:**
- **IndexOperation**: Extends for common functionality
- **LuceneIndexManager**: Acquires write lock, resets reader
- **AddEntryOperation, RemoveEntryOperation, etc.**: Extend this class

---

### 6. ReadFromIndexOperation (Abstract)

**Package:** `org.apache.roller.weblogger.business.search.lucene`

**Role:** Base class for operations that read from the index.

**Description:**
`ReadFromIndexOperation` extends `IndexOperation` to provide read-specific behavior. It acquires a shared read lock, allowing multiple concurrent reads while blocking writes.

**Extends:** `IndexOperation`

**Constructor:**

| Constructor | Description |
|-------------|-------------|
| `ReadFromIndexOperation(mgr: LuceneIndexManager)` | Creates a new ReadFromIndexOperation with the given manager |

**Methods:**

| Method | Description |
|--------|-------------|
| `run()` | Acquires read lock, calls doRun(), releases lock |


**Interactions:**
- **IndexOperation**: Extends for common functionality
- **LuceneIndexManager**: Acquires read lock
- **SearchOperation**: Extends this class

---

### 7. AddEntryOperation

**Package:** `org.apache.roller.weblogger.business.search.lucene`

**Role:** Operation to add a new entry to the search index.

**Description:**
`AddEntryOperation` adds a single weblog entry to the Lucene index. It extends `WriteToIndexOperation` for proper locking semantics. The operation re-queries the entry from the database to avoid lazy initialization issues when run on a background thread.

**Extends:** `WriteToIndexOperation`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `data` | WeblogEntry | The entry to index |
| `roller` | Weblogger | Reference to Weblogger instance |

**Constructor:**

| Constructor | Description |
|-------------|-------------|
| `AddEntryOperation(roller: Weblogger, mgr: LuceneIndexManager, data: WeblogEntry)` | Creates a new AddEntryOperation for the given entry |

**Methods:**

| Method | Description |
|--------|-------------|
| `doRun()` | Adds the entry document to the index |

**Operation Flow:**

```
1. Acquire write lock (inherited)
2. Re-query WeblogEntry from database
3. Begin writing (open IndexWriter)
4. Create Document from entry
5. Add document to index
6. End writing (close IndexWriter)
7. Release resources
8. Release write lock (inherited)
9. Reset shared reader (inherited)
```

**Interactions:**
- **WriteToIndexOperation**: Extends for write semantics
- **WeblogEntry**: Source of data to index (indexes)
- **WeblogEntryManager**: Re-queries entry

---

### 8. RemoveEntryOperation

**Package:** `org.apache.roller.weblogger.business.search.lucene`

**Role:** Operation to remove an entry from the search index.

**Description:**
`RemoveEntryOperation` removes a single weblog entry from the Lucene index by its unique ID. This operation is typically executed immediately (not in background) to ensure consistency when entries are deleted.

**Extends:** `WriteToIndexOperation`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `data` | WeblogEntry | The entry to remove |
| `roller` | Weblogger | Reference to Weblogger instance |

**Constructor:**

| Constructor | Description |
|-------------|-------------|
| `RemoveEntryOperation(roller: Weblogger, mgr: LuceneIndexManager, data: WeblogEntry)` | Creates a new RemoveEntryOperation for the given entry |

**Methods:**

| Method | Description |
|--------|-------------|
| `doRun()` | Removes the entry document from the index |

**Operation Flow:**

```
1. Acquire write lock (inherited)
2. Re-query WeblogEntry from database
3. Begin writing (open IndexWriter)
4. Create Term for entry ID
5. Delete documents matching term
6. End writing (close IndexWriter)
7. Release write lock (inherited)
8. Reset shared reader (inherited)
```

**Interactions:**
- **WriteToIndexOperation**: Extends for write semantics
- **WeblogEntry**: Source of ID for deletion
- **FieldConstants**: Uses ID field name

---

### 9. ReIndexEntryOperation

**Package:** `org.apache.roller.weblogger.business.search.lucene`

**Role:** Operation to update an existing entry in the search index.

**Description:**
`ReIndexEntryOperation` updates a weblog entry in the index by first removing the old document and then adding the updated version. This atomic delete-then-add approach ensures index consistency.

**Extends:** `WriteToIndexOperation`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `data` | WeblogEntry | The entry to re-index |
| `roller` | Weblogger | Reference to Weblogger instance |

**Constructor:**

| Constructor | Description |
|-------------|-------------|
| `ReIndexEntryOperation(roller: Weblogger, mgr: LuceneIndexManager, data: WeblogEntry)` | Creates a new ReIndexEntryOperation for the given entry |

**Methods:**

| Method | Description |
|--------|-------------|
| `doRun()` | Removes old document and adds updated document |

**Operation Flow:**

```
1. Acquire write lock (inherited)
2. Re-query WeblogEntry from database
3. Begin writing (open IndexWriter)
4. Create Term for entry ID
5. Delete existing documents
6. Create new Document from entry
7. Add document to index
8. End writing (close IndexWriter)
9. Release resources
10. Release write lock (inherited)
11. Reset shared reader (inherited)
```

**Interactions:**
- **WriteToIndexOperation**: Extends for write semantics
- **WeblogEntry**: Source of data
- **FieldConstants**: Uses ID field name

---

### 10. RebuildWebsiteIndexOperation

**Package:** `org.apache.roller.weblogger.business.search.lucene`

**Role:** Operation to rebuild the index for a weblog or the entire site.

**Description:**
`RebuildWebsiteIndexOperation` performs a complete rebuild of the search index. It can rebuild the index for a specific weblog or for all weblogs in the system. The operation deletes existing index entries and re-indexes all published entries.

**Extends:** `WriteToIndexOperation`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `weblog` | Weblog | Target weblog (null for all) |
| `roller` | Weblogger | Reference to Weblogger instance |

**Constructor:**

| Constructor | Description |
|-------------|-------------|
| `RebuildWebsiteIndexOperation(roller: Weblogger, mgr: LuceneIndexManager, weblog: Weblog)` | Creates a new RebuildWebsiteIndexOperation for the given weblog (or all if null) |

**Methods:**

| Method | Description |
|--------|-------------|
| `doRun()` | Deletes existing entries and re-indexes all published entries |

**Operation Flow:**

```
For specific weblog:
1. Acquire write lock (inherited)
2. Re-query Weblog from database
3. Begin writing (open IndexWriter)
4. Delete all documents matching weblog handle
5. Query all PUBLISHED entries for weblog
6. For each entry: add document to index
7. End writing (close IndexWriter)
8. Release resources
9. Release write lock (inherited)
10. Reset shared reader (inherited)

For all weblogs:
- Similar flow but deletes ALL documents using constant term
- Queries all PUBLISHED entries across system
```

**Interactions:**
- **WriteToIndexOperation**: Extends for write semantics
- **Weblog**: Source of weblog data
- **WeblogEntryManager**: Queries published entries
- **IndexUtil**: Uses getTerm() for deletion terms

---

### 11. RemoveWebsiteIndexOperation

**Package:** `org.apache.roller.weblogger.business.search.lucene`

**Role:** Operation to remove all index entries for a weblog.

**Description:**
`RemoveWebsiteIndexOperation` removes all indexed entries belonging to a specific weblog. This is typically called when a weblog is being deleted from the system.

**Extends:** `WriteToIndexOperation`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `weblog` | Weblog | Target weblog to remove |
| `roller` | Weblogger | Reference to Weblogger instance |

**Constructor:**

| Constructor | Description |
|-------------|-------------|
| `RemoveWebsiteIndexOperation(roller: Weblogger, mgr: LuceneIndexManager, weblog: Weblog)` | Creates a new RemoveWebsiteIndexOperation for the given weblog |

**Methods:**

| Method | Description |
|--------|-------------|
| `doRun()` | Removes all documents for the weblog |

**Operation Flow:**

```
1. Acquire write lock (inherited)
2. Re-query Weblog from database
3. Begin writing (open IndexWriter)
4. Create Term for weblog handle
5. Delete all documents matching handle
6. End writing (close IndexWriter)
7. Release write lock (inherited)
8. Reset shared reader (inherited)
```

**Interactions:**
- **WriteToIndexOperation**: Extends for write semantics
- **Weblog**: Source of handle for deletion
- **IndexUtil**: Uses getTerm() for deletion terms

---

### 12. SearchOperation

**Package:** `org.apache.roller.weblogger.business.search.lucene`

**Role:** Operation to execute a search query against the index.

**Description:**
`SearchOperation` performs full-text searches against the Lucene index. It supports filtering by weblog handle, category, and locale. Results are sorted by publication date (newest first) and returned as `TopFieldDocs`.

**Extends:** `ReadFromIndexOperation`

**Static Constants:**

| Constant | Type | Value | Description |
|----------|------|-------|-------------|
| `SEARCH_FIELDS` | String[] | ["content", "title", "comment"] | Fields to search |
| `SORTER` | Sort | Sort by published DESC | Result ordering |

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `searcher` | IndexSearcher | Lucene searcher instance |
| `searchresults` | TopFieldDocs | Search results |
| `term` | String | Search query string |
| `weblogHandle` | String | Filter by weblog (optional) |
| `category` | String | Filter by category (optional) |
| `locale` | String | Filter by locale (optional) |
| `parseError` | String | Error message if parsing fails |

**Constructor:**

| Constructor | Description |
|-------------|-------------|
| `SearchOperation(mgr: IndexManager)` | Creates a new SearchOperation with the given manager |

**Methods:**

| Method | Description |
|--------|-------------|
| `getResultsCount()` | Returns total hit count |
| `getResults()` | Returns search results |
| `getParseError()` | Returns parse error message |
| `doRun()` | Executes the search |

**Search Algorithm:**

```
1. Acquire read lock (inherited)
2. Get shared IndexReader from manager
3. Create IndexSearcher from reader
4. Create MultiFieldQueryParser for search fields
5. Parse search term into Query
6. If weblog filter set: wrap in BooleanQuery with MUST clause
7. If category filter set: wrap in BooleanQuery with MUST clause
8. If locale filter set: wrap in BooleanQuery with MUST clause
9. Execute search with 500 document limit, sorted by date
10. Store results in searchresults field
11. Release read lock (inherited)
```

**Interactions:**
- **ReadFromIndexOperation**: Extends for read semantics
- **LuceneIndexManager**: Gets shared reader
- **FieldConstants**: Uses field names
- **IndexUtil**: Uses getTerm() for filter terms

---

### 13. FieldConstants

**Package:** `org.apache.roller.weblogger.business.search.lucene`

**Role:** Constants for Lucene document field names.

**Description:**
`FieldConstants` provides string constants for all field names used in Lucene documents. Using constants ensures consistency between indexing and searching operations.

**Constants:**

| Constant | Type | Value | Description |
|----------|------|-------|-------------|
| `ANCHOR` | String | "anchor" | Entry anchor/slug |
| `UPDATED` | String | "updated" | Last update timestamp |
| `ID` | String | "id" | Unique entry identifier |
| `USERNAME` | String | "username" | Creator's username |
| `CATEGORY` | String | "cat" | Category name |
| `TITLE` | String | "title" | Entry title |
| `PUBLISHED` | String | "published" | Publication date |
| `CONTENT` | String | "content" | Entry text content |
| `C_CONTENT` | String | "comment" | Comment content |
| `C_EMAIL` | String | "email" | Commenter email |
| `C_NAME` | String | "name" | Commenter name |
| `WEBSITE_HANDLE` | String | "handle" | Weblog handle |
| `LOCALE` | String | "locale" | Entry locale |

**Interactions:**
- **IndexOperation**: Uses for document field names
- **SearchOperation**: Uses for query field names
- **RebuildWebsiteIndexOperation**: Uses for deletion terms
- **RemoveWebsiteIndexOperation**: Uses for deletion terms

---

### 14. IndexUtil (Utility)

**Package:** `org.apache.roller.weblogger.business.search.lucene`

**Role:** Utility class for index-related helper methods.

**Description:**
`IndexUtil` provides static utility methods for common index operations. Its primary function is creating Lucene Term objects from input strings by tokenizing them through the configured analyzer.

**Constructor:**

| Constructor | Description |
|-------------|-------------|
| `IndexUtil()` | Private constructor prevents instantiation |

**Methods:**

| Method | Description |
|--------|-------------|
| `getTerm(field, input)` | Creates a Lucene Term from the first token of input |

**Term Creation Process:**

```
1. Validate field and input are not null
2. Get analyzer from LuceneIndexManager
3. Create TokenStream from input
4. Extract first token
5. Create Term with field name and token value
6. Return Term (or null if no tokens)
```

**Interactions:**
- **SearchOperation**: Uses getTerm() for creating filter terms
- **RebuildWebsiteIndexOperation**: Uses getTerm() for deletion terms
- **RemoveWebsiteIndexOperation**: Uses getTerm() for deletion terms
- **LuceneIndexManager**: Gets analyzer instance

---

## UI Layer - Servlets

### 15. SearchServlet

**Package:** `org.apache.roller.weblogger.ui.rendering.servlets`

**Role:** HTTP servlet that handles search requests.

**Description:**
`SearchServlet` is the entry point for all search requests in Apache Roller. It processes HTTP GET requests, validates the search parameters, loads the appropriate rendering models, and renders the search results page using the weblog's theme template.

**Extends:** `HttpServlet`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `themeReload` | Boolean | Development mode theme reloading flag |

**Methods:**

| Method | Description |
|--------|-------------|
| `init(config)` | Initializes servlet, reads theme reload config |
| `doGet(request, response)` | Handles search requests |

**Request Processing Flow:**

```
1. Parse WeblogSearchRequest from HTTP request
2. Validate weblog exists
3. Handle theme reloading (development mode)
4. Apply locale restrictions if needed
5. Look up search template (or fall back to default)
6. Set content type to text/html
7. Build initialization data for models
8. Load rendering models (SearchResultsModel, etc.)
9. Look up appropriate renderer
10. Render template with model
11. Write output to response
```

**Interactions:**
- **WeblogSearchRequest**: Creates to parse request parameters
- **IndexManager**: Uses for search (via WebloggerFactory)
- **WebloggerFactory**: Gets weblog and theme manager
- **ModelLoader**: Loads rendering models
- **RendererManager**: Gets appropriate renderer
- **SearchResultsModel**: Provides search data to template

---

## UI Layer - Request

### 16. WeblogSearchRequest

**Package:** `org.apache.roller.weblogger.ui.rendering.util`

**Role:** Request object representing a weblog search request.

**Description:**
`WeblogSearchRequest` extends `WeblogRequest` to add search-specific parameters. It parses and validates search request parameters from the HTTP request, including the query string, page number and category filter.

**Extends:** `WeblogRequest`

**Static Constants:**

| Constant | Type | Value | Description |
|----------|------|-------|-------------|
| `SEARCH_SERVLET` | String | "/roller-ui/rendering/search" | Servlet path |

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `query` | String | Search query string |
| `pageNum` | int | Current page number (0-based) |
| `weblogCategoryName` | String | Category filter name |
| `weblogCategory` | WeblogCategory | Resolved category object |

**Constructors:**

| Constructor | Description |
|-------------|-------------|
| `WeblogSearchRequest()` | Default constructor |
| `WeblogSearchRequest(request: HttpServletRequest)` | Parses search parameters from HTTP request |


**Request Parsing:**

```
1. Call parent constructor (parses weblog handle, locale)
2. Validate servlet path matches SEARCH_SERVLET
3. Reject any extra path info
4. Parse 'q' parameter for search query
5. Parse 'page' parameter for page number
6. Parse 'cat' parameter for category filter (URL decoded)
```

**Interactions:**
- **WeblogRequest**: Extends for common weblog request handling
- **SearchServlet**: Creates instances to handle requests
- **SearchResultsModel**: Uses for model initialization
- **SearchResultsPager**: Uses for pagination
- **WeblogEntryManager**: Resolves category name to object

---

## UI Layer - Model

### 17. SearchResultsModel

**Package:** `org.apache.roller.weblogger.ui.rendering.model`

**Role:** Rendering model for search results pages.

**Description:**
`SearchResultsModel` extends `PageModel` to provide search-specific data for template rendering. It executes the search operation during initialization and organizes results by date for display.

**Extends:** `PageModel`

**Static Constants:**

| Constant | Type | Value | Description |
|----------|------|-------|-------------|
| `RESULTS_PER_PAGE` | int | 10 | Default number of results per page |

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `searchRequest` | WeblogSearchRequest | Original search request |
| `urlStrategy` | URLStrategy | Strategy for building URLs |
| `results` | Map&lt;Date, Set&lt;WeblogEntryWrapper&gt;&gt; | Results grouped by date |
| `pager` | SearchResultsPager | Pager for navigation |
| `hits` | int | Number of results returned |
| `offset` | int | Current result offset |
| `limit` | int | Result limit |
| `categories` | Set&lt;String&gt; | Categories in results |
| `errorMessage` | String | Error message if search failed |

**Methods:**

| Method | Description |
|--------|-------------|
| `init(initData)` | Initializes model and executes search |
| `getSearchRequest()` | Returns the search request |
| `getUrlStrategy()` | Returns the URL strategy |
| `getResults()` | Returns results map |
| `getPager()` | Returns the pager |
| `getWeblogEntriesPager()` | Returns SearchResultsPager |
| `getWeblogEntriesPager(category)` | Returns SearchResultsPager (category ignored) |
| `getTerm()` | Returns HTML-escaped search term |
| `getRawTerm()` | Returns unescaped search term |
| `getHits()` | Returns hit count |
| `getResultsCount()` | Returns result count |
| `getOffset()` | Returns current offset |
| `getLimit()` | Returns result limit |
| `getCategories()` | Returns category set |
| `getErrorMessage()` | Returns error message |
| `getWeblogCategoryName()` | Returns category filter name |
| `getWeblogCategory()` | Returns wrapped category object |

**Initialization Flow:**

```
1. Extract searchRequest from initData
2. Extract or create urlStrategy
3. Call parent init()
4. If no query: create empty pager and return
5. Get IndexManager from WebloggerFactory
6. Execute search with parameters:
   - query, weblogHandle, category, locale
   - pageNum, RESULTS_PER_PAGE, urlStrategy
7. Process results:
   - Store hits, offset, limit, categories
   - Group entries by date (midnight)
   - Filter out future posts
8. Create SearchResultsPager with results and hasMore flag
```

**Result Grouping:**
Results are organized into a `Map<Date, Set<WeblogEntryWrapper>>` where:
- Keys are dates (midnight of publication day)
- Values are Sets of entries (sorted by pubTime, deduplicated)

**Interactions:**
- **PageModel**: Extends for base page functionality
- **WeblogSearchRequest**: Uses for search parameters
- **IndexManager**: Uses for search operation
- **SearchResultList**: Uses to get search results
- **SearchResultsPager**: Creates for pagination
- **WeblogEntryWrapper**: Wraps result entries

---

## UI Layer - Pager

### 18. WeblogEntriesPager (Interface)

**Package:** `org.apache.roller.weblogger.ui.rendering.pagers`

**Role:** Interface for weblog entry pagination.

**Description:**
`WeblogEntriesPager` defines the contract for paginating weblog entries. It provides methods for retrieving entries and generating navigation links.

**Methods:**

| Method | Description |
|--------|-------------|
| `getEntries()` | Returns entries grouped by date |
| `getHomeLink()` | Returns link to pager home |
| `getHomeName()` | Returns name for home link |
| `getNextLink()` | Returns link to next page |
| `getNextName()` | Returns name for next link |
| `getPrevLink()` | Returns link to previous page |
| `getPrevName()` | Returns name for previous link |

**Interactions:**
- **SearchResultsPager**: Implements for search results
- **SearchResultsModel**: Returns pager instance
- **Template**: Uses for rendering navigation

---

### 19. SearchResultsPager

**Package:** `org.apache.roller.weblogger.ui.rendering.pagers`

**Role:** Pager implementation for navigating search results.

**Description:**
`SearchResultsPager` implements `WeblogEntriesPager` specifically for search results. It generates pagination links that preserve the search query and filters.

**Implements:** `WeblogEntriesPager`

**Attributes:**

| Attribute | Type | Description |
|-----------|------|-------------|
| `messageUtils` | I18nMessages | Internationalization helper |
| `urlStrategy` | URLStrategy | Strategy for building URLs |
| `entries` | Map&lt;Date, Set&lt;WeblogEntryWrapper&gt;&gt; | Search results |
| `weblog` | Weblog | Target weblog |
| `locale` | String | Current locale |
| `query` | String | Search query |
| `category` | String | Category filter |
| `page` | int | Current page number |
| `moreResults` | boolean | Whether more results exist |

**Constructor:**

| Constructor | Description |
|-------------|-------------|
| `SearchResultsPager(strat: URLStrategy, searchRequest: WeblogSearchRequest, entries: Map, more: boolean)` | Creates a new SearchResultsPager with the given parameters |

**Methods:**

| Method | Description |
|--------|-------------|
| `getMessageUtils()` | Returns i18n message utilities |
| `getUrlStrategy()` | Returns URL strategy |
| `getEntries()` | Returns search results map |
| `getWeblog()` | Returns target weblog |
| `getLocale()` | Returns current locale |
| `getQuery()` | Returns search query |
| `getCategory()` | Returns category filter |
| `getPage()` | Returns current page number |
| `hasMoreResults()` | Returns whether more results exist |
| `getHomeLink()` | Returns weblog home URL |
| `getHomeName()` | Returns "Home" (i18n) |
| `getNextLink()` | Returns URL for next page (if more results) |
| `getNextName()` | Returns "Next" (i18n) if has next |
| `getPrevLink()` | Returns URL for previous page (if page > 0) |
| `getPrevName()` | Returns "Previous" (i18n) if has prev |
| `getNextCollectionLink()` | Returns null (not applicable) |
| `getNextCollectionName()` | Returns null (not applicable) |
| `getPrevCollectionLink()` | Returns null (not applicable) |
| `getPrevCollectionName()` | Returns null (not applicable) |



**Interactions:**
- **WeblogEntriesPager**: Implements this interface
- **SearchResultsModel**: Creates instances
- **WeblogSearchRequest**: Uses for search parameters
- **URLStrategy**: Generates navigation URLs
- **I18nMessages**: Provides localized link names

---

## Relationships Summary

### Inheritance Relationships

| Child Class | Parent Class/Interface | Relationship |
|-------------|------------------------|--------------|
| LuceneIndexManager | IndexManager | implements |
| WriteToIndexOperation | IndexOperation | extends |
| ReadFromIndexOperation | IndexOperation | extends |
| AddEntryOperation | WriteToIndexOperation | extends |
| RemoveEntryOperation | WriteToIndexOperation | extends |
| ReIndexEntryOperation | WriteToIndexOperation | extends |
| RebuildWebsiteIndexOperation | WriteToIndexOperation | extends |
| RemoveWebsiteIndexOperation | WriteToIndexOperation | extends |
| SearchOperation | ReadFromIndexOperation | extends |
| SearchResultsPager | WeblogEntriesPager | implements |
| SearchServlet | HttpServlet | extends |
| WeblogSearchRequest | WeblogRequest | extends |
| SearchResultsModel | PageModel | extends |

### Dependency Relationships

| Source Class | Target Class | Relationship |
|--------------|--------------|--------------|
| LuceneIndexManager | IndexOperation | schedules |
| LuceneIndexManager | SearchResultList | returns |
| LuceneIndexManager | FieldConstants | uses |
| IndexOperation | LuceneIndexManager | references |
| AddEntryOperation | WeblogEntry | indexes |
| SearchOperation | FieldConstants | uses |
| SearchOperation | IndexUtil | uses getTerm() |
| RebuildWebsiteIndexOperation | IndexUtil | uses getTerm() |
| RemoveWebsiteIndexOperation | IndexUtil | uses getTerm() |
| SearchServlet | WeblogSearchRequest | creates |
| SearchServlet | IndexManager | uses (via WebloggerFactory) |
| SearchResultsModel | WeblogSearchRequest | uses |
| SearchResultsModel | IndexManager | uses |
| SearchResultsModel | SearchResultsPager | creates |
| SearchResultsModel | SearchResultList | uses |
| SearchResultsPager | WeblogSearchRequest | uses |

---

## Integration Points

### With WeblogEntryManager
- Called when entries are created, updated, or deleted
- Triggers corresponding index operations

### With WeblogManager
- Called when weblogs are deleted
- Triggers removal of weblog from index

### With ThreadManager
- Background operations scheduled via `executeInBackground()`
- Immediate operations via `executeInForeground()`

### With RendererManager
- Search results rendered using theme templates
- Template selection based on weblog theme

---

## Class Summary Table

| # | Class/Interface | Package | Type | Description |
|---|-----------------|---------|------|-------------|
| 1 | IndexManager | business.search | Interface | Primary search/index service interface |
| 2 | SearchResultList | business.search | Class | Search results as list |
| 3 | LuceneIndexManager | business.search.lucene | Class | Lucene-based IndexManager implementation |
| 4 | IndexOperation | business.search.lucene | Abstract Class | Base class for all index operations |
| 5 | WriteToIndexOperation | business.search.lucene | Abstract Class | Base class for write operations |
| 6 | ReadFromIndexOperation | business.search.lucene | Abstract Class | Base class for read operations |
| 7 | AddEntryOperation | business.search.lucene | Class | Adds entry to index |
| 8 | RemoveEntryOperation | business.search.lucene | Class | Removes entry from index |
| 9 | ReIndexEntryOperation | business.search.lucene | Class | Updates entry in index |
| 10 | RebuildWebsiteIndexOperation | business.search.lucene | Class | Rebuilds weblog/site index |
| 11 | RemoveWebsiteIndexOperation | business.search.lucene | Class | Removes weblog from index |
| 12 | SearchOperation | business.search.lucene | Class | Executes search queries |
| 13 | FieldConstants | business.search.lucene | Class | Lucene field name constants |
| 14 | IndexUtil | business.search.lucene | Utility Class | Index helper methods |
| 15 | SearchServlet | ui.rendering.servlets | Class | HTTP search request handler |
| 16 | WeblogSearchRequest | ui.rendering.util | Class | Search request parser |
| 17 | SearchResultsModel | ui.rendering.model | Class | Search results rendering model |
| 18 | WeblogEntriesPager | ui.rendering.pagers | Interface | Entry pagination interface |
| 19 | SearchResultsPager | ui.rendering.pagers | Class | Search results pagination |

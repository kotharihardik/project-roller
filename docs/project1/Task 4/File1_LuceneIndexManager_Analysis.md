# File 1: LuceneIndexManager.java - Agentic Refactoring Analysis

## File Information

| Property | Value |
|----------|-------|
| **File Path** | `app/src/main/java/org/apache/roller/weblogger/business/search/lucene/LuceneIndexManager.java` |
| **Package** | `org.apache.roller.weblogger.business.search.lucene` |
| **Total Lines** | 486 |
| **Subsystem** | Search and Indexing |

---

## Stage 1: Smell Identification

### Prompt Used

You are an expert software architect

**Target File:** LuceneIndexManager.java (486 lines)

**Subsystem:** Search and Indexing

**Context:** Apache Roller - Enterprise Java Blog Platform

**Task 1 - Smell Identification:**
Analyze this file for the following design smells with exact line references:
1. God Class - Does this class have too many responsibilities?
2. Feature Envy - Are there methods that use other classes' data excessively?
3. Long Method - Identify methods exceeding 20 lines with complex logic
4. SOLID Violations - Single Responsibility and OCP (Open-Closed)
5. Tight Coupling - Identify hard dependencies on concrete implementations
6. Improper Exception Handling - Catch-all blocks or exceptions

**Task 2 - Refactoring Planning:**
For each identified smell:
- Propose a specific refactoring technique
- Explain why this refactoring is appropriate
- List affected methods/classes

**Task 3 - Refactoring Execution:**
Provide complete before/after code snippets that:
- Are syntactically correct and compilable
- Preserve original functionality

**Output Format:** Use clear sections with code blocks and line references.


### Identified Design Smells

#### Smell 1: God Class
**Severity:** HIGH  
**Location:** Entire class (Lines 1-486)

**Evidence:**
- The class handles too many responsibilities:
  1. Index creation and deletion (Lines 356-396)
  2. Index reading/writing coordination (Lines 250-290)
  3. Search operations (Lines 195-230)
  4. Index consistency management (Lines 107-165)
  5. Result conversion (Lines 419-486)
- Total method count: 25+ methods
- Multiple instance variables managing different concerns

**Code Sample (Multiple Responsibilities):**
```java
// Responsibility 1: Index lifecycle
private void deleteIndex() { ... }
private void createIndex(Directory dir) { ... }

// Responsibility 2: Search coordination
public SearchResultList search(String term, ...) { ... }

// Responsibility 3: Reader management
public synchronized IndexReader getSharedIndexReader() { ... }

// Responsibility 4: Result conversion
static SearchResultList convertHitsToEntryList(...) { ... }
```

---

#### Smell 2: Feature Envy
**Severity:** MEDIUM  
**Location:** Lines 419-486 (`convertHitsToEntryList` method)

**Evidence:**
- The static method `convertHitsToEntryList` extensively manipulates `WeblogEntryManager` and `WeblogEntry` objects
- Accesses multiple external classes' data: `SearchOperation`, `WeblogEntry`, `WeblogEntryWrapper`
- This logic would be more appropriate in a dedicated result converter class

**Code Sample:**
```java
static SearchResultList convertHitsToEntryList(
    ScoreDoc[] hits,
    SearchOperation search, ...) throws WebloggerException {
    
    // Heavy manipulation of external objects
    WeblogEntryManager weblogMgr = roller.getWeblogEntryManager();
    entry = weblogMgr.getWeblogEntry(doc.getField(FieldConstants.ID).stringValue());
    results.add(WeblogEntryWrapper.wrap(entry, urlStrategy));
    ...
}
```

---

#### Smell 3: Long Method
**Severity:** MEDIUM  
**Location:** Lines 107-165 (`initialize` method - 58 lines)

**Evidence:**
- Method performs multiple distinct operations
- Complex nested conditionals
- Difficult to unit test individual behaviors

**Code Sample:**
```java
@Override
public void initialize() throws InitializationException {
    if (this.searchEnabled) {
        if (indexConsistencyMarker.exists()) {
            logger.debug("Index inconsistent: marker exists");
            inconsistentAtStartup = true;
            deleteIndex();
        } else {
            try {
                File makeIndexDir = new File(indexDir);
                if (!makeIndexDir.exists()) {
                    makeIndexDir.mkdirs();
                    inconsistentAtStartup = true;
                    // ... more nested logic
                }
            } catch (IOException e) { ... }
        }
        // ... 40+ more lines
    }
}
```

---

#### Smell 4: Improper Exception Handling
**Severity:** MEDIUM  
**Location:** Lines 234-250 (Analyzer instantiation)

**Evidence:**
- Multiple exceptions caught and handled the same way
- Error logging without proper recovery strategy
- Fallback to default analyzer may hide configuration issues

**Code Sample:**
```java
private static Analyzer instantiateAnalyzer() {
    try {
        final Class<?> clazz = Class.forName(className);
        return (Analyzer) ConstructorUtils.invokeConstructor(clazz, null);
    } catch (final ClassNotFoundException e) {
        logger.error("failed to lookup analyzer class: " + className, e);
        return instantiateDefaultAnalyzer();
    } catch (final NoSuchMethodException | InstantiationException | 
             IllegalAccessException | InvocationTargetException e) {
        logger.error("failed to instantiate analyzer: " + className, e);
        return instantiateDefaultAnalyzer();
    }
}
```

---

#### Smell 5: Synchronization Issues
**Severity:** LOW  
**Location:** Lines 278-292 (Reader management)

**Evidence:**
- Mixed use of `synchronized` methods and `ReadWriteLock`
- Potential for inconsistent locking strategy

---

## Stage 2: Refactoring Planning

### Proposed Refactoring Strategies

#### Strategy 1: Extract Class - IndexLifecycleManager
**Smell Addressed:** God Class  
**Technique:** Extract Class

**Rationale:**
- Separate index creation, deletion, and consistency management into a dedicated class
- Improves Single Responsibility Principle (SRP) compliance
- Makes testing easier

**Affected Methods:**
- `deleteIndex()`
- `createIndex()`
- `indexExists()`
- Index consistency marker management

---

#### Strategy 2: Extract Class - SearchResultConverter
**Smell Addressed:** Feature Envy  
**Technique:** Extract Class / Move Method

**Rationale:**
- The `convertHitsToEntryList` method has feature envy towards search result objects
- Moving to a dedicated converter class improves cohesion
- Enables easier mocking in tests

---

#### Strategy 3: Extract Method - Decompose Initialize
**Smell Addressed:** Long Method  
**Technique:** Extract Method

**Rationale:**
- Break down `initialize()` into smaller, focused methods
- Each method handles one aspect of initialization
- Improves readability and testability

**Proposed Methods:**
- `handleInconsistentIndex()`
- `ensureIndexDirectory()`
- `validateExistingIndex()`
- `scheduleRebuildIfNeeded()`

---

#### Strategy 4: Create Factory for Analyzer
**Smell Addressed:** Improper Exception Handling  
**Technique:** Replace Conditional with Polymorphism + Factory Pattern

**Rationale:**
- Encapsulate analyzer creation in a factory class
- Provide clearer exception handling strategy
- Support different analyzer configurations

---

## Stage 3: Refactoring Execution

### Refactoring 1: Extract SearchResultConverter Class

**Before (Original Code):**
```java
// In LuceneIndexManager.java (Lines 419-486)
static SearchResultList convertHitsToEntryList(
    ScoreDoc[] hits,
    SearchOperation search,
    int pageNum,
    int entryCount,
    String weblogHandle,
    boolean websiteSpecificSearch,
    URLStrategy urlStrategy) throws WebloggerException {

    List<WeblogEntryWrapper> results = new ArrayList<>();
    int offset = pageNum * entryCount;
    if (offset >= hits.length) {
        offset = 0;
    }
    int limit = entryCount;
    if (offset + limit > hits.length) {
        limit = hits.length - offset;
    }

    try {
        Set<String> categories = new TreeSet<>();
        TreeSet<String> categorySet = new TreeSet<>();
        Weblogger roller = WebloggerFactory.getWeblogger();
        WeblogEntryManager weblogMgr = roller.getWeblogEntryManager();
        // ... rest of implementation
    } catch (IOException e) {
        throw new WebloggerException(e);
    }
}
```

**After (Refactored Code):**
```java
// New file: SearchResultConverter.java
package org.apache.roller.weblogger.business.search.lucene;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;
import org.apache.lucene.document.Document;
import org.apache.lucene.search.ScoreDoc;
import org.apache.roller.weblogger.WebloggerException;
import org.apache.roller.weblogger.business.URLStrategy;
import org.apache.roller.weblogger.business.WeblogEntryManager;
import org.apache.roller.weblogger.business.Weblogger;
import org.apache.roller.weblogger.business.WebloggerFactory;
import org.apache.roller.weblogger.business.search.SearchResultList;
import org.apache.roller.weblogger.pojos.WeblogEntry;
import org.apache.roller.weblogger.pojos.wrapper.WeblogEntryWrapper;

/**
 * Converts Lucene search hits to WeblogEntry results.
 * Extracted from LuceneIndexManager to improve cohesion.
 */
public class SearchResultConverter {

    private final WeblogEntryManager weblogEntryManager;
    private final URLStrategy urlStrategy;

    public SearchResultConverter(Weblogger roller, URLStrategy urlStrategy) {
        this.weblogEntryManager = roller.getWeblogEntryManager();
        this.urlStrategy = urlStrategy;
    }

    public SearchResultList convert(
            ScoreDoc[] hits,
            SearchOperation search,
            int pageNum,
            int entryCount,
            String weblogHandle,
            boolean websiteSpecificSearch) throws WebloggerException {

        PaginationInfo pagination = calculatePagination(hits.length, pageNum, entryCount);
        
        try {
            return buildResultList(hits, search, pagination, weblogHandle, websiteSpecificSearch);
        } catch (IOException e) {
            throw new WebloggerException("Error converting search results", e);
        }
    }

    private PaginationInfo calculatePagination(int totalHits, int pageNum, int entryCount) {
        int offset = pageNum * entryCount;
        if (offset >= totalHits) {
            offset = 0;
        }
        int limit = Math.min(entryCount, totalHits - offset);
        return new PaginationInfo(offset, limit);
    }

    private SearchResultList buildResultList(
            ScoreDoc[] hits,
            SearchOperation search,
            PaginationInfo pagination,
            String weblogHandle,
            boolean websiteSpecificSearch) throws IOException, WebloggerException {

        List<WeblogEntryWrapper> results = new ArrayList<>();
        Set<String> categories = new TreeSet<>();
        Timestamp now = new Timestamp(new Date().getTime());

        for (int i = pagination.offset(); i < pagination.offset() + pagination.limit(); i++) {
            Document doc = search.getSearcher().doc(hits[i].doc);
            String handle = doc.getField(FieldConstants.WEBSITE_HANDLE).stringValue();
            
            WeblogEntry entry = weblogEntryManager.getWeblogEntry(
                doc.getField(FieldConstants.ID).stringValue());

            collectCategory(doc, categories, websiteSpecificSearch, handle, weblogHandle);
            
            if (isValidEntry(entry, now)) {
                results.add(WeblogEntryWrapper.wrap(entry, urlStrategy));
            }
        }

        return new SearchResultList(results, categories, pagination.limit(), pagination.offset());
    }

    private void collectCategory(Document doc, Set<String> categories, 
            boolean websiteSpecificSearch, String handle, String weblogHandle) {
        if (!(websiteSpecificSearch && handle.equals(weblogHandle))
                && doc.getField(FieldConstants.CATEGORY) != null) {
            categories.add(doc.getField(FieldConstants.CATEGORY).stringValue());
        }
    }

    private boolean isValidEntry(WeblogEntry entry, Timestamp now) {
        return entry != null && entry.getPubTime().before(now);
    }

    private record PaginationInfo(int offset, int limit) {}
}
```

**In LuceneIndexManager.java (Updated):**
```java
@Override
public SearchResultList search(
    String term,
    String weblogHandle,
    String category,
    String locale,
    int pageNum,
    int entryCount,
    URLStrategy urlStrategy) throws WebloggerException {

    SearchOperation search = new SearchOperation(this);
    search.setTerm(term);
    boolean weblogSpecific = !WebloggerRuntimeConfig.isSiteWideWeblog(weblogHandle);
    if (weblogSpecific) {
        search.setWeblogHandle(weblogHandle);
    }
    if (category != null) {
        search.setCategory(category);
    }
    if (locale != null) {
        search.setLocale(locale);
    }

    executeIndexOperationNow(search);
    if (search.getResultsCount() >= 0) {
        // Delegate to converter
        SearchResultConverter converter = new SearchResultConverter(roller, urlStrategy);
        return converter.convert(
            search.getResults().scoreDocs,
            search,
            pageNum,
            entryCount,
            weblogHandle,
            weblogSpecific);
    }
    throw new WebloggerException("Error executing search");
}
```

---

### Refactoring 2: Decompose Initialize Method

**Before (Original Code):**
```java
@Override
public void initialize() throws InitializationException {
    if (this.searchEnabled) {
        if (indexConsistencyMarker.exists()) {
            logger.debug("Index inconsistent: marker exists");
            inconsistentAtStartup = true;
            deleteIndex();
        } else {
            try {
                File makeIndexDir = new File(indexDir);
                if (!makeIndexDir.exists()) {
                    makeIndexDir.mkdirs();
                    inconsistentAtStartup = true;
                    logger.debug("Index inconsistent: new");
                }
                indexConsistencyMarker.createNewFile();
            } catch (IOException e) {
                logger.error(e);
            }
        }

        if (indexExists()) {
            try {
                synchronized(this) {
                    reader = DirectoryReader.open(getIndexDirectory());
                }
            } catch (IOException | IllegalArgumentException ex) {
                logger.warn("Failed to open search index, scheduling rebuild.", ex);
                inconsistentAtStartup = true;
                deleteIndex();
            }
        } else {
            logger.debug("Creating index");
            inconsistentAtStartup = true;
            deleteIndex();
            createIndex(getIndexDirectory());
        }

        if (inconsistentAtStartup) {
            logger.info("Index was inconsistent. Rebuilding index in the background...");
            try {
                rebuildWeblogIndex();
            } catch (WebloggerException ex) {
                logger.error("ERROR: scheduling re-index operation", ex);
            }
        } else {
            logger.info("Index initialized and ready for use.");
        }
    }
}
```

**After (Refactored Code):**
```java
@Override
public void initialize() throws InitializationException {
    if (!this.searchEnabled) {
        logger.info("Search is disabled. Skipping index initialization.");
        return;
    }

    handleIndexConsistencyMarker();
    initializeOrRecoverIndex();
    scheduleRebuildIfNeeded();
}

private void handleIndexConsistencyMarker() {
    if (indexConsistencyMarker.exists()) {
        logger.debug("Index inconsistent: marker exists");
        inconsistentAtStartup = true;
        deleteIndex();
    } else {
        ensureIndexDirectoryExists();
    }
}

private void ensureIndexDirectoryExists() {
    try {
        File makeIndexDir = new File(indexDir);
        if (!makeIndexDir.exists()) {
            makeIndexDir.mkdirs();
            inconsistentAtStartup = true;
            logger.debug("Index inconsistent: new directory created");
        }
        indexConsistencyMarker.createNewFile();
    } catch (IOException e) {
        logger.error("Failed to create index consistency marker", e);
    }
}

private void initializeOrRecoverIndex() {
    if (indexExists()) {
        tryOpenExistingIndex();
    } else {
        createNewIndex();
    }
}

private void tryOpenExistingIndex() {
    try {
        synchronized (this) {
            reader = DirectoryReader.open(getIndexDirectory());
        }
    } catch (IOException | IllegalArgumentException ex) {
        logger.warn("Failed to open search index, scheduling rebuild.", ex);
        inconsistentAtStartup = true;
        deleteIndex();
    }
}

private void createNewIndex() {
    logger.debug("Creating new index");
    inconsistentAtStartup = true;
    deleteIndex();
    createIndex(getIndexDirectory());
}

private void scheduleRebuildIfNeeded() {
    if (inconsistentAtStartup) {
        logger.info("Index was inconsistent. Rebuilding index in the background...");
        try {
            rebuildWeblogIndex();
        } catch (WebloggerException ex) {
            logger.error("ERROR: scheduling re-index operation", ex);
        }
    } else {
        logger.info("Index initialized and ready for use.");
    }
}
```

---

### Refactoring 3: Create AnalyzerFactory

**Before (Original Code):**
```java
private static Analyzer instantiateAnalyzer() {
    final String className = WebloggerConfig.getProperty("lucene.analyzer.class");
    try {
        final Class<?> clazz = Class.forName(className);
        return (Analyzer) ConstructorUtils.invokeConstructor(clazz, null);
    } catch (final ClassNotFoundException e) {
        logger.error("failed to lookup analyzer class: " + className, e);
        return instantiateDefaultAnalyzer();
    } catch (final NoSuchMethodException | InstantiationException | 
             IllegalAccessException | InvocationTargetException e) {
        logger.error("failed to instantiate analyzer: " + className, e);
        return instantiateDefaultAnalyzer();
    }
}

private static Analyzer instantiateDefaultAnalyzer() {
    return new StandardAnalyzer();
}
```

**After (Refactored Code):**
```java
// New file: AnalyzerFactory.java
package org.apache.roller.weblogger.business.search.lucene;

import org.apache.commons.beanutils.ConstructorUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.roller.weblogger.config.WebloggerConfig;

/**
 * Factory for creating Lucene Analyzer instances.
 * Centralizes analyzer creation and configuration.
 */
public class AnalyzerFactory {

    private static final Log logger = LogFactory.getLog(AnalyzerFactory.class);
    private static final String CONFIG_KEY = "lucene.analyzer.class";
    private static final Analyzer DEFAULT_ANALYZER = new StandardAnalyzer();

    private AnalyzerFactory() {
        // Utility class
    }

    /**
     * Creates an Analyzer based on configuration.
     * Falls back to StandardAnalyzer if configured class cannot be instantiated.
     *
     * @return configured Analyzer or StandardAnalyzer as fallback
     */
    public static Analyzer createAnalyzer() {
        String className = WebloggerConfig.getProperty(CONFIG_KEY);
        
        if (className == null || className.isBlank()) {
            logger.info("No analyzer configured, using StandardAnalyzer");
            return DEFAULT_ANALYZER;
        }

        return tryCreateAnalyzer(className)
                .orElseGet(() -> {
                    logger.warn("Falling back to StandardAnalyzer");
                    return DEFAULT_ANALYZER;
                });
    }

    private static java.util.Optional<Analyzer> tryCreateAnalyzer(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Analyzer analyzer = (Analyzer) ConstructorUtils.invokeConstructor(clazz, null);
            logger.info("Successfully instantiated analyzer: " + className);
            return java.util.Optional.of(analyzer);
        } catch (ClassNotFoundException e) {
            logger.error("Analyzer class not found: " + className, e);
        } catch (ReflectiveOperationException e) {
            logger.error("Failed to instantiate analyzer: " + className, e);
        }
        return java.util.Optional.empty();
    }

    /**
     * Returns the default analyzer (StandardAnalyzer).
     */
    public static Analyzer getDefaultAnalyzer() {
        return DEFAULT_ANALYZER;
    }
}
```

**Updated usage in LuceneIndexManager.java:**
```java
public static final Analyzer getAnalyzer() {
    return AnalyzerFactory.createAnalyzer();
}
```

---

## Summary

| Smell | Refactoring Applied | Files Modified/Created |
|-------|---------------------|----------------------|
| God Class | Extract Class | `SearchResultConverter.java` (new) |
| Feature Envy | Move Method | `SearchResultConverter.java` (new) |
| Long Method | Extract Method | `LuceneIndexManager.java` |
| Improper Exception Handling | Factory Pattern | `AnalyzerFactory.java` (new) |

**Estimated Impact:**
- Lines of code: -50 in `LuceneIndexManager.java`, +120 in new classes
- Testability: Significantly improved
- Maintainability: Improved through SRP compliance

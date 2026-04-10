# File 3: SearchOperation.java - Agentic Refactoring Analysis

## File Information

| Property | Value |
|----------|-------|
| **File Path** | `app/src/main/java/org/apache/roller/weblogger/business/search/lucene/SearchOperation.java` |
| **Package** | `org.apache.roller.weblogger.business.search.lucene` |
| **Total Lines** | 220 |
| **Subsystem** | Search and Indexing |

---

## Stage 1: Smell Identification

### Prompt Used

You are an expert software architect

**Target File:** SearchOperation.java (220 lines)

**Subsystem:** Search and Indexing

**Context:** Apache Roller - Lucene search query execution component

**Task 1 - Smell Identification:**
Analyze this file for the following design smells with exact line references:
1. God Class - Does this class have too many responsibilities?
2. Feature Envy - Are there methods that use other classes' data excessively?
3. Long Method - Identify methods exceeding 20 lines with complex logic
4. SOLID Violations - Specifically SRP (Single Responsibility) and OCP (Open-Closed)
5. Tight Coupling - Identify hard dependencies on concrete implementations
6. Improper Exception Handling - Catch-all blocks or swallowed exceptions

**Task 2 - Refactoring Planning:**
For each identified smell:
- Propose a specific refactoring technique (Extract Class, Extract Method, etc.)
- Explain why this refactoring is appropriate
- List affected methods/classes

**Task 3 - Refactoring Execution:**
Provide complete before/after code snippets that:
- Are syntactically correct and compilable
- Preserve original functionality

**Output Format:** Use clear sections with code blocks and line references.


### Identified Design Smells

#### Smell 1: Primitive Obsession / Data Clumps
**Severity:** MEDIUM  
**Location:** Lines 60-68 (Instance variables)

**Evidence:**
- Multiple related primitive fields represent search criteria
- These fields travel together and should be encapsulated

**Code Sample:**
```java
private String term;
private String weblogHandle;
private String category;
private String locale;
private String parseError;
```

**Problem:**
- Related search parameters are scattered as primitive strings
- No validation or encapsulation of search criteria
- Difficult to add new search parameters consistently

---

#### Smell 2: Missing Abstraction - Query Builder
**Severity:** MEDIUM  
**Location:** Lines 85-125 (`doRun` method)

**Evidence:**
- Complex query construction logic embedded in the run method
- Multiple conditional query modifications
- Should be extracted to a dedicated builder pattern

**Code Sample:**
```java
@Override
public void doRun() {
    // ... setup code ...
    
    Query query = multiParser.parse(term);

    Term handleTerm = IndexUtil.getTerm(FieldConstants.WEBSITE_HANDLE, weblogHandle);
    if (handleTerm != null) {
        query = new BooleanQuery.Builder()
            .add(query, BooleanClause.Occur.MUST)
            .add(new TermQuery(handleTerm), BooleanClause.Occur.MUST)
            .build();
    }

    if (category != null) {
        Term catTerm = new Term(FieldConstants.CATEGORY, category.toLowerCase());
        query = new BooleanQuery.Builder()
            .add(query, BooleanClause.Occur.MUST)
            .add(new TermQuery(catTerm), BooleanClause.Occur.MUST)
            .build();
    }

    Term localeTerm = IndexUtil.getTerm(FieldConstants.LOCALE, locale);
    if (localeTerm != null) {
        query = new BooleanQuery.Builder()
            .add(query, BooleanClause.Occur.MUST)
            .add(new TermQuery(localeTerm), BooleanClause.Occur.MUST)
            .build();
    }
    // ...
}
```

---

#### Smell 3: Magic Numbers
**Severity:** LOW  
**Location:** Line 87

**Evidence:**
- Hard-coded document limit without explanation

**Code Sample:**
```java
@Override
public void doRun() {
    final int docLimit = 500;  // Magic number - why 500?
    // ...
}
```

---

#### Smell 4: Poor Error Handling
**Severity:** MEDIUM  
**Location:** Lines 126-133

**Evidence:**
- `ParseException` is caught and ignored with just "who cares?" comment
- Error state stored as string rather than proper exception handling

**Code Sample:**
```java
} catch (ParseException e) {
    // who cares?
    parseError = e.getMessage();
}
```

---

#### Smell 5: Mixed Responsibilities
**Severity:** LOW  
**Location:** Entire class

**Evidence:**
- Class handles both query construction AND search execution
- Result access methods (getters) mixed with operational methods

---

## Stage 2: Refactoring Planning

### Proposed Refactoring Strategies

#### Strategy 1: Introduce SearchCriteria Value Object
**Smell Addressed:** Primitive Obsession, Data Clumps  
**Technique:** Replace Data Value with Object

**Rationale:**
- Encapsulate related search parameters into a single object
- Add validation at the criteria level
- Improve API clarity

---

#### Strategy 2: Extract QueryBuilder
**Smell Addressed:** Missing Abstraction  
**Technique:** Extract Class + Builder Pattern

**Rationale:**
- Separate query construction from search execution
- Make query building testable
- Support future query extensions

---

#### Strategy 3: Define Search Configuration Constants
**Smell Addressed:** Magic Numbers  
**Technique:** Replace Magic Number with Symbolic Constant

---

#### Strategy 4: Implement Proper Exception Handling
**Smell Addressed:** Poor Error Handling  
**Technique:** Replace Error Code with Exception

---

## Stage 3: Refactoring Execution

### Refactoring 1: Introduce SearchCriteria Value Object

**Before (Original scattered fields):**
```java
private String term;
private String weblogHandle;
private String category;
private String locale;

public void setTerm(String term) { this.term = term; }
public void setWeblogHandle(String weblogHandle) { this.weblogHandle = weblogHandle; }
public void setCategory(String category) { this.category = category; }
public void setLocale(String locale) { this.locale = locale; }
```

**After (Refactored Code):**

```java
// New file: SearchCriteria.java
package org.apache.roller.weblogger.business.search;

import org.apache.commons.lang3.StringUtils;

/**
 * Immutable value object representing search criteria.
 * Encapsulates all parameters needed for a search operation.
 */
public final class SearchCriteria {

    private final String term;
    private final String weblogHandle;
    private final String category;
    private final String locale;

    private SearchCriteria(Builder builder) {
        this.term = builder.term;
        this.weblogHandle = builder.weblogHandle;
        this.category = builder.category;
        this.locale = builder.locale;
    }

    public String getTerm() {
        return term;
    }

    public String getWeblogHandle() {
        return weblogHandle;
    }

    public String getCategory() {
        return category;
    }

    public String getLocale() {
        return locale;
    }

    public boolean hasWeblogHandle() {
        return StringUtils.isNotBlank(weblogHandle);
    }

    public boolean hasCategory() {
        return StringUtils.isNotBlank(category);
    }

    public boolean hasLocale() {
        return StringUtils.isNotBlank(locale);
    }

    /**
     * Validates the search criteria.
     * 
     * @throws IllegalArgumentException if criteria is invalid
     */
    public void validate() {
        if (StringUtils.isBlank(term)) {
            throw new IllegalArgumentException("Search term cannot be blank");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for SearchCriteria.
     */
    public static class Builder {
        private String term;
        private String weblogHandle;
        private String category;
        private String locale;

        public Builder term(String term) {
            this.term = term;
            return this;
        }

        public Builder weblogHandle(String weblogHandle) {
            this.weblogHandle = weblogHandle;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder locale(String locale) {
            this.locale = locale;
            return this;
        }

        public SearchCriteria build() {
            SearchCriteria criteria = new SearchCriteria(this);
            criteria.validate();
            return criteria;
        }
    }

    @Override
    public String toString() {
        return String.format("SearchCriteria[term=%s, weblog=%s, category=%s, locale=%s]",
            term, weblogHandle, category, locale);
    }
}
```

---

### Refactoring 2: Extract LuceneQueryBuilder

**Before (Query construction in doRun):**
```java
@Override
public void doRun() {
    // ... 
    MultiFieldQueryParser multiParser = new MultiFieldQueryParser(
            SEARCH_FIELDS, LuceneIndexManager.getAnalyzer());
    multiParser.setDefaultOperator(MultiFieldQueryParser.Operator.AND);

    Query query = multiParser.parse(term);

    Term handleTerm = IndexUtil.getTerm(FieldConstants.WEBSITE_HANDLE, weblogHandle);
    if (handleTerm != null) {
        query = new BooleanQuery.Builder()
            .add(query, BooleanClause.Occur.MUST)
            .add(new TermQuery(handleTerm), BooleanClause.Occur.MUST)
            .build();
    }
    // ... more conditions
}
```

**After (Refactored Code):**

```java
// New file: LuceneQueryBuilder.java
package org.apache.roller.weblogger.business.search.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.roller.weblogger.business.search.SearchCriteria;

/**
 * Builds Lucene queries from SearchCriteria.
 * Separates query construction concern from search execution.
 */
public class LuceneQueryBuilder {

    private static final String[] SEARCH_FIELDS = new String[] {
        FieldConstants.CONTENT,
        FieldConstants.TITLE,
        FieldConstants.C_CONTENT
    };

    private final Analyzer analyzer;

    public LuceneQueryBuilder(Analyzer analyzer) {
        this.analyzer = analyzer;
    }

    /**
     * Builds a Lucene Query from the given search criteria.
     *
     * @param criteria the search criteria
     * @return the constructed Lucene query
     * @throws ParseException if the query term cannot be parsed
     */
    public Query buildQuery(SearchCriteria criteria) throws ParseException {
        Query baseQuery = parseBaseQuery(criteria.getTerm());
        return applyFilters(baseQuery, criteria);
    }

    private Query parseBaseQuery(String term) throws ParseException {
        MultiFieldQueryParser parser = new MultiFieldQueryParser(SEARCH_FIELDS, analyzer);
        parser.setDefaultOperator(MultiFieldQueryParser.Operator.AND);
        return parser.parse(term);
    }

    private Query applyFilters(Query query, SearchCriteria criteria) {
        query = applyWeblogFilter(query, criteria);
        query = applyCategoryFilter(query, criteria);
        query = applyLocaleFilter(query, criteria);
        return query;
    }

    private Query applyWeblogFilter(Query query, SearchCriteria criteria) {
        if (!criteria.hasWeblogHandle()) {
            return query;
        }

        Term handleTerm = IndexUtil.getTerm(
            FieldConstants.WEBSITE_HANDLE, 
            criteria.getWeblogHandle()
        );
        
        if (handleTerm == null) {
            return query;
        }

        return new BooleanQuery.Builder()
            .add(query, BooleanClause.Occur.MUST)
            .add(new TermQuery(handleTerm), BooleanClause.Occur.MUST)
            .build();
    }

    private Query applyCategoryFilter(Query query, SearchCriteria criteria) {
        if (!criteria.hasCategory()) {
            return query;
        }

        Term categoryTerm = new Term(
            FieldConstants.CATEGORY, 
            criteria.getCategory().toLowerCase()
        );

        return new BooleanQuery.Builder()
            .add(query, BooleanClause.Occur.MUST)
            .add(new TermQuery(categoryTerm), BooleanClause.Occur.MUST)
            .build();
    }

    private Query applyLocaleFilter(Query query, SearchCriteria criteria) {
        if (!criteria.hasLocale()) {
            return query;
        }

        Term localeTerm = IndexUtil.getTerm(FieldConstants.LOCALE, criteria.getLocale());
        
        if (localeTerm == null) {
            return query;
        }

        return new BooleanQuery.Builder()
            .add(query, BooleanClause.Occur.MUST)
            .add(new TermQuery(localeTerm), BooleanClause.Occur.MUST)
            .build();
    }
}
```

---

### Refactoring 3: Extract Search Configuration Constants

**Before:**
```java
public void doRun() {
    final int docLimit = 500;
    // ...
}
```

**After:**

```java
// New file: SearchConfiguration.java
package org.apache.roller.weblogger.business.search;

import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.roller.weblogger.business.search.lucene.FieldConstants;
import org.apache.roller.weblogger.config.WebloggerConfig;

/**
 * Configuration constants for search operations.
 */
public final class SearchConfiguration {

    private SearchConfiguration() {
        // Utility class
    }

    /**
     * Maximum number of documents to return from a search.
     * Can be overridden via configuration property "search.max.results".
     */
    public static final int DEFAULT_MAX_RESULTS = 500;

    /**
     * Default sort order for search results (by publication date, descending).
     */
    public static final Sort DEFAULT_SORT = new Sort(
        new SortField(FieldConstants.PUBLISHED, SortField.Type.STRING, true)
    );

    /**
     * Gets the configured maximum results limit.
     */
    public static int getMaxResults() {
        String configured = WebloggerConfig.getProperty("search.max.results");
        if (configured != null) {
            try {
                return Integer.parseInt(configured);
            } catch (NumberFormatException e) {
                // Fall back to default
            }
        }
        return DEFAULT_MAX_RESULTS;
    }
}
```

---

### Refactoring 4: Improved SearchOperation with Better Error Handling

**Before (Original SearchOperation.java):**
```java
public class SearchOperation extends ReadFromIndexOperation {

    private static Log logger = LogFactory.getFactory().getInstance(SearchOperation.class);

    private static final String[] SEARCH_FIELDS = new String[] {
        FieldConstants.CONTENT,
        FieldConstants.TITLE,
        FieldConstants.C_CONTENT
    };

    private static final Sort SORTER = new Sort(new SortField(
            FieldConstants.PUBLISHED, SortField.Type.STRING, true));

    private IndexSearcher searcher;
    private TopFieldDocs searchresults;
    private String term;
    private String weblogHandle;
    private String category;
    private String locale;
    private String parseError;

    public SearchOperation(IndexManager mgr) {
        super((LuceneIndexManager) mgr);
    }

    public void setTerm(String term) { this.term = term; }

    @Override
    public void doRun() {
        final int docLimit = 500;
        searchresults = null;
        searcher = null;

        try {
            IndexReader reader = manager.getSharedIndexReader();
            searcher = new IndexSearcher(reader);

            MultiFieldQueryParser multiParser = new MultiFieldQueryParser(
                    SEARCH_FIELDS, LuceneIndexManager.getAnalyzer());
            multiParser.setDefaultOperator(MultiFieldQueryParser.Operator.AND);

            Query query = multiParser.parse(term);
            // ... filter application
            searchresults = searcher.search(query, docLimit, SORTER);

        } catch (IOException e) {
            logger.error("Error searching index", e);
            parseError = e.getMessage();

        } catch (ParseException e) {
            // who cares?
            parseError = e.getMessage();
        }
    }
    // ... getters
}
```

**After (Refactored SearchOperation.java):**

```java
package org.apache.roller.weblogger.business.search.lucene;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.roller.weblogger.business.search.IndexManager;
import org.apache.roller.weblogger.business.search.SearchConfiguration;
import org.apache.roller.weblogger.business.search.SearchCriteria;

import java.io.IOException;
import java.util.Optional;

/**
 * An operation that searches the Lucene index.
 * Refactored to use SearchCriteria and LuceneQueryBuilder.
 */
public class SearchOperation extends ReadFromIndexOperation {

    private static final Log logger = LogFactory.getLog(SearchOperation.class);

    private final LuceneQueryBuilder queryBuilder;
    
    private IndexSearcher searcher;
    private TopFieldDocs searchResults;
    private SearchCriteria criteria;
    private SearchError error;

    public SearchOperation(IndexManager mgr) {
        super((LuceneIndexManager) mgr);
        this.queryBuilder = new LuceneQueryBuilder(LuceneIndexManager.getAnalyzer());
    }

    /**
     * Sets the search criteria for this operation.
     */
    public void setCriteria(SearchCriteria criteria) {
        this.criteria = criteria;
    }

    // Legacy setters for backward compatibility
    @Deprecated
    public void setTerm(String term) {
        // Will be removed in future version
        this.criteria = SearchCriteria.builder().term(term).build();
    }

    @Deprecated
    public void setWeblogHandle(String weblogHandle) {
        if (criteria != null) {
            criteria = SearchCriteria.builder()
                .term(criteria.getTerm())
                .weblogHandle(weblogHandle)
                .category(criteria.getCategory())
                .locale(criteria.getLocale())
                .build();
        }
    }

    @Override
    public void doRun() {
        resetState();

        if (criteria == null) {
            error = SearchError.of("No search criteria provided", null);
            return;
        }

        try {
            executeSearch();
        } catch (IOException e) {
            logger.error("Error searching index", e);
            error = SearchError.of("Index access error: " + e.getMessage(), e);
        } catch (ParseException e) {
            logger.warn("Failed to parse search query: " + criteria.getTerm(), e);
            error = SearchError.of("Invalid search query: " + e.getMessage(), e);
        }
    }

    private void resetState() {
        searchResults = null;
        searcher = null;
        error = null;
    }

    private void executeSearch() throws IOException, ParseException {
        IndexReader reader = manager.getSharedIndexReader();
        searcher = new IndexSearcher(reader);

        Query query = queryBuilder.buildQuery(criteria);
        int maxResults = SearchConfiguration.getMaxResults();
        
        searchResults = searcher.search(query, maxResults, SearchConfiguration.DEFAULT_SORT);
        
        logger.debug(String.format("Search completed: %d results for '%s'", 
            getResultsCount(), criteria.getTerm()));
    }

    public IndexSearcher getSearcher() {
        return searcher;
    }

    public TopFieldDocs getResults() {
        return searchResults;
    }

    public int getResultsCount() {
        if (searchResults == null) {
            return -1;
        }
        return (int) searchResults.totalHits.value;
    }

    /**
     * Returns any error that occurred during the search.
     */
    public Optional<SearchError> getError() {
        return Optional.ofNullable(error);
    }

    /**
     * @deprecated Use getError() instead
     */
    @Deprecated
    public String getParseError() {
        return error != null ? error.getMessage() : null;
    }

    /**
     * Represents an error that occurred during search.
     */
    public static class SearchError {
        private final String message;
        private final Exception cause;

        private SearchError(String message, Exception cause) {
            this.message = message;
            this.cause = cause;
        }

        public static SearchError of(String message, Exception cause) {
            return new SearchError(message, cause);
        }

        public String getMessage() {
            return message;
        }

        public Optional<Exception> getCause() {
            return Optional.ofNullable(cause);
        }
    }
}
```

---

## Summary

| Smell | Refactoring Applied | New Class/Change |
|-------|---------------------|------------------|
| Primitive Obsession | Introduce Value Object | `SearchCriteria.java` |
| Data Clumps | Introduce Value Object | `SearchCriteria.java` |
| Missing Abstraction | Extract Class + Builder | `LuceneQueryBuilder.java` |
| Magic Numbers | Extract Constants | `SearchConfiguration.java` |
| Poor Error Handling | Replace Error Code with Exception | `SearchError` inner class |

**Impact Assessment:**

| Metric | Before | After |
|--------|--------|-------|
| Lines in SearchOperation.java | 220 | ~150 |
| New supporting classes | 0 | 3 |
| Test Coverage Potential | LOW | HIGH |
| Code Reusability | LOW | HIGH |

**Benefits:**
1. SearchCriteria can be validated and reused
2. Query building is testable in isolation
3. Configuration is centralized
4. Error handling is explicit and type-safe
5. Better adherence to Single Responsibility Principle

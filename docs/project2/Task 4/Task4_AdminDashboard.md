# Task 4: Admin Site Summary Dashboard - Implementation Documentation

## Table of Contents
1. [Overview](#overview)
2. [Requirements Recap](#requirements-recap)
3. [Architecture & Design](#architecture--design)
4. [Design Patterns Used](#design-patterns-used)
5. [Metrics Implemented](#metrics-implemented)
6. [File-by-File Breakdown](#file-by-file-breakdown)
7. [View Definitions](#view-definitions)
8. [Integration Points](#integration-points)
9. [How to Access the Dashboard](#how-to-access-the-dashboard)
10. [Extensibility Guide](#extensibility-guide)
11. [Quality Attributes & Trade-offs](#quality-attributes--trade-offs)

---

## Overview

The **Admin Site Summary Dashboard** is a centralized monitoring feature for Roller administrators that provides site-wide engagement metrics. It supports two distinct view modes:

- **Minimalist View**: Shows only the user count and top category (compact)
- **Full View**: Shows the entire suite of metrics (comprehensive)

The dashboard is accessible from the Server Admin tab menu and requires global admin permissions.

---

## Requirements Recap

From the project specification:

> Administrators currently lack a centralized way to track site engagement. To solve this, you need to implement a **Site Summary Dashboard**. This dashboard must be capable of showing the total number of users, the top-performing category, the most starred blog, and a leaderboard of the top 3 most active users (or any other metric you find useful and trackable; implement at least 5). The admin should be able to toggle between two distinct modes: a **Minimalist View** (user count + top category) and a **Full View** (entire suite of metrics). **Definition of each view shouldn't be described with the logic to fetch data**, so that one can flexibly modify what and what not to include in each view.

---

## Architecture & Design

The implementation follows a clean layered architecture:

```
┌─────────────────────────────────────────────────┐
│              JSP View Layer                      │
│   SiteSummaryDashboard.jsp                      │
│   (Renders metrics grid, toggle button)         │
└─────────────────┬───────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────┐
│           Struts Action Layer                    │
│   SiteSummaryDashboard.java (UIAction)          │
│   (Handles HTTP, delegates to DashboardService) │
└─────────────────┬───────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────┐
│         DashboardService                         │
│   DashboardServiceImpl.java                     │
│   (Orchestrates factory + builder + strategies) │
└─────────┬───────────┬───────────────────────────┘
          │           │
┌─────────▼─────────┐ │ ┌────────────────────────┐
│ DashboardViewFactory│ │ │ DashboardReportBuilder │
│ (Factory Pattern)  │ │ │ (Builder Pattern)      │
│ Creates view defs  │ │ │ Constructs reports     │
│ from type string   │ │ └──────┬─────────────────┘
└────────┬──────────┘ │        │
         │            │        │
┌────────▼──────────┐ │ ┌──────▼──────────────────┐
│ View Definitions   │ │ │ MetricCollector Strategies│
│ (What to show)     │ │ │ (Strategy Pattern)       │
│ - Minimalist       │ │ │ - TotalUsersCollector    │
│ - Full             │ │ │ - TotalWeblogsCollector  │
└────────────────────┘ │ │ - TotalEntriesCollector  │
                       │ │ - TotalCommentsCollector  │
                       │ │ - TopCategoryCollector    │
                       │ │ - MostStarredBlogCollector│
                       │ │ - TopActiveUsersCollector │
                       │ │ - MostActiveWeblogCollector│
                       │ │ - MostCommentedEntry...   │
                       │ └──────────┬────────────────┘
                       │            │
                       └────────────▼─────────────────┐
                       │   Existing Business Layer     │
                       │   UserManager, WeblogManager, │
                       │   WeblogEntryManager,         │
                       │   StarService,                │
                       │   JPAPersistenceStrategy      │
                       └──────────────────────────────┘
```

### Key Architectural Decision: Separation of View Definition from Data Logic

The critical requirement was: *"Definition of each view shouldn't be described with the logic to fetch data."*

This is achieved by:
1. **`MetricCollector`** implementations only know how to **fetch** data (Strategy Pattern)
2. **`DashboardViewDefinition`** implementations only know **which** metrics to include
3. **`DashboardViewFactory`** creates the appropriate view definition from a type string (Factory Pattern)
4. **`DashboardReportBuilder`** bridges the two, using the view definition's metric keys to select which collectors to run (Builder Pattern)

---

## Design Patterns Used

### 1. Factory Pattern (`DashboardViewFactory`)

**What**: Creates `DashboardViewDefinition` instances from a view-type string (e.g., `"full"`, `"minimalist"`). The factory registers available view definitions at construction time and returns the correct instance when `createView(type)` is called. If the requested type is unknown, it falls back to the `"full"` view with a log warning.

**Why**: The service layer and the Struts action layer should not be responsible for knowing which view-definition classes exist or how to instantiate them. By centralizing creation in a Factory, we decouple the *what* (which view) from the *who* (which concrete class). Adding a new view only requires implementing `DashboardViewDefinition` and registering it in the factory - no other code changes are needed.

**Quality Attribute Improvement**:
- **Extensibility**: New views can be introduced without modifying the service or the action layer (Open/Closed Principle)
- **Decoupling**: Clients pass a simple string; the factory resolves the concrete class

**Files**: `DashboardViewFactory.java`, `DashboardViewDefinition.java`, `MinimalistViewDefinition.java`, `FullViewDefinition.java`

### 2. Builder Pattern (`DashboardReportBuilder`)

**What**: Constructs `DashboardReport` instances step-by-step, selecting which metrics to include based on the requested view.

**Why**: The report object is complex (multiple metrics, view mode label, timestamp) and different views need different subsets. The Builder separates construction from representation - the same builder can produce both a minimalist report (2 metrics) and a full report (9 metrics).

**Quality Attribute Improvement**:
- **Maintainability**: New views can be created without modifying the builder
- **Flexibility**: The same metrics can be composed into different report configurations

**Files**: `DashboardReportBuilder.java`, `DashboardReport.java`

### 3. Strategy Pattern (`MetricCollector` interface)

**What**: Each metric is collected by an independent `MetricCollector` implementation. The `DashboardServiceImpl` holds a list of strategies and selects which to execute at runtime.

**Why**: Each metric has different data sources and query logic. The Strategy pattern allows adding/removing/swapping metrics independently.

**Quality Attribute Improvement**:
- **Extensibility**: New metrics added by creating a new class implementing `MetricCollector` - no existing code changes needed (Open/Closed Principle)
- **Testability**: Each collector can be unit tested in isolation

**Trade-off**: Slight overhead from having many small classes instead of one large method. Justified by the significant improvement in modularity.

**Files**: `MetricCollector.java`, `collectors/TotalUsersCollector.java`, `collectors/TopCategoryCollector.java`, etc.

### How the Three Patterns Collaborate

```
User clicks "Full View"
        │
        ▼
Struts Action → DashboardService.generateReport("full")
        │
        ▼
DashboardViewFactory.createView("full")     ← Factory Pattern
        │ returns FullViewDefinition
        ▼
DashboardReportBuilder
   .forView("full")
   .withMetrics(viewDef.getIncludedMetricKeys())   ← Builder Pattern
   .build()
        │ iterates over collectors whose keys match
        ▼
MetricCollector.collect()  ← Strategy Pattern
   (each collector is an independent algorithm)
        │
        ▼
DashboardReport (immutable product)
```

---

## Metrics Implemented

| # | Metric Key | Label | Data Source | Description |
|---|-----------|-------|-------------|-------------|
| 1 | `totalUsers` | Total Users | `UserManager.getUserCount()` | Count of all enabled users |
| 2 | `totalWeblogs` | Total Weblogs | `WeblogManager.getWeblogCount()` | Count of all active weblogs |
| 3 | `totalEntries` | Total Blog Entries | `WeblogEntryManager.getEntryCount()` | Count of all blog entries |
| 4 | `totalComments` | Total Comments | `WeblogEntryManager.getCommentCount()` | Count of all comments |
| 5 | `topCategory` | Top Category | JPQL aggregate query | Category with most published entries |
| 6 | `mostStarredBlog` | Most Starred Blog Entry | `StarService.getTopStarredEntries()` | Entry with most stars |
| 7 | `topActiveUsers` | Top 3 Most Active Users | JPQL aggregate query | Users with most published entries |
| 8 | `mostActiveWeblog` | Most Active Weblog (by Hits) | `WeblogEntryManager.getHotWeblogs()` | Weblog with most daily hits |
| 9 | `mostCommentedEntry` | Most Commented Entry | `WeblogEntryManager.getMostCommentedWeblogEntries()` | Entry with most comments |

**Efficiency Note**: Metrics 5 and 7 use efficient JPQL aggregate queries with `GROUP BY` and `ORDER BY COUNT(...) DESC` with `LIMIT 1/3`. They do **not** iterate over individual articles - they use database-level aggregation for optimal performance.

---

## File-by-File Breakdown

### New Files Created

| File | Layer | Purpose |
|------|-------|---------|
| `business/dashboard/DashboardMetric.java` | Model | Value object carrying one metric (key, label, value, detail) |
| `business/dashboard/DashboardReport.java` | Model | Product of the Builder; immutable list of metrics |
| `business/dashboard/MetricCollector.java` | Interface | Strategy interface for metric collection |
| `business/dashboard/DashboardViewDefinition.java` | Interface | Interface defining which metrics a view includes |
| `business/dashboard/MinimalistViewDefinition.java` | View Def | Minimalist view: totalUsers + topCategory |
| `business/dashboard/FullViewDefinition.java` | View Def | Full view: all 9 metrics |
| `business/dashboard/DashboardViewFactory.java` | Factory | Creates DashboardViewDefinition from a type string (Factory Pattern) |
| `business/dashboard/DashboardReportBuilder.java` | Builder | Constructs DashboardReport from selected collectors |
| `business/dashboard/DashboardService.java` | Interface | Service interface for report generation |
| `business/dashboard/DashboardServiceImpl.java` | Service | Orchestrates factory, builder, and strategies |
| `business/dashboard/collectors/TotalUsersCollector.java` | Strategy | Collects total user count |
| `business/dashboard/collectors/TotalWeblogsCollector.java` | Strategy | Collects total weblog count |
| `business/dashboard/collectors/TotalEntriesCollector.java` | Strategy | Collects total entry count |
| `business/dashboard/collectors/TotalCommentsCollector.java` | Strategy | Collects total comment count |
| `business/dashboard/collectors/TopCategoryCollector.java` | Strategy | Collects top category by entry count |
| `business/dashboard/collectors/MostStarredBlogCollector.java` | Strategy | Collects most starred entry |
| `business/dashboard/collectors/TopActiveUsersCollector.java` | Strategy | Collects top 3 active users |
| `business/dashboard/collectors/MostActiveWeblogCollector.java` | Strategy | Collects most active weblog by hits |
| `business/dashboard/collectors/MostCommentedEntryCollector.java` | Strategy | Collects most commented entry |
| `ui/struts2/admin/SiteSummaryDashboard.java` | Action | Struts2 action handling HTTP requests |
| `WEB-INF/jsps/admin/SiteSummaryDashboard.jsp` | View | JSP rendering the dashboard UI |

### Modified Files

| File | Change |
|------|--------|
| `business/Weblogger.java` | Added `getDashboardService()` method to interface |
| `business/WebloggerImpl.java` | Added `DashboardService` field, constructor param, and getter |
| `business/jpa/JPAWebloggerImpl.java` | Added `DashboardService` to constructor and `super()` call |
| `business/jpa/JPAWebloggerModule.java` | Registered `DashboardService` → `DashboardServiceImpl` Guice binding |
| `resources/struts.xml` | Added `siteSummaryDashboard` action mapping |
| `WEB-INF/tiles.xml` | Added `.SiteSummaryDashboard` tiles definition |
| `resources/.../admin-menu.xml` | Added dashboard menu item in admin tab |
| `resources/ApplicationResources.properties` | Added i18n strings for the dashboard |

---

## View Definitions

### Minimalist View
Includes exactly 2 metrics:
- **Total Users** (`totalUsers`)
- **Top Category** (`topCategory`)

### Full View
Includes all 9 metrics:
- Total Users, Total Weblogs, Total Entries, Total Comments
- Top Category, Most Starred Blog, Top 3 Active Users
- Most Active Weblog, Most Commented Entry

The toggle between views is handled by the `toggleView()` action method, which switches the `viewMode` parameter and re-executes metric collection.

---

## Integration Points

### Guice Dependency Injection
- `DashboardServiceImpl` is annotated `@Singleton` and `@Inject`
- Registered in `JPAWebloggerModule`: `DashboardService.class → DashboardServiceImpl.class`
- Constructor-injected with `UserManager`, `WeblogManager`, `WeblogEntryManager`, `StarService`, and `JPAPersistenceStrategy`

### Weblogger Service Locator
- Added `getDashboardService()` to the `Weblogger` interface
- The Struts action accesses it via `WebloggerFactory.getWeblogger().getDashboardService()`

### Struts2 Action
- Follows the existing pattern: extends `UIAction`, overrides `requiredGlobalPermissionActions()` to require `ADMIN`
- `isWeblogRequired()` returns `false` (this is a global/site-level view)
- Registered in `struts.xml` under the `weblogger-admin` package

### Navigation
- Added as a menu item in `admin-menu.xml` under the Server Admin tab
- Label: "Site Dashboard" (via `tabbedmenu.admin.siteSummaryDashboard` i18n key)

---

## How to Access the Dashboard

1. Log in as an **admin** user
2. Navigate to the **Server Admin** tab in the top navigation
3. Click **"Site Dashboard"** in the sub-navigation tabs
4. The Full View is shown by default
5. Click **"Switch to Minimalist View"** to toggle to the compact view
6. Click **"Switch to Full View"** to toggle back

---

## Extensibility Guide

### Adding a New Metric
1. Create a new class implementing `MetricCollector`:
   ```java
   public class MyNewCollector implements MetricCollector {
       public static final String KEY = "myNewMetric";
       
       @Override
       public String getMetricKey() { return KEY; }
       
       @Override
       public DashboardMetric collect() throws WebloggerException {
           // Compute your metric
           return new DashboardMetric(KEY, "My Label", "computed-value");
       }
   }
   ```
2. Register it in `DashboardServiceImpl` constructor:
   ```java
   collectors.add(new MyNewCollector(...));
   ```
3. Add its key to whichever `DashboardViewDefinition` should include it

### Adding a New View
1. Create a new class implementing `DashboardViewDefinition`:
   ```java
   public class CustomViewDefinition implements DashboardViewDefinition {
       @Override
       public String getViewName() { return "custom"; }
       
       @Override
       public Set<String> getIncludedMetricKeys() {
           return Set.of("totalUsers", "myNewMetric");
       }
   }
   ```
2. Register it in `DashboardViewFactory` constructor:
   ```java
   register(new CustomViewDefinition());
   ```

**No existing collectors or views need to be modified** - this is the power of the Strategy + Builder combination.

---

## Quality Attributes & Trade-offs

| Quality Attribute | How Addressed | Trade-off |
|-------------------|---------------|-----------|
| **Modularity** | Each metric is a separate class; views are separate from data fetching | More files to maintain (19 new files) |
| **Maintainability** | Changes to one metric don't affect others; clear separation of concerns | Indirection adds some complexity for simple cases |
| **Extensibility** | New metrics and views added without modifying existing code (OCP) | Initial setup requires understanding the architecture |
| **Testability** | Each collector can be unit tested independently | Need to mock managers for testing |
| **Performance** | JPQL aggregate queries avoid iterating over individual articles | Multiple separate DB queries (one per metric) instead of one large query |
| **Usability** | Clean toggling UI with clear visual feedback | Two views may not cover all admin needs (mitigated by extensibility) |

---

## Summary

The Admin Site Summary Dashboard implements a clean, extensible monitoring solution using three key design patterns:

1. **Factory**: Creates the appropriate view definition from a type string
2. **Builder**: Constructs view-specific reports step-by-step
3. **Strategy**: Encapsulates independent, interchangeable metric computation algorithms

The solution meets all requirements:
- ✅ 9 metrics (exceeds minimum of 5)
- ✅ Two distinct view modes (Minimalist + Full)
- ✅ View definition separated from data-fetching logic
- ✅ Efficient database queries (aggregate JPQL, no article iteration)
- ✅ Toggle between views
- ✅ Admin-only access enforced
- ✅ Integrated into existing Roller navigation

# Bug Report Feature — Deep Dive Personal Reference

> **Purpose:** This is YOUR personal study guide. Before your TA evaluation, read this top-to-bottom. It explains every class, every design decision, the email system, the database, and the reasoning behind each choice — in plain language.

---

## Table of Contents

1. [One-Line Summary of What You Built](#1-one-line-summary)
2. [System Layer Map — How Everything Connects](#2-system-layer-map)
3. [The Data Model — BugReport Entity](#3-the-data-model)
4. [The Database Mapping — BugReport.orm.xml](#4-the-database-mapping)
5. [The Manager Layer — Business Logic](#5-the-manager-layer)
6. [The Controller Layer — HTTP Actions](#6-the-controller-layer)
7. [The View Layer — JSP Pages](#7-the-view-layer)
8. [The Notification Subsystem — Full Deep Dive](#8-the-notification-subsystem)
9. [The Email System — How Mail Actually Flows](#9-the-email-system)
10. [Design Patterns — With Deep Justification](#10-design-patterns)
11. [SOLID Principles Applied](#11-solid-principles-applied)
12. [Extensibility — Adding Slack/Teams Without Breaking Anything](#12-extensibility)
13. [Concurrency — Why Emails Don't Block the User](#13-concurrency)
14. [Security and Authorization](#14-security-and-authorization)
15. [Files Added vs Modified — Full Summary](#15-files-added-vs-modified)
16. [End-to-End Flow Walkthroughs](#16-end-to-end-flow-walkthroughs)
17. [TA Likely Questions and Your Strong Answers](#17-ta-likely-questions)

---

## 1. One-Line Summary

You built a **complete bug reporting subsystem** inside Apache Roller that lets users file bug reports through a web form, lets admins manage them via a dashboard, and automatically sends **HTML emails** to both admins and users on every create/update/status-change/delete event — all through a **modular, pattern-driven notification pipeline** that can grow to Slack, Teams, SMS etc. by adding one class at a time.

---

## 2. System Layer Map — How Everything Connects

The system has 4 layers, each talking only to the layer directly below it via **interfaces** (never directly to concrete classes). This is the Dependency Inversion Principle in action.

```
┌──────────────────────────────────────────────────┐
│  VIEW LAYER (JSP pages — what user sees)         │
│  BugReportSubmit.jsp | BugReportList.jsp         │
│  BugReportAdmin.jsp                              │
├──────────────────────────────────────────────────┤
│  CONTROLLER LAYER (Struts2 Actions)              │
│  BugReportSubmit.java | BugReportList.java       │
│  BugReportAdmin.java                             │
├──────────────────────────────────────────────────┤
│  BUSINESS / SERVICE LAYER (Pure Java logic)      │
│  BugReportManager (interface)                    │
│  JPABugReportManagerImpl (concrete)              │
│  BugReportNotificationService (interface)        │
│  └── BugReportNotificationServiceImpl            │
│       └── NotificationDispatcher                │
│            └── NotificationChannel (interface)  │
│                 └── EmailNotificationChannel     │
├──────────────────────────────────────────────────┤
│  PERSISTENCE LAYER (Database)                    │
│  BugReport.java (POJO entity)                    │
│  BugReport.orm.xml (JPA mapping + named queries) │
│  JPAPersistenceStrategy (existing Roller class)  │
└──────────────────────────────────────────────────┘
```

**Why this layering?**
- Controllers know nothing about database queries — they call `manager.saveBugReport()`.
- The manager knows nothing about how emails are formatted — it calls `notificationService.notifyBugCreated()`.
- The notification service knows nothing about SMTP — it calls `channel.send(event)`.
- Each layer is independently testable and replaceable.

---

## 3. The Data Model

**File:** `app/src/main/java/org/apache/roller/weblogger/pojos/BugReport.java`

This is a **plain Java object (POJO)**. No JPA annotations inside the class itself — all mapping is in the XML file. This is intentional (see design decision in Section 4).

### What fields exist and why

| Field | Type | Reason It Exists |
|---|---|---|
| `id` | String (UUID) | Primary key; UUID means globally unique without a DB sequence |
| `creator` | FK → User | Links to the Roller user who filed it |
| `title` | String (max 255) | Short searchable summary |
| `description` | CLOB | Can be very long; stored as database LOB |
| `category` | String constant | Classifies the type: BROKEN_LINK, BROKEN_BUTTON, UI_ISSUE, PERFORMANCE, FUNCTIONALITY, OTHER |
| `severity` | String constant | Priority: LOW, MEDIUM, HIGH, CRITICAL |
| `status` | String constant | Lifecycle: OPEN → IN_PROGRESS → RESOLVED / CLOSED; soft-delete via DELETED |
| `pageUrl` | String (max 2048) | URL of the page where the bug was seen |
| `browserInfo` | String (max 512) | Auto-detected from the browser (Client Hints API in JS) |
| `stepsToReproduce` | CLOB | Optional steps — very useful for devs |
| `screenshotUrl` | String | Link to screenshot or screen recording |
| `adminNotes` | CLOB | Private notes from admin — never shown to users |
| `lastModifiedBy` | FK → User | Audit trail — who last touched it |
| `createdAt` | TIMESTAMP | When filed |
| `updatedAt` | TIMESTAMP | Last modified time |
| `resolvedAt` | TIMESTAMP | Set when status → RESOLVED or CLOSED; cleared if re-opened |
| `optLock` | Integer | JPA optimistic locking version field |

### Status constants (defined as `public static final String` inside the class)

```
STATUS_OPEN        = "OPEN"
STATUS_IN_PROGRESS = "IN_PROGRESS"
STATUS_RESOLVED    = "RESOLVED"
STATUS_CLOSED      = "CLOSED"
STATUS_DELETED     = "DELETED"   ← soft delete, not a real DB delete
```

### Why constants in the class instead of a Java enum?

Roller uses JPA XML mapping with string columns. Using Java enums would require extra JPA `@Enumerated` handling. Plain string constants work cleanly with the XML-mapped `VARCHAR` column and are simpler to pass in JPQL named queries.

### `equals()` and `hashCode()` are based on `id` only

This is the JPA best practice. Two `BugReport` objects are the same report if and only if they have the same UUID id, regardless of what other fields contain.

---

## 4. The Database Mapping

**File:** `app/src/main/resources/org/apache/roller/weblogger/pojos/BugReport.orm.xml`

### Why XML mapping instead of JPA annotations in the class?

Apache Roller's established pattern is to use XML ORM mapping files, not annotations. If we added `@Entity`, `@Table`, `@Column` etc. directly to `BugReport.java`, we'd be mixing persistence concerns into the domain model. The POJO stays clean — it knows nothing about the database. This is the **separation of concerns** principle.

### Database table structure (mapped to `bug_report` table)

| Column Name | Java Field | Type | Constraint |
|---|---|---|---|
| `id` | `id` | VARCHAR(48) | NOT NULL, PRIMARY KEY |
| `optlock` | `optLock` | INTEGER | JPA @Version — optimistic locking |
| `title` | `title` | VARCHAR(255) | NOT NULL |
| `description` | `description` | CLOB/TEXT | NOT NULL |
| `category` | `category` | VARCHAR(50) | NOT NULL |
| `severity` | `severity` | VARCHAR(20) | NOT NULL |
| `status` | `status` | VARCHAR(20) | NOT NULL |
| `pageurl` | `pageUrl` | VARCHAR(2048) | nullable |
| `browserinfo` | `browserInfo` | VARCHAR(512) | nullable |
| `stepstoreproduce` | `stepsToReproduce` | CLOB | nullable |
| `screenshoturl` | `screenshotUrl` | VARCHAR(2048) | nullable |
| `adminnotes` | `adminNotes` | CLOB | nullable |
| `createdat` | `createdAt` | TIMESTAMP | NOT NULL |
| `updatedat` | `updatedAt` | TIMESTAMP | NOT NULL |
| `resolvedat` | `resolvedAt` | TIMESTAMP | nullable |
| `creatorid` | `creator` | FK → useraccount | NOT NULL |
| `lastmodifiedbyid` | `lastModifiedBy` | FK → useraccount | nullable |

### Three database indexes

```xml
<index name="idx_bugreport_creator" column-list="creatorid"/>
<index name="idx_bugreport_status"  column-list="status"/>
<index name="idx_bugreport_created" column-list="createdat"/>
```

**Why these three specifically?**
- `creatorid` index → speeds up "show me MY reports" (user list page query filters by creator)
- `status` index → speeds up admin dashboard filter-by-status queries
- `createdat` index → speeds up ORDER BY createdAt DESC on every list

Without these indexes, every query would be a full table scan, which gets very slow as reports accumulate.

### Named Queries in the ORM XML

All JPQL queries live in the XML, NOT hardcoded as strings in Java:

| Query Name | What It Does |
|---|---|
| `BugReport.getById` | Fetch one report by UUID |
| `BugReport.getByUser` | Fetch non-deleted reports by creator, newest first |
| `BugReport.getByStatus` | Fetch reports matching a status |
| `BugReport.getAll` | Fetch all non-DELETED reports |
| `BugReport.getByStatusAndSeverity` | Admin filter with both dropdowns set |
| `BugReport.getBySeverity` | Filter by severity only |
| `BugReport.countByUser` | Count user's non-deleted reports (for pagination) |
| `BugReport.countByStatus` | Count by status (for dashboard summary cards) |
| `BugReport.countAll` | Count all non-deleted (for admin total) |

**Why named queries in XML and not inline JPQL strings in Java?**

Named queries are compiled and validated by the JPA provider **at application startup**, not at first use. If there's a typo in a query, you find out immediately when the app boots, not when a user accidentally triggers that code path. Inline strings are validated at runtime — bugs hide until someone clicks the right button.

### Optimistic Locking (`optLock` / `optlock`)

```xml
<version name="optLock">
    <column name="optlock"/>
</version>
```

JPA's `<version>` element makes the `optlock` column a version counter. Every time a row is updated, JPA automatically checks that the version in the DB matches what was read. If two admins open the same report and both try to save, the second one will get an `OptimisticLockException` — no silent overwrite.

**Why optimistic (not pessimistic) locking?**
Pessimistic locking locks a database row with a `SELECT FOR UPDATE` — it blocks other users from even reading the row. For an admin dashboard where concurrent edits of the same report are extremely rare, this is overkill. Optimistic locking costs nothing during normal operation and only kicks in when an actual conflict occurs.

---

## 5. The Manager Layer

### 5.1 The Interface

**File:** `app/src/main/java/org/apache/roller/weblogger/business/BugReportManager.java`

The interface defines the **contract** — the list of things the bug report system can do:

```
saveBugReport(BugReport report)
updateBugReport(BugReport report, User modifier)
updateBugReportStatus(String reportId, String newStatus, String adminNotes, User modifier)
removeBugReport(String reportId, User requestingUser)
getBugReport(String id)
getBugReportsByUser(User user, int offset, int length)
getBugReports(String statusFilter, String severityFilter, int offset, int length)
getBugReportCount(String statusFilter)
getBugReportCountByUser(User user)
getOpenBugCount()
getResolvedBugCount()
release()
```

**Why an interface at all?**

Controllers reference `BugReportManager` (the interface), not `JPABugReportManagerImpl` (the implementation). This means:
- You could swap in a `MongoDBBugReportManagerImpl` tomorrow with no changes to any controller.
- You can mock the interface in unit tests without needing a real database.
- It enforces the Dependency Inversion Principle.

### 5.2 The JPA Implementation

**File:** `app/src/main/java/org/apache/roller/weblogger/business/jpa/JPABugReportManagerImpl.java`

This is a Guice `@Singleton` — only one instance exists in the entire application. It's injected everywhere via Guice's dependency injection.

#### Constructor fields

```java
private final Weblogger roller;                                    // to check admin permissions
private final JPAPersistenceStrategy strategy;                     // to do actual DB operations
private final BugReportNotificationService notificationService;    // to fire notifications
```

#### `saveBugReport(BugReport report)` — create flow

1. Validates: `report != null`, `creator != null`, `title` not blank, `description` not blank
2. Sets managed fields: `id` = new UUID, `status` = OPEN, `createdAt` = now, `updatedAt` = now
3. Applies defaults: category = OTHER if null, severity = MEDIUM if null
4. Calls `strategy.store(report)` → INSERT into DB
5. Calls `strategy.flush()` → commits the transaction
6. **After** successful flush: calls `notificationService.notifyBugCreated(report)`
7. The notification call is wrapped in its own try-catch — if email fails, the save is NOT rolled back

**Critical design decision: notification happens AFTER flush**

The database commit is the source of truth. If the email server is down, the bug report still gets saved. The user's data is safe. The notification is best-effort. Without this separation, a down mail server would make it impossible to file a bug report — the worst possible UX for a bug-reporting system.

#### `updateBugReportStatus(String reportId, String newStatus, String adminNotes, User modifier)` — admin status change

1. Validates all inputs are non-null/non-empty
2. Validates `newStatus` is one of the 5 allowed values
3. Fetches the existing report from DB
4. Checks it's not already DELETED
5. Checks `modifier` has `GlobalPermission.ADMIN` — **only admins can change status**
6. Captures `oldStatus` before changing it
7. Sets new status, updatedAt, lastModifiedBy, adminNotes
8. If going to RESOLVED or CLOSED: sets `resolvedAt = now()` (if not already set)
9. If going back to OPEN or IN_PROGRESS: clears `resolvedAt = null`
10. Persists → notification → same post-flush pattern

#### `removeBugReport(String reportId, User requestingUser)` — soft delete

1. Validates inputs
2. Fetches report — if already DELETED, returns silently (idempotent)
3. Checks: `requestingUser` is either the creator OR an admin — throws if neither
4. Sets `status = DELETED`, `updatedAt = now()`, `lastModifiedBy = requestingUser`
5. **Does NOT call `em.remove()`** — the row stays in the DB, just with status=DELETED
6. Fires `notifyBugDeleted` notification

**Why soft delete?**
- Data recovery: if wrong report was deleted, it can be un-deleted by an admin
- Audit trail: history of all reports stays intact
- Foreign key safety: any future tables referencing bug report IDs won't break
- Named query `BugReport.getAll` filters `WHERE status <> 'DELETED'` so deleted records are invisible in all normal views

#### `getBugReports(String statusFilter, String severityFilter, ...)` — smart query routing

This method checks which filters are set and picks the appropriate named query:

```
statusFilter != null  AND  severityFilter != null  →  BugReport.getByStatusAndSeverity
statusFilter != null  AND  severityFilter == null   →  BugReport.getByStatus
statusFilter == null  AND  severityFilter != null   →  BugReport.getBySeverity
both null                                           →  BugReport.getAll
```

This is clean conditional routing. No mega-JPQL with dynamic `WHERE` clauses.

---

## 6. The Controller Layer

### 6.1 BugReportSubmit

**File:** `app/src/main/java/org/apache/roller/weblogger/ui/struts2/core/BugReportSubmit.java`

URL: `GET /roller-ui/bugReport` → show form  
URL: `POST /roller-ui/bugReport!submit` → process form

**Key design decisions:**

`isWeblogRequired()` returns `false` — this form is accessible to any logged-in user, not just blog owners.

`@SkipValidation` on `execute()` — Struts2 otherwise tries to validate the form even on the GET request (to show the empty form). `@SkipValidation` tells it to skip validation and just render the page.

`populateDropdowns()` — builds `LinkedHashMap<String,String>` for category and severity dropdowns. `LinkedHashMap` preserves insertion order, so categories always appear in the same logical order in the UI.

`validateForm()` — manually validates required fields and length limits (title max 255 chars). Returns `true` if valid. If invalid, adds field errors and returns `false` so the form is re-displayed with the user's input still filled in.

The action fields (`title`, `description`, `category`, `severity`, etc.) all have both a getter and a setter. Struts2 requires this for parameter binding — it calls the setter when the form POST comes in.

### 6.2 BugReportList

**File:** `app/src/main/java/org/apache/roller/weblogger/ui/struts2/core/BugReportList.java`

URL: `GET /roller-ui/bugReportList` → show user's own reports  
URL: `POST /roller-ui/bugReportList!deleteReport` → user deletes their own report

`PAGE_SIZE = 20` — pagination constant. `isHasMore()` computes whether there's a next page: `(page + 1) * 20 < totalReports`.

`deleteReport()` — after successful deletion, calls `return execute()` which reloads the list. This way the user is shown the updated list without a redirect, preserving their current page position.

Why `@AllowedMethods({"execute","deleteReport"})?` — Struts2 security feature. Only these two method names can be called on this action via URL. Without this, any method on the action class could be invoked.

### 6.3 BugReportAdmin

**File:** `app/src/main/java/org/apache/roller/weblogger/ui/struts2/admin/BugReportAdmin.java`

URL: `GET /roller-ui/admin/bugReportAdmin` → admin dashboard  
URL: `POST /roller-ui/admin/bugReportAdmin!update` → change status  
URL: `POST /roller-ui/admin/bugReportAdmin!delete` → soft-delete

**Admin-only enforcement:**

```java
@Override
public List<String> requiredGlobalPermissionActions() {
    return Collections.singletonList(GlobalPermission.ADMIN);
}
```

This is the Roller framework pattern — override `requiredGlobalPermissionActions()` and return `["admin"]`. The Struts2 interceptor checks this before the action even runs. Non-admins get a 403.

**loadDashboard()** — called from `execute()`, `update()`, and `delete()`. After any operation, the dashboard is reloaded with fresh data so the admin immediately sees the updated state.

**Why does `update()` call `mgr.updateBugReportStatus()` rather than building the notification itself?**

All notification logic lives in the manager. The controller only knows about HTTP concerns — form parameters, action results. If the controller also sent emails, you'd have the same email logic duplicated in multiple places. Manager handles it all.

---

## 7. The View Layer

### 7.1 BugReportSubmit.jsp

**File:** `app/src/main/webapp/WEB-INF/jsps/core/BugReportSubmit.jsp`

Two-panel Bootstrap 3 form:
- **Panel 1 (required):** Title, Category dropdown, Severity dropdown, Description
- **Panel 2 (optional, collapsible):** Steps to Reproduce, Page URL, Browser Info, Screenshot URL

**Browser auto-detection in JavaScript:**
The form includes a `<script>` block that runs on page load:
1. Tries `navigator.userAgentData.brands` first — this is the modern **Client Hints API** (Chrome 90+). It returns a clean list like "Chrome 120 on Windows".
2. Falls back to regex parsing of `navigator.userAgent` for Firefox, Safari, Chrome on older browsers.
3. Writes the result into the `browserInfo` hidden-ish text field automatically.

**Severity hint:**
When the user selects a severity from the dropdown, a `change` event listener shows a colored Bootstrap badge + description:
- CRITICAL → red badge: "System unusable or data loss"
- HIGH → orange badge: "Major feature broken, no workaround"
- MEDIUM → blue badge: "Feature partially broken, workaround exists"
- LOW → gray badge: "Minor, cosmetic or trivial"

This helps users choose the right severity without guessing.

### 7.2 BugReportList.jsp

**File:** `app/src/main/webapp/WEB-INF/jsps/core/BugReportList.jsp`

Shows the logged-in user's own reports as Bootstrap cards. Each card shows:
- Title, category badge, severity badge (color-coded), status badge
- Created date, description snippet
- Delete button (POST to `!deleteReport` with `reportId`)

Cards only show reports belonging to the current user. The `getBugReportsByUser()` query enforces this at the database level — not just in the UI.

### 7.3 BugReportAdmin.jsp

**File:** `app/src/main/webapp/WEB-INF/jsps/admin/BugReportAdmin.jsp`

Admin-only dashboard showing:

**Summary row:** 4 Bootstrap stat cards showing live counts of OPEN / IN_PROGRESS / RESOLVED / CLOSED reports. These counts come from separate `getBugReportCount()` calls in `loadDashboard()`.

**Filter bar:** Status dropdown + Severity dropdown → clicking Filter POSTs back to `execute()` with the chosen filters.

**Report cards:** One Bootstrap card per report. Each card has:
- Header: title, severity badge, status badge
- Body: reporter, category, dates, description snippet
- Admin action buttons: status dropdown + "Update" button, "Delete" button

**XSS prevention:** The admin status modal populates fields using jQuery's `.text()` method (not `.html()`). Even if a report title contained `<script>alert(1)</script>`, `.text()` treats it as plain text — it gets HTML-encoded and displayed safely.

---

## 8. The Notification Subsystem — Full Deep Dive

This is the most architecturally interesting part of the system. Read this carefully.

### Overview of the subsystem

```
JPABugReportManagerImpl
    │ calls
    ▼
BugReportNotificationService  (interface)
    │ implemented by
    ▼
BugReportNotificationServiceImpl
    │ builds NotificationEvent, then calls
    ▼
NotificationDispatcher
    │ has a list of registered NotificationChannels, calls each
    ▼
NotificationChannel  (interface)
    │ implemented by
    ▼
EmailNotificationChannel
    │ uses
    ├── AdminEmailResolver      (resolves admin emails from UserManager)
    ├── EmailTemplateRenderer   (picks HTML template for event × audience)
    │       │ uses
    │       └── EmailBodyBuilder  (builds the HTML email body)
    └── MailUtil.sendHTMLMessage  (Roller's existing SMTP sender)
```

### 8.1 `BugReportNotificationService` — the interface

**File:** `app/src/main/java/org/apache/roller/weblogger/business/notification/BugReportNotificationService.java`

Four methods, one per event type:

```
notifyBugCreated(BugReport report)
notifyBugUpdated(BugReport report, User modifier)
notifyBugStatusChanged(BugReport report, String oldStatus, String newStatus)
notifyBugDeleted(BugReport report, User deletedBy)
```

**Why an interface?** The manager depends on this interface, not the implementation. You could write a `TestNotificationService` that just logs to a file, swap it in for testing, and not touch the manager at all.

### 8.2 `BugReportNotificationServiceImpl` — the wiring hub

**File:** `app/src/main/java/org/apache/roller/weblogger/business/notification/BugReportNotificationServiceImpl.java`

This class does two things:
1. In its **constructor**, it wires together the entire notification pipeline: creates `AdminEmailResolver` → creates `EmailTemplateRenderer` → creates `EmailNotificationChannel` → creates `NotificationDispatcher` → registers the email channel with the dispatcher.
2. In each `notify*()` method, it creates a `NotificationEvent` using the Builder pattern and calls `dispatcher.dispatch(event)`.

**Future extension is visible here:**
```java
this.dispatcher.registerChannel(emailChannel);
// Future: dispatcher.registerChannel(new SlackNotificationChannel(...));
// Future: dispatcher.registerChannel(new TeamsNotificationChannel(...));
```

### 8.3 `NotificationEvent` — the data carrier

**File:** `app/src/main/java/org/apache/roller/weblogger/business/notification/model/NotificationEvent.java`

This is an **immutable** value object. Once built, nothing can change it. Fields:

```
EventType type    → BUG_CREATED | BUG_UPDATED | BUG_STATUS_CHANGED | BUG_DELETED
BugReport report  → the report this event is about
User actor        → who triggered the event (creator, modifier, deleter)
String oldStatus  → only for STATUS_CHANGED events
String newStatus  → only for STATUS_CHANGED events
```

Built via a **Builder** (see Section 10.3). The private constructor takes only a `Builder` — you cannot create a `NotificationEvent` any other way.

### 8.4 `NotificationDispatcher` — the async router

**File:** `app/src/main/java/org/apache/roller/weblogger/business/notification/NotificationDispatcher.java`

This class:
- Holds a `CopyOnWriteArrayList<NotificationChannel>` — thread-safe because multiple background threads may be reading the list while new channels are registered
- Holds an `ExecutorService` backed by a fixed thread pool (default 4 threads)
- On `dispatch(event)`: iterates over channels, skips disabled ones, submits each enabled channel's `send(event)` call to the thread pool

**Why `CopyOnWriteArrayList`?**
The regular `ArrayList` is not thread-safe. If one thread adds a channel while another thread is iterating the list during dispatch, you get a `ConcurrentModificationException`. `CopyOnWriteArrayList` solves this by making a fresh copy of the array on every write — reads see a stable snapshot.

**Sync vs Async mode:**
The dispatcher reads `notification.dispatch.mode` from config properties:
- `async` (default) → uses thread pool, HTTP request returns immediately
- `sync` → sends directly in the calling thread, useful for integration tests where you need to assert the email was sent before the test assertion runs

**Graceful shutdown:**
```java
executor.shutdown();
executor.awaitTermination(5, TimeUnit.SECONDS);
```
On JVM shutdown (via shutdown hook), the dispatcher gives in-flight notifications 5 seconds to complete before forcing termination. Without this, a clean server restart could lose notifications that were queued but not yet sent.

**Error isolation:**
Each channel's send() is wrapped in its own try-catch inside the submitted task. If the email channel throws (SMTP connection refused), the Slack channel (if registered) still gets the event. Channels cannot kill each other.

### 8.5 `NotificationChannel` — the strategy interface

**File:** `app/src/main/java/org/apache/roller/weblogger/business/notification/channel/NotificationChannel.java`

Three methods:
```
String getChannelName()    → "email", "slack", "teams" etc.
boolean isEnabled()        → checked before each send; can be toggled via config without redeployment
void send(NotificationEvent event) → deliver the notification
```

This is the **Strategy Pattern interface**. The dispatcher doesn't know what's behind `send()` — it just calls it.

### 8.6 `EmailNotificationChannel` — the adapter

**File:** `app/src/main/java/org/apache/roller/weblogger/business/notification/channel/EmailNotificationChannel.java`

Implements `NotificationChannel`. In its `send()` method:

1. Checks `isEnabled()` — reads `notification.bugreport.enabled` from config
2. Switches on `event.getType()`:
   - `BUG_CREATED` → admin email + user confirmation email
   - `BUG_UPDATED` → admin email + user email only if someone else updated (not self-update)
   - `BUG_STATUS_CHANGED` → admin email + user status update email always
   - `BUG_DELETED` → admin email + user email only if someone else deleted (not self-delete)
3. For admin email: calls `adminResolver.resolveAdminEmails()` → `MailUtil.sendHTMLMessage(...)`
4. For user email: validates user has a non-empty email address → `MailUtil.sendHTMLMessage(...)`

**Why not notify the user if they themselves updated/deleted?**  
It's annoying and pointless to email someone "your report was deleted" when they're the one who just clicked Delete. The channel specifically checks: `if (!report.getCreator().getId().equals(event.getActor().getId()))` — only send if actor ≠ creator.

### 8.7 `AdminEmailResolver`

**File:** `app/src/main/java/org/apache/roller/weblogger/business/notification/resolver/AdminEmailResolver.java`

Called at notification time (not at app startup). Flow:
1. Calls `userManager.getUsers(Boolean.TRUE, null, null, 0, -1)` — get all enabled users
2. For each user: checks `userManager.checkPermission(adminPerm, user)` where `adminPerm` = GlobalPermission.ADMIN
3. Collects non-null, non-empty email addresses
4. Returns the list

**Why resolve at dispatch time (not cache at startup)?**
Admin users can be added, removed, or have permissions changed at any time. A cached list from startup would become stale. Resolving at dispatch time ensures the notification always goes to whoever is currently an admin.

**Email validation:**
Invalid/empty addresses are skipped with a warning log, not an exception. This prevents a single bad email from blocking all other admin notifications.

### 8.8 `EmailTemplateRenderer`

**File:** `app/src/main/java/org/apache/roller/weblogger/business/notification/template/EmailTemplateRenderer.java`

One method per event-type × audience combination:

```
renderAdminCreated(event)       → red header, bug details table, description section
renderAdminUpdated(event)       → orange header, who modified, details table
renderAdminStatusChanged(event) → blue header, status flow arrow (OLD → NEW), details
renderAdminDeleted(event)       → gray header, who deleted, soft-delete notice box
renderUserCreated(event)        → green header, confirmation, "what happens next" box
renderUserStatusChanged(event)  → blue header, status flow, dynamic message per new status
renderUserDeleted(event)        → gray header, "your report was removed by an admin"
```

Each method creates a new `EmailBodyBuilder` and chains calls on it to produce HTML.

**Why different templates per audience?**
Admins get operational detail (who filed it, all fields, admin notes). Users get friendly personal messages ("Hi John, thank you for submitting your report"). Same data, totally different framing.

### 8.9 `EmailBodyBuilder`

**File:** `app/src/main/java/org/apache/roller/weblogger/business/notification/template/EmailBodyBuilder.java`

A **fluent builder** for HTML email bodies. The constructor emits a full inline-CSS `<head>` block with styles for `.badge`, `.status-flow`, `.section`, `.notice`, `.info` table etc. Then each method appends more HTML:

```
.header(color, title, subtitle)     → colored top bar
.intro(text)                        → opening paragraph
.reportTable(bugReport)             → data table: Title, Reporter, Category, Severity, Status, Dates
.statusFlow(oldStatus, newStatus)   → arrow diagram: [OPEN] → [RESOLVED]
.section(title, body, borderColor)  → colored-left-border section (Description, Steps, Notes)
.noticeBox(text, bg, border, color) → notice/info box
.actorBox(label, actor, color)      → "Modified by: john" sidebar
.footer(contextNote)                → gray footer bar
.build()                            → returns the complete HTML string
```

Every method returns `this`, so calls chain. If a section's body is blank, `.section()` returns `this` without adding anything — no empty sections in emails.

**Severity badge CSS:** The email contains CSS classes `.sev-CRITICAL`, `.sev-HIGH`, `.sev-MEDIUM`, `.sev-LOW` with red/orange/yellow/green backgrounds. The `reportTable()` method emits `<span class='badge sev-${severity}'>${severity}</span>` — the badge automatically colors itself based on the severity string.

---

## 9. The Email System — How Mail Actually Flows

### Step 0: Configuration

In `roller-custom.properties`, you set:
```properties
mail.configurationType=properties
mail.hostname=smtp.gmail.com
mail.port=587
mail.username=your-email@gmail.com
mail.password=your-app-password
mail.from.address=your-email@gmail.com
mail.smtp.starttls.enable=true
notification.bugreport.enabled=true
notification.dispatch.mode=async
notification.dispatch.poolsize=4
```

### Step 1: `MailProvider` (modified existing file)

**File:** `app/src/main/java/org/apache/roller/weblogger/business/MailProvider.java`

This is an existing Roller file that was **hardened** by your changes. The key fix was changing from `Session.getDefaultInstance()` to `Session.getInstance()` with an `Authenticator`.

**Why this matters:** `Session.getDefaultInstance()` in JavaMail means "return the existing shared Session, ignore my properties if one already exists." For Gmail's STARTTLS on port 587, this causes authentication failures because the STARTTLS flag gets ignored. `Session.getInstance()` creates a **fresh session** with your specific properties every time, and the `Authenticator` callback ensures credentials are passed correctly when the SMTP server asks for them.

The `MailProvider` also does a **fail-fast connection test at startup**:
```java
Transport transport = getTransport();
transport.close();
```
If it can't connect to the SMTP server at startup, the app throws a `StartupException` and refuses to boot. This is better than discovering the mail is broken only when the first bug report arrives.

### Step 2: `MailUtil.sendHTMLMessage()`

**File:** `app/src/main/java/org/apache/roller/weblogger/util/MailUtil.java` (existing, unchanged)

This utility builds a `MimeMessage`, sets recipients, subject, HTML content, and calls `transport.sendMessage()`. It handles partial failures via a retry loop — if sending to 5 addresses where 2 bounce, it retries with the remaining 3 valid addresses.

Your notification system calls:
```java
MailUtil.sendHTMLMessage(from, to[], null, null, subject, htmlBody)
```

### Step 3: Transport reuse and SMTP connection

`MailProvider.getTransport()` returns a connected `Transport` object. `MailUtil.sendMessage()` calls `transport.close()` in a `finally` block — each email gets its own SMTP connection. For low-volume bug traffic this is fine. High-volume systems would maintain a persistent connection, but that's premature optimization here.

### Step 4: Async execution

`EmailNotificationChannel.send(event)` is called from a background thread submitted to the `ExecutorService`. So the sequence is:

```
HTTP request thread:
  ├── User posts form
  ├── Manager saves to DB (synchronous, blocking)
  ├── Manager flushes transaction (synchronous)
  ├── Manager calls notificationService.notifyBugCreated()
  │       └── dispatcher.dispatch(event)
  │               └── executor.submit(task)   ← RETURNS IMMEDIATELY
  └── Returns redirect to user's list page

Background thread (from pool):
  └── Runs EmailNotificationChannel.send(event)
          ├── Get admin emails from DB
          ├── Render admin HTML email
          ├── sendHTMLMessage to admins  ← actual SMTP call, may take 200-500ms
          ├── Render user HTML email
          └── sendHTMLMessage to user
```

The user never waits for the SMTP call. Their browser gets the redirect in milliseconds.

### Email events and their triggers

| Event | Admin gets | User gets |
|---|---|---|
| Bug filed | "New Bug Report" (red header) | "Bug Report Received — confirmation" (green) |
| Admin updates content | "Bug Report Updated" (orange) | "Your Bug Was Updated" only if admin did it |
| Status changes | "Bug Status Changed" (blue, with flow arrow) | "Update on Your Bug Report" with context message |
| Report deleted | "Bug Report Deleted" (gray) | "Your Report Was Removed" only if admin deleted it |

---

## 10. Design Patterns — With Deep Justification

### 10.1 Strategy Pattern

**What:** Define a family of algorithms, put each in a class, make them interchangeable.

**Where:** `NotificationChannel` interface + `EmailNotificationChannel` implementation (and future Slack, Teams etc.)

**The actual problem it solves:**  
Before the pattern, you'd write:
```java
if (channel == "email") { sendEmail(...); }
else if (channel == "slack") { callSlackWebhook(...); }
else if (channel == "teams") { callTeamsWebhook(...); }
```
This is an `if/else` tree that grows every time you add a channel. Adding Slack means editing this method — which risks breaking email. The open/closed principle is violated.

**With Strategy:**  
The dispatcher does `channel.send(event)` — it doesn't know or care what `send()` does inside. Email channel makes SMTP calls. Slack channel would call a webhook. Teams channel would post an Adaptive Card. Each is independent.

**Without Strategy:** Every new channel type requires modifying the dispatcher, the service, and potentially the manager. One change breaks three classes. It violates Single Responsibility — the dispatcher shouldn't know about SMTP or REST webhooks.

**What you tell the TA:** "Strategy lets me add a Slack channel by writing one new class. Zero existing classes change. The dispatcher iterates over a list of channel objects and calls `send()` on each. It doesn't know what's behind that interface, so adding another implementation is invisible to it."

---

### 10.2 Observer Pattern

**What:** Objects subscribe to events from a subject. The subject notifies all observers without knowing who they are.

**Where:** `JPABugReportManagerImpl` (subject/event producer) → `NotificationDispatcher` (event router) → `NotificationChannel` list (observers/subscribers)

**The actual problem it solves:**  
The manager should focus on one thing: database operations for bug reports. If the manager also contained email-sending logic, any change to email format would require touching the same class that handles bug persistence. That's two reasons to change one class.

With Observer, the manager fires a generic event (`notifyBugCreated(report)`). It doesn't know there's email involved. It doesn't know how many listeners there are. Tomorrow, if you unregister the email channel and register Slack instead, the manager is completely blind to this — it still just fires the event.

**Without Observer:** The manager would hardcode calls to `EmailNotificationChannel`. Adding Slack would mean adding another hardcoded call. Removing email would mean finding all the places email is mentioned in the manager. The manager becomes a coordination nightmare.

**What you tell the TA:** "The manager is the subject. It doesn't know who's listening. The dispatcher holds the observer list. The manager just calls `notificationService.notifyBugCreated()` after a save, and every registered channel automatically picks it up. This is exactly the Observer pattern — decoupled producer from consumers."

---

### 10.3 Builder Pattern

**Where 1: `NotificationEvent.Builder`**

`NotificationEvent` is immutable — all fields set at construction time. A `BUG_STATUS_CHANGED` event needs `oldStatus` and `newStatus` which don't exist for a `BUG_CREATED` event. If you had a constructor with all 5 fields, you'd call it as `new NotificationEvent(type, report, actor, null, null)` for a created event — 2 meaningless nulls. The Builder makes this:
```java
new NotificationEvent.Builder()
    .type(EventType.BUG_CREATED)
    .report(report)
    .actor(creator)
    .build()
```
Only relevant fields are set. Self-documenting. No null confusion.

**Where 2: `EmailBodyBuilder`**

HTML emails have complex structure: header, intro, table, sections, notice boxes, footer. Different events need different combinations of these sections. Without a builder, you'd need dozens of overloaded `buildEmail()` methods (one per combination) or a huge method with many nullable parameters. The builder lets you compose exactly the sections you need:
```java
new EmailBodyBuilder()
    .header("#d9534f", "New Bug Report", "subtitle")
    .reportTable(bugReport)
    .section("Description", report.getDescription(), "#d9534f")
    .footer("Confirmation")
    .build()
```
Adding a new email section type tomorrow means adding one method to `EmailBodyBuilder` — all existing call sites are unaffected.

---

### 10.4 Adapter Pattern

**What:** Wrap an existing incompatible interface to make it compatible with a new interface.

**Where:** `EmailNotificationChannel` adapts Roller's existing `MailUtil.sendHTMLMessage()` to the `NotificationChannel.send(NotificationEvent)` interface.

**The mismatch:**
- `NotificationChannel.send()` takes a `NotificationEvent` object
- `MailUtil.sendHTMLMessage()` takes `(String from, String[] to, String[] cc, String[] bcc, String subject, String htmlBody)`

These two are structurally incompatible. `EmailNotificationChannel` bridges the gap:
1. Takes the `NotificationEvent`
2. Calls `adminResolver` to get `to[]` addresses
3. Calls `renderer` to get the `htmlBody` string
4. Constructs the `from` address from config
5. Calls `MailUtil.sendHTMLMessage(from, to, null, null, subject, html)`

**Why this is Adapter:** The existing `MailUtil` doesn't need to change. The notification infrastructure doesn't need to know how Roller sends email internally. The adapter translates between the two worlds.

---

## 11. SOLID Principles Applied

### S — Single Responsibility

Every class has one job:
- `BugReport.java` → just a data holder
- `JPABugReportManagerImpl` → bug report lifecycle business logic only
- `NotificationDispatcher` → routing events to the right channels
- `EmailNotificationChannel` → email-specific delivery only
- `AdminEmailResolver` → the specific task of finding admin emails
- `EmailBodyBuilder` → building HTML only
- `EmailTemplateRenderer` → selecting and composing the right template for each event

None of these cross into each other's responsibilities.

### O — Open/Closed

The notification system is **open for extension** (add new channels) and **closed for modification** (don't change existing code to do so). Adding Slack requires one new class — no existing class changes.

### L — Liskov Substitution

Any class that implements `NotificationChannel` can be dropped into the channel list without breaking the dispatcher. A `SlackNotificationChannel` is a valid substitute for `EmailNotificationChannel` because both satisfy the same interface contract.

### I — Interface Segregation

`NotificationChannel` has exactly 3 methods — all of them are needed by every implementer. No method that makes no sense for some channels. If a future channel needed radically different behavior, a separate interface could be introduced without bloating the existing one.

### D — Dependency Inversion

`JPABugReportManagerImpl` depends on `BugReportNotificationService` (interface), not `BugReportNotificationServiceImpl`. Controllers depend on `BugReportManager` (interface), not the JPA implementation. High-level modules depend on abstractions, never on concretions.

---

## 12. Extensibility

### How you'd add Slack in exactly 3 steps

**Step 1: Write one new class**

```java
// File: .../notification/channel/SlackNotificationChannel.java
public class SlackNotificationChannel implements NotificationChannel {
    private final String webhookUrl;

    public SlackNotificationChannel(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @Override public String getChannelName() { return "slack"; }

    @Override public boolean isEnabled() {
        return "true".equals(WebloggerConfig.getProperty("notifications.slack.enabled"));
    }

    @Override public void send(NotificationEvent event) {
        String text = buildSlackText(event);   // format for Slack
        // make HTTP POST to webhookUrl with JSON payload
    }
}
```

**Step 2: Register in `BugReportNotificationServiceImpl` constructor** (the only existing line that changes)

```java
dispatcher.registerChannel(new SlackNotificationChannel(
    WebloggerConfig.getProperty("notifications.slack.webhookUrl")
));
```

**Step 3: Add config properties**

```properties
notifications.slack.enabled=true
notifications.slack.webhookUrl=https://hooks.slack.com/services/...
```

**What happens?** The dispatcher's `for (channel : channels)` loop now includes the Slack channel. Every bug event automatically notifies Slack too. Email continues exactly as before — no email code was touched.

**For Teams:** Identical process. The payload format is different (Teams uses Adaptive Cards JSON, Slack uses Block Kit JSON) but that's entirely inside the new class.

**Why this works:** The dispatcher iterates `List<NotificationChannel>` and calls `send()` on each enabled entry. The list grows by registration. The dispatcher never needs to know what concrete types are in the list.

---

## 13. Concurrency

### Why async matters

Sending an email via SMTP can take 100–500 milliseconds or more if the relay is under load or if SPF/DKIM verification is slow. If the HTTP request thread had to wait for the email to send before returning the redirect to the user, every bug submission would feel noticeably slow.

### Thread pool (ExecutorService)

```java
this.executor = Executors.newFixedThreadPool(pool);  // default: 4
```

When `dispatch(event)` is called, it does:
```java
executor.submit(() -> channel.send(event));
```
The `submit()` call returns immediately. The task goes into the executor's queue. One of the 4 worker threads picks it up and runs `send()` in the background. The HTTP request thread is already done and the user sees their confirmation page.

### Transaction safety

Notifications are triggered **only after `strategy.flush()` succeeds**. The notification call is in a try-catch that logs but does not rethrow. This guarantees:
1. If the DB commit fails → notification never fires → consistent (no email about a report that wasn't saved)
2. If the notification fails → the DB commit is not rolled back → the bug report is saved, email is best-effort

### Graceful shutdown

```java
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);
}));
```

When Tomcat stops, the JVM runs shutdown hooks. The dispatcher tells the thread pool to stop accepting new tasks and gives in-flight tasks 5 seconds to finish. After 5 seconds, it forces shutdown. This prevents email loss on clean server restarts.

---

## 14. Security and Authorization

### Two-layer authorization

Authorization is enforced at **both** the controller level **and** the manager level, not just one.

- Controller level: `requiredGlobalPermissionActions()` = `["admin"]` on `BugReportAdmin` — Struts2 interceptor checks this before the action runs at all.
- Manager level: `isAdminUser(modifier)` check inside `updateBugReportStatus()` and in `removeBugReport()`.

Even if someone somehow bypassed the controller check (e.g., by calling the manager directly from a servlet they injected), the manager would still refuse unauthorized operations.

### Authorization matrix

| Operation | Who | Enforcement |
|---|---|---|
| Submit bug | Any logged-in user | Roller's `requiredLogin()` base action interceptor |
| View own bugs | Creator only | DB query filters by creator ID |
| Delete own bug | Creator only | Manager compares `report.getCreator().getId().equals(requestingUser.getId())` |
| Admin view all | ADMIN permission | `requiredGlobalPermissionActions()` Struts2 check |
| Admin status change | ADMIN permission | Manager `isAdminUser()` check |
| Admin delete | ADMIN permission | Manager `isAdminUser()` check |

### XSS prevention

The admin detail modal uses jQuery's `.text()` to write data from hidden spans into the modal. `.text()` treats everything as plain text, HTML-encodes it before inserting into the DOM. A report with `<script>alert(1)</script>` as its title will display literally as `<script>alert(1)</script>` — the browser won't execute it.

---

## 15. Files Added vs Modified

### New files (you wrote these from scratch)

| File | Package/Path | What It Is |
|---|---|---|
| `BugReport.java` | `pojos/` | Entity POJO |
| `BugReportManager.java` | `business/` | Manager interface |
| `JPABugReportManagerImpl.java` | `business/jpa/` | JPA manager implementation |
| `BugReportSubmit.java` | `ui/struts2/core/` | Submit form action |
| `BugReportList.java` | `ui/struts2/core/` | User list action |
| `BugReportAdmin.java` | `ui/struts2/admin/` | Admin dashboard action |
| `BugReportNotificationService.java` | `business/notification/` | Notification service interface |
| `BugReportNotificationServiceImpl.java` | `business/notification/` | Service + wiring hub |
| `NotificationDispatcher.java` | `business/notification/` | Async dispatcher + thread pool |
| `NotificationChannel.java` | `business/notification/channel/` | Strategy interface |
| `EmailNotificationChannel.java` | `business/notification/channel/` | Email strategy implementation |
| `NotificationEvent.java` | `business/notification/model/` | Immutable event DTO + Builder |
| `AdminEmailResolver.java` | `business/notification/resolver/` | Admin email lookup |
| `EmailBodyBuilder.java` | `business/notification/template/` | Fluent HTML email builder |
| `EmailTemplateRenderer.java` | `business/notification/template/` | Template selector/composer |
| `BugReport.orm.xml` | `resources/.../pojos/` | JPA mapping + named queries |
| `BugReportSubmit.jsp` | `webapp/WEB-INF/jsps/core/` | Submit form view |
| `BugReportList.jsp` | `webapp/WEB-INF/jsps/core/` | User list view |
| `BugReportAdmin.jsp` | `webapp/WEB-INF/jsps/admin/` | Admin dashboard view |

### Modified files (existing Roller files you changed)

| File | What Changed |
|---|---|
| `business/Weblogger.java` | Added `getBugReportManager()` and `getBugReportNotificationService()` methods to the central factory interface |
| `resources/META-INF/persistence.xml` | Added `BugReport.orm.xml` as a mapping file entry so JPA discovers it |
| `resources/struts.xml` | Added 3 action definitions: `bugReport`, `bugReportList`, `bugReportAdmin` |
| `webapp/WEB-INF/tiles.xml` | Added 3 tile definitions (Roller's view compositing mechanism) |
| `resources/ApplicationResources.properties` | Added ~35 i18n key-value pairs for all UI text, error messages, email subjects |
| `business/MailProvider.java` | Fixed `Session.getDefaultInstance()` → `Session.getInstance()` with Authenticator; added STARTTLS/SSL trust/TLSv1.2 config; added fail-fast connection test at startup |

---

## 16. End-to-End Flow Walkthroughs

### Flow 1: User Files a Bug Report

```
1. User navigates to /roller-ui/bugReport
2. GET → BugReportSubmit.execute() → populateDropdowns() → returns INPUT
3. JSP renders the two-panel form
4. JavaScript auto-fills browserInfo field with detected browser + OS
5. User fills title, picks category/severity, writes description
6. User clicks Submit → POST /roller-ui/bugReport!submit

7. BugReportSubmit.submit() runs:
   ├── getAuthenticatedUser() → current logged-in User
   ├── validateForm() → checks title/desc/category/severity not blank
   ├── Creates new BugReport(), sets all fields from form params
   └── mgr.saveBugReport(report)

8. JPABugReportManagerImpl.saveBugReport():
   ├── Validates inputs (null checks)
   ├── report.setId(UUIDGenerator.generateUUID())
   ├── report.setStatus("OPEN")
   ├── report.setCreatedAt(new Date())
   ├── strategy.store(report)  →  DB INSERT
   ├── strategy.flush()        →  COMMIT
   └── notificationService.notifyBugCreated(report)  ← after flush

9. BugReportNotificationServiceImpl.notifyBugCreated():
   ├── Builds NotificationEvent(type=BUG_CREATED, report, actor=creator)
   └── dispatcher.dispatch(event)

10. NotificationDispatcher.dispatch():
    └── executor.submit(task)  ← RETURNS IMMEDIATELY
        HTTP thread continues → redirect to bugReportList

11. [Background thread] EmailNotificationChannel.send(event):
    ├── adminResolver.resolveAdminEmails() → list of admin email addresses
    ├── renderer.renderAdminCreated(event) → red-header HTML email
    ├── MailUtil.sendHTMLMessage(admins, "New Bug Report", html)
    ├── renderer.renderUserCreated(event) → green-header HTML email
    └── MailUtil.sendHTMLMessage([creator.email], "Bug Report Received", html)

12. User sees their new report on the list page (status = OPEN)
```

---

### Flow 2: Admin Changes Status to RESOLVED

```
1. Admin visits /roller-ui/admin/bugReportAdmin
2. Dashboard loads with summary cards (open/in-progress/resolved counts)
3. Admin selects "RESOLVED" from the status dropdown on a report card
4. Admin adds optional note in the notes field
5. Admin clicks Update → POST /roller-ui/admin/bugReportAdmin!update

6. BugReportAdmin.update():
   ├── Validates reportId and newStatus not blank
   ├── Fetches report to confirm it exists
   └── mgr.updateBugReportStatus(reportId, "RESOLVED", adminNotes, currentAdmin)

7. JPABugReportManagerImpl.updateBugReportStatus():
   ├── Validates all inputs
   ├── Validates "RESOLVED" is a valid status value
   ├── Fetches report from DB
   ├── Checks report is not already DELETED
   ├── isAdminUser(modifier) → true  (passes)
   ├── oldStatus = "OPEN" (captured before changing)
   ├── report.setStatus("RESOLVED")
   ├── report.setResolvedAt(new Date())
   ├── report.setAdminNotes(adminNotes)
   ├── strategy.store() → DB UPDATE
   ├── strategy.flush() → COMMIT
   └── notificationService.notifyBugStatusChanged(report, "OPEN", "RESOLVED")

8. [Background thread] EmailNotificationChannel.send():
   ├── Admin email: "Bug Status Changed" (blue header, OPEN → RESOLVED arrow)
   └── User email: "Update on Your Bug Report"
           └── renderUserStatusChanged: sees newStatus="RESOLVED"
               → adds green notice box: "Resolved! Your bug has been fixed."

9. Admin dashboard reloads with updated report card
```

---

### Flow 3: User Deletes Their Own Report

```
1. User on /roller-ui/bugReportList clicks Delete on their own report
2. POST /roller-ui/bugReportList!deleteReport?reportId=<uuid>

3. BugReportList.deleteReport():
   ├── currentUser = authenticated user
   └── mgr.removeBugReport(reportId, currentUser)

4. JPABugReportManagerImpl.removeBugReport():
   ├── getBugReport(reportId) → fetches from DB
   ├── isAdmin(currentUser) → false (it's a regular user)
   ├── isOwner: report.getCreator().getId().equals(currentUser.getId()) → true ✓
   ├── report.setStatus("DELETED")
   ├── strategy.store() → DB UPDATE (NOT DELETE)
   ├── strategy.flush()
   └── notificationService.notifyBugDeleted(report, currentUser)

5. [Background thread] EmailNotificationChannel.send():
   ├── Admin email: "Bug Report Deleted" (gray header)
   └── User email: NOT sent (actor == creator → self-delete, no notification)

6. BugReportList.deleteReport() returns execute() → reloads list
7. Report is gone from user's list (query excludes DELETED status)
```

---

## 17. TA Likely Questions and Your Strong Answers

**Q: Why did you use Strategy pattern for notifications and not just a list of if-else checks?**

A: An if-else chain over channel types means every new channel requires modifying the dispatch logic — violating the Open/Closed Principle. The Strategy pattern puts each channel's delivery logic in its own class. The dispatcher calls `channel.send(event)` on whatever is in the list. Adding a Slack channel means writing one class and registering it — the dispatcher, the event service, and the manager are untouched.

---

**Q: How does your design allow adding Slack without changing existing code?**

A: Implement `NotificationChannel` in a new `SlackNotificationChannel` class. Register it in `BugReportNotificationServiceImpl`'s constructor with one line: `dispatcher.registerChannel(new SlackNotificationChannel(...))`. Add config properties. The dispatcher iterates `List<NotificationChannel>` and calls `send()` — it doesn't care how many are in the list or what type they are.

---

**Q: Why fire the notification AFTER the database flush?**

A: Because the database is the source of truth. If the DB commit fails, we should not have sent an email about a report that doesn't exist. If the email fails, the DB commit should not be rolled back — the user's data must be safe. The try-catch around the notification call ensures email failure logs a warning and stops there — it never propagates to the HTTP request thread.

---

**Q: What is soft delete and why did you use it?**

A: Soft delete sets `status = 'DELETED'` instead of running `DELETE FROM bug_report`. The row stays in the database. Benefits: data recovery is possible, audit trail is intact, future foreign keys won't break, admins can still see deleted reports with the right filter. The named query `BugReport.getAll` filters `WHERE status <> 'DELETED'` so deleted records are invisible in normal operation.

---

**Q: What is optimistic locking and why not pessimistic?**

A: Optimistic locking uses a version counter. On update, JPA checks if the version in the DB matches what was read. Two concurrent updates on the same report = one succeeds, the other gets `OptimisticLockException`. Pessimistic locking would lock the row with `SELECT FOR UPDATE` — blocks other reads/writes. For an admin dashboard where concurrent edits of the same report are statistically very rare, pessimistic locking is unnecessary overhead. Optimistic locking costs nothing during normal operation.

---

**Q: Why define all JPQL queries as named queries in XML instead of inline strings in Java?**

A: Named queries are compiled and validated by the JPA provider at application startup. Inline JPQL strings are compiled on first execution — a typo in a query stays hidden until that specific code path runs. Named queries also keep persistence concerns out of Java business logic.

---

**Q: Why does the email channel resolve admin emails at notification time instead of caching them?**

A: Admin users can be granted or revoked admin permissions at any time while the application is running. A cached list from startup would become stale. Resolving at dispatch time ensures every notification goes to whoever is currently an admin, not whoever was admin when the app started.

---

**Q: Why use a thread pool for notifications?**

A: SMTP sends are I/O-bound operations taking 100–500ms. Blocking the HTTP request thread for this duration would add noticeable latency to every bug submission and status update. The thread pool submits the notification task and returns immediately. The user's browser gets the redirect response in milliseconds. The email is sent in the background by one of the pool's worker threads.

---

**Q: What happens if the email server is down?**

A: The background thread's `catch (Exception e)` logs the error and returns. No exception propagates back. The HTTP request has already completed. The database commit is intact. The user's bug report is saved. Email is best-effort; the DB is the source of truth.

---

*End of deep-dive reference document. Total system: 19 new files + 6 modified files, 4 design patterns, 4 SOLID principles, 1 complete async notification pipeline.*

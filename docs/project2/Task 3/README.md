# Task 3 — Bug Reports  
---

## Table of Contents

1. [Feature Overview](#1-feature-overview)
2. [System Architecture](#2-system-architecture)
3. [Component Breakdown](#3-component-breakdown)
4. [Design Patterns](#4-design-patterns)
5. [Software Engineering Principles](#5-software-engineering-principles)
6. [Extensibility — Adding New Notification Channels](#6-extensibility--adding-new-notification-channels)
7. [Concurrency — Async Notification via Thread Pool](#7-concurrency--async-notification-via-thread-pool)
8. [Database Design Decisions](#8-database-design-decisions)
9. [Security and Authorization](#9-security-and-authorization)
10. [Trade-offs and Justifications](#10-trade-offs-and-justifications)
11. [End-to-End Request Flow](#11-end-to-end-request-flow)
12. [Summary of Files Added / Modified](#12-summary-of-files-added--modified)

---

## 1. Feature Overview

Task 3 adds a full bug-reporting subsystem to Apache Roller. The feature satisfies the following requirements:

| Requirement | How It Is Met |
|---|---|
| Users submit issues | Dedicated form page (`/roller-ui/bugReport`) with category, severity, browser auto-detection |
| Admin dashboard | Filterable card-based dashboard at `/roller-ui/admin/bugReportAdmin` |
| Admin can view, change status, delete | Inline status editor with notes; soft-delete button on each card |
| Users can delete their own reports | Delete action on the user list page with owner-only authorization |
| Email admins on every event | Async email channel fires on create / update / status-change / delete |
| Email reporter on relevant events | Reporter receives confirmation on create, status-change notification, and "your report was removed" if an admin deletes it |
| Modular — new channels (Slack, Teams) without touching existing code | Strategy + Observer architecture; one new class + one registration line to add any channel |

---

## 2. System Architecture

The implementation is divided into four clearly separated layers. Each layer depends only on the layer below it via interfaces, never on concrete implementations directly.

```
┌─────────────────────────────────────────────────────┐
│                  View Layer                          │
│  BugReportSubmit.jsp  BugReportList.jsp              │
│  BugReportAdmin.jsp   (Struts2 / Bootstrap 3)        │
├─────────────────────────────────────────────────────┤
│               Controller Layer                       │
│  BugReportSubmit.java  BugReportList.java            │
│  BugReportAdmin.java   (Struts2 UIActions)           │
├─────────────────────────────────────────────────────┤
│               Business / Service Layer               │
│  BugReportManager (interface)                        │
│  JPABugReportManagerImpl (Guice @Singleton)          │
│  ├── BugReportNotificationService (interface)        │
│  └── BugReportNotificationServiceImpl                │
│       └── NotificationDispatcher                     │
│            └── NotificationChannel (interface)       │
│                 └── EmailNotificationChannel         │
├─────────────────────────────────────────────────────┤
│               Persistence Layer                      │
│  BugReport.java (POJO entity)                        │
│  BugReport.orm.xml (JPA XML mapping)                 │
│  JPAPersistenceStrategy                              │
└─────────────────────────────────────────────────────┘
```

The notification subsystem is fully decoupled from the persistence layer. The manager fires a notification only *after* the database flush succeeds, wrapped in a try-catch so that a mail server being unavailable never rolls back a database transaction.

---

## 3. Component Breakdown

### 3.1 Entity — `BugReport.java`

A plain Java object with no JPA annotations; all mapping is done in `BugReport.orm.xml`. This keeps the domain model free of framework coupling.

**Key fields:**

| Field | Type | Purpose |
|---|---|---|
| `id` | UUID String | Primary key, generated on save |
| `creator` | FK → User | Who filed the report |
| `title`, `description` | String / CLOB | Core content |
| `category` | String enum | BROKEN_LINK, BROKEN_BUTTON, UI_ISSUE, PERFORMANCE, FUNCTIONALITY, OTHER |
| `severity` | String enum | LOW, MEDIUM, HIGH, CRITICAL |
| `status` | String enum | OPEN → IN_PROGRESS → RESOLVED/CLOSED; soft-deleted via DELETED |
| `pageUrl`, `browserInfo` | String | Diagnostic context |
| `stepsToReproduce` | CLOB | Reproduction steps |
| `adminNotes` | CLOB | Private admin-only field |
| `lastModifiedBy` | FK → User | Audit trail |
| `createdAt`, `updatedAt`, `resolvedAt` | TIMESTAMP | Full temporal tracking |
| `optLock` | Integer | Optimistic locking version |

### 3.2 Manager — `BugReportManager` / `JPABugReportManagerImpl`

The manager interface is the only thing controllers ever reference. It exposes operations for:

- Creating, updating, and soft-deleting reports
- Querying by user (paginated), by status/severity (admin), and summary counts
- Triggering notifications (delegated to `BugReportNotificationService`)

The JPA implementation is bound via Guice and is a `@Singleton`. It enforces authorization (`GlobalPermission.ADMIN` required for status changes and admin deletes), validates inputs, manages timestamps, and uses named queries defined in `BugReport.orm.xml` for every database read.

### 3.3 Controllers

| Class | URL | Actors | Methods |
|---|---|---|---|
| `BugReportSubmit` | `/roller-ui/bugReport` | Any logged-in user | `execute()` (show form), `submit()` (process) |
| `BugReportList` | `/roller-ui/bugReportList` | Any logged-in user | `execute()` (paginated list), `deleteReport()` |
| `BugReportAdmin` | `/roller-ui/admin/bugReportAdmin` | Admin only | `execute()` (dashboard + filter), `update()`, `delete()` |

### 3.4 Notification Subsystem

All notification logic lives in `org.apache.roller.weblogger.business.notification`. The subsystem has five main types:

- **`BugReportNotificationService`** — top-level API; one method per event
- **`BugReportNotificationServiceImpl`** — builds `NotificationEvent` objects and hands them to the dispatcher
- **`NotificationDispatcher`** — holds the channel list, dispatches events asynchronously
- **`NotificationChannel`** — the plug-in interface every channel must implement
- **`EmailNotificationChannel`** — the current channel; wraps Roller's existing `MailUtil`

Supporting utilities:
- **`NotificationEvent`** — immutable event DTO with a Builder
- **`AdminEmailResolver`** — resolves all admin email addresses at dispatch time
- **`EmailBodyBuilder`** — fluent HTML email constructor
- **`EmailTemplateRenderer`** — renders the right HTML template per event type × audience

---

## 4. Design Patterns

### 4.1 Strategy Pattern

**Where:** `NotificationChannel` interface and all its implementations (`EmailNotificationChannel`, and future `SlackNotificationChannel`, `TeamsNotificationChannel`, etc.)

**What it is:** Strategy lets you define a family of algorithms (or behaviors), encapsulate each one in a class, and make them interchangeable at runtime without changing the code that uses them.

**Exact reason applied here:** The notification behavior (how an event is communicated) must vary independently from the business logic that detects the event. When a bug is created, the manager should not care whether the notification goes out as an email, a Slack message, or a Teams webhook. All it does is fire an event. Each channel encapsulates a different delivery strategy. Adding Slack tomorrow means adding one class — nothing else changes.

**Quality attribute improved:** *Modifiability* and *Extensibility*. New channels can be added without touching the manager, dispatcher, or service implementation.

**Trade-off:** There is a small indirection overhead — a virtual dispatch through the interface. For a notification path (which is already I/O-bound), this overhead is completely negligible. The cost is a tiny amount of abstraction complexity, which is more than justified by the benefit.

---

### 4.2 Observer Pattern

**Where:** `NotificationDispatcher` + `BugReportNotificationServiceImpl` + event lifecycle

**What it is:** Observer decouples producers (the thing that changes state) from consumers (the things that react to that change). The producer does not need to know who is listening.

**Exact reason applied here:** The `JPABugReportManagerImpl` is the event producer — it knows when a bug is created or updated. It should not know that an email gets sent, or that in the future a Slack message goes out. By dispatching through `BugReportNotificationService → NotificationDispatcher → channels`, the manager is completely blind to who consumes events or how many consumers there are. The number and type of observers can change at runtime without modifying the manager.

**Quality attribute improved:** *Loose coupling*, *Single Responsibility*. Notification logic is completely separated from persistence logic.

**Trade-off:** The event-driven model introduces temporal decoupling (events are processed asynchronously). This means if an email fails, the caller does not see the failure as an exception. This is an intentional trade-off — a broken mail server must never cause a bug-submission transaction to roll back. Failure is logged, not propagated.

---

### 4.3 Builder Pattern

**Where (1): `NotificationEvent.Builder`**

`NotificationEvent` is an immutable object. Building it requires between 2 and 5 fields depending on the event type — for example, a `BUG_STATUS_CHANGED` event needs `oldStatus` and `newStatus` which a `BUG_CREATED` event never has. Using a constructor with five parameters where two are sometimes null is a maintenance hazard. The Builder provides a clear, intention-revealing construction API.

**Where (2): `EmailBodyBuilder`**

HTML email bodies are complex multi-section documents. `EmailBodyBuilder` exposes a fluent chain:

```java
new EmailBodyBuilder()
    .header("#DC3545", " New Bug Report", bugReport.getTitle())
    .reportTable(bugReport)
    .section("Description", bugReport.getDescription(), "#DC3545")
    .footer("This is an automated notification.")
    .build();
```

Each method returns `this`, allowing any combination of sections to be assembled without a proliferating number of overloaded constructors. Adding a new section type tomorrow means adding one method — no existing call sites break.

**Quality attribute improved:** *Readability* and *Maintainability*. The API is self-documenting and resistant to parameter-order mistakes.

**Trade-off:** The builder introduces one extra class per built object. This is the standard, accepted cost — worth it for objects with optional parameters.

---

### 4.4 Adapter Pattern

**Where:** `EmailNotificationChannel` acting as an Adapter over `MailUtil`

**What it is:** Adapter wraps an existing interface that cannot be changed and makes it compatible with a new interface.

**Exact reason applied here:** Roller already has `MailUtil.sendHTMLMessage()` for sending mail. The `NotificationChannel` interface requires a `send(NotificationEvent)` method. These two are structurally incompatible. `EmailNotificationChannel` bridges the gap — it translates a `NotificationEvent` into the specific `MailUtil` calls, resolves recipient addresses, and picks the right template.  This means the notification infrastructure does not need to know anything about how Roller sends email internally, and `MailUtil` does not need to be modified.

**Quality attribute improved:** *Reusability* (existing `MailUtil` is reused unchanged) and *Separation of Concerns*.

**Trade-off:** There is a thin translation layer that must be kept in sync if `MailUtil`'s API changes. This is a low-risk trade-off since `MailUtil` is stable infrastructure.


---

## 5. Software Engineering Principles

### Open / Closed Principle (OCP)

The notification system is **open for extension, closed for modification**. Adding Slack notifications requires writing one new class (`SlackNotificationChannel`) and registering it — no existing class is touched. The dispatcher, the service, the manager, and all existing channels are unchanged.

### Single Responsibility Principle (SRP)

Each class has one clear job:
- `BugReportManager` — business logic for the bug report lifecycle
- `NotificationDispatcher` — routing events to channels
- `EmailNotificationChannel` — email-specific delivery logic
- `AdminEmailResolver` — the specific task of finding admin email addresses
- `EmailBodyBuilder` — HTML construction only
- `EmailTemplateRenderer` — per-event template selection and composition

None of these cross into each other's concerns.

### Dependency Inversion Principle (DIP)

High-level modules (`JPABugReportManagerImpl`) depend on abstractions (`BugReportNotificationService`, `BugReportManager`), not concrete implementations. Low-level modules (`JPABugReportManagerImpl`, `EmailNotificationChannel`) also depend on abstractions (`JPAPersistenceStrategy`, `MailUtil` wrapper).

### Interface Segregation Principle (ISP)

`NotificationChannel` is a lean three-method interface (`getChannelName()`, `isEnabled()`, `send(NotificationEvent)`). Implementers are not forced to implement any unrelated methods. If a future channel type required a different delivery mechanism (e.g., polling), a separate interface could be introduced without breaking existing channels.

---

## 6. Extensibility — Adding New Notification Channels

The design was explicitly built so that adding a new channel (Slack, MS Teams, PagerDuty, SMS, etc.) requires exactly three steps and touches zero existing files.

**Step 1 — Implement the interface:**

```java
public class SlackNotificationChannel implements NotificationChannel {

    private final SlackWebhookClient client; // injected

    @Override
    public String getChannelName() { return "slack"; }

    @Override
    public boolean isEnabled() {
        return RollerConfig.getBooleanProperty("notifications.slack.enabled");
    }

    @Override
    public void send(NotificationEvent event) {
        String text = buildSlackText(event); // format the message
        client.postToWebhook(text);
    }
}
```

**Step 2 — Register in the Guice module** (the one line that changes in existing code):

```java
dispatcher.registerChannel(new SlackNotificationChannel(slackClient));
```

**Step 3 — Add configuration property:**

```properties
notifications.slack.enabled=true
notifications.slack.webhookUrl=https://hooks.slack.com/services/...
```

That is it. `NotificationDispatcher` will automatically include the new channel for every event type, all 7 existing emails continue to work exactly as before, and the new channel benefits from the existing async thread pool without any extra work.

**Why this works:** The dispatcher iterates over `List<NotificationChannel>` and calls `send()` on each enabled channel. It does not know what channels are in the list — it only knows they all implement `NotificationChannel`. The Strategy + Observer combination makes the system genuinely open for extension.

**Adding a Teams webhook** would follow the identical pattern. The only difference from Slack would be the message payload format (Teams uses Adaptive Cards JSON vs Slack's Block Kit JSON) — a detail entirely encapsulated inside the new channel class.

---

## 7. Concurrency — Async Notification via Thread Pool

Sending email is an I/O-bound network operation that can take hundreds of milliseconds or more when an SMTP relay is under load. Blocking the user's HTTP request thread until the email send completes would add noticeable latency to every bug submission and status update.

### Implementation

`NotificationDispatcher` maintains an `ExecutorService` backed by a fixed-size thread pool:

```java
// Created at construction time
this.executor = Executors.newFixedThreadPool(poolSize); // default: 4

// On each dispatch call
for (NotificationChannel channel : channels) {
    if (channel.isEnabled()) {
        executor.submit(() -> {
            try {
                channel.send(event);
            } catch (Exception e) {
                log.error("Channel {} failed for event {}", channel.getChannelName(), event.getType(), e);
            }
        });
    }
}
```

The pool size is configurable via `notification.dispatch.poolsize` in `roller-custom.properties`. The dispatch mode (`notification.dispatch.mode`) can also be set to `"sync"` for environments where thread pools are inappropriate (e.g., certain serverless runtimes or integration test environments).

### Graceful Shutdown

On application shutdown, `NotificationDispatcher.shutdown()` calls `executor.shutdown()` followed by `executor.awaitTermination(5, TimeUnit.SECONDS)`. This gives in-flight notifications a 5-second window to complete before hard shutdown. This prevents notification loss when the application is cleanly stopped or redeployed.

### Error Isolation

Each channel's `send()` call is individually wrapped in a try-catch inside the submitted task. A failure in the email channel (e.g., SMTP connection refused) will **not** prevent a future Slack channel from receiving the same event. Channels are fully isolated from each other at the execution level.

### Transaction Safety

Notifications are triggered only **after** `JPAPersistenceStrategy.flush()` succeeds and the transaction is committed. The notification call is then wrapped in its own try-catch that catches all `Throwable` and logs it without rethrowing. This guarantees that a mail server being down cannot roll back a bug report submission. The database is the source of truth; email is best-effort.

### Quality Attributes Improved

- **Performance / Responsiveness:** User HTTP threads are freed immediately; email sends happen in the background.
- **Resilience:** Email/channel failures are isolated from both the user experience and from each other.
- **Throughput:** Four parallel channels can be sending simultaneously under load.

### Trade-off

The fixed thread pool consumes `poolSize × (stack size)` of memory at all times. For a low-traffic deployment this is negligible. If the pool is too small under very high load (e.g., 1000 simultaneous submits), tasks queue in the executor's task queue. The queue is unbounded by default, so no events are dropped — they simply wait. For a production site with sustained high load, switching to a bounded queue with a rejection policy (e.g., `CallerRunsPolicy` to degrade gracefully to sync) would be a sensible next step.

---

## 8. Database Design Decisions

### Soft Delete

Reports are never physically removed from the database. Instead, their `status` field is set to `DELETED`. This has several advantages:

- Admin can still see deleted reports in the dashboard (useful for audit trails).
- The data is recoverable if a report was deleted by mistake.
- Foreign keys from other potential future tables (e.g., audit logs, responses) are not broken.
- The named query `BugReport.getAll` filters `status <> 'DELETED'` so deleted records are invisible to all normal queries.

### Optimistic Locking

The `optLock` (integer version) field on `BugReport` is mapped as JPA's `@Version` equivalent. If two admins attempt to update the same report simultaneously, one will receive an `OptimisticLockException` instead of silently overwriting the other's changes. This is the lowest-overhead concurrency control strategy for the expected access patterns (admin dashboard edits are rare, not high-frequency).

### Named Queries in ORM XML

All queries are defined as named queries in `BugReport.orm.xml` rather than as inline JPQL strings in the Java code. This separates persistence concerns from business logic, is easier to audit, and means the JPA provider can compile and validate all queries at startup rather than at first use.

### Indexes

Three indexes are created at the database level:
- `idx_bugreport_creator` — speeds up the "show me my reports" query used on the user list page
- `idx_bugreport_status` — speeds up the admin filter-by-status queries
- `idx_bugreport_created` — speeds up "most recent reports" ordering

These were chosen because they correspond directly to the three most common query patterns in the application.

---

## 9. Security and Authorization

Authorization is enforced at the **manager layer**, not just at the controller layer. This is a deliberate layered defense.

| Operation | Who is allowed | How enforced |
|---|---|---|
| Submit bug | Any logged-in user | Struts2 `requiredLogin()` in base action |
| View own bugs | Creator only | `BugReport.getByUser` query filters by creator |
| Delete own bug | Creator only | Manager checks `report.getCreator().equals(requestingUser)` |
| Admin view all | `GlobalPermission.ADMIN` | `BugReportAdmin.requiredGlobalPermissionActions()` |
| Admin status change | `GlobalPermission.ADMIN` | Manager checks admin permission before update |
| Admin delete | `GlobalPermission.ADMIN` | Manager checks admin permission before delete |

**XSS Prevention in JSP:** The admin detail modal is populated entirely via JavaScript that reads data from hidden `<span>` elements using jQuery's `.text()` method (never `.html()`). This means even if a report title or description contained `<script>` tags, they would be treated as plain text and never executed.

**Email recipient validation:** `AdminEmailResolver` validates that each address is non-null and non-empty before adding it to the recipient list. Invalid/empty email addresses are skipped with a log warning rather than causing the entire notification to fail.

---

## 10. Trade-offs and Justifications

| Decision | Trade-off | Justification |
|---|---|---|
| Async email dispatch | Email failures are silent from the user's perspective | User experience > email reliability; DB commit is the source of truth |
| Soft delete | Storage grows over time; queries must always filter DELETED | Data safety and auditability justify the storage cost |
| ORM XML mapping (no annotations) | POJO is harder to understand without the XML file | Keeps domain model clean of framework coupling; consistent with Roller's existing mapping style |
| Guice DI for notification | Adds Guice wiring complexity | Consistent with existing Roller DI; gives testability |
| Fixed thread pool (4 threads) | Memory overhead; potential queue under high load | Simple and reliable; configurable; sufficient for a blogging platform |
| Email body built in Java (not Velocity) | HTML logic is in Java code, not a template file | Programmatic badge coloring and conditional sections are cleaner in Java fluent builder than in template conditionals |
| Optimistic locking | `OptimisticLockException` possible on concurrent edits | Statistically rare on an admin dashboard; far cheaper than pessimistic row locks |
| Notification fired after flush | Race condition window between flush and notification | Negligible in practice; the alternative (2-phase commit with email) is far too complex for this use case |

---

## 11. End-to-End Request Flow

**Scenario: User submits a new bug report**

```
1. User fills BugReportSubmit.jsp form (browser auto-detects browserInfo via Client Hints API)
2. POST /roller-ui/bugReport!submit  →  BugReportSubmit.submit()
3. validateForm() checks required fields; adds action errors if invalid
4. On valid:  manager.saveBugReport(report)
5.   ↳ JPABugReportManagerImpl.saveBugReport():
      - UUID generated for id
      - status = OPEN, createdAt/updatedAt = now()
      - jpa.store(report) → INSERT into bug_report
      - jpa.flush()
      - notificationService.notifyBugCreated(report)  [outside transaction]
6.   ↳ BugReportNotificationServiceImpl.notifyBugCreated():
      - Creates NotificationEvent (type=BUG_CREATED, report, actor=creator)
      - Calls dispatcher.dispatch(event)
7.   ↳ NotificationDispatcher.dispatch():
      - Submits task to ExecutorService thread pool
      - Returns immediately (non-blocking for HTTP thread)
8.   ↳ [Background thread] EmailNotificationChannel.send(event):
      - adminEmailResolver.resolveAdminEmails() → list of admin addresses
      - emailTemplateRenderer.renderAdminCreated(report) → HTML
      - MailUtil.sendHTMLMessage(admins, "New Bug Report", html)
      - emailTemplateRenderer.renderUserCreated(report) → HTML
      - MailUtil.sendHTMLMessage([creator], "Bug Report Received", html)
9. HTTP thread: redirect to /roller-ui/bugReportList
10. User sees their new report (status=OPEN) in the card list
```

**Scenario: Admin changes status to RESOLVED**

```
1. Admin uses status dropdown on BugReportAdmin.jsp → POST !update
2. BugReportAdmin.update():  manager.updateBugReportStatus(id, "RESOLVED", notes, admin)
3. JPABugReportManagerImpl:
   - Checks admin has GlobalPermission.ADMIN
   - Sets status=RESOLVED, resolvedAt=now(), adminNotes=notes
   - jpa.store() → UPDATE bug_report
   - jpa.flush()
   - notificationService.notifyBugStatusChanged(report, "OPEN", "RESOLVED")
4. [Background thread] EmailNotificationChannel:
   - Sends admin: "Status Changed" email (blue header, OPEN → RESOLVED flow)
   - Sends reporter: "Your bug has been fixed!" email (context-aware message)
5. Admin dashboard refreshes with updated card
```

---

## 12. Summary of Files Added / Modified

### New Files Added

| File | Description |
|---|---|
| `pojos/BugReport.java` | Entity POJO |
| `business/BugReportManager.java` | Manager interface |
| `business/jpa/JPABugReportManagerImpl.java` | JPA implementation |
| `ui/struts2/core/BugReportSubmit.java` | Submit action |
| `ui/struts2/core/BugReportList.java` | User list action |
| `ui/struts2/admin/BugReportAdmin.java` | Admin dashboard action |
| `business/notification/BugReportNotificationService.java` | Notification service interface |
| `business/notification/BugReportNotificationServiceImpl.java` | Notification service implementation |
| `business/notification/NotificationDispatcher.java` | Async dispatcher with thread pool |
| `business/notification/channel/NotificationChannel.java` | Channel strategy interface |
| `business/notification/channel/EmailNotificationChannel.java` | Email channel (Adapter over MailUtil) |
| `business/notification/model/NotificationEvent.java` | Immutable event DTO (Builder) |
| `business/notification/resolver/AdminEmailResolver.java` | Admin email resolution utility |
| `business/notification/template/EmailBodyBuilder.java` | Fluent HTML email builder |
| `business/notification/template/EmailTemplateRenderer.java` | Template method renderer |
| `BugReport.orm.xml` | JPA XML mapping with named queries |
| `jsps/core/BugReportSubmit.jsp` | Submit form |
| `jsps/core/BugReportList.jsp` | User report card list |
| `jsps/admin/BugReportAdmin.jsp` | Admin dashboard |

### Modified Files

| File | Change |
|---|---|
| `business/Weblogger.java` | Added `getBugReportManager()` and `getBugReportNotificationService()` |
| `resources/META-INF/persistence.xml` | Added `BugReport.orm.xml` mapping entry |
| `resources/struts.xml` | Added three action definitions |
| `webapp/WEB-INF/tiles.xml` | Added three tile definitions |
| `resources/ApplicationResources.properties` | Added ~35 i18n keys for all UI text |
| `business/MailProvider.java` | Hardened: `Session.getInstance()` with Authenticator, STARTTLS config, fail-fast verify |

---



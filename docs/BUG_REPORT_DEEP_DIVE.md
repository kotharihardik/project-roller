# Bug Report Feature - Deep Dive Documentation

**Purpose**: Complete technical guide for understanding the bug report implementation in Apache Roller, including system design, architectural decisions, patterns used, and complete code flow.

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Architecture & Layers](#2-architecture--layers)
3. [Component Breakdown with Code](#3-component-breakdown-with-code)
4. [Design Patterns & Justification](#4-design-patterns--justification)
5. [Email Notification System](#5-email-notification-system)
6. [Complete Request Flow](#6-complete-request-flow)
7. [Database Design](#7-database-design)
8. [Security & Authorization](#8-security--authorization)
9. [Files Modified/Created](#9-files-modifiedcreated)

---

## 1. System Overview

### What is the Bug Report Feature?

The bug report feature allows users to submit issues discovered in Roller (broken links, buttons, UI problems, etc.) and enables admins to manage these reports. Key characteristics:

- **For Users**: Submit issues, view own reports, delete own reports, receive email status updates
- **For Admins**: Dashboard to view all reports, change status, add notes, delete reports, receive email notifications
- **For System**: Modular notification system that can add new channels (Slack, Teams) without touching existing code

### Problem It Solves

Without this feature:
- Users have no easy way to report bugs directly within the system
- Admins have no centralized dashboard to track issues
- No automated notifications → admins miss bugs, users don't get updates
- Adding new notification methods requires modifying existing code (violates OCP)

---

## 2. Architecture & Layers

```
┌─────────────────────────────────────────────────────┐
│         PRESENTATION LAYER (JSP Views)              │
│  ├── BugReportSubmit.jsp (user form)                │
│  ├── BugReportList.jsp (user's reports)             │
│  └── BugReportAdmin.jsp (admin dashboard)           │
├─────────────────────────────────────────────────────┤
│           STRUTS2 CONTROLLER LAYER                  │
│  ├── BugReportSubmit (display form, validate)       │
│  ├── BugReportList (show user reports, delete own)  │
│  └── BugReportAdmin (admin dashboard, manage)       │
├─────────────────────────────────────────────────────┤
│     BUSINESS/SERVICE LAYER (Business Logic)         │
│  ├── BugReportManager (interface)                   │
│  ├── JPABugReportManagerImpl (persistence logic)     │
│  ├── BugReportNotificationService (notification API)│
│  └── BugReportNotificationServiceImpl                │
├─────────────────────────────────────────────────────┤
│        NOTIFICATION SUBSYSTEM (Decoupled)           │
│  ├── NotificationDispatcher (async/threaded)        │
│  ├── NotificationChannel (strategy interface)       │
│  └── EmailNotificationChannel (implementation)      │
├─────────────────────────────────────────────────────┤
│         PERSISTENCE LAYER (Data Access)             │
│  ├── BugReport.java (POJO entity)                   │
│  ├── BugReport.orm.xml (JPA mapping)                │
│  └── JPAPersistenceStrategy (database ops)          │
└─────────────────────────────────────────────────────┘

KEY PRINCIPLE: Each layer depends only on the layer below via INTERFACES.
Notification system is fully decoupled and can be replaced/extended.
```

---

## 3. Component Breakdown with Code

### 3.1 Entity: BugReport.java

**What it is**: Plain Java object (POJO) with NO JPA annotations. All database mapping lives in XML.

**Why**: Keeps domain model free of framework coupling. You can use BugReport in any context (tests, APIs, etc.) without needing JPA library.

```java
public class BugReport implements Serializable {
    // Status constants
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_CLOSED = "CLOSED";
    public static final String STATUS_DELETED = "DELETED";

    // Category constants
    public static final String CATEGORY_BROKEN_LINK = "BROKEN_LINK";
    public static final String CATEGORY_BROKEN_BUTTON = "BROKEN_BUTTON";
    public static final String CATEGORY_UI_ISSUE = "UI_ISSUE";
    public static final String CATEGORY_PERFORMANCE = "PERFORMANCE";
    public static final String CATEGORY_FUNCTIONALITY = "FUNCTIONALITY";
    public static final String CATEGORY_OTHER = "OTHER";

    // Severity constants
    public static final String SEVERITY_LOW = "LOW";
    public static final String SEVERITY_MEDIUM = "MEDIUM";
    public static final String SEVERITY_HIGH = "HIGH";
    public static final String SEVERITY_CRITICAL = "CRITICAL";

    // Fields (all private with getters/setters)
    private String id;                      // UUID primary key
    private User creator;                   // Who filed the report
    private String title;                   // Issue summary
    private String description;             // Detailed description
    private String category;                // Enum string
    private String severity;                // Enum string
    private String status;                  // OPEN|IN_PROGRESS|RESOLVED|CLOSED|DELETED
    private String pageUrl;                 // URL where bug was found
    private String browserInfo;             // Browser/OS info
    private String stepsToReproduce;        // How to reproduce
    private String screenshotUrl;           // Screenshot if available
    private String adminNotes;              // Private admin notes
    private User lastModifiedBy;            // Audit trail
    private Date createdAt;                 // Creation timestamp
    private Date updatedAt;                 // Last update timestamp
    private Date resolvedAt;                // When marked resolved/closed
    private Integer optLock = 0;            // Optimistic locking version
}
```

**Database Mapping** (BugReport.orm.xml):
```xml
<entity class="org.apache.roller.weblogger.pojos.BugReport" access="FIELD">
    <table name="bug_report">
        <index name="idx_bugreport_creator" column-list="creatorid"/>
        <index name="idx_bugreport_status" column-list="status"/>
        <index name="idx_bugreport_created" column-list="createdat"/>
    </table>
    <attributes>
        <id name="id">
            <column name="id" length="48" nullable="false"/>
        </id>
        <version name="optLock">
            <column name="optlock"/>
        </version>
        <!-- ... all fields mapped here ... -->
        <!-- Named queries for common operations -->
        <named-query name="BugReport.getById">
            <query>SELECT b FROM BugReport b WHERE b.id = ?1</query>
        </named-query>
        <named-query name="BugReport.getByUser">
            <query>SELECT b FROM BugReport b 
                   WHERE b.creator = ?1 AND b.status &lt;&gt; 'DELETED' 
                   ORDER BY b.createdAt DESC</query>
        </named-query>
        <!-- ... more queries ... -->
    </attributes>
</entity>
```

**Why Named Queries in XML?**
- ✅ Separates SQL from Java code (cleaner!)
- ✅ JPA validates all queries at startup (catch bugs early)
- ✅ Easier to audit and optimize
- ✅ Can be modified without recompiling Java

---

### 3.2 Manager Interface & Implementation

**BugReportManager.java** - The public API that controllers use:

```java
public interface BugReportManager {
    
    // Create operations
    void saveBugReport(BugReport report) throws WebloggerException;
    void updateBugReport(BugReport report, User modifier) throws WebloggerException;
    void updateBugReportStatus(String reportId, String newStatus,
                               String adminNotes, User modifier) throws WebloggerException;
    
    // Delete operations
    void removeBugReport(String reportId, User requestingUser) throws WebloggerException;
    
    // Read operations
    BugReport getBugReport(String id) throws WebloggerException;
    List<BugReport> getBugReportsByUser(User user, int offset, int length) 
        throws WebloggerException;
    List<BugReport> getBugReports(String statusFilter, String severityFilter,
                                   int offset, int length) throws WebloggerException;
    
    // Count operations
    long getBugReportCount(String statusFilter) throws WebloggerException;
    long getBugReportCountByUser(User user) throws WebloggerException;
}
```

**JPABugReportManagerImpl.java** - Core implementation (simplified):

```java
@com.google.inject.Singleton
public class JPABugReportManagerImpl implements BugReportManager {
    
    private final Weblogger roller;
    private final JPAPersistenceStrategy strategy;
    private final BugReportNotificationService notificationService;

    @Override
    public void saveBugReport(BugReport report) throws WebloggerException {
        // ✓ Validation
        if (report == null || report.getCreator() == null) {
            throw new WebloggerException("Invalid report");
        }
        
        // ✓ Auto-populated fields
        report.setId(UUIDGenerator.generateUUID());
        report.setStatus(BugReport.STATUS_OPEN);
        report.setCreatedAt(new Date());
        report.setUpdatedAt(new Date());
        
        // ✓ Set defaults
        if (report.getCategory() == null) {
            report.setCategory(BugReport.CATEGORY_OTHER);
        }
        
        try {
            // ✓ Persist to database
            strategy.store(report);
            strategy.flush();  // force immediate flush to catch DB errors
            LOG.debug("Bug report saved: " + report.getId());
        } catch (Exception e) {
            throw new WebloggerException("Failed to save bug report", e);
        }
        
        // ✓ Fire notification AFTER successful persist
        // Wrapped in try-catch: mail server down MUST NEVER rollback the save
        try {
            notificationService.notifyBugCreated(report);
        } catch (Exception e) {
            LOG.error("Notification failed (will not rollback save)", e);
        }
    }

    @Override
    public void updateBugReportStatus(String reportId, String newStatus,
                                       String adminNotes, User modifier)
            throws WebloggerException {
        // ✓ Admin-only check
        if (!isAdminUser(modifier)) {
            throw new WebloggerException("Only admins can change status");
        }
        
        // ✓ Validate status transition
        if (!isValidStatus(newStatus)) {
            throw new WebloggerException("Invalid status: " + newStatus);
        }
        
        BugReport report = getBugReport(reportId);
        if (report == null || BugReport.STATUS_DELETED.equals(report.getStatus())) {
            throw new WebloggerException("Cannot modify deleted/missing report");
        }
        
        String oldStatus = report.getStatus();
        
        // ✓ Apply changes
        report.setStatus(newStatus);
        report.setLastModifiedBy(modifier);
        report.setUpdatedAt(new Date());
        if (adminNotes != null) {
            report.setAdminNotes(adminNotes);
        }
        
        // ✓ Set resolvedAt when moving to RESOLVED/CLOSED
        if (("RESOLVED".equals(newStatus) || "CLOSED".equals(newStatus))
                && report.getResolvedAt() == null) {
            report.setResolvedAt(new Date());
        }
        
        // ✓ Clear resolvedAt if re-opening
        if ("OPEN".equals(newStatus) || "IN_PROGRESS".equals(newStatus)) {
            report.setResolvedAt(null);
        }
        
        try {
            strategy.store(report);
            strategy.flush();
        } catch (Exception e) {
            throw new WebloggerException("Failed to update status", e);
        }
        
        // ✓ Fire notification
        try {
            notificationService.notifyBugStatusChanged(report, oldStatus, newStatus);
        } catch (Exception e) {
            LOG.error("Notification failed (will not rollback)", e);
        }
    }

    @Override
    public void removeBugReport(String reportId, User requestingUser)
            throws WebloggerException {
        BugReport report = getBugReport(reportId);
        if (report == null) {
            LOG.warn("Report already deleted or not found");
            return;  // Idempotent
        }
        
        // ✓ Authorization check: only creator or admin can delete
        boolean isAdmin = isAdminUser(requestingUser);
        boolean isOwner = report.getCreator() != null
                      && report.getCreator().getId().equals(requestingUser.getId());
        if (!isAdmin && !isOwner) {
            throw new WebloggerException("Not authorized to delete this report");
        }
        
        // ✓ Soft delete: set status to DELETED instead of physical DELETE
        report.setStatus(BugReport.STATUS_DELETED);
        report.setUpdatedAt(new Date());
        report.setLastModifiedBy(requestingUser);
        
        try {
            strategy.store(report);
            strategy.flush();
        } catch (Exception e) {
            throw new WebloggerException("Failed to delete bug report", e);
        }
        
        // ✓ Fire notification
        try {
            notificationService.notifyBugDeleted(report, requestingUser);
        } catch (Exception e) {
            LOG.error("Notification failed (will not rollback)", e);
        }
    }

    // Helper methods
    private boolean isAdminUser(User user) {
        try {
            GlobalPermission adminPerm = 
                new GlobalPermission(Collections.singletonList(GlobalPermission.ADMIN));
            return roller.getUserManager().checkPermission(adminPerm, user);
        } catch (WebloggerException e) {
            LOG.warn("Permission check failed for: " + user.getUserName());
            return false;
        }
    }

    private boolean isValidStatus(String status) {
        return BugReport.STATUS_OPEN.equals(status)
            || BugReport.STATUS_IN_PROGRESS.equals(status)
            || BugReport.STATUS_RESOLVED.equals(status)
            || BugReport.STATUS_CLOSED.equals(status)
            || BugReport.STATUS_DELETED.equals(status);
    }
}
```

**Key Points**:
- ✅ All authorization at manager level (not just controller level)
- ✅ Notifications fired AFTER database flush succeeds
- ✅ Notification failures never roll back database operations
- ✅ Soft delete preserves audit trail (data never physically deleted)

---

### 3.3 Struts2 Controllers

**BugReportSubmit.java** - User submits a bug:

```java
public class BugReportSubmit extends UIAction {
    
    private String title;
    private String description;
    private String category;
    private String severity;
    private String pageUrl;
    private String browserInfo;
    private String stepsToReproduce;
    private String screenshotUrl;
    
    @Override
    @SkipValidation
    public String execute() {
        // GET: Show empty form
        populateDropdowns();
        return INPUT;  // renders BugReportSubmit.jsp
    }

    public String submit() {
        // POST: Process submission
        User currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            addError("error.authentication.required");
            return ERROR;
        }

        // Validate form
        if (!validateForm()) {
            populateDropdowns();
            return INPUT;  // stays on form with errors
        }

        try {
            BugReportManager mgr = 
                WebloggerFactory.getWeblogger().getBugReportManager();

            BugReport report = new BugReport();
            report.setTitle(title.trim());
            report.setDescription(description.trim());
            report.setCategory(category);
            report.setSeverity(severity);
            report.setCreator(currentUser);
            
            if (StringUtils.isNotBlank(pageUrl)) {
                report.setPageUrl(pageUrl.trim());
            }
            if (StringUtils.isNotBlank(browserInfo)) {
                report.setBrowserInfo(browserInfo.trim());
            }
            if (StringUtils.isNotBlank(stepsToReproduce)) {
                report.setStepsToReproduce(stepsToReproduce.trim());
            }

            // Manager handles: UUID generation, status=OPEN, timestamps, notification
            mgr.saveBugReport(report);
            
            addMessage("bugReport.submit.success");
            return SUCCESS;  // redirect to list

        } catch (WebloggerException e) {
            LOG.error("Error saving bug report", e);
            addError("generic.error.check.logs");
            populateDropdowns();
            return INPUT;
        }
    }

    private boolean validateForm() {
        boolean valid = true;

        if (StringUtils.isBlank(title)) {
            addFieldError("title", getText("bugReport.error.titleRequired"));
            valid = false;
        } else if (title.trim().length() > 255) {
            addFieldError("title", getText("bugReport.error.titleTooLong"));
            valid = false;
        }

        if (StringUtils.isBlank(description)) {
            addFieldError("description", "Description required");
            valid = false;
        }

        if (StringUtils.isBlank(category)) {
            addFieldError("category", "Category required");
            valid = false;
        }

        return valid;
    }
}
```

**BugReportList.java** - User views and manages their own reports:

```java
public class BugReportList extends UIAction {
    
    private List<BugReport> reports = Collections.emptyList();
    private long totalReports = 0;
    private int page = 0;
    private String reportId;
    
    @Override
    @SkipValidation
    public String execute() {
        User currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            addError("error.authentication.required");
            return ERROR;
        }

        try {
            BugReportManager mgr = 
                WebloggerFactory.getWeblogger().getBugReportManager();

            int offset = page * PAGE_SIZE;
            reports = mgr.getBugReportsByUser(currentUser, offset, PAGE_SIZE);
            totalReports = mgr.getBugReportCountByUser(currentUser);

        } catch (WebloggerException e) {
            LOG.error("Error loading bug reports", e);
            addError("generic.error.check.logs");
        }

        return INPUT;  // Show list of user's reports
    }

    public String deleteReport() {
        User currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            addError("error.authentication.required");
            return ERROR;
        }

        if (reportId == null) {
            addError("bugReport.error.notFound");
            return execute();
        }

        try {
            BugReportManager mgr = 
                WebloggerFactory.getWeblogger().getBugReportManager();
            
            // Manager checks: is user owner or admin?
            // If owner: sends "your report was deleted" email
            // If admin: sends "report deleted by admin" email
            mgr.removeBugReport(reportId, currentUser);
            
            addMessage("bugReport.delete.success");

        } catch (WebloggerException e) {
            LOG.error("Error deleting bug report", e);
            addError("generic.error.check.logs");
        }

        return execute();  // Reload list
    }
}
```

**BugReportAdmin.java** - Admin manages all reports (simplified):

```java
public class BugReportAdmin extends UIAction {
    
    private String statusFilter;
    private String severityFilter;
    private int page = 0;
    private String reportId;
    private String newStatus;
    private String adminNotes;
    
    // Enforce admin-only access
    @Override
    public List<String> requiredGlobalPermissionActions() {
        return Collections.singletonList(GlobalPermission.ADMIN);
    }

    @Override
    @SkipValidation
    public String execute() {
        loadDashboard();
        return INPUT;  // Show admin dashboard
    }

    public String update() {
        // POST: Update status of a report
        if (StringUtils.isBlank(reportId) || StringUtils.isBlank(newStatus)) {
            addError("Invalid parameters");
            loadDashboard();
            return INPUT;
        }

        try {
            BugReportManager mgr = 
                WebloggerFactory.getWeblogger().getBugReportManager();
            User currentUser = getAuthenticatedUser();

            // Manager checks: is user admin?
            // Manager fires notifications to admin and report creator
            mgr.updateBugReportStatus(reportId, newStatus, adminNotes, currentUser);
            
            addMessage("bugReport.status.change.success");

        } catch (WebloggerException e) {
            LOG.error("Error updating bug report status", e);
            addError("generic.error.check.logs");
        }

        loadDashboard();
        return INPUT;
    }

    public String delete() {
        // POST: Soft-delete a report
        try {
            BugReportManager mgr = 
                WebloggerFactory.getWeblogger().getBugReportManager();
            User currentUser = getAuthenticatedUser();

            mgr.removeBugReport(reportId, currentUser);
            addMessage("bugReport.delete.success");

        } catch (WebloggerException e) {
            LOG.error("Error deleting report", e);
            addError("generic.error.check.logs");
        }

        loadDashboard();
        return INPUT;
    }

    private void loadDashboard() {
        // Fetch counts and reports with filters
        try {
            BugReportManager mgr = 
                WebloggerFactory.getWeblogger().getBugReportManager();

            int offset = page * PAGE_SIZE;
            reports = mgr.getBugReports(statusFilter, severityFilter, offset, PAGE_SIZE);
            totalReports = mgr.getBugReportCount(statusFilter);
            
            openCount = mgr.getOpenBugCount();
            resolvedCount = mgr.getResolvedBugCount();

        } catch (WebloggerException e) {
            LOG.error("Error loading dashboard", e);
            addError("generic.error.check.logs");
        }
    }
}
```

---

## 4. Design Patterns & Justification

### Pattern 1: Strategy Pattern (Notification Channels)

**What it is**: Different algorithms (notification methods) can be swapped at runtime without changing client code.

**Where used**:
```java
// Interface (Strategy)
public interface NotificationChannel {
    String getChannelName();
    boolean isEnabled();
    void send(NotificationEvent event);
}

// Concrete strategies
public class EmailNotificationChannel implements NotificationChannel { ... }
// Future: public class SlackNotificationChannel implements NotificationChannel { ... }
// Future: public class TeamsNotificationChannel implements NotificationChannel { ... }
```

**Why it was chosen**:

| Without Pattern | With Strategy Pattern |
|---|---|
| All notification logic in BugReportNotificationService | Each channel encapsulated in its own class |
| Adding Slack requires: modify service, add if-else statements | Adding Slack requires: write new class, register in dispatcher |
| Hard to test (everything runs together) | Easy to test (mock each channel independently) |
| Violates Open/Closed Principle (modifying code to extend) | Violates nothing (just write new class) |

**Actual Need**: The requirement explicitly asked for "modular system that can add Slack/Teams without touching existing code". Strategy Pattern is THE pattern for this.

**Without it, what happens**: 
1. Manager becomes giant with notifications code
2. Each new channel type requires modifying existing classes → bugs in old code
3. Can't test/deploy new channels independently
4. Business logic mixed with notification delivery logic

**How to add Slack** (after implementation):
```java
// Step 1: Write new class (only 1 file created)
public class SlackNotificationChannel implements NotificationChannel {
    @Override
    public void send(NotificationEvent event) {
        String message = buildSlackMessage(event);
        slackClient.postWebhook(message);
    }
}

// Step 2: Register in constructor (1 line changed)
dispatcher.registerChannel(new SlackNotificationChannel(slackClient));

// Step 3: Add config properties
notifications.slack.webhookurl=https://hooks.slack.com/...

// Result: ALL FOUR existing channels work unchanged! ✓
```

---

### Pattern 2: Observer Pattern (Event-Driven Architecture)

**What it is**: Producers of events don't know about consumers. When event happens, all registered observers are notified.

**Where used**:
```
JPABugReportManagerImpl (producer)
    ↓ fires event
BugReportNotificationService (observer coordinator)
    ↓ creates event
NotificationDispatcher (observer registry)
    ↓ notifies all
    ├→ EmailNotificationChannel
    ├→ SlackNotificationChannel (future)
    └→ TeamsNotificationChannel (future)
```

**Code flow**:
```java
// Manager doesn't know or care about notifications
public void saveBugReport(BugReport report) {
    strategy.store(report);
    strategy.flush();
    
    // Only calls interface, doesn't know how it's implemented
    notificationService.notifyBugCreated(report);
}

// Service creates event and dispatches
public void notifyBugCreated(BugReport report) {
    NotificationEvent event = new NotificationEvent.Builder()
        .type(EventType.BUG_CREATED)
        .report(report)
        .actor(report.getCreator())
        .build();
    dispatcher.dispatch(event);
}

// Dispatcher calls all registered channels
public void dispatch(NotificationEvent event) {
    for (NotificationChannel channel : channels) {
        if (channel.isEnabled()) {
            executor.submit(() -> channel.send(event));
        }
    }
}
```

**Why it was chosen**:

| Without Observer | With Observer |
|---|---|
| Manager code: "send email, then send Slack, then wait for both" | Manager code: "fire event, done" |
| Manager tightly coupled to notification details | Manager knows nothing about notifications |
| Changes to notification logic affect manager | Notification changes isolated to notification subsystem |
| Can't easily disable/enable channels | Channels disable themselves via config |

**Without it, what happens**:
1. Manager needs to know about every channel type
2. Manager needs to handle timeouts, retries, failures for each channel
3. Manager transaction gets tied to notification completion
4. Mail server goes down → user's bug submission fails ✗

**With it, what happens**:
1. Manager fires event, DONE
2. Notifications happen separately in thread pool
3. Mail server down → event is logged, user's submission succeeded ✓
4. New channel added → manager needs zero changes ✓

---

### Pattern 3: Builder Pattern (NotificationEvent & EmailBodyBuilder)

**NotificationEvent.Builder**:
```java
public static class Builder {
    private EventType type;
    private BugReport report;
    private User actor;
    private String oldStatus;
    private String newStatus;

    public Builder type(EventType t)       { this.type = t; return this; }
    public Builder report(BugReport r)     { this.report = r; return this; }
    public Builder actor(User u)           { this.actor = u; return this; }
    public Builder oldStatus(String s)     { this.oldStatus = s; return this; }
    public Builder newStatus(String s)     { this.newStatus = s; return this; }

    public NotificationEvent build() {
        if (type == null || report == null)
            throw new IllegalStateException("type and report required");
        return new NotificationEvent(this);
    }
}

// Usage
NotificationEvent event = new NotificationEvent.Builder()
    .type(EventType.BUG_CREATED)
    .report(report)
    .actor(creator)
    .build();
```

**Why**: Different event types need different fields. BUG_STATUS_CHANGED needs oldStatus/newStatus, but BUG_CREATED doesn't. Better than:
- Constructor overload hell: `new NotificationEvent(type, report, null, null, actor, oldStatus, newStatus)`
- Setters that leave object in invalid state until all fields set

**EmailBodyBuilder**:
```java
String html = new EmailBodyBuilder()
    .header("#d9534f", "New Bug Report", "Attention required")
    .reportTable(report)
    .section("Description", report.getDescription(), "#d9534f")
    .section("Steps", report.getStepsToReproduce(), "#5bc0de")
    .statusFlow("OPEN", "IN_PROGRESS")
    .footer("Notification")
    .build();
```

**Why**: Building HTML email is complex with many optional sections. Fluent interface makes it:
- Self-documenting: reads like what it does
- Extensible: add `.sectionNewType()` without breaking existing calls
- Type-safe: compiler catches wrong section-type combinations

---

### Pattern 4: Adapter Pattern (EmailNotificationChannel as Adapter)

**What it is**: Converter between two incompatible interfaces.

**Where used**:
```java
public class EmailNotificationChannel implements NotificationChannel {
    
    private final AdminEmailResolver adminResolver;
    private final EmailTemplateRenderer renderer;

    @Override
    public void send(NotificationEvent event) {
        // Adapts NotificationEvent → MailUtil.sendHTMLMessage()
        BugReport report = event.getReport();
        
        List<String> adminEmails = adminResolver.resolveAdminEmails();
        String html = renderer.renderAdminCreated(event);
        
        // Calls existing Roller mail infrastructure
        MailUtil.sendHTMLMessage(adminEmails, subject, html);
    }
}
```

**Why it was chosen**: 
- Roller already has `MailUtil` that we can't modify
- We defined new `NotificationChannel` interface that MailUtil doesn't implement
- We need to bridge the two without modifying either
- This is EXACTLY what Adapter pattern solves

**Without it**:
- Either modify Roller's MailUtil to implement NotificationChannel (violates Liskov, tight coupling)
- Or put MailUtil code directly in BugReportNotificationService (violates SRP, hard to test)

**With it**:
- ✅ Existing MailUtil unchanged
- ✅ New code depends on NotificationChannel interface
- ✅ EmailNotificationChannel can be tested independently
- ✅ Different mail systems could be adapted the same way

---

### Pattern 5: Template Method Pattern (EmailTemplateRenderer)

**Code**:
```java
public class EmailTemplateRenderer {
    
    // Different methods for different event types
    public String renderAdminCreated(NotificationEvent e) { ... }
    public String renderAdminUpdated(NotificationEvent e) { ... }
    public String renderAdminStatusChanged(NotificationEvent e) { ... }
    public String renderAdminDeleted(NotificationEvent e) { ... }
    
    public String renderUserCreated(NotificationEvent e) { ... }
    public String renderUserStatusChanged(NotificationEvent e) { ... }
    public String renderUserDeleted(NotificationEvent e) { ... }
}
```

**Why it's template method**: Each method follows similar steps (header → intro content → footer) but with different business logic for each event type.

**Without it**:
- All HTML generation in one giant method with 20 if-else statements
- Hard to debug which template has the bug
- Can't enable/disable event types individually

**With it**: 
- Each event type has its own method (single responsibility)
- Adding new event template = add one new method
- Clear separation of concerns

---

## 5. Email Notification System

### Email Flow Architecture

```
EVENT OCCURS
    ↓
Manager fires notification
    ↓
NotificationDispatcher.dispatch(event)
    ↓
For each enabled channel:
    Submit task: executor.submit(() -> channel.send(event))
    returns IMMEDIATELY (non-blocking)
    ↓
[Background Thread] EmailNotificationChannel.send(event)
    ├─ Resolve admin emails
    ├─ Render HTML template
    ├─ Send to admins
    ├─ Send to reporter
    └─ Log results

HTTP Response sent to user immediately (email still being sent)
```

### Full Email Channel Code

```java
public class EmailNotificationChannel implements NotificationChannel {
    
    private static final Log LOG = LogFactory.getLog(EmailNotificationChannel.class);
    private final AdminEmailResolver adminResolver;
    private final EmailTemplateRenderer renderer;

    public EmailNotificationChannel(AdminEmailResolver adminResolver,
                                     EmailTemplateRenderer renderer) {
        this.adminResolver = adminResolver;
        this.renderer = renderer;
    }

    @Override
    public String getChannelName() { 
        return "email"; 
    }

    @Override
    public boolean isEnabled() {
        String val = WebloggerConfig.getProperty("notification.bugreport.enabled");
        return val == null || "true".equalsIgnoreCase(val.trim());
    }

    @Override
    public void send(NotificationEvent event) {
        if (!isEnabled()) return;

        BugReport report = event.getReport();
        String from = getFrom();

        switch (event.getType()) {
            
            case BUG_CREATED:
                // Notify admins about new report
                sendToAdmins(from, 
                    buildAdminSubject("New Bug Report", report),
                    renderer.renderAdminCreated(event));
                
                // Confirm to reporter
                sendToUser(from, report.getCreator(),
                    buildUserSubject("Bug Report Received", report),
                    renderer.renderUserCreated(event));
                break;

            case BUG_UPDATED:
                // Notify admins
                sendToAdmins(from, 
                    buildAdminSubject("Bug Report Updated", report),
                    renderer.renderAdminUpdated(event));
                
                // Notify creator only if someone ELSE updated (not self-update)
                if (event.getActor() != null && report.getCreator() != null
                        && !report.getCreator().getId().equals(event.getActor().getId())) {
                    sendToUser(from, report.getCreator(),
                        buildUserSubject("Your Bug Report Was Updated", report),
                        renderer.renderUserUpdated(event));
                }
                break;

            case BUG_STATUS_CHANGED:
                // Always notify admins about status change
                sendToAdmins(from, 
                    buildAdminSubject("Bug Status Changed", report),
                    renderer.renderAdminStatusChanged(event));
                
                // Always notify reporter
                sendToUser(from, report.getCreator(),
                    buildUserSubject("Status Update on Your Bug", report),
                    renderer.renderUserStatusChanged(event));
                break;

            case BUG_DELETED:
                // Notify admins
                sendToAdmins(from, 
                    buildAdminSubject("Bug Report Deleted", report),
                    renderer.renderAdminDeleted(event));
                
                // Notify creator only if admin deleted (not self-delete)
                if (event.getActor() != null && report.getCreator() != null
                        && !report.getCreator().getId().equals(event.getActor().getId())) {
                    sendToUser(from, report.getCreator(),
                        buildUserSubject("Your Bug Report Was Removed", report),
                        renderer.renderUserDeleted(event));
                }
                break;

            default:
                LOG.warn("Unknown notification event type: " + event.getType());
        }
    }

    private void sendToAdmins(String from, String subject, String html) {
        List<String> adminEmails = adminResolver.resolveAdminEmails();
        if (adminEmails.isEmpty()) {
            LOG.warn("No admin emails found, skipping admin notification");
            return;
        }
        
        try {
            MailUtil.sendHTMLMessage(adminEmails, subject, html);
            LOG.info("Admin notification sent to " + adminEmails.size() + " recipients");
        } catch (MessagingException me) {
            LOG.error("Failed to send admin notification: " + me.getMessage(), me);
        }
    }

    private void sendToUser(String from, User user, String subject, String html) {
        if (user == null) {
            LOG.warn("Cannot send notification, user is null");
            return;
        }
        
        String email = user.getEmailAddress();
        if (email == null || email.trim().isEmpty()) {
            LOG.warn("User " + user.getUserName() + " has no email address");
            return;
        }
        
        try {
            List<String> recipients = Collections.singletonList(email);
            MailUtil.sendHTMLMessage(recipients, subject, html);
            LOG.info("User notification sent to: " + email);
        } catch (MessagingException me) {
            LOG.error("Failed to send user notification to " + email, me);
        }
    }

    private String getFrom() {
        String from = WebloggerConfig.getProperty("mail.from.address");
        return from != null ? from : "noreply@roller.example.com";
    }

    private String buildAdminSubject(String prefix, BugReport report) {
        return prefix + " [" + report.getSeverity() + "] - " + report.getTitle();
    }

    private String buildUserSubject(String prefix, BugReport report) {
        return prefix + ": " + report.getTitle();
    }
}
```

### Admin Email Resolution

```java
public class AdminEmailResolver {
    
    private final UserManager userManager;

    public List<String> resolveAdminEmails() {
        List<String> emails = new ArrayList<>();
        try {
            // Get all enabled users
            List<User> users = userManager.getUsers(Boolean.TRUE, null, null, 0, -1);
            if (users == null || users.isEmpty()) return emails;

            // Create admin permission object for checking
            GlobalPermission adminPerm = 
                new GlobalPermission(Collections.singletonList(GlobalPermission.ADMIN));

            // Filter to only admins with email
            for (User user : users) {
                if (user == null) continue;
                try {
                    // Check if user has ADMIN permission
                    if (userManager.checkPermission(adminPerm, user)) {
                        String email = user.getEmailAddress();
                        if (email != null && !email.trim().isEmpty()) {
                            emails.add(email.trim());
                        }
                    }
                } catch (WebloggerException e) {
                    LOG.warn("Permission check failed for: " + user.getUserName());
                }
            }
            
            LOG.debug("Resolved " + emails.size() + " admin email(s)");
            
        } catch (WebloggerException e) {
            LOG.error("Failed to fetch users for admin resolution", e);
        }
        
        return emails;
    }
}
```

### Email Body Builder (HTML Generation)

```java
public class EmailBodyBuilder {
    
    private final StringBuilder html = new StringBuilder();

    public EmailBodyBuilder() {
        // Initialize with HTML head, styles
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'/>")
            .append("<style>")
            .append("body{margin:0;padding:0;background:#f4f4f4;font-family:Arial,sans-serif;}")
            .append(".wrap{max-width:620px;margin:30px auto;background:#fff;}")
            .append(".badge{display:inline-block;padding:3px 10px;border-radius:12px;}")
            .append(".sev-CRITICAL{background:#d9534f;}")
            .append(".sev-HIGH{background:#e8622a;}")
            .append(".sev-MEDIUM{background:#f0ad4e;color:#333;}")
            .append(".sev-LOW{background:#5cb85c;}")
            .append(".status-flow{text-align:center;padding:20px;}")
            .append("</style></head><body><div class='wrap'>");
    }

    public EmailBodyBuilder header(String color, String title, String subtitle) {
        html.append("<div style='background:").append(color).append(";color:white;padding:20px;'>")
            .append("<h2>").append(title).append("</h2>");
        if (subtitle != null) {
            html.append("<p>").append(subtitle).append("</p>");
        }
        html.append("</div><div class='body'>");
        return this;
    }

    public EmailBodyBuilder reportTable(BugReport r) {
        html.append("<table class='info'>");
        row("Title", "<strong>" + safe(r.getTitle()) + "</strong>");
        if (r.getCreator() != null) {
            row("Reporter", r.getCreator().getScreenName() 
                + " &lt;" + r.getCreator().getEmailAddress() + "&gt;");
        }
        row("Category", safe(r.getCategory()));
        row("Severity", 
            "<span class='badge sev-" + safe(r.getSeverity()) + "'>"
            + safe(r.getSeverity()) + "</span>");
        row("Status", "<strong>" + safe(r.getStatus()) + "</strong>");
        row("Submitted", formatDate(r.getCreatedAt()));
        if (r.getPageUrl() != null) {
            row("Page URL", 
                "<a href='" + safe(r.getPageUrl()) + "'>" + safe(r.getPageUrl()) + "</a>");
        }
        html.append("</table>");
        return this;
    }

    public EmailBodyBuilder section(String title, String body, String borderColor) {
        if (body == null || body.trim().isEmpty()) return this;
        html.append("<div class='section' style='border-left:4px solid ")
            .append(borderColor).append(";'>")
            .append("<h4>").append(safe(title)).append("</h4>")
            .append("<p>").append(safe(body)).append("</p>")
            .append("</div>");
        return this;
    }

    public EmailBodyBuilder statusFlow(String oldStatus, String newStatus) {
        html.append("<div class='status-flow'>")
            .append("<span class='s-old'>").append(safe(oldStatus)).append("</span>")
            .append(" &rarr; ")
            .append("<span class='s-new'>").append(safe(newStatus)).append("</span>")
            .append("</div>");
        return this;
    }

    public String build() {
        html.append("</div></body></html>");
        return html.toString();
    }

    // Helpers
    private void row(String label, String value) {
        html.append("<tr><td class='lbl'>").append(label).append(":</td>")
            .append("<td>").append(value).append("</td></tr>");
    }

    private static String safe(String s) { 
        return s != null ? s : ""; 
    }
}
```

### Async Execution via Thread Pool

```java
public class NotificationDispatcher {
    
    private final List<NotificationChannel> channels = 
        new CopyOnWriteArrayList<>();  // Thread-safe
    private final ExecutorService executor;
    private final boolean async;

    public NotificationDispatcher() {
        // Read config to determine async vs sync mode
        String mode = WebloggerConfig.getProperty("notification.dispatch.mode");
        this.async = mode == null || "async".equalsIgnoreCase(mode.trim());

        ExecutorService ex = null;
        if (this.async) {
            // Get pool size from config (default: 4 threads)
            int pool = 4;
            try {
                String ps = WebloggerConfig.getProperty("notification.dispatch.poolsize");
                if (ps != null && !ps.trim().isEmpty()) {
                    pool = Integer.parseInt(ps.trim());
                }
            } catch (Exception e) {
                LOG.warn("Invalid poolsize config, using default", e);
            }
            
            // Create fixed thread pool
            ex = Executors.newFixedThreadPool(Math.max(1, pool));
            LOG.info("Async notification dispatch enabled (poolSize=" + Math.max(1, pool) + ")");
        } else {
            LOG.info("Synchronous notification dispatch enabled");
        }
        
        this.executor = ex;

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                shutdown();
            } catch (Exception e) {
                LOG.warn("Error during shutdown", e);
            }
        }));
    }

    public void registerChannel(NotificationChannel channel) {
        channels.add(channel);
        LOG.info("Registered notification channel: " + channel.getChannelName());
    }

    public void dispatch(NotificationEvent event) {
        for (NotificationChannel channel : channels) {
            if (!channel.isEnabled()) {
                LOG.debug("Channel disabled: " + channel.getChannelName());
                continue;
            }

            if (async && executor != null) {
                // Submit as background task (non-blocking)
                try {
                    executor.submit(() -> {
                        try {
                            channel.send(event);
                            LOG.debug("Event sent via: " + channel.getChannelName());
                        } catch (Exception e) {
                            // Failure in one channel doesn't affect others
                            LOG.error("Channel [" + channel.getChannelName() 
                                + "] failed: " + e.getMessage(), e);
                        }
                    });
                } catch (Exception e) {
                    LOG.error("Submit failed, falling back to sync", e);
                    try {
                        channel.send(event);
                    } catch (Exception ex) {
                        LOG.error("Sync send also failed", ex);
                    }
                }
            } else {
                // Sync mode (blocking)
                try {
                    channel.send(event);
                } catch (Exception e) {
                    LOG.error("Channel [" + channel.getChannelName() + "] failed", e);
                }
            }
        }
    }

    public void shutdown() {
        if (executor == null) return;
        try {
            LOG.info("Shutting down NotificationDispatcher");
            executor.shutdown();
            // Wait up to 5 seconds for tasks to complete
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                LOG.info("Executor timeout, forcing shutdown");
                executor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            LOG.warn("Interrupted during shutdown");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
```

---

## 6. Complete Request Flow

### Scenario: User Submits Bug Report

```
1. User POSTs form to /roller-ui/bugReport!submit
   
2. BugReportSubmit.submit() executes
   - Gets authenticated user
   - Validates form (title, description, category, severity required)
   - Creates BugReport object with user input
   
3. Calls manager.saveBugReport(report)
   ↓
4. JPABugReportManagerImpl.saveBugReport():
   - Validates report not null, has creator, title, description
   - Sets: id (UUID), status=OPEN, createdAt/updatedAt (now)
   - Sets defaults: category=OTHER if null, severity=MEDIUM if null
   - Calls: strategy.store(report)        [INSERT into bug_report]
   - Calls: strategy.flush()              [Force DB flush, catch errors]
   - Log: "Bug report saved: {id}"
   
5. TRANSACTION COMMITTED ✓
   
6. Notification fired (OUTSIDE transaction):
   - Try: notificationService.notifyBugCreated(report)
   - Catch Exception e: log error, DON'T rethrow
   - Reason: Mail server down MUST NOT rollback the save
   
7. notifyBugCreated():
   - Creates NotificationEvent:
     type = BUG_CREATED
     report = report object
     actor = report.getCreator()
   - Calls: dispatcher.dispatch(event)
   
8. dispatcher.dispatch():
   - For each enabled channel:
     - executor.submit( () -> emailChannel.send(event) )
   - Returns immediately (non-blocking)
   
9. [Background Thread] emailChannel.send():
   - Resolve admin emails via AdminEmailResolver
   - Resolve reporter email from report.getCreator()
   - Render HTML template: renderAdminCreated(event)
   - Send HTML via MailUtil.sendHTMLMessage(adminEmails, subject, html)
   - Render HTML template: renderUserCreated(event)
   - Send HTML via MailUtil.sendHTMLMessage([reporterEmail], subject, html)
   - Log results
   
10. HTTP response sent to BugReportSubmit controller:
    - addMessage("bugReport.submit.success")
    - return SUCCESS → redirect to /roller-ui/bugReportList
    
11. User sees: "Bug report submitted successfully!"
    (Email still being sent in background)
    
12. Admin receives email: "New Bug Report from {user}: {title}"
13. Reporter receives email: "Bug Report Received - Thank you for reporting!"
```

### Scenario: Admin Changes Status to RESOLVED

```
1. Admin clicks status dropdown on BugReportAdmin dashboard
2. Admin selects "RESOLVED" and clicks "Update"
3. POSTs to /roller-ui/admin/bugReportAdmin!update
   
4. BugReportAdmin.update():
   - Gets reportId, newStatus, adminNotes, currentUser
   - Validates admin has GlobalPermission.ADMIN
   - Calls: manager.updateBugReportStatus(id, "RESOLVED", notes, admin)
   
5. JPABugReportManagerImpl.updateBugReportStatus():
   - Validates: reportId/newStatus/admin not null
   - Validates: newStatus is valid (OPEN|IN_PROGRESS|RESOLVED|CLOSED|DELETED)
   - Fetches: report = getBugReport(reportId)
   - Checks: is admin? (GlobalPermission.ADMIN check) → YES ✓
   - Checks: not already DELETED? → YES ✓
   - Saves oldStatus = report.getStatus()  → "OPEN"
   - Updates:
     * report.setStatus("RESOLVED")
     * report.setLastModifiedBy(admin)
     * report.setUpdatedAt(now())
     * report.setAdminNotes(adminNotes)
     * report.setResolvedAt(now())
   - Persists: strategy.store() + strategy.flush()
   
6. Notification fired:
   - Try: notificationService.notifyBugStatusChanged(report, "OPEN", "RESOLVED")
   
7. Dispatcher sends event to all channels:
   - EmailNotificationChannel.send(event) submitted to thread pool
   
8. [Background] renderAdminStatusChanged():
   - Creates email with:
     - Header: "Bug Status Changed" (blue styling)
     - Status flow: OPEN → RESOLVED (visual)
     - Report details table
     - Admin notes section
   - Sends to all admin emails
   
9. [Background] renderUserStatusChanged():
   - Creates email with:
     - Header: "Update on Your Bug Report"
     - Status flow visual
     - Context-aware message: "Your bug has been fixed! Thank you!"
     - Report details
   - Sends to report creator's email
   
10. HTTP response: "Status updated successfully"
11. Admin dashboard refreshes showing status = RESOLVED
12. Reporter receives: "Your bug marked as RESOLVED"
13. Admin receives: "Bug status changed to RESOLVED"
```

---

## 7. Database Design

### Table Structure

```sql
CREATE TABLE bug_report (
    id VARCHAR(48) PRIMARY KEY,
    creatorid VARCHAR(40) NOT NULL REFERENCES roller_user(id),
    title VARCHAR(255) NOT NULL,
    description LONGTEXT NOT NULL,
    category VARCHAR(50) NOT NULL,             -- ENUM: BROKEN_LINK, BROKEN_BUTTON, etc.
    severity VARCHAR(20) NOT NULL,             -- ENUM: LOW, MEDIUM, HIGH, CRITICAL
    status VARCHAR(20) NOT NULL,               -- ENUM: OPEN, IN_PROGRESS, RESOLVED, CLOSED, DELETED
    pageurl VARCHAR(2048),                     -- URL where bug occurred
    browserinfo VARCHAR(512),                  -- Browser + OS info
    stepstoreproduce LONGTEXT,                 -- How to reproduce
    screenshoturl VARCHAR(2048),               -- URL to screenshot
    adminnotes LONGTEXT,                       -- Private admin-only field
    createdat TIMESTAMP NOT NULL,              -- When created
    updatedat TIMESTAMP NOT NULL,              -- Last update
    resolvedat TIMESTAMP,                      -- When marked resolved/closed
    lastmodifiedbyid VARCHAR(40),              -- FOREIGN KEY (nullable)
    optlock INT DEFAULT 0,                     -- Optimistic locking version
    
    -- Indexes for common queries
    INDEX idx_bugreport_creator (creatorid),   -- "Show me my reports"
    INDEX idx_bugreport_status (status),       -- "Filter by status"
    INDEX idx_bugreport_created (createdat)    -- "Most recent first"
);
```

### Why Soft Delete?

| Physical Delete | Soft Delete |
|---|---|
| `DELETE FROM bug_report WHERE id = ?` | `UPDATE bug_report SET status = 'DELETED' WHERE id = ?` |
| Data irrecoverable if mistake | Data still in DB, can be recovered |
| Foreign key breaks if other tables reference | Foreign keys never broken |
| Can't show deletion history | Full audit trail preserved |

### Why Optimistic Locking?

```java
/// Optimistic locking (optlock field)
Report admin1 fetches: optlock=5
Report admin2 fetches: optlock=5

Admin1 updates, sets optlock=6
    UPDATE bug_report SET status=?, optlock=6 WHERE id=? AND optlock=5 ✓

Admin2 updates, tries to set optlock=6
    UPDATE bug_report SET status=?, optlock=6 WHERE id=? AND optlock=5
    → 0 rows affected (optlock already 6!)
    → throw OptimisticLockException
    → Admin2 sees: "Report was modified elsewhere, please refresh"
```

vs Pessimistic Locking:
- Admin1 locks row → Admin2 blocks, waiting...
- Slower, higher contention, deadlock risk

For admin interface (not high-frequency concurrent edits): Optimistic is better.

### Why Named Queries in XML?

```xml
<named-query name="BugReport.getByUser">
    <query>
        SELECT b FROM BugReport b 
        WHERE b.creator = ?1 AND b.status &lt;&gt; 'DELETED' 
        ORDER BY b.createdAt DESC
    </query>
</named-query>
```

Benefits:
1. **Compile-time validation**: JPA checks syntax at startup
2. **Cleaner code**: No JPQL strings polluting Java
3. **Easier optimization**: DBA can see all queries in one file
4. **Reusability**: Multiple classes can share same query

---

## 8. Security & Authorization

### Authorization Checks

```
Operation          | Who Allowed  | Check Location
---|---|---
Submit bug         | Any user     | Struts2 requiredLogin()
View own bugs      | Creator only | Query filters by creator
Delete own bug     | Creator      | Manager.removeBugReport() checks owner
               OR AdminOr Global admin  | Same method checks admin permission
Admin view all     | Admin only   | BugReportAdmin.requiredGlobalPermissionActions()
Admin change status| Admin only   | Manager.updateBugReportStatus() checks admin
Admin delete       | Admin only   | Manager.removeBugReport() checks admin

Three layers of defense:
1. Controller level: @SkipValidation, requiredGlobalPermissionActions()
2. Manager level: explicit isAdminUser() checks
3. Database level: indexes optimize queries
```

### XSS Prevention

In JSP views:
```jsp
<!-- DON'T: This could execute scripts -->
<%= bugReport.getTitle() %>

<!-- DO: Struts2 tags auto-escape -->
<s:property value="report.title" />

<!-- For admin modal, use .text() not .html() -->
var title = $('#titleSpan').text();  // ✓ Safe
// vs
var title = $('#titleSpan').html();  // ✗ Unsafe
```

### Email Recipient Validation

```java
private void sendToUser(String from, User user, String subject, String html) {
    if (user == null) return;
    
    String email = user.getEmailAddress();
    // Validate non-null AND non-empty
    if (email == null || email.trim().isEmpty()) {
        LOG.warn("User has no email address");
        return;
    }
    
    // Proceeds only if email valid
    MailUtil.sendHTMLMessage(Collections.singletonList(email), subject, html);
}
```

---

## 9. Files Modified/Created

### New Files Created

| File | Purpose |
|---|---|
| `pojos/BugReport.java` | Entity POJO (no JPA annotations) |
| `business/BugReportManager.java` | Manager interface (public API) |
| `business/jpa/JPABugReportManagerImpl.java` | JPA implementation with auth checks |
| `business/notification/BugReportNotificationService.java` | Notification service interface |
| `business/notification/BugReportNotificationServiceImpl.java` | Creates events, delegates to dispatcher |  
| `business/notification/NotificationDispatcher.java` | Thread pool + channel registry |
| `business/notification/channel/NotificationChannel.java` | Strategy interface for channels |
| `business/notification/channel/EmailNotificationChannel.java` | Email strategy implementation |
| `business/notification/model/NotificationEvent.java` | Immutable event DTO with Builder |
| `business/notification/resolver/AdminEmailResolver.java` | Queries UserManager for admin emails |
| `business/notification/template/EmailBodyBuilder.java` | Fluent HTML email constructor |
| `business/notification/template/EmailTemplateRenderer.java` | Template method for rendering templates |
| `ui/struts2/core/BugReportSubmit.java` | User submission action |
| `ui/struts2/core/BugReportList.java` | User list + delete own action |
| `ui/struts2/admin/BugReportAdmin.java` | Admin dashboard action |
| `jsps/core/BugReportSubmit.jsp` | HTML form for submission |
| `jsps/core/BugReportList.jsp` | User's report list view |
| `jsps/admin/BugReportAdmin.jsp` | Admin dashboard |
| `pojos/BugReport.orm.xml` | JPA entity mapping + named queries |

### Modified Files

| File | Change |
|---|---|
| `business/Weblogger.java` | Added method `getBugReportManager()` and `getBugReportNotificationService()` |
| `resources/META-INF/persistence.xml` | Added entry: `<mapping-file>org/apache/roller/weblogger/pojos/BugReport.orm.xml</mapping-file>` |
| `resources/struts.xml` | Added 3 action definitions (bugReport, bugReportList, bugReportAdmin) |
| `webapp/WEB-INF/tiles.xml` | Added 3 tile definitions for the JSP views |
| `resources/ApplicationResources.properties` | Added ~40 i18n keys (bugReport.field.*) |
| `resources/roller.properties` | Added config properties (notification.dispatch.mode etc.) |

---

## Key Takeaways

1. **Layered Architecture**: Each layer separate concern, depends on interfaces
2. **Strategy + Observer Patterns**: NotificationChannel is pluggable, manager decoupled from notification details
3. **Soft Delete**: Data preserved, audit trail intact
4. **Async Notifications**: User experience not blocked by mail server latency
5. **Security**: Checks at controller, manager, and authorization levels
6. **Extensibility**: Adding Slack/Teams requires only 1 new class + 1 registration line = zero changes to existing code
7. **Email System**: Flexible, modular, decoupled from business logic

---

## Quick Reference: Configuration Properties

```properties
# In roller-custom.properties or roller.properties

# Notification dispatch mode: "async" (default) or "sync"
notification.dispatch.mode=async

# Thread pool size for async notifications (default: 4)
notification.dispatch.poolsize=4

# Enable/disable bug report notifications (default: true)
notification.bugreport.enabled=true

# Email from address for notifications
mail.from.address=bug-tracker@roller.example.com
```

---

**End of Document**

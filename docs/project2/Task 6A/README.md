# Task 6A — Discussion Overview (Team 27)

Community Pulse Dashboard — Part A: per-entry high-level snapshot of comment activity inside Apache Roller Weblogger.

---

## Table of Contents

1. [Feature Summary](#1-feature-summary)
2. [Screenshots](#2-screenshots)
3. [The Three Indicators](#3-the-three-indicators)
4. [Architecture Overview](#4-architecture-overview)
5. [Implementation Details](#5-implementation-details)
   - 5.1 [DiscussionOverview.java — Data Holder POJO](#51-discussionoverviewjava--data-holder-pojo)
   - 5.2 [DiscussionOverviewService.java — Computation Engine](#52-discussionoverviewservicejava--computation-engine)
   - 5.3 [Comments.java — Action Layer Integration](#53-commentsjava--action-layer-integration)
   - 5.4 [Comments.jsp — UI Panel](#54-commentsjsp--ui-panel)
6. [Design Patterns Applied](#6-design-patterns-applied)
7. [File Map](#7-file-map)
8. [Request Flow — End to End](#8-request-flow--end-to-end)
9. [Technical Decisions and Bug Fixes](#9-technical-decisions-and-bug-fixes)
10. [How to Test](#10-how-to-test)
11. [Constraints and Limitations](#11-constraints-and-limitations)

---

## 1. Feature Summary

When a blog author opens the **Comment Management** page for a specific entry, a
**Discussion Overview** panel now appears above the comment table. The panel gives
the author a quick, at-a-glance understanding of reader engagement without having
to read every comment individually.

The panel is:
- Only shown when filtering by a specific entry (not on the global all-comments view).
- Computed entirely server-side using classical, computationally inexpensive methods — no LLM, no deep learning.
- Read-only: it never modifies, approves, or deletes any comment.
- Includes both **APPROVED** and **PENDING** comments so keywords are visible even before moderation is complete (SPAM and DISAPPROVED are excluded).

---

## 2. Screenshots

> **Place screenshots in this directory (`docs/project2/Task 6A/`) and update the paths below.**

### Discussion Overview Panel — Full View
![Discussion Overview Panel](/docs/project2/screenshots/task6a_discussion_overview.png)

---

## 3. The Three Indicators

### Indicator 1 — Activity Level

| Field | Description |
|---|---|
| Total comments | Count of eligible (APPROVED + PENDING) comments |
| Avg comments/day | `totalComments / max(1, spanDays)`, rounded to 2 d.p. |
| Activity label | Colour-coded badge: **Very Active** (≥3/day), **Moderately Active** (≥1/day), **Quiet** (<1/day) |

The time span is measured from the earliest to the latest post timestamp. If all
comments were posted on the same day the span is treated as 1 day to avoid division
by zero.

### Indicator 2 — Comment-Type Distribution

Each comment is classified into exactly one of five mutually exclusive categories
using precompiled Java regex patterns checked in priority order:

| Priority | Type | Detection signal |
|---|---|---|
| 1 | **Question** | Ends with `?`, or contains question words (`why`, `how`, `what`, `is there`, `can you`, …) |
| 2 | **Debate** | Contains disagreement signals (`disagree`, `wrong`, `however`, `actually`, `but`, …) |
| 3 | **Appreciation** | Contains positive sentiment words (`thanks`, `great`, `awesome`, `helpful`, …) |
| 4 | **Feedback** | Contains improvement/suggestion words (`suggest`, `improve`, `should`, `missing`, …) |
| 5 | **Neutral** | Everything else |

The panel renders a mini-table with each type's count and percentage, plus a
progress bar for quick visual comparison. The **dominant type** (highest count) is
highlighted below the table.

### Indicator 3 — Top Recurring Keywords

All eligible comment content is tokenised: HTML tags are stripped, non-alpha
characters are removed, and the text is lower-cased. A comprehensive English
stop-word list (≈160 words) is applied, tokens shorter than 3 characters are
discarded, and the top 10 remaining words by frequency are returned ordered
descending by count (alphabetically as a tiebreaker).

The keywords are displayed as Bootstrap label badges.

---

## 4. Architecture Overview

```
Comments.java (Struts2 Action)
  │
  ├─ loadComments()
  │    ├─ [existing] paginated query → pager (display table)
  │    └─ [NEW] overview query (entry scope, no status filter)
  │              └─→ DiscussionOverviewService.compute(allForEntry)
  │                      ├─ computeActivityLevel()    → DiscussionOverview
  │                      ├─ computeTypeDistribution() → DiscussionOverview
  │                      └─ computeTopKeywords()      → DiscussionOverview
  │
  └─ getDiscussionOverview()  ← exposed to OGNL / JSP

Comments.jsp
  └─ <s:if test="discussionOverview != null">
       ├── Indicator 1 panel (activity table + badge)
       ├── Indicator 2 panel (type distribution table + progress bars)
       └── Indicator 3 panel (keyword badges)
```

**New classes introduced:**

| Class | Package | Role |
|---|---|---|
| `DiscussionOverview` | `business.discussion` | Plain data-holder (POJO) for computed indicator values |
| `DiscussionOverviewService` | `business.discussion` | Static utility; performs all computations on a comment list |

**Modified classes:**

| Class | Change |
|---|---|
| `Comments` (Struts2 action) | Added `discussionOverview` field + getter/setter; added overview query block inside `loadComments()` |
| `Comments.jsp` | Added Discussion Overview panel between the subtitle and the comment table |

---

## 5. Implementation Details

### 5.1 `DiscussionOverview.java` — Data Holder POJO

A plain Java object that carries the results of all three indicators. It lives in
the new package `org.apache.roller.weblogger.business.discussion`.

**Fields:**

```java
// Indicator 1 – Activity Level
private int    totalComments;        // count of eligible comments
private double avgCommentsPerDay;    // rounded to 2 d.p.
private String activityLabel;        // "Very Active" | "Moderately Active" | "Quiet" | "No Comments"

// Indicator 2 – Comment-Type Distribution
private Map<String, Integer> typeDistribution;  // LinkedHashMap: type → count
private String dominantType;                    // type with the highest count

// Indicator 3 – Top Recurring Keywords
private List<Map.Entry<String, Integer>> topKeywords;  // ordered by frequency desc
```

**Default constructor** initialises safe empty-state values so JSP rendering never
throws a NullPointerException even when there are no comments:

```java
public DiscussionOverview() {
    typeDistribution = Collections.emptyMap();
    topKeywords      = Collections.emptyList();
    activityLabel    = "No Comments";
    dominantType     = "Neutral";
}
```

All fields have standard getter/setter pairs so Struts2 OGNL can access them in
the JSP via expressions like `discussionOverview.totalComments`.

---

### 5.2 `DiscussionOverviewService.java` — Computation Engine

A utility class with a single public static entry point and three private
computation methods. No instance state — all methods are `static`.

#### Entry Point: `compute()`

```java
public static DiscussionOverview compute(List<WeblogEntryComment> comments) {

    DiscussionOverview overview = new DiscussionOverview();

    if (comments == null || comments.isEmpty()) {
        return overview;   // returns safe default object, never null
    }

    // Include APPROVED and PENDING; exclude SPAM and DISAPPROVED
    List<WeblogEntryComment> eligible = new ArrayList<>();
    for (WeblogEntryComment c : comments) {
        if ((WeblogEntryComment.ApprovalStatus.APPROVED.equals(c.getStatus()) ||
             WeblogEntryComment.ApprovalStatus.PENDING.equals(c.getStatus()))
                && c.getContent() != null && !c.getContent().trim().isEmpty()) {
            eligible.add(c);
        }
    }

    if (eligible.isEmpty()) {
        return overview;
    }

    computeActivityLevel(overview, eligible);
    computeTypeDistribution(overview, eligible);
    computeTopKeywords(overview, eligible);

    return overview;
}
```

#### Indicator 1: `computeActivityLevel()`

Scans the eligible list to find the earliest and latest `postTime` timestamps.
Calculates the span in days (minimum 1 to guard against division by zero when all
comments were posted on the same day), then derives the average comments per day:

```java
long spanMs    = latest.getTime() - earliest.getTime();
double spanDays = Math.max(1.0, (double) spanMs / MS_PER_DAY);  // MS_PER_DAY = 86_400_000L
double avgPerDay = total / spanDays;

overview.setAvgCommentsPerDay(Math.round(avgPerDay * 100.0) / 100.0);

if      (avgPerDay >= 3.0) { label = "Very Active"; }
else if (avgPerDay >= 1.0) { label = "Moderately Active"; }
else                       { label = "Quiet"; }
```

#### Indicator 2: `computeTypeDistribution()`

Uses four precompiled `Pattern` constants. Each comment's content is lower-cased
and tested against each pattern in priority order via `classifyComment()`:

```java
// Patterns compiled once at class-load time (static final)
private static final Pattern QUESTION_PATTERN = Pattern.compile(
    "\\?|\\b(why|how|what|when|where|who|whose|which|"
    + "is there|are there|can you|could you|would you|"
    + "do you|does|did|have you|has|will|shall)\\b",
    Pattern.CASE_INSENSITIVE);

private static final Pattern DEBATE_PATTERN = Pattern.compile(
    "\\b(disagree|wrong|incorrect|inaccurate|misleading|false|"
    + "not true|actually|but |however|although|on the contrary|"
    + "dispute|challenge|counter|oppose|objection|"
    + "i think you|i believe you|that is not|that's not)\\b",
    Pattern.CASE_INSENSITIVE);

private static final Pattern APPRECIATION_PATTERN = Pattern.compile(
    "\\b(thank|thanks|thank you|appreciated|appreciate|great|"
    + "excellent|awesome|wonderful|fantastic|amazing|brilliant|"
    + "love this|well done|nice post|good article|helpful|"
    + "enjoyed|impressive|good job|kudos|bravo|perfect)\\b",
    Pattern.CASE_INSENSITIVE);

private static final Pattern FEEDBACK_PATTERN = Pattern.compile(
    "\\b(suggest|suggestion|recommend|improve|improvement|"
    + "maybe|perhaps|should|could|would be better|might want|"
    + "consider|alternatively|missing|lacks|need to|"
    + "please add|it would be|i wish|feature request)\\b",
    Pattern.CASE_INSENSITIVE);
```

```java
private static String classifyComment(String lowerText) {
    if (QUESTION_PATTERN.matcher(lowerText).find())     { return "Question"; }
    if (DEBATE_PATTERN.matcher(lowerText).find())       { return "Debate"; }
    if (APPRECIATION_PATTERN.matcher(lowerText).find()) { return "Appreciation"; }
    if (FEEDBACK_PATTERN.matcher(lowerText).find())     { return "Feedback"; }
    return "Neutral";
}
```

After classifying every comment the dominant type (highest count) is determined:

```java
String dominant = "Neutral";
int    max      = -1;
for (Map.Entry<String, Integer> e : dist.entrySet()) {
    if (e.getValue() > max) {
        max      = e.getValue();
        dominant = e.getKey();
    }
}
overview.setDominantType(dominant);
```

#### Indicator 3: `computeTopKeywords()`

Each comment's text is cleaned, tokenised, filtered and counted:

```java
String text = c.getContent()
    .replaceAll("<[^>]*>", " ")       // strip HTML tags
    .replaceAll("[^a-zA-Z\\s]", " ")  // keep only letters + whitespace
    .toLowerCase(Locale.ENGLISH);

for (String token : text.split("\\s+")) {
    if (token.length() < MIN_WORD_LENGTH) { continue; }  // MIN_WORD_LENGTH = 3
    if (STOP_WORDS.contains(token))       { continue; }  // ~160 English stop-words
    freq.merge(token, 1, Integer::sum);
}
```

The frequency map is sorted descending by count (alphabetically as tiebreaker) and
the top 10 entries are stored as a plain `ArrayList`:

```java
List<Map.Entry<String, Integer>> sorted = new ArrayList<>(freq.entrySet());
sorted.sort(
    Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
             .thenComparing(Map.Entry.comparingByKey())
);

int limit = Math.min(TOP_KEYWORD_LIMIT, sorted.size()); // TOP_KEYWORD_LIMIT = 10
overview.setTopKeywords(new ArrayList<>(sorted.subList(0, limit)));
```

> **Why `new ArrayList<>` and not `Collections.unmodifiableList`?**  
> See [Section 9.1](#91-arraylist-instead-of-collectionsunmodifiablelist) in Technical Decisions.

---

### 5.3 `Comments.java` — Action Layer Integration

Two imports were added:

```java
import org.apache.roller.weblogger.business.discussion.DiscussionOverview;
import org.apache.roller.weblogger.business.discussion.DiscussionOverviewService;
```

A new field was added alongside the existing action fields:

```java
// discussion overview snapshot for the currently filtered entry (null when
// viewing all comments across the whole weblog)
private DiscussionOverview discussionOverview = null;
```

Inside `loadComments()`, after the existing paginated query completes, an
independent read-only overview query was added:

```java
// Compute discussion overview when viewing a specific entry
if (getQueryEntry() != null) {
    CommentSearchCriteria overviewCsc = new CommentSearchCriteria();
    overviewCsc.setWeblog(getActionWeblog());
    overviewCsc.setEntry(getQueryEntry());
    // No status filter: compute() will exclude SPAM/DISAPPROVED but
    // include APPROVED and PENDING so keywords appear before moderation.
    overviewCsc.setReverseChrono(false);
    List<WeblogEntryComment> allForEntry = wmgr.getComments(overviewCsc);
    setDiscussionOverview(DiscussionOverviewService.compute(allForEntry));
} else {
    setDiscussionOverview(null);
}
```

The getter/setter pair exposes the object to OGNL:

```java
public DiscussionOverview getDiscussionOverview() {
    return discussionOverview;
}

public void setDiscussionOverview(DiscussionOverview discussionOverview) {
    this.discussionOverview = discussionOverview;
}
```

---

### 5.4 `Comments.jsp` — UI Panel

The panel is injected between the subtitle paragraph and the `<s:if test="pager.items.isEmpty">` block. It is guarded by a three-part condition:

```jsp
<s:if test="actionName == 'comments' && queryEntry != null && discussionOverview != null">
```

The panel uses Bootstrap 3 grid (three `col-sm-4` columns — one per indicator).

**Indicator 1** renders a condensed table plus a colour-coded Bootstrap badge:

```jsp
<s:if test="discussionOverview.activityLabel == 'Very Active'">
    <span class="label label-danger">...</span>
</s:if>
<s:elseif test="discussionOverview.activityLabel == 'Moderately Active'">
    <span class="label label-warning">...</span>
</s:elseif>
<s:elseif test="discussionOverview.activityLabel == 'Quiet'">
    <span class="label label-info">...</span>
</s:elseif>
<s:else>
    <span class="label label-default">...</span>
</s:else>
```

**Indicator 2** iterates the `typeDistribution` map entry-set and computes
percentages inline using OGNL arithmetic, rendering a Bootstrap progress bar
for each type:

```jsp
<s:iterator value="discussionOverview.typeDistribution.entrySet()" var="entry">
    <tr>
        <td><s:property value="#entry.key"/></td>
        <td>
            <s:property value="#entry.value"/>
            (<s:property
                value="((double)#entry.value / (double)discussionOverview.totalComments * 100).intValue()"/>%)
        </td>
        <td>
            <div class="progress" style="height:8px;">
                <div class="progress-bar" role="progressbar"
                     style="width:<s:property
                         value='((double)#entry.value / (double)discussionOverview.totalComments * 100).intValue()'/>%;">
                </div>
            </div>
        </td>
    </tr>
</s:iterator>
```

**Indicator 3** renders keyword badges (each `kw` is a `Map.Entry<String,Integer>`):

```jsp
<s:if test="!discussionOverview.topKeywords.isEmpty()">
    <s:iterator value="discussionOverview.topKeywords" var="kw">
        <span class="label label-default" style="margin:2px;font-size:0.85em;">
            <s:property value="#kw.key"/>
            <span style="color:#ccc;margin-left:3px;">
                &times;<s:property value="#kw.value"/>
            </span>
        </span>
    </s:iterator>
</s:if>
<s:else>
    <p style="color:#aaa;font-size:0.9em;">No recurring keywords found.</p>
</s:else>
```

---

## 6. Design Patterns Applied

### 6.1 Strategy Pattern ✅

**Where:** `DiscussionOverviewService` — the `classifyComment()` method.

**How:** Each comment type (Question, Debate, Appreciation, Feedback, Neutral) is
determined by a distinct precompiled `Pattern` object. These patterns are the
concrete *strategies* for classification. The `classifyComment()` method acts as the
*context* that delegates to each strategy in priority order:

```java
if (QUESTION_PATTERN.matcher(lowerText).find())     { return "Question"; }
if (DEBATE_PATTERN.matcher(lowerText).find())       { return "Debate"; }
if (APPRECIATION_PATTERN.matcher(lowerText).find()) { return "Appreciation"; }
if (FEEDBACK_PATTERN.matcher(lowerText).find())     { return "Feedback"; }
return "Neutral";
```

Each `Pattern` encapsulates a self-contained classification algorithm (strategy)
that can be modified, replaced, or extended independently without touching the
calling code. Adding a new comment type requires only adding a new `Pattern`
constant and a new `if` branch — the rest of the system is unchanged.

---

### 6.2 Builder Pattern ✅

**Where:** `DiscussionOverviewService.compute()` building a `DiscussionOverview`
object.

**How:** The `compute()` method orchestrates the incremental, step-by-step
population of the `DiscussionOverview` POJO through a sequence of private methods
(`computeActivityLevel`, `computeTypeDistribution`, `computeTopKeywords`), each of
which sets a distinct group of fields on the same object:

```java
DiscussionOverview overview = new DiscussionOverview();
computeActivityLevel(overview, eligible);     // sets totalComments, avgPerDay, label
computeTypeDistribution(overview, eligible);  // sets typeDistribution, dominantType
computeTopKeywords(overview, eligible);       // sets topKeywords
return overview;
```

This is the classic Director + ConcreteBuilder relationship: `compute()` is the
Director that coordinates the build steps; the three `compute*` helpers are the
concrete build steps. The caller always receives a fully constructed, consistent
`DiscussionOverview` object.

---

### 6.3 Chain of Responsibility Pattern ✅

**Where:** Comment classification in `classifyComment()`.

**How:** The five classification `Pattern` objects form a chain. Each link
inspects the comment text and either claims responsibility (returns a type label) or
passes control to the next link. The chain terminates when the first matching
pattern is found, or at the final "Neutral" default:

```
[QUESTION?] → [DEBATE?] → [APPRECIATION?] → [FEEDBACK?] → [NEUTRAL (default)]
```

This is the Chain of Responsibility pattern because:
- Each handler (pattern) processes the request independently.
- Each handler either handles it (returns a label) or passes it on.
- The sender (`classifyComment`) does not know in advance which handler will respond.
- Handlers are ordered by priority and can be reordered, added, or removed without changing other handlers.

---

### 6.4 Patterns Considered but Not Applied

| Pattern | Reason not applied |
|---|---|
| **Observer** | Would be appropriate if the overview needed to update reactively when new comments are saved. Here the overview is computed on-demand per page load, so no event subscription is needed. |
| **Factory** | Would be useful if multiple implementations of `DiscussionOverview` or `DiscussionOverviewService` needed to be instantiated polymorphically. Currently there is only one implementation of each. |
| **Adapter** | Would be needed if an existing incompatible interface had to be adapted. No such mismatch exists here — `WeblogEntryComment` is used directly. |
| **Command** | Would encapsulate each computation as a discrete invocable object. Beneficial if computations needed to be queued, logged, or undone. The synchronous on-demand computation here does not require that overhead. |

---

## 7. File Map

```
app/src/main/java/org/apache/roller/weblogger/
  business/discussion/
    DiscussionOverview.java              ← NEW: data-holder POJO
    DiscussionOverviewService.java       ← NEW: all computation logic

  ui/struts2/editor/
    Comments.java                        ← MODIFIED: added overview field + query

app/src/main/webapp/WEB-INF/jsps/editor/
  Comments.jsp                           ← MODIFIED: added Discussion Overview panel
```

---

## 8. Request Flow — End to End

```
1. Author opens: /roller-ui/authoring/comments?weblog=<handle>&entryId=<id>

2. Comments.java :: loadComments()
   a. [Existing] Builds paginated query → fetches page of comments for table display
   b. [NEW] Builds overview query (same entry, no status filter, no paging)
         → wmgr.getComments(overviewCsc)  returns all comments for the entry
         → DiscussionOverviewService.compute(allForEntry)
              → filters to APPROVED + PENDING (excludes SPAM, DISAPPROVED)
              → computeActivityLevel()   fills totalComments, avgPerDay, activityLabel
              → computeTypeDistribution() fills typeDistribution, dominantType
              → computeTopKeywords()     fills topKeywords (ArrayList<Map.Entry>)
              → returns populated DiscussionOverview
         → setDiscussionOverview(overview)

3. Struts2 renders Comments.jsp
   → OGNL exposes discussionOverview via getDiscussionOverview()
   → <s:if test="discussionOverview != null"> panel renders:
        col 1: Activity Level table + badge
        col 2: Type distribution table + progress bars + dominant type
        col 3: Keyword label badges (or "No recurring keywords found.")
```

---

## 9. Technical Decisions and Bug Fixes

### 9.1 `ArrayList` instead of `Collections.unmodifiableList`

The initial implementation stored `topKeywords` as
`Collections.unmodifiableList(sorted.subList(0, limit))` — a `Collections$UnmodifiableRandomAccessList` (private inner class of `java.util.Collections`).

Struts2's OGNL security layer cannot invoke methods on private inner classes, so
`.isEmpty()` and `.size()` both silently returned nothing, causing the condition
`!discussionOverview.topKeywords.isEmpty()` to always evaluate to false.

**Fix:** Changed to `new ArrayList<>(sorted.subList(0, limit))` which gives OGNL
a standard public `java.util.ArrayList`.

### 9.2 Including PENDING comments

The original DB query used `.setStatus(APPROVED)`, which meant a freshly posted
comment awaiting moderation would never appear in the keyword cloud even though its
content is perfectly valid for analysis.

**Fix:** Removed the status filter from the overview DB query. `compute()` itself
filters to `APPROVED || PENDING`, excluding only `SPAM` and `DISAPPROVED`.

### 9.3 Stop-word list includes `"etc"`

The word `"etc"` was added to the stop-word set to prevent it from ever surfacing
as a top keyword — it carries no semantic value in this context.

---

## 10. How to Test

1. Build and start the application:
   ```
   mvn clean package -pl app -am -DskipTests
   ```
   Then start the server (or use Docker Compose).

2. Log in as a blog author and navigate to an entry that has at least one comment.

3. Open **Manage → Comments** and use the **Filter** to filter by that specific entry.

4. The **Discussion Overview** panel appears above the comment table showing:
   - Activity Level (total count, avg/day, badge)
   - Response Types (distribution table with percentages and dominant type)
   - Top Keywords (badge cloud)

5. To verify keyword extraction: post a comment with domain-specific words
   (e.g., *"Software engineering, Database, Object Oriented Programming"*) and
   confirm those words appear as keyword badges after a rebuild and refresh.

---

## 11. Constraints and Limitations

| Constraint | Detail |
|---|---|
| Entry-specific only | The panel does not appear on the global admin comment management page (only on per-entry filtered views) |
| English stop-words | The stop-word list is English-only; comments in other languages may produce noisier keyword results |
| No persistence | The overview is computed fresh on every page load; results are not cached or stored |
| No LLM | Deliberately uses only classical methods (regex, frequency counting, basic statistics) as required by the task specification |
| Regex classification | Comment type classification is heuristic and can misclassify short or ambiguous comments |

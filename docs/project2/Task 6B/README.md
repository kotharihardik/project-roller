# Task 6B — Conversation Breakdown (Team 27)

AI-powered and rule-based structured summary of a blog entry's comment section inside Apache Roller Weblogger. Identifies major discussion themes, surfaces representative comments per theme, and produces an overall conversation recap.

---

## Table of Contents

1. [Feature Summary](#1-feature-summary)
2. [Screenshots](#2-screenshots)
3. [Architecture Overview](#3-architecture-overview)
4. [Design Patterns Applied](#4-design-patterns-applied)
5. [Implementation Details](#5-implementation-details)
   - 5.1 [Theme.java — Theme Data Holder](#51-themejava--theme-data-holder)
   - 5.2 [ConversationBreakdown.java — Top-level Output DTO](#52-conversationbreakdownjava--top-level-output-dto)
   - 5.3 [BreakdownException.java — Domain Exception](#53-breakdownexceptionjava--domain-exception)
   - 5.4 [BreakdownStrategy.java — Strategy Interface](#54-breakdownstrategyjava--strategy-interface)
   - 5.5 [KeywordClusterStrategy.java — Rule-based Strategy (No LLM)](#55-keywordclusterstrategyjava--rule-based-strategy-no-llm)
   - 5.6 [GeminiBreakdownStrategy.java — AI-powered Strategy](#56-geminibreakdownstrategyjava--ai-powered-strategy)
   - 5.7 [BreakdownStrategyFactory.java — Factory](#57-breakdownstrategyfactoryjava--factory)
   - 5.8 [BreakdownServlet.java — HTTP Façade](#58-breakdownservletjava--http-façade)
   - 5.9 [breakdown.js — Frontend](#59-breakdownjs--frontend)
   - 5.10 [Comments.jsp — UI Integration](#510-commentsjsp--ui-integration)
   - 5.11 [web.xml — Servlet Registration](#511-webxml--servlet-registration)
   - 5.12 [roller.properties — Configuration Keys](#512-rollerproperties--configuration-keys)
6. [Request Flow — End to End](#6-request-flow--end-to-end)
7. [AI vs. Non-AI Decision](#7-ai-vs-non-ai-decision)
8. [Security Considerations](#8-security-considerations)
9. [Configuration Reference](#9-configuration-reference)
10. [How to Test](#10-how-to-test)

---

## 1. Feature Summary

When a blog author opens the **Comment Management** page for a specific entry, two new buttons appear below the Discussion Overview panel:

| Button | Label | Method |
|---|---|---|
| **Fast Breakdown (Keyword)** | Rule-based | TF-IDF cosine-similarity clustering, zero LLM calls |
| **AI Breakdown (Gemini)** | AI-powered | Single Google Gemini API call, explicit opt-in |

Both buttons POST to `/roller-services/breakdown` and render a **Breakdown Results** panel on the page with:

- A **strategy badge** showing which method was used.
- An **overall recap** paragraph summarising the conversation.
- A **theme list** where each theme has a descriptive label and 1–2 representative comment excerpts.

The feature is:
- Only available when viewing comments scoped to a specific entry (`queryEntry != null`).
- Protected by session authentication — anonymous users receive HTTP 401.
- Rate-limited to 10 requests per session per minute to prevent abuse.
- Non-destructive: it never modifies, approves, or deletes any comment.

---

## 2. Screenshots

> **Place screenshots in this directory (`docs/project2/Task 6B/`) and update the paths below.**

### Breakdown Results Panel — Keyword Strategy
![Breakdown Panel Keyword](/docs/project2/screenshots/task6b_manual_breakdown.png)

### Breakdown Results Panel — Gemini AI Strategy
![Breakdown Panel Gemini](/docs/project2/screenshots/task6b_gemini_breakdown.png)

---

## 3. Architecture Overview

```
Comments.jsp  (two buttons + result panel)
    │
    │  XHR POST /roller-services/breakdown?entryId=X&method=keyword|gemini
    ▼
BreakdownServlet.java  (Façade — auth, rate-limit, orchestration)
    │
    ├─ RollerSession.getAuthenticatedUser()  ← session auth check
    │
    └─ BreakdownStrategyFactory.get(method)
              │
              ├─── "keyword"  →  KeywordClusterStrategy
              │                      ├─ tokenise() + computeIdf()
              │                      ├─ computeTfIdf() per comment
              │                      ├─ selectSeeds() (greedy max-spread)
              │                      ├─ assign each comment → nearest cluster
              │                      ├─ label cluster from top IDF terms
              │                      └─ buildRecap() via DiscussionOverviewService
              │
              └─── "gemini"   →  GeminiBreakdownStrategy
                                     ├─ loadDotEnv()  ← reads .env file if OS env not set
                                     ├─ buildPrompt() ← cap 50 comments × 300 chars
                                     ├─ callGemini()  ← single HTTP POST
                                     └─ parseResponse() ← JSON → ConversationBreakdown
                                                          (fallback on malformed JSON)

ConversationBreakdown  ←  List<Theme> + String overallRecap + String strategyName
    │
    ▼
BreakdownServlet.toJson()  →  {"strategy":…, "themes":[…], "recap":…}
    │
    ▼
breakdown.js  →  renderBreakdown()  →  DOM panel update
```

**New files introduced:**

| File | Package / Location | Role |
|---|---|---|
| `Theme.java` | `business.breakdown` | Leaf DTO: theme name + representative excerpts |
| `ConversationBreakdown.java` | `business.breakdown` | Top-level output DTO |
| `BreakdownException.java` | `business.breakdown` | Checked domain exception with HTTP status code |
| `BreakdownStrategy.java` | `business.breakdown` | Strategy interface |
| `KeywordClusterStrategy.java` | `business.breakdown` | Rule-based strategy (TF-IDF, no LLM) |
| `GeminiBreakdownStrategy.java` | `business.breakdown` | AI-powered strategy (Google Gemini) |
| `BreakdownStrategyFactory.java` | `business.breakdown` | Factory — maps method name string to strategy |
| `BreakdownServlet.java` | `ui.struts2.ajax` | POST endpoint — auth, rate-limit, orchestration |
| `breakdown.js` | `roller-ui/scripts/` | Frontend XHR + DOM rendering |

**Modified files:**

| File | Change |
|---|---|
| `Comments.jsp` | Injected breakdown buttons panel, result panel, and `<script>` tag |
| `web.xml` | Registered `BreakdownServlet` at `/roller-services/breakdown` |
| `roller.properties` | Added `breakdown.*` configuration section |
| `GeminiBreakdownStrategy.java` | Added dotenv file parser fallback for `GEMINI_API_KEY` |
| `docker-compose.yml` | Added `env_file: .env` to the `roller` service |

---

## 4. Design Patterns Applied

### 4.1 Strategy Pattern

**Location:** `BreakdownStrategy.java` (interface) + `KeywordClusterStrategy.java` + `GeminiBreakdownStrategy.java`

The Strategy pattern is the central architectural decision. The interface defines a single contract:

```java
public interface BreakdownStrategy {
    String getStrategyName();
    ConversationBreakdown generate(List<WeblogEntryComment> comments)
            throws BreakdownException;
}
```

Both concrete strategies implement this interface independently. The servlet holds only a `BreakdownStrategy` reference — it has no knowledge of TF-IDF, clustering, or Gemini API details. Adding a third strategy (e.g., a local LLM) requires creating one new class and registering it in the factory; no other code changes.

**Why Strategy over inheritance?**
The two methods are structurally unrelated (one is pure math, the other is an HTTP call). Inheritance would force a false coupling. Strategy keeps the algorithms encapsulated and interchangeable.

---

### 4.2 Factory Pattern

**Location:** `BreakdownStrategyFactory.java`

```java
public static BreakdownStrategy get(String method) {
    String resolved = (method != null && !method.trim().isEmpty())
            ? method.trim().toLowerCase()
            : WebloggerConfig.getProperty("breakdown.defaultMethod", "keyword");

    switch (resolved) {
        case "keyword": return new KeywordClusterStrategy();
        case "gemini":  return new GeminiBreakdownStrategy();
        default:        return new KeywordClusterStrategy();
    }
}
```

The factory decouples the servlet (the caller) from concrete strategy classes. The servlet passes a plain string from the HTTP request; the factory resolves it to a strategy object. Crucially, the default strategy is configurable from `roller.properties` — the operator can switch the default method without touching the code.

---

### 4.3 Builder Pattern (Implicit — DTO Construction)

**Location:** `ConversationBreakdown.java`, `Theme.java`

Both DTOs are constructed with all required fields supplied at construction time and expose only getters after that (immutability intent). `Theme` wraps `representativeComments` in `Collections.unmodifiableList()` to prevent post-construction mutation.

```java
public Theme(String name, List<String> representativeComments) {
    this.name = name;
    this.representativeComments = Collections.unmodifiableList(
            new ArrayList<>(representativeComments));
}
```

While a full `Builder` inner class was not needed for two-field objects, the DTOs follow the same principle: separate construction from use, and guard the output object.

---

### 4.4 Adapter Pattern (Façade Variant)

**Location:** `BreakdownServlet.java`

`BreakdownServlet` acts as an adapter between the HTTP world (raw `HttpServletRequest` parameters, `HttpServletResponse` JSON output) and the Strategy/Factory internal API (`BreakdownStrategy`, `ConversationBreakdown`). It translates:

- HTTP `entryId` param → `WeblogEntry` → `List<WeblogEntryComment>` (via Roller's `WeblogEntryManager`)
- `ConversationBreakdown` object → JSON string → HTTP response body

Neither the strategies nor the DTOs know anything about HTTP; neither the servlet knows anything about clustering or Gemini.

---

## 5. Implementation Details

### 5.1 `Theme.java` — Theme Data Holder

**Path:** `app/src/main/java/org/apache/roller/weblogger/business/breakdown/Theme.java`

A leaf DTO that represents one identified theme within the conversation.

```java
public class Theme {
    private String       name;                     // short descriptive label
    private List<String> representativeComments;   // 1–2 excerpt strings (unmodifiable)

    public Theme(String name, List<String> representativeComments) {
        this.name = name;
        this.representativeComments = Collections.unmodifiableList(
                new ArrayList<>(representativeComments));
    }

    public String       getName()                   { return name; }
    public List<String> getRepresentativeComments() { return representativeComments; }
}
```

**Key decision:** The list is defensively copied and made unmodifiable at construction to prevent external mutation of the breakdown result after it has been produced.

---

### 5.2 `ConversationBreakdown.java` — Top-level Output DTO

**Path:** `app/src/main/java/org/apache/roller/weblogger/business/breakdown/ConversationBreakdown.java`

The single return type of `BreakdownStrategy.generate()`. Carries the complete result of one breakdown run.

```java
public class ConversationBreakdown {
    private final List<Theme> themes;
    private final String      overallRecap;
    private final String      strategyName;

    public ConversationBreakdown(List<Theme> themes,
                                 String overallRecap,
                                 String strategyName) {
        this.themes       = Collections.unmodifiableList(new ArrayList<>(themes));
        this.overallRecap = overallRecap;
        this.strategyName = strategyName;
    }
    // getters ...
}
```

All three fields are set at construction and wrapped in unmodifiable wrappers, ensuring the DTO is effectively immutable once returned from a strategy.

---

### 5.3 `BreakdownException.java` — Domain Exception

**Path:** `app/src/main/java/org/apache/roller/weblogger/business/breakdown/BreakdownException.java`

A checked exception that carries an optional `int statusCode`. This lets a strategy signal "the API returned HTTP 429 (rate limited)" and the servlet can forward it directly as an HTTP 429 response to the browser.

```java
public class BreakdownException extends Exception {
    private final int statusCode;   // default 500

    public BreakdownException(String message)                  { this(message, 500); }
    public BreakdownException(String message, int statusCode)  { ... }
    public BreakdownException(String message, Throwable cause) { ... }

    public int getStatusCode() { return statusCode; }
}
```

---

### 5.4 `BreakdownStrategy.java` — Strategy Interface

**Path:** `app/src/main/java/org/apache/roller/weblogger/business/breakdown/BreakdownStrategy.java`

```java
public interface BreakdownStrategy {

    /** Short identifier (e.g. "keyword", "gemini"). Embedded in the output DTO. */
    String getStrategyName();

    /**
     * Analyses the supplied comments and returns a structured breakdown.
     *
     * @param comments  All comments for the entry (any approval status).
     *                  Strategies filter internally for eligible ones.
     * @throws BreakdownException if the analysis cannot be completed.
     */
    ConversationBreakdown generate(List<WeblogEntryComment> comments)
            throws BreakdownException;
}
```

The interface is minimal: two methods, no dependencies on HTTP, Roller internals, or external APIs.

---

### 5.5 `KeywordClusterStrategy.java` — Rule-based Strategy (No LLM)

**Path:** `app/src/main/java/org/apache/roller/weblogger/business/breakdown/KeywordClusterStrategy.java`

The rule-based strategy. Performs all computation locally using pure Java — no external API calls, no LLM.

#### Algorithm

```
1. Filter eligible comments (APPROVED + PENDING, non-empty content).
2. Tokenise each comment: strip HTML tags → lowercase → remove non-alpha → remove stop words → discard tokens < 3 chars.
3. Compute IDF (Inverse Document Frequency):
       idf(term) = log(N / df(term))
   where N = total eligible comments, df = number of comments containing the term.
4. Compute TF-IDF vector per comment:
       tf(term, doc) = count(term in doc) / totalTerms(doc)
       tfidf(term, doc) = tf(term, doc) × idf(term)
5. Select k seed comments (greedy max-spread):
       seed[0] = comment with highest total TF-IDF weight (most content-rich)
       seed[i] = comment with minimum cosine similarity to any existing seed
6. Assign every comment to the nearest seed by cosine similarity.
7. Label each cluster using the top 3 IDF-weighted terms from the aggregated cluster vector.
8. Select 2 representative comments per cluster (highest summed TF-IDF weight).
9. Build recap using DiscussionOverviewService.compute() → template string
       (activity label + dominant comment type + top 5 keywords).
```

#### Key methods

```java
private static List<String>        tokenise(String text)
private static Map<String, Double> computeIdf(List<List<String>> tokenized)
private static Map<String, Double> computeTfIdf(List<String> tokens, Map<String, Double> idf)
private static int[]               selectSeeds(List<Map<String, Double>> vectors, int k)
private static double              cosineSimilarity(Map<String, Double> a, Map<String, Double> b)
private static String              buildRecap(List<WeblogEntryComment> eligible)
```

**Why TF-IDF + cosine?**
- No external dependencies.
- Naturally surfaces topic differences between comments — comments about different subjects will have low cosine similarity and end up in different clusters.
- Deterministic: same input always produces the same output.

**Number of clusters (k)**

| Eligible comments | k (clusters) |
|---|---|
| ≤ 3 | 1 |
| 4 – 9 | 2 |
| 10 – 19 | 3 |
| ≥ 20 | 4 |

---

### 5.6 `GeminiBreakdownStrategy.java` — AI-powered Strategy

**Path:** `app/src/main/java/org/apache/roller/weblogger/business/breakdown/GeminiBreakdownStrategy.java`

The AI-powered strategy. Makes exactly **one** Gemini API call per breakdown request.

#### API key resolution (priority order)

1. `System.getenv("GEMINI_API_KEY")` — OS / Docker environment variable
2. `.env` file parsed by the built-in `loadDotEnv()` method (searches `user.dir/.env`, `user.dir/../.env`, `catalina.base/.env`, `catalina.base/../../.env`)
3. `roller.properties` key `breakdown.gemini.apiKey` (or `translation.gemini.apiKey`)

The `.env` parser was added to make development easier — the key defined in the project's `.env` file is found automatically without any extra setup:

```java
private static String getEnv(String key) {
    String val = System.getenv(key);
    if (val != null && !val.trim().isEmpty()) return val.trim();
    String dotVal = loadDotEnv().get(key);
    return (dotVal != null && !dotVal.isEmpty()) ? dotVal : null;
}
```

#### Cost controls

| Limit | Value | Reason |
|---|---|---|
| Max comments sent | 50 | Stay within Gemini input token limits |
| Max chars per comment | 300 | Truncated with `…` to cap token count |
| Temperature | 0 | Deterministic JSON output |
| Retries | None | Rate-limit errors bubble up as HTTP 429 to caller |

#### Prompt structure

The prompt is built by `buildPrompt()`. Each eligible comment is embedded verbatim (back-ticks escaped to single quotes, text capped at `MAX_COMMENT_CHARS`) and the model is instructed to return strict JSON:

```java
private String buildPrompt(List<WeblogEntryComment> comments) {
    StringBuilder sb = new StringBuilder();
    sb.append("You are a helpful assistant analysing reader comments on a blog post.\n\n");
    sb.append("Below are ").append(comments.size())
      .append(" reader comments, numbered from 1.\n");
    sb.append("Each comment is enclosed in triple back-ticks.\n\n");

    for (int i = 0; i < comments.size(); i++) {
        String text = comments.get(i).getContent();
        if (text.length() > MAX_COMMENT_CHARS) {
            text = text.substring(0, MAX_COMMENT_CHARS) + "…";
        }
        // Escape back-ticks inside the comment to avoid prompt confusion
        text = text.replace("`", "'");
        sb.append("Comment ").append(i + 1).append(":\n```\n").append(text).append("\n```\n\n");
    }

    sb.append("TASK:\n");
    sb.append("1. Identify 2 to 4 major themes being discussed.\n");
    sb.append("2. For each theme, provide 1 to 2 short representative excerpts.\n");
    sb.append("3. Write a 2-sentence overall recap of the conversation.\n\n");
    sb.append("RULES:\n");
    sb.append("- Reply ONLY with a valid JSON object — no markdown, no explanation.\n");
    sb.append("- Use exactly this structure:\n");
    sb.append("{\n");
    sb.append("  \"themes\": [\n");
    sb.append("    {\n");
    sb.append("      \"name\": \"short theme label\",\n");
    sb.append("      \"representatives\": [\"excerpt 1\", \"excerpt 2\"]\n");
    sb.append("    }\n");
    sb.append("  ],\n");
    sb.append("  \"recap\": \"Overall recap sentence(s).\"\n");
    sb.append("}\n");

    return sb.toString();
}
```

Key decisions in the prompt:
- **Triple back-tick delimiters** clearly separate each comment from the surrounding instruction text, reducing the chance of the model treating comment content as instructions.
- **Back-tick escaping** (`text.replace("\`", "'")`) prevents a comment containing ` ``` ` from breaking the delimiter structure.
- **Temperature 0** (set in `callGemini()`) combined with the strict JSON-only rule produces consistent, parseable output across requests.

#### Response parsing with fallback

`parseResponse()` strips optional Markdown fences (` ```json ... ``` `), then parses the JSON. If parsing fails (malformed model output), a fallback `ConversationBreakdown` is returned containing a single "General Discussion" theme and a message indicating the format was unexpected — it never throws to the servlet in the parse-error case.

---

### 5.7 `BreakdownStrategyFactory.java` — Factory

**Path:** `app/src/main/java/org/apache/roller/weblogger/business/breakdown/BreakdownStrategyFactory.java`

```java
public static BreakdownStrategy get(String method) {
    String resolved = (method != null && !method.trim().isEmpty())
            ? method.trim().toLowerCase()
            : WebloggerConfig.getProperty("breakdown.defaultMethod", "keyword");

    switch (resolved) {
        case "keyword": return new KeywordClusterStrategy();
        case "gemini":  return new GeminiBreakdownStrategy();
        default:
            LOG.warn("Unknown breakdown method '" + resolved + "', falling back to keyword.");
            return new KeywordClusterStrategy();
    }
}
```

Strategy objects are created fresh per request (stateless — no instance data is reused). The `GeminiBreakdownStrategy` constructor reads environment variables and configures the `HttpClient` at construction time, which is cheap.

---

### 5.8 `BreakdownServlet.java` — HTTP Façade

**Path:** `app/src/main/java/org/apache/roller/weblogger/ui/struts2/ajax/BreakdownServlet.java`

A plain `HttpServlet` (not a Struts2 action) registered at `/roller-services/breakdown`. Accepts only `POST` requests.

#### Request lifecycle

```
POST /roller-services/breakdown?entryId=X&method=keyword

1. Load HTTP session → look up RollerSession object
       session.getAttribute("org.apache.roller.weblogger.rollersession")
2. Check authenticated user via rollerSession.getAuthenticatedUser()
       → 401 JSON if null
3. Rate-limit check (10 requests / session / 60-second tumbling window)
       → 429 JSON if exceeded
4. Parse entryId → load WeblogEntry → load all its comments
       → 400 JSON if entryId missing or entry not found
5. BreakdownStrategyFactory.get(method).generate(comments)
       → 500 / 429 JSON on BreakdownException
6. toJson(breakdown) → 200 JSON
```

#### `toJson()` output format

```json
{
  "strategy": "keyword",
  "themes": [
    {
      "name": "Performance and Speed",
      "representatives": [
        "The page loads within 2 seconds on my machine.",
        "Response time is noticeably better with the new cache."
      ]
    }
  ],
  "recap": "Readers discussed performance improvements and configuration options. Overall sentiment was positive with some questions about advanced setup."
}
```

#### Authentication — critical detail

Roller stores its authentication state as a `RollerSession` object in the HTTP session under the key `"org.apache.roller.weblogger.rollersession"` (constant `RollerSession.ROLLER_SESSION`). A plain `session.getAttribute("user")` check would always return null even for logged-in users — this is why the correct lookup is:

```java
HttpSession session = req.getSession(false);
RollerSession rollerSession = (session != null)
        ? (RollerSession) session.getAttribute(RollerSession.ROLLER_SESSION)
        : null;
boolean loggedIn = rollerSession != null
        && rollerSession.getAuthenticatedUser() != null;
```

---

### 5.9 `breakdown.js` — Frontend

**Path:** `app/src/main/webapp/roller-ui/scripts/breakdown.js`

Vanilla JavaScript (no jQuery dependency). Loaded by a `<script>` tag injected into `Comments.jsp`.

#### Responsibilities

- Wire click handlers to both breakdown buttons.
- Show a spinner in the result panel while the XHR is in-flight.
- On success: call `renderBreakdown(data)` to build the DOM panel.
- On error: call `showPanelError(message)` and, if debug fields are present in the response, render a debug table.

#### `renderBreakdown(data)` output structure

```html
<div>
  <!-- Strategy badge -->
  <span class="label label-info">keyword</span>

  <!-- Overall recap -->
  <div class="alert alert-info">
    <strong>Recap:</strong> Readers discussed performance improvements...
  </div>

  <!-- Themes list-group -->
  <div class="list-group">
    <div class="list-group-item">
      <h4 class="list-group-item-heading">Performance and Speed</h4>
      <ul>
        <li>The page loads within 2 seconds...</li>
        <li>Response time is noticeably better...</li>
      </ul>
    </div>
    ...
  </div>
</div>
```

#### Context-path auto-detection

The servlet URL is derived from the script's own `src` attribute, avoiding hard-coded context paths:

```javascript
var src = document.currentScript
        ? document.currentScript.src
        : (function() { /* IE fallback */ })();
var ctx = src ? src.replace(/\/roller-ui\/scripts\/breakdown\.js.*/, '') : '';
var url = ctx + '/roller-services/breakdown';
```

---

### 5.10 `Comments.jsp` — UI Integration

**Path:** `app/src/main/webapp/WEB-INF/jsps/editor/Comments.jsp`

The following block is injected after the existing Discussion Overview panel, wrapped in a `<s:if>` guard so it only appears when a specific entry is selected:

```jsp
<s:if test="actionName == 'comments' && queryEntry != null">

  <!-- Breakdown buttons panel -->
  <div class="panel panel-default">
    <div class="panel-heading"><strong>Conversation Breakdown</strong></div>
    <div class="panel-body">
      <p class="text-muted">Analyse the themes and patterns...</p>
      <button id="btn-breakdown-keyword" class="btn btn-default"
              data-entry-id="<s:property value='queryEntry.id'/>">
        <span class="glyphicon glyphicon-tags"></span> Fast Breakdown (Keyword)
      </button>
      <button id="btn-breakdown-gemini" class="btn btn-primary"
              data-entry-id="<s:property value='queryEntry.id'/>">
        <span class="glyphicon glyphicon-flash"></span> AI Breakdown (Gemini)
      </button>
    </div>
  </div>

  <!-- Result panel (hidden until a button is clicked) -->
  <div id="breakdown-panel" class="panel panel-info" style="display:none;">
    <div class="panel-heading"><strong>Breakdown Results</strong></div>
    <div class="panel-body" id="breakdown-content"></div>
  </div>

  <!-- Script -->
  <script src="<s:url value='/roller-ui/scripts/breakdown.js' />?v=1"></script>

</s:if>
```

---

### 5.11 `web.xml` — Servlet Registration

**Path:** `app/src/main/webapp/WEB-INF/web.xml`

```xml
<servlet>
  <servlet-name>BreakdownServlet</servlet-name>
  <servlet-class>
    org.apache.roller.weblogger.ui.struts2.ajax.BreakdownServlet
  </servlet-class>
  <load-on-startup>11</load-on-startup>
</servlet>

<servlet-mapping>
  <servlet-name>BreakdownServlet</servlet-name>
  <url-pattern>/roller-services/breakdown</url-pattern>
</servlet-mapping>
```

`load-on-startup=11` ensures the servlet initialises after Roller's core servlets (which use 1–10), so `WebloggerFactory.getWeblogger()` is available.

---

### 5.12 `roller.properties` — Configuration Keys

**Path:** `app/src/main/resources/org/apache/roller/weblogger/config/roller.properties`

```properties
# ---- Conversation Breakdown (Task 6B) ----------------------------------------

# Default method to use when no method is specified in the request.
# Valid values: keyword, gemini
breakdown.defaultMethod=keyword

# Gemini API settings (only needed for the "gemini" method).
# Recommended: set GEMINI_API_KEY and TRANSLATION_GEMINI_API_URL as environment
# variables or in the .env file instead of hard-coding here.
#breakdown.gemini.apiKey=
#breakdown.gemini.apiUrl=
#breakdown.gemini.model=gemini-2.5-flash
#breakdown.gemini.timeoutSeconds=20
```

---

## 6. Request Flow — End to End

```
User (logged-in blog author)
  │
  │  click "Fast Breakdown (Keyword)" button
  │
  ▼
breakdown.js
  │  disables both buttons, shows spinner
  │  XHR POST /roller-services/breakdown
  │       entryId = <from data-entry-id attribute>
  │       method  = "keyword"
  │
  ▼
BreakdownServlet.doPost()
  │
  ├─ req.getSession(false) → HttpSession
  ├─ session.getAttribute(RollerSession.ROLLER_SESSION) → RollerSession
  ├─ rollerSession.getAuthenticatedUser() → User  (→ 401 if null)
  │
  ├─ rate-limit check on session ID  (→ 429 if exceeded)
  │
  ├─ WeblogEntryManager.getWeblogEntry(entryId)  (→ 400 if not found)
  ├─ WeblogEntryManager.getComments(params)  → List<WeblogEntryComment>
  │
  ├─ BreakdownStrategyFactory.get("keyword") → KeywordClusterStrategy
  │
  └─ strategy.generate(comments) → ConversationBreakdown
          │
          ├─ filter eligible (APPROVED + PENDING)
          ├─ tokenise each comment
          ├─ compute IDF across corpus
          ├─ compute TF-IDF vector per comment
          ├─ select k seeds (greedy max-spread)
          ├─ assign each comment to nearest seed (cosine similarity)
          ├─ label clusters from top-IDF terms
          ├─ select 2 representatives per cluster
          └─ buildRecap() via DiscussionOverviewService
                 → "Very Active conversation with 23 comments.
                    Dominant type: Question.
                    Top keywords: performance, cache, latency…"

BreakdownServlet.toJson(breakdown)
  → {"strategy":"keyword","themes":[…],"recap":"…"}
  → HTTP 200

breakdown.js xhr.onload
  │  re-enables buttons
  │  data = JSON.parse(xhr.responseText)
  └─ renderBreakdown(data)
          ├─ strategy badge
          ├─ recap alert-info div
          └─ themes list-group (with representatives as <ul><li>)
          → sets #breakdown-panel to visible
```

---

## 7. AI vs. Non-AI Decision

| Scenario | Strategy Used | Rationale |
|---|---|---|
| Page load (automatic) | **Neither** | No breakdown is computed on load — avoids unsolicited API costs and latency |
| Author clicks "Fast Breakdown" | **KeywordClusterStrategy** | Deterministic, no cost, no external dependency, instant results |
| Author clicks "AI Breakdown" | **GeminiBreakdownStrategy** | Explicit opt-in; used when nuanced natural-language themes are needed |

The rule of thumb applied: **use an LLM only when the task requires natural-language generation or semantic understanding that rules cannot replicate at ≥90% accuracy**. Identifying themes and generating a two-sentence recap requires natural-language understanding — that is where the Gemini strategy is justified. Keyword extraction and clustering do not require an LLM.

---

## 8. Security Considerations

| Risk | Mitigation |
|---|---|
| Unauthenticated access | `BreakdownServlet` checks `RollerSession.getAuthenticatedUser()` and returns HTTP 401 before touching any data |
| API key exposure | Key read from environment or `.env` file; never hard-coded in source; `.env` excluded from Docker images via `dockerignore` |
| Prompt injection via comment content | Back-ticks inside comments are escaped to single quotes before embedding in the Gemini prompt |
| Excessive API usage / cost | Rate limiter: 10 requests/session/minute; hard cap at 50 comments × 300 chars per Gemini call |
| Denial of Service via large comment sets | `KeywordClusterStrategy` is O(N²) in cosine similarity assignment but capped implicitly by `MAX_COMMENTS=50` on the eligible set fed to either strategy |

---

## 9. Configuration Reference

| Environment Variable | `.env` key | `roller.properties` key | Default | Description |
|---|---|---|---|---|
| `GEMINI_API_KEY` | `GEMINI_API_KEY` | `breakdown.gemini.apiKey` | _(none)_ | Google Gemini API key |
| `TRANSLATION_GEMINI_API_URL` | `TRANSLATION_GEMINI_API_URL` | `breakdown.gemini.apiUrl` | _(none)_ | Full Gemini endpoint URL |
| `TRANSLATION_GEMINI_MODEL` | `TRANSLATION_GEMINI_MODEL` | `breakdown.gemini.model` | `gemini-2.5-flash` | Gemini model name |
| — | — | `breakdown.defaultMethod` | `keyword` | Default strategy when `method` param is absent |
| — | — | `breakdown.gemini.timeoutSeconds` | `20` | HTTP connect timeout for Gemini calls |

---

## 10. How to Test

### Prerequisites
- Roller running locally (Docker or Maven embedded Tomcat).
- At least one blog entry with 3+ approved/pending comments.
- For AI breakdown: `GEMINI_API_KEY` set in `.env` or as an OS environment variable.

### Steps

1. Log in as an editor or admin.
2. Navigate to **Entries → Comments** or click "Manage Comments" for a specific entry.
3. The **Conversation Breakdown** panel with two buttons appears below the Discussion Overview.
4. Click **Fast Breakdown (Keyword)**:
   - The panel header shows "Breakdown Results".
   - Strategy badge reads `keyword`.
   - Themes are labelled with keywords (e.g., "performance cache latency").
   - Representatives are short comment excerpts.
   - Recap is a template sentence using activity level and dominant type.
5. Click **AI Breakdown (Gemini)**:
   - Strategy badge reads `gemini`.
   - Themes have natural-language labels (e.g., "Performance Concerns").
   - Recap is a two-sentence AI-written paragraph.
6. Open browser DevTools → Network tab → verify request is `POST /roller-services/breakdown`.
7. Verify that loading the page without clicking any button makes **zero** calls to the breakdown endpoint.

### Error cases to verify

| Case | Expected behaviour |
|---|---|
| Not logged in | HTTP 401, error message displayed in panel |
| `entryId` missing from request | HTTP 400, error message displayed |
| Gemini API key not set | HTTP 500, "Gemini API key not configured" message |
| Rate limit exceeded (> 10 req/min) | HTTP 429, "Too many requests" message |

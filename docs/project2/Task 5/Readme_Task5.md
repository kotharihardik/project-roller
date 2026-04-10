# Translation Module — Task 5 (Team 27)

Complete reference for the in-page translation feature added to Apache Roller Weblogger.

---

## Table of Contents

1. [What This Feature Does](#1-what-this-feature-does)
2. [Full Request Flow — End to End](#2-full-request-flow--end-to-end)
3. [Architecture Overview](#3-architecture-overview)
4. [Design Patterns](#4-design-patterns)
5. [File Map](#5-file-map)
6. [Supported Languages](#6-supported-languages)
7. [Configuration Files — Which One to Edit](#7-configuration-files--which-one-to-edit)
8. [Environment Variables (.env)](#8-environment-variables-env)
9. [How to Run Locally](#9-how-to-run-locally)
10. [How to Switch Between Gemini and Sarvam](#10-how-to-switch-between-gemini-and-sarvam)
11. [How to Add Translation to a New Page](#11-how-to-add-translation-to-a-new-page)
12. [Client-Side Caching (Task 5B)](#12-client-side-caching-task-5b)
13. [API Reference](#13-api-reference)
14. [Validation Commands](#14-validation-commands)
15. [Troubleshooting](#15-troubleshooting)
16. [Security Notes](#16-security-notes)

---

## 1. What This Feature Does

The translation module adds a **translate bar** at the top of supported Roller pages.
Users can select an Indian language and click **Translate** — all text on the page is
replaced in-place with the translated version. Clicking **Reset** restores the original.

Key properties:
- Zero page reload — DOM text nodes are replaced directly
- Results cached in browser `localStorage` (7 days) — instant re-apply on revisit
- Partial retranslation: only changed text is re-sent to the API on subsequent visits
- Supports two backend providers: **Sarvam AI** and **Google Gemini**
- The translate bar label shows which provider is active: *Translate via Gemini* or *Translate via Sarvam*

---

## 2. Full Request Flow — End to End

```
Browser (user clicks Translate)
        │
        ▼
translation.js
  ├─ 1. DOMContentLoaded fires
  ├─ 2. fetchMetadata()  →  GET /roller/roller-services/translate
  │         ← { "provider": "gemini", "languages": ["en","hi","gu",...] }
  ├─ 3. injectWidget()   →  renders translate bar in DOM
  ├─ 4. autoApplyFromCache() → checks localStorage, applies instantly if cached
  │
  │ (user selects Tamil, clicks Translate)
  │
  ├─ 5. collectTextNodes(root)    → TreeWalker walks DOM, collects TEXT_NODEs
  │      collectButtonInputs(root) → also collects <input type="submit"> / <button>
  ├─ 6. computeFingerprint() → hash of heading + content text
  ├─ 7. check localStorage cache
  │      ├─ FULL HIT   → applyTranslationsFromCache() — zero API calls
  │      └─ MISS/PARTIAL →
  │
  ├─ 8. translateInBatches()
  │      splits texts into batches of 180
  │      POST /roller/roller-services/translate
  │        body: { "source":"auto", "target":"ta", "texts":["Hello","Sign in",...] }
  │
  ▼
TranslationServlet.java  (javax.servlet.HttpServlet)
  ├─ doGet()  → returns provider name + supported language list
  └─ doPost() → reads JSON body → calls TranslationService.translate()
        │
        ▼
TranslationService.java
  ├─ validates request (language supported? texts within limits?)
  ├─ checks in-memory server-side TTL cache (24h TTL)
  │    cache misses only → calls provider
  │
  └─ buildProvider("gemini")  or  buildProvider("sarvam")
       reads translation.provider from roller-jettyrun.properties
        │
        ▼
GeminiTranslationProvider.java  (Gemini)
  ├─ builds strict systemInstruction prompt (5 rules: no omit, no summarize, etc.)
  ├─ sends ALL texts as one JSON array in a single POST
  ├─ POST https://generativelanguage.googleapis.com/.../gemini-2.5-flash:generateContent
  │       ?key=GEMINI_API_KEY  (from env var)
  ├─ stripMarkdownFences()  → removes ```json fences Gemini sometimes adds
  ├─ cleanTranslatedText()  → removes wrapping quotes, literal \n, trailing commas
  └─ returns List<String>

       OR

SarvamTranslationProvider.java  (Sarvam)
  ├─ maps ISO 639-1 → Sarvam BCP-47  (e.g. "ta" → "ta-IN")
  ├─ fires ONE CompletableFuture per text (all concurrent, not sequential)
  │       Header: API-Subscription-Key: SARVAM_API_KEY
  ├─ per-item fallback: if one item fails (e.g. HTTP 422), keeps original text
  │       → prevents entire batch from failing (root cause of missing "Password (Confirm)")
  └─ returns List<String>

        │
        ▼
TranslationServlet.java
  └─ JSON response: { "provider": "gemini", "translations": ["வணக்கம்", ...] }

        │
        ▼
translation.js
  ├─ 9.  applyTranslations() → sets node.nodeValue / input.value per text node
  ├─ 10. saveCache()         → stores to localStorage
  └─ 11. status: "Translated to தமிழ் (Tamil)"
```

---

## 3. Architecture Overview

### Frontend — `translation.js`

| Responsibility | How |
|----------------|-----|
| Auto-detect context path | Parses `/roller` prefix from `<script src>` |
| Collect translatable text | `TreeWalker` visiting `TEXT_NODE`s |
| Collect button text | `collectButtonInputs()` for `<input type="submit">` and `<button>` |
| Skip non-translatable elements | `SKIP_TAGS`: SCRIPT, STYLE, CODE, INPUT, SELECT, etc. |
| Send to API | Batches of 180 texts per POST |
| Cache results | `localStorage` key: `roller_tx|<url>|<lang>` |
| Fingerprint content | Hash of `h1–h4` + article/entry content text only |
| Partial retranslation | Only sends changed/new text nodes to API |
| Remember last language | `localStorage` key: `roller_tx_config` |
| Auto-apply on page load | Reads cache before any API call |

### Backend — Java

| Class | Role |
|-------|------|
| `TranslationServlet` | HTTP entry point, rate limiting (60 req/min/IP), CORS |
| `TranslationService` | Input validation, server-side TTL cache, provider factory |
| `TranslationProvider` | Interface: `translate()`, `getProviderName()`, `getSupportedLanguages()` |
| `GeminiTranslationProvider` | Gemini API: batches all texts in one prompt, retry logic, strict system instruction, response cleanup |
| `RemoteTranslationProvider` | `@Deprecated` thin wrapper — delegates to `GeminiTranslationProvider`. Kept only for `translation.provider=remote` backward compatibility. |
| `SarvamTranslationProvider` | Sarvam AI: ISO→BCP-47 mapping, all texts sent **concurrently** via `CompletableFuture` (parallel HTTP calls) |

### Config loading order (highest priority wins)

```
roller-jettyrun.properties   ← mvn jetty:run  ✅ EDIT THIS for local dev
roller-custom.properties     ← mvn test (JUnit) only
roller.properties            ← base defaults, never edit
```

---

## 4. Design Patterns

The translation module is intentionally structured around well-known GoF and architectural patterns.
Each pattern is identified, located in the code, and explained below.

---

### 4.1 Strategy Pattern

**Intent:** Define a family of algorithms, encapsulate each one, and make them interchangeable.

**Where:**
- Interface: `TranslationProvider.java`
- Concrete strategies: `GeminiTranslationProvider.java`, `SarvamTranslationProvider.java`

**How it works:**
```
TranslationProvider          ← Strategy interface
    translate(texts, src, tgt)
    getProviderName()
    getSupportedLanguages()
    detectLanguage(text)

        ┌──────────────────────────┐  ┌──────────────────────────┐
        │ GeminiTranslationProvider│  │ SarvamTranslationProvider│
        │  POST Gemini API         │  │  POST Sarvam API         │
        │  batch in one call       │  │  parallel futures        │
        └──────────────────────────┘  └──────────────────────────┘
```

`TranslationService` holds a reference to `TranslationProvider` — it calls
`provider.translate(...)` without knowing whether Gemini or Sarvam is running.
Switching providers only requires changing `translation.provider` in a properties file.

---

### 4.2 Factory Method Pattern

**Intent:** Define an interface for creating an object, but let a method decide which class to instantiate.

**Where:** `TranslationService.buildProvider(String name)` — lines ~355–410 of `TranslationService.java`

**How it works:**
```java
// Caller:
this.provider = buildProvider("gemini");   // or "sarvam"

// Factory:
private TranslationProvider buildProvider(String name) {
    switch (name.toLowerCase()) {
        case "sarvam" -> return new SarvamTranslationProvider(...);
        case "gemini" -> return new GeminiTranslationProvider(...);
        case "remote" -> return new RemoteTranslationProvider(...); // deprecated
    }
}
```

The factory reads all configuration (API URL, API key, model, timeout) and passes it
to the constructor. The rest of the system never calls `new GeminiTranslationProvider(...)` directly.

---

### 4.3 Adapter Pattern

**Intent:** Convert the interface of a class into another interface that clients expect.

**Where (instance 1 — API protocol adapter):**
Both provider classes adapt two different REST API protocols to the single `TranslationProvider` interface:

| External API | Protocol detail | Adapter class |
|---|---|---|
| Google Gemini | `POST .../generateContent`, JSON body with `contents[].parts[].text`, response at `candidates[0].content.parts[0].text` | `GeminiTranslationProvider` |
| Sarvam AI | `POST /translate`, header `API-Subscription-Key`, response field `translated_text` | `SarvamTranslationProvider` |

**Where (instance 2 — language code adapter):**
`SarvamTranslationProvider.resolveSarvamCode()` adapts ISO 639-1 codes used by the
rest of the application into Sarvam's BCP-47 locale codes:
```
"ta"  →  "ta-IN"
"or"  →  "od-IN"   ← different code entirely
"hi"  →  "hi-IN"
```
---

### Pattern Summary

| # | Pattern | Location | Purpose |
|---|---------|----------|---------|
| 1 | **Strategy** | `TranslationProvider` interface + providers | Swap Gemini ↔ Sarvam without changing callers |
| 2 | **Factory Method** | `TranslationService.buildProvider()` | Instantiate correct provider from config |
| 3 | **Adapter** | `GeminiTranslationProvider`, `SarvamTranslationProvider` | Adapt external REST APIs + ISO 639-1 → BCP-47 |

---

## 5. File Map

```
app/src/main/webapp/roller-ui/scripts/
  translation.js                         ← frontend widget (all client logic)

app/src/main/webapp/roller-ui/styles/
  roller.css                             ← .roller-page-title: Indic font-family

app/src/main/java/.../translation/
  TranslationProvider.java               ← interface
  TranslationService.java                ← service layer + cache + provider factory
  GeminiTranslationProvider.java         ← Gemini implementation (renamed from RemoteTranslationProvider)
  RemoteTranslationProvider.java         ← @Deprecated wrapper → delegates to GeminiTranslationProvider
  SarvamTranslationProvider.java         ← Sarvam implementation (parallel batch via CompletableFuture)
  TranslationException.java              ← checked exception

app/src/main/java/.../ajax/
  TranslationServlet.java                ← REST endpoint /roller-services/translate

app/src/main/webapp/WEB-INF/
  web.xml                                ← maps /roller-services/translate → servlet

app/src/main/webapp/WEB-INF/jsps/tiles/
  tiles-simplepage.jsp                   ← includes translation.js (UI pages)
  tiles-loginpage.jsp                    ← includes translation.js (login/register)

app/src/test/resources/
  roller-jettyrun.properties             ← ✅ active config for mvn jetty:run
  roller-custom.properties               ← config for mvn test (JUnit only)

app/src/main/resources/.../config/
  roller.properties                      ← base defaults (never edit)
```

---

## 6. Supported Languages

| Language  | ISO Code | Gemini | Sarvam |
|-----------|----------|--------|--------|
| English   | `en`  | ✅ | ✅ |
| Hindi     | `hi`  | ✅ | ✅ |
| Gujarati  | `gu`  | ✅ | ✅ |
| Marathi   | `mr`  | ✅ | ✅ |
| Tamil     | `ta`  | ✅ | ✅ |
| Telugu    | `te`  | ✅ | ✅ |
| Punjabi   | `pa`  | ✅ | ✅ |
| Kannada   | `kn`  | ✅ | ✅ |

> **Note:** Gemini can technically translate any language, but the supported set is aligned to Sarvam's 23 languages so both providers offer an identical language dropdown.

---

## 7. Configuration Files — Which One to Edit

### For `mvn jetty:run` (local development)

**File:** `app/src/test/resources/roller-jettyrun.properties`

```properties
# ── Translation service ──────────────────────────────────────────────
# Change 'gemini' to 'sarvam' to switch providers
translation.provider=sarvam

# Gemini — apiUrl and apiKey are picked up from env vars automatically.
# Set TRANSLATION_GEMINI_API_URL and GEMINI_API_KEY in .env
translation.gemini.apiUrl=https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
translation.gemini.model=gemini-2.5-flash
# translation.gemini.apiKey=   ← leave blank; use env var instead

# Sarvam — leave apiKey blank; use env var SARVAM_API_KEY
# translation.sarvam.apiKey=
```

### For `mvn test` (JUnit tests)

**File:** `app/src/test/resources/roller-custom.properties`

```properties
translation.provider=sarvam
# apiKey read automatically from env var SARVAM_API_KEY
```

### For production (Tomcat)

Place `roller-custom.properties` in `WEB-INF/classes/` with:

```properties
translation.provider=gemini
translation.gemini.apiKey=YOUR_KEY
```

---

## 8. Environment Variables (.env)

The code reads API keys from environment variables when the property file value is blank.

### .env file structure (never commit this file)

Create `.env` in the project root and add it to `.gitignore`:

```
# .env  — never commit this file

# ── Google Gemini ──────────────────────────────────────────────────
GEMINI_API_KEY=your_gemini_api_key_here
TRANSLATION_GEMINI_API_URL=https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
TRANSLATION_GEMINI_MODEL=gemini-2.5-flash

# ── Sarvam AI ──────────────────────────────────────────────────────
SARVAM_API_KEY=your_sarvam_api_key_here
TRANSLATION_SARVAM_API_URL=https://api.sarvam.ai/translate
```

### Load from .env before running Jetty

```bash
cd /home/gaurav/SE/project-1-team-27
set -a && source .env && set +a
cd app && mvn jetty:run
```

> `set -a` auto-exports every variable loaded by `source .env`. Plain `source .env` does **not** export variables — Jetty would not see them.

### Set manually for current shell session

```bash
# Gemini
export GEMINI_API_KEY="your_gemini_api_key_here"
export TRANSLATION_GEMINI_API_URL="https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
export TRANSLATION_GEMINI_MODEL="gemini-2.0-flash"

# Sarvam
export SARVAM_API_KEY="your_sarvam_api_key_here"
```

### Persist in shell profile

```bash
echo 'export GEMINI_API_KEY="your_key"' >> ~/.bashrc
source ~/.bashrc
```

### Validate keys are set

```bash
[[ -n "$GEMINI_API_KEY" ]] && echo "Gemini key: SET"  || echo "Gemini key: NOT SET"
[[ -n "$SARVAM_API_KEY" ]] && echo "Sarvam key: SET"  || echo "Sarvam key: NOT SET"
```

### Unset

```bash
unset GEMINI_API_KEY
unset SARVAM_API_KEY
```

### Key resolution order (in code)

```
For Gemini:
  1. translation.gemini.apiKey in roller-jettyrun.properties  (if non-empty)
  2. GEMINI_API_KEY  environment variable          ← recommended

For Sarvam:
  1. translation.sarvam.apiKey in roller-jettyrun.properties  (if non-empty)
  2. SARVAM_API_KEY  environment variable          ← recommended
```

---

## 9. How to Run Locally

### Step 1 — Set API key

```bash
export GEMINI_API_KEY="your_key"
# or for Sarvam:
export SARVAM_API_KEY="your_key"
```

### Step 2 — Set provider in roller-jettyrun.properties

```properties
translation.provider=gemini   # or sarvam
```

### Step 3 — Build

```bash
cd /home/gaurav/SE/project-1-team-27
mvn -DskipTests=true install
```

### Step 4 — Run

```bash
cd app
mvn jetty:run
```

Open: `http://localhost:8080/roller`

> **Important:** The API key must be exported in the **same terminal** where you run `mvn jetty:run`. A new terminal loses the exported variable.

---

## 10. How to Switch Between Gemini and Sarvam

Edit `app/src/test/resources/roller-jettyrun.properties`:

```properties
translation.provider=sarvam   # ← switch to Gemini
# translation.provider=sarvam  # ← switch to Sarvam
```

Restart Jetty. The translate bar label will automatically update:

| Property value | Bar shows |
|----------------|-----------|
| `translation.provider=sarvam` | **Translate via Gemini** |
| `translation.provider=sarvam` | **Translate via Sarvam** |

---

## 11. How to Add Translation to a New Page

### JSP tile page

Add in the tile template (already done in `tiles-simplepage.jsp` and `tiles-loginpage.jsp`):

```jsp
<script src="<s:url value='/roller-ui/scripts/translation.js' />?v=5" defer></script>
```

Wrap translatable content:

```html
<div id="roller-translation-content">
  <h1>Page Title</h1>
  <p>Content here...</p>
</div>
```

### Velocity (.vm) blog theme

```velocity
<script src="$url.absoluteSite/roller-ui/scripts/translation.js?v=5" defer></script>

<div id="roller-translation-content">
  #foreach($entry in $entries)
    <h2>$entry.title</h2>
    <div>$entry.text</div>
  #end
</div>
```

### Rules

- `id="roller-translation-content"` marks the translatable region
- If missing, script automatically falls back to `document.body`
- The translate bar is inserted **just above** the content root element
- Elements in `SKIP_TAGS` (SCRIPT, STYLE, CODE, PRE, TEXTAREA, INPUT, SELECT…) are never translated
- Buttons `<input type="submit">` and `<button>` are translated separately via `collectButtonInputs()`

---

## 12. Client-Side Caching (Task 5B)

### localStorage structure

```
Key:   "roller_tx|https://localhost:8080/roller/register.rol|ta"

Value: {
  "fingerprint": "a3f9c2...",        ← hash of h1-h4 + article text
  "provider":    "gemini",
  "entries": [
    { "orig": "New User Registration", "tx": "புதிய பயனர் பதிவு" },
    { "orig": "Sign Up",               "tx": "பதிவு செய்யுங்கள்" },
    ...
  ],
  "savedAt": 1709876543000
}
```

### Config key

```
Key:   "roller_tx_config"
Value: { "lang": "ta", "provider": "gemini" }
```

### Cache TTL

7 days. Expired entries are ignored — page is re-translated from API.

### Fingerprinting

Only headings (`h1`–`h4`) and primary content (`.entryContent`, `article`, etc.) are hashed.
Navigation, footer, and sidebar changes do **not** invalidate the cache.

### Partial invalidation on content change

| Text node | Action |
|-----------|--------|
| Same `original` text as cached | Reuse cached translation — zero API call |
| New or changed text | Batch to API only for that item |

### Auto-apply on page load

If the user previously translated a page, the widget reads `roller_tx_config`, finds the last language, checks the cache, and re-applies instantly — without any API call.

---

## 13. API Reference

### GET /roller/roller-services/translate

Returns active provider name and supported languages.

**Request:**
```bash
curl -i http://localhost:8080/roller/roller-services/translate
```

**Response (HTTP 200):**
```json
{
  "provider": "gemini",
  "languages": ["en","gu","hi","kn","mr","pa","ta","te"]
}
```

---

### POST /roller/roller-services/translate

Translates a list of plain-text strings.

**Request:**
```bash
curl -X POST \
  -H 'Content-Type: application/json' \
  -d '{"source":"en","target":"ta","texts":["Hello","Sign in","Welcome"]}' \
  http://localhost:8080/roller/roller-services/translate
```

**Request body:**
```json
{
  "source": "auto",
  "target": "ta",
  "texts": ["Hello", "Sign in", "Welcome"]
}
```

**Response (HTTP 200):**
```json
{
  "provider": "gemini",
  "translations": ["வணக்கம்", "உள்நுழைக", "வரவேற்கிறோம்"]
}
```

**Error responses:**

| HTTP | Meaning |
|------|---------|
| 400 | Missing target language / unsupported language |
| 429 | API quota exceeded at provider |
| 503 | Provider not configured / API key missing |

---

## 14. Validation Commands

### Check env vars

```bash
[[ -n "$GEMINI_API_KEY" ]] && echo "SET" || echo "NOT SET"
[[ -n "$SARVAM_API_KEY" ]] && echo "SET" || echo "NOT SET"
```

### Test metadata endpoint

```bash
curl -i -H 'Accept: application/json' \
  http://localhost:8080/roller/roller-services/translate
```

### Test translation endpoint

```bash
curl -X POST \
  -H 'Content-Type: application/json' \
  -d '{"source":"en","target":"hi","texts":["Good morning","Sign in"]}' \
  http://localhost:8080/roller/roller-services/translate
```

### Test Gemini API key directly

```bash
curl -s -X POST \
  "$TRANSLATION_GEMINI_API_URL?key=$GEMINI_API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"contents":[{"parts":[{"text":"Translate to Tamil: Hello"}]}]}' \
  | python3 -m json.tool
```

### Test Sarvam API key directly

```bash
curl -s -X POST https://api.sarvam.ai/translate \
  -H 'Content-Type: application/json' \
  -H "API-Subscription-Key: $SARVAM_API_KEY" \
  -d '{"input":"Hello","source_language_code":"en-IN","target_language_code":"hi-IN","model":"mayura:v1","enable_preprocessing":false}' \
  | python3 -m json.tool
```

---

## 15. Troubleshooting

### "Translate via unknown" on the bar
Jetty is not running or GET metadata failed. Start Jetty and hard-refresh (Ctrl+Shift+R).

### Boxes □□□□ in page heading after translation
Font issue — the heading font has no Tamil/Devanagari glyphs. Fixed in `roller.css` — `.roller-page-title` now explicitly lists Noto Sans Tamil, Noto Sans Devanagari, etc. Hard-refresh browser.

### Extra quotes in translated text, e.g. `"வணக்கம்"`
Fixed in `cleanTranslatedText()` in `GeminiTranslationProvider.java`. It loops until all wrapping quote layers and trailing commas are stripped.

### Literal `\n` appears in translated text
Fixed — texts are normalized (newlines → spaces) before sending to Gemini. `cleanTranslatedText()` also strips literal `\n` sequences the model returns.

### Buttons (Sign Up / Cancel) not translated
Fixed — `collectButtonInputs()` in `translation.js` handles `<input type="submit">` and `<button>`. Their text lives in the `value` attribute, not a text node, so TreeWalker alone misses them.

### Changed `roller-custom.properties` but provider did not change
`mvn jetty:run` reads **`roller-jettyrun.properties`**, not `roller-custom.properties`. Edit the correct file.

### "API key missing" / HTTP 403 PERMISSION_DENIED from Gemini
The env var is not visible to the Jetty process. Load `.env` using `set -a` in the **same terminal** before starting Jetty:
```bash
set -a && source .env && set +a
cd app && mvn jetty:run
```
Plain `export GEMINI_API_KEY=...` in a different terminal will NOT work — Jetty inherits only the exporting shell's environment.

### Sarvam returns HTTP 403 `{"detail":"Subscription not found."}`
Same cause — `SARVAM_API_KEY` not exported in the Jetty terminal. Use `set -a && source .env && set +a` as above.

### Password (Confirm) field / last form field missing after Sarvam translation  
Fixed — `SarvamTranslationProvider` now fires all HTTP calls concurrently via `CompletableFuture`. Previously, a single HTTP 422 on any item aborted the rest of the sequential loop, so the last few fields were never returned. Per-item fallback now keeps the original text instead of crashing the batch.

---

## 16. Security Notes

- **Never commit real API keys** to git — use environment variables or a `.env` file in `.gitignore`
- If a key was accidentally committed, rotate it immediately at the provider dashboard
- API keys are read server-side only — they are never sent to the browser
- The endpoint is rate-limited to **60 requests per minute per IP**
- Request body is capped at **64 KB**
- DOM replacement uses `node.nodeValue` (not `innerHTML`) — XSS is impossible

---

## Quick Checklist (Team Handoff)

- [ ] `.env` file created with `GEMINI_API_KEY` / `TRANSLATION_GEMINI_API_URL` (or Sarvam equivalents)
- [ ] `.env` added to `.gitignore` and untracked from git (`git rm --cached .env`)
- [ ] `set -a && source .env && set +a` run **before** Jetty in the **same terminal**
- [ ] `translation.provider=sarvam` (or `sarvam`) set in `roller-jettyrun.properties`
- [ ] `mvn -DskipTests=true install` completed from project root
- [ ] `mvn jetty:run` running from `app/` directory
- [ ] `GET /roller/roller-services/translate` returns JSON with correct provider name
- [ ] `POST /roller/roller-services/translate` returns `translations` array
- [ ] Translate bar shows **"Translate via Gemini"** (or Sarvam)
- [ ] Indian script renders correctly in page heading (no □□□□ boxes)
- [ ] Buttons (Sign Up, Cancel) translate correctly
- [ ] Reset button restores original text

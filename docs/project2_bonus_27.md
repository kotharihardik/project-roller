# Bonus 1 Report: Weblog Q&A Chatbot

## 1. Problem Statement and Scope
Bonus 1 asks for a Weblog Q&A Chatbot that:

1. Answers natural-language questions grounded in a weblog's published entries.
2. Supports at least two distinct answering strategies.
3. Integrates into Roller UI on weblog pages.
4. Compares the implemented approaches with practical trade-offs as weblog size and query type vary.

This report documents the implementation present in this branch and evaluates it against those requirements.

## 2. Requirement Coverage Matrix

| Bonus 1 Requirement | Implementation Evidence | Status |
|---|---|---|
| Natural-language Q&A over weblog content | Chatbot API receives free-text `question` and answers from weblog entry corpus | Completed |
| At least two strategies | `LongContextStrategy` and `RAGStrategy` in chatbot package | Completed |
| UI integration on weblog page | Widget JS/CSS injected in multiple themes; floating chatbot available in weblog/permalink/search pages | Completed |
| Grounded answers | Both strategies use grounding prompts and only pass weblog-derived context to LLM | Completed |
| Approach comparison in report | Sections 7-10 include scaling and query-type trade-offs | Completed |

## 3. High-Level Architecture
The chatbot is implemented as a modular pipeline with clear separation of concerns.

1. UI widget captures user question and selected strategy.
2. Servlet endpoint validates input and applies rate limiting.
3. Service delegates to strategy via strategy factory.
4. Strategy builds context from published entries and calls LLM.
5. Response metadata (strategy used, entries consulted, latency) is returned to UI.

### 3.1 Main Components
- API endpoint: `ChatbotServlet`
- Coordinator: `ChatbotService`
- Strategy abstraction: `ChatbotAnsweringStrategy`
- Strategy factory/registry: `ChatbotStrategyFactory`
- Shared LLM and retrieval utilities: `AbstractLlmStrategy`
- Strategy A: `LongContextStrategy`
- Strategy B: `RAGStrategy`
- Response model: `ChatbotResponse`
- Frontend: `roller-ui/scripts/chatbot.js` and `roller-ui/styles/chatbot.css`
- Routing/registration: `WEB-INF/web.xml`
- Runtime configs: `roller.properties`

## 4. UI Integration Details
The chatbot is integrated via theme-level script/style injection and global context variables.

1. Chat widget assets are loaded from theme templates.
2. The weblog ID is passed to browser context (`window.ROLLER_CHATBOT_WEBLOG_ID`).
3. Entry pages additionally set `window.ROLLER_CHATBOT_ENTRY_ID` for scoped Q&A.
4. Widget loads available strategies using `GET /roller-services/chatbot` and sends questions using `POST /roller-services/chatbot`.

Integration is included across key themes (`basic`, `basicmobile`, `frontpage`, `fauxcoly`, `gaurav`) and page variants (weblog, permalink, search results, mobile templates), making the feature practically accessible and not limited to a single template.

## 5. Backend Design and Patterns Used

### 5.1 Strategy Pattern
`ChatbotAnsweringStrategy` defines a common interface; `LongContextStrategy` and `RAGStrategy` implement distinct answer-generation approaches.

Benefit:
- Enables runtime strategy switching from UI without changing servlet/service code.

### 5.2 Factory Pattern
`ChatbotStrategyFactory` maps strategy keys (`longcontext`, `rag`) to constructors and centralizes default strategy selection.

Benefit:
- New strategies can be added by registry extension with minimal wiring changes.


### 5.3 Service Layer
`ChatbotService` acts as a simple facade between servlet and strategy subsystem.

Benefit:
- Keeps transport concerns (HTTP/JSON) isolated from chatbot business logic.


## 6. Strategy Implementations

## 6.1 Strategy A: Long Context
Approach:
1. Fetch up to 500 latest published entries for weblog.
2. Build one large context string with title, publication time, and stripped body text.
3. Truncate context at configured max characters (`chatbot.longcontext.maxChars`, default `800000`).
4. Submit entire compiled context with user question to LLM.

Key characteristics:
- Uses broad, full-corpus context.
- Maximizes chance of including supporting passage for cross-post or synthesis queries.
- Prompt explicitly asks model to answer only from provided content.

## 6.2 Strategy B: RAG (Retrieval-Augmented)
Approach:
1. Build in-memory per-weblog chunk index (TTL 10 minutes).
2. Chunk post body text (default chunk size `1500`, overlap default `150`; overlap clamped if invalid).
3. Build lexical TF-IDF representation for each chunk.
4. Retrieve top-K chunks (`chatbot.rag.topK`, default `5`) via cosine similarity.
5. For broad queries, diversify retrieval across entries (one best chunk per entry where possible).
6. Send only retrieved excerpts to LLM.

Key characteristics:
- Lower prompt size per query.
- Faster and cheaper steady-state behavior for repeated queries on same weblog.
- Retrieval is lexical (not embedding-based), so synonym-heavy queries may be weaker than semantic retrievers.

## 6.3 Entry-Scoped Mode
Both strategies support optional `entryId` scope, passed from entry pages. If valid, retrieval/context can be narrowed to that specific entry.

Practical value:
- Improves precision for page-local questions.
- Reduces irrelevant context when user intent is clearly post-specific.

## 7. Practical Comparison of Implemented Approaches

| Dimension | Long Context | RAG |
|---|---|---|
| Context construction | Full corpus concatenation (bounded by max chars) | Top-K chunk retrieval from indexed chunks |
| First-query overhead | Low preprocessing, but large payload | Index build cost (chunking + TF-IDF) |
| Repeated queries | Rebuild/resent large context each time | Reuses in-memory index during TTL |
| Prompt token cost | High as corpus grows | Controlled by top-K and chunk size |
| Recall on broad synthesis questions | Strong (if within maxChars bound) | Depends on retriever coverage/diversification |
| Precision for narrow factual query | Can include noise | Usually stronger due to focused excerpts |
| Failure mode | Truncation may hide older posts | Lexical mismatch may miss semantically relevant passages |

## 8. Behavior as Weblog Size Grows

### 8.1 Small weblogs (few posts)
- Long Context is straightforward and often robust since all posts fit easily.
- RAG still works, but indexing overhead may not be noticeably beneficial.

Observation from implementation:
- With defaults, either strategy is acceptable for small corpora.

### 8.2 Medium weblogs
- Long Context starts paying larger per-query context cost.
- RAG starts showing efficiency benefits due to bounded top-K prompt construction.

Observation from implementation:
- RAG index cache (10-minute TTL) improves repeated query efficiency for active sessions.

### 8.3 Large weblogs
- Long Context faces truncation risk (`maxChars`), potentially omitting older but relevant posts.
- RAG remains bounded in prompt size and generally scales better for latency/cost.

Observation from implementation:
- RAG is operationally better for larger archives, but retrieval quality depends on lexical overlap and chunk parameters.

## 9. Query-Type Analysis

### 9.1 Specific fact lookup
Example: "When was topic X last discussed?"
- Long Context: can answer if target passage is retained after truncation.
- RAG: typically stronger due to focused retrieval on query terms.

### 9.2 Broad thematic summary
Example: "What has this blog said about data privacy?"
- Long Context: good when full relevant range is included in context.
- RAG: broad-query diversification heuristic improves multi-entry coverage, but still limited by top-K chunks.

### 9.3 Ambiguous or out-of-scope questions
Both strategies rely on grounding instructions and are designed to acknowledge insufficient evidence instead of hallucinating.

## 10. Trade-Offs 

1. We chose to implement both a full-context baseline and a retrieval-based strategy to satisfy functional requirements and provide practical scaling flexibility.
2. We used lexical TF-IDF retrieval for simplicity, transparency, and low dependency overhead inside Roller.
3. We added lightweight operational safeguards in servlet/API layer:
- input validation,
- request body cap,
- IP-based rate limiting,
- clear error propagation.
4. We returned response metadata (`strategyUsed`, `entriesConsulted`, `latencyMs`) to improve observability for comparisons and debugging.

## 11. Configuration and Operational Controls
Runtime properties allow tuning without code changes:
- `chatbot.defaultStrategy`
- `chatbot.llm.apiUrl`
- `chatbot.llm.apiKey`
- `chatbot.llm.timeoutSeconds`
- `chatbot.rateLimit.requestsPerMinute`
- `chatbot.rateLimit.windowSeconds`
- `chatbot.rateLimit.maxTrackedIps`
- `chatbot.longcontext.maxChars`
- `chatbot.rag.topK`
- `chatbot.rag.chunkSize`

Operational implication:
- Team can switch default strategy and tune quality/latency/cost behavior per deployment profile.

## 12. Limitations and Future Improvements

Current limitations:
1. RAG retrieval is lexical only (no semantic embeddings).
2. Index is in-memory and per-node (not persisted/distributed).
3. Long Context may lose older evidence under max-character truncation.
4. No dedicated automated tests for chatbot module in current branch.

Planned improvements:
1. Add embedding-based retrieval option as third strategy.
2. Persist index in cache store for multi-node consistency.
3. Add strategy-level evaluation suite with fixed Q&A benchmark set.
4. Expose retrieval snippets in UI for explainability.

## 13. Conclusion
Bonus 1 has been implemented with two distinct, switchable Q&A strategies integrated into Roller UI and grounded on weblog content.

- `LongContextStrategy` provides a strong baseline for broad understanding, especially for smaller corpora.
- `RAGStrategy` offers better scaling behavior for growing archives by constraining per-query context and reusing an index.

The final design is modular and extensible, and the comparative behavior aligns with expected practical trade-offs across corpus size and query type, satisfying the report requirement for concrete, implementation-backed comparison.

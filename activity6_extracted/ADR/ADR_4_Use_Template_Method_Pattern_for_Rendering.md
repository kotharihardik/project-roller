# ADR 4: Use Template Method Pattern for Rendering Pipeline

## Status
Accepted

## Date
2026-04-10

## Context
Apache Roller allows users to write their entries in various formats, such as plain text, HTML, or Markdown. However, the final output delivered to the user must be well-formed HTML. Developing a monolithic rendering engine for every possible text format is hard to maintain.

## Decision
We decided to use the Template Method and Strategy patterns to formulate a Plugin rendering system. A base renderer handles common processing (like resolving macros or embedded links), while specific concrete formatters (e.g., Markdown Plugin) are injected to handle specific syntax transformations.

## Consequences
- **Positive:** The system is open to extension via custom user plugins without modifying the core codebase.
- **Positive:** Separation of concerns between different textual format processing systems.
- **Negative:** Multiple passes of rendering (e.g., Template -> Markdown -> HTML macro replacement) can induce slight overhead on request processing if results are not cached properly.

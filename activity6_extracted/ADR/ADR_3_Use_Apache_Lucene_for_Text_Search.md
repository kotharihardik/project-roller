# ADR 3: Use Apache Lucene for Text Search

## Status
Accepted

## Date
2026-04-10

## Context
A core feature of any blog server is the ability to search through past posts effectively. Standard SQL `LIKE` queries perform poorly on large text bodies and lack ranking or relevance scoring. Relying on an external service (like Elasticsearch) would increase the minimum deployment footprint for Apache Roller administrators.

## Decision
We decided to integrate Apache Lucene directly as an embedded search library within the Web Application. All entries will be indexed into local file-system directories based on their updates.

## Consequences
- **Positive:** Highly performant, full-text tokenized search with relevance ranking.
- **Positive:** Remains self-contained inside the Roller WAR file without needing a separate Elasticsearch or Solr daemon running.
- **Negative:** Lucene indexes require file-system synchronization. During heavy write operations or clustered deployments, the embedded index requires careful locking or periodic rebuilding to remain consistent.

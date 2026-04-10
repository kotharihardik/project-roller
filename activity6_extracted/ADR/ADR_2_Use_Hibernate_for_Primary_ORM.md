# ADR 2: Use Hibernate for Primary ORM

## Status
Accepted

## Date
2026-04-10

## Context
Apache Roller must persist a complex data model including Users, Weblogs, Entries, Categories, and Comments across a variety of relational databases supported by users (MySQL, PostgreSQL, Oracle). Writing pure JDBC would result in verbose, database-specific SQL queries and tight coupling between the Java model and database tables.

## Decision
We adopted Hibernate (via JPA standard annotations where possible) to serve as our Object-Relational Mapping (ORM) and data abstraction layer. 

## Consequences
- **Positive:** Developers interact with simple Java POJOs instead of raw SQL strings. Database dialect differences are automatically abstracted via Hibernate dialects.
- **Positive:** Improved development velocity due to transparent persisting, caching, and lazy loading mechanisms.
- **Negative:** Increased memory consumption and occasional "N+1 select" performance issues if relationships and collections are not carefully mapped and eagerly fetched when needed.

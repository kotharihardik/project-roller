# ADR 5: Asynchronous Email Notification via ThreadPool

## Status
Accepted

## Date
2026-04-10

## Context
When a user posts a comment on an entry, the author might choose to be notified by email. Sending emails via SMTP is a blocking, potentially high-latency network operation. If the email is dispatched sequentially during the web request, the user rendering the comment page will experience significant lag.

## Decision
We decided to implement the email notification system asynchronously. When an event requiring a notification occurs, an `EmailTask` is pushed to a dedicated Java `ExecutorService` (ThreadPool) instead of executing on the main Tomcat HTTP handler thread.

## Consequences
- **Positive:** Immediate HTTP response for the user posting the comment, dramatically improving perceived application performance.
- **Positive:** If the SMTP server is temporarily slow, it does not exhaust the Tomcat HTTP connection pool.
- **Negative:** If the Tomcat server restarts immediately after a comment but before the email thread completes, the notification could be permanently lost because tasks are held in memory rather than a durable message queue.

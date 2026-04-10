# ADR 1: Use Struts 2 as MVC Framework

## Status
Accepted

## Date
2026-04-10

## Context
Apache Roller is a web application that needs a robust framework to handle HTTP requests, routing, user input validation, and rendering UI views. The architecture required an MVC (Model-View-Controller) based structure. Historically, Roller evaluated several UI frameworks.

## Decision
We decided to adopt Apache Struts 2 as the primary web MVC framework. All web traffic and user interactions will be routed through Struts Action classes, which will act as the Controller, delegating business logic to the underlying Manager layers.

## Consequences
- **Positive:** Struts 2 provides a well-defined way to handle requests, cleanly separating request processing (Controller) from presentation code (View Templates) and business logic (Model).
- **Positive:** It offers a rich ecosystem of interceptors for reusability (e.g., authentication, logging, CSRF protection).
- **Negative:** Struts 2 carries a steep learning curve and has historically required regular updates due to security vulnerabilities in its OGNL expression evaluation engine.

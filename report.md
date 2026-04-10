# Activity 6: Architectural Documentation Report

**Repository Chosen:** Apache Roller (https://github.com/apache/roller)

## Part 1: Architecture Diagrams
I created three levels of diagrams for the Apache Roller repository using the C4 model:
- **C1 Diagram (System Context):** This shows the big picture. It shows how the Bloggers and Readers interact with the main Apache Roller system, and how the system connects to external tools like a Database and an SMTP Email Server.
- **C2 Diagram (Container):** This breaks the main system down into its major containers: the Web Application (running on Tomcat), the Relational Database, and the Lucene Search Index.
- **C3 Diagram (Component):** This zooms into the Web Application container. It shows the internal components like the Struts Action Controllers, the various Managers (Weblog, User, Search), and the DAOs that talk to the database.

## Part 2: Architectural Decision Records (ADRs)
I created 5 ADRs that explain the big design choices in the repository. They are saved in the `ADR/` folder:
- **ADR 1:** Decided to use Struts 2 to handle all web requests and routing.
- **ADR 2:** Picked Hibernate for the database ORM so developers don't have to write complex raw SQL queries.
- **ADR 3:** Chose Apache Lucene for the search engine because it runs directly inside the app.
- **ADR 4:** Used the Template Method pattern to handle rendering different blog text formats (like Markdown and HTML).
- **ADR 5:** Used a ThreadPool to send email notifications in the background so the website doesn't freeze when users post a comment.

---

## Part 3: AI Usage Report

**How I generated the artifacts (tool + approach):**
I used an AI assistant prompt to get the starting code for the C4 diagrams in PlantUML format. I also asked the AI to help me identify and draft the 5 ADRs based on the tech stack of Apache Roller.

**What the initial generated version contained:**
- For the diagrams, the AI generated the starting PlantUML code. It had the general shape right, but it was too generic. It just said "Spring MVC" instead of identifying the actual framework.
- For the ADRs, the AI wrote 5 records, but it only included the positive benefits of the decisions. It missed the negative side effects.

**What changes I made:**
- **Diagrams:** I manually edited the PlantUML code to change generic frameworks to the exact ones used by Apache Roller (like explicitly naming Struts 2). I also added the Lucene Index into the C3 diagram. After fixing the code, I used the Kroki API to convert the code into the final PNG images.
- **ADRs:** I went through all 5 markdown files and added the "Negative" consequences. For example, I added that Hibernate uses more memory, and Lucene indexes need to be synced to the file system.

**Why these changes were necessary:**
The changes to the diagrams were needed because the original AI output wasn't completely accurate for Apache Roller. It looked like a generic Java app instead of this specific repository. Putting the negative consequences into the ADRs was necessary to make them realistic. In real software engineering, every choice has downsides, and an ADR shouldn't just be purely positive.

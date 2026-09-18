# Project Report Guide

The VITyarthi portal wants a 15-section PDF. This file maps each section to
the material already in the repository so the report becomes assembly rather
than invention. Write it in your own words — the rubric gives 20% to the
report and 15% to demonstrated depth, and both reward evidence of your own
reasoning.

| # | Report section | Source in this repo | Notes |
|---|---|---|---|
| 1 | Cover page | — | Name, registration number, course code CSE2006, project title, faculty, date |
| 2 | Introduction | `statement.md` → "The problem" | Set the context: outpatient scheduling in small hospitals |
| 3 | Problem statement | `statement.md` | Lead with the four failure modes; name the core issue as single-allocation of a shared resource |
| 4 | Functional requirements | `README.md` → Features | Present as the three modules, then the features under each |
| 5 | Non-functional requirements | `docs/ARCHITECTURE.md` §9 | Eight are documented; four are the minimum required |
| 6 | System architecture | `ARCHITECTURE.md` §1 | Include the layer diagram and the "why this shape" paragraph |
| 7 | Design diagrams | `ARCHITECTURE.md` §2–8 | Use case, class, workflow, two sequence diagrams, thread state, ER |
| 8 | Design decisions & rationale | see below | The highest-value section — do not skip it |
| 9 | Implementation details | `README.md` → Syllabus coverage, project structure | Walk through one flow end to end (booking is the best) |
| 10 | Screenshots / results | run the app | Listed under README → Screenshots |
| 11 | Testing approach | `README.md` → Testing | Include the `64 passed, 0 failed` output |
| 12 | Challenges faced | see below | Be specific about what went wrong and what you changed |
| 13 | Learnings & takeaways | see below | Tie back to syllabus units |
| 14 | Future enhancements | see below | |
| 15 | References | see below | |

---

## Section 8 — design decisions worth writing up

Each of these is a real decision with a defensible alternative, which is what
a rationale section is meant to show.

**Why the lock is per doctor, not global.** A single global lock would also
prevent double booking, but it would serialise every booking in the hospital,
including ones for different doctors that cannot possibly conflict. Lock
objects are held in a `ConcurrentHashMap` keyed by doctor id, so contention
is scoped to the resource actually being contended.

**Why every validation runs inside the lock.** The natural instinct is to
validate first and lock only around the write, because validation is the slow
part. That is precisely the bug: between "the slot is free" and "write the
row" another thread can do both. The check-then-act sequence is atomic or it
is broken.

**Why slots are computed and never stored.** Storing a slot row per doctor per
day per interval means thousands of rows that must be regenerated whenever a
doctor's hours change, plus a migration problem for slots already booked.
Deriving the grid from `workStart`, `workEnd` and `slotMinutes` makes a
timing change take effect immediately and removes a whole class of stale-data
bugs. The cost is recomputing a short list on each view, which is negligible.

**Why the service layer depends on `Repository<T>`.** The syllabus requires
JDBC, but a project that must have a database server running to start is
hostile to whoever evaluates it. Coding against the interface let the file
implementation be the default while `JdbcAppointmentRepository` demonstrates
the same contract over `PreparedStatement` and `ResultSet`. Substituting one
for the other is a single line in `ServiceRegistry`.

**Why late cancellation becomes NO_SHOW rather than CANCELLED.** The status
carries the billing rule: `calculateBill()` charges 0% for `CANCELLED` and
50% for `NO_SHOW`. Encoding the policy in the status keeps billing logic in
one place instead of scattering time comparisons through the report code.

**Why reschedule releases the old slot first.** Booking the new slot before
releasing the old one would make it impossible to move an appointment to a
slot adjacent to itself, and would double-count the patient against the
fair-use cap. Releasing first requires a rollback path, which is implemented:
if the new booking throws for any reason, the original is restored to
`BOOKED`.

**Why a custom test harness instead of JUnit.** JUnit means a jar in `lib/`
and a classpath the evaluator has to get right. `MiniTest` is 60 lines, gives
named assertions and a non-zero exit code, and keeps the whole project
runnable with nothing but `javac` and `java`.

**Why an abstract `RoleMenu`.** Three role menus share the same loop, header,
pause and error handling. The template method pattern puts that once in the
base class; each subclass supplies only its title, options and dispatch.

---

## Section 12 — challenges (adapt to what you actually hit)

- **Making the race condition visible.** A double-booking bug does not appear
  in manual testing — a human cannot click twice in the same millisecond. The
  test uses a `CountDownLatch` as a start gate so all 25 threads fire
  together, which turns an intermittent bug into a deterministic failure.
- **Cache coherence across threads.** The repositories keep an in-memory list
  for speed, but the reminder daemon reads while the console thread writes.
  Making the load/flush methods `synchronized` and the shutdown flags
  `volatile` was necessary; without `volatile`, a background loop can keep
  reading a stale copy of the flag and never stop.
- **Free text corrupting delimited files.** A diagnosis containing a `|`
  silently shifted every later column. Escaping the delimiter and the newline
  on write, and unescaping on read, fixed it; the round-trip is asserted in
  the CSV test.
- **Ids surviving a restart.** `AtomicInteger` counters reset to zero on
  restart, producing duplicate ids. Each repository now reseeds its counter
  from the highest stored id at construction.
- **A corrupt row killing the application.** A single unparseable line used to
  throw during load. Parsing is now per-row inside a try/catch that logs and
  skips, so one bad record costs one record.

---

## Section 13 — learnings

- Unit 2 concepts stop being academic when the domain needs them: the
  `User` hierarchy exists because three roles genuinely share identity and
  differ in data, not because inheritance was on the syllabus.
- Unit 3 synchronization is invisible until you write the test that exposes
  its absence. Writing the failing test first was more instructive than
  reading about `synchronized`.
- Programming to an interface is what made the JDBC requirement cheap to
  satisfy instead of invasive.
- Exceptions are a design surface. A hierarchy with error codes let the UI
  print a specific, actionable message for every rejection path without a
  single `instanceof` chain.

---

## Section 14 — future enhancements

1. Real notification delivery (SMTP or an SMS gateway) behind a `Notifier`
   interface — `ReminderDaemon` already produces the events.
2. A JavaFX or Spring Boot front end over the unchanged service layer.
3. Doctor leave and holiday calendars that subtract from the generated grid.
4. Waitlist: when a booked slot is cancelled, auto-offer it to the first
   waiting patient.
5. Migrate the remaining repositories to JDBC and add transactional
   booking with `SELECT ... FOR UPDATE`, replacing the in-process lock with a
   database-level one so the system can scale to multiple nodes.
6. Role-based report exports in PDF, and per-patient billing statements.
7. Replace the hand-rolled hashing with PBKDF2 or bcrypt once third-party
   libraries are permitted.

---

## Section 15 — references

1. Herbert Schildt, *Java: The Complete Reference*, 11th edition, Oracle
   Press, 2018.
2. Cay S. Horstmann and Gary Cornell, *Core Java Volume I – Fundamentals*,
   Pearson.
3. Oracle, *Java SE 17 API Documentation* — `java.util.concurrent`,
   `java.time`, `java.sql`, `java.security`.
4. Oracle, *The Java Tutorials: Concurrency* — intrinsic locks, the happens-
   before relationship, and `volatile` semantics.
5. Brian Goetz et al., *Java Concurrency in Practice*, Addison-Wesley —
   check-then-act compound actions.
6. CSE2006 Programming in Java course syllabus and lab list, VIT.

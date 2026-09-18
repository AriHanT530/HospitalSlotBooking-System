# Hospital Slot Booking System

**Student Name:** Arihant Jain

**Branch:** Artificial Intelligence & Machine Learning (AIML)

**Registration No:** 25BAI10246

**Course:** CSE2006 – Programming in Java (*VITyarthi: Build Your Own Project*)

---

### What is this project?

In most small-to-medium clinics, walk-in desks still juggle physical registers. The results are predictable: double-booked slots, cancelled appointments that never get updated, and hours lost manually figuring out doctor occupancy and revenue.

This project solves that with a lightweight, thread-safe appointment booking system built in pure Java without third-party frameworks like Spring Boot or external servlet engines.

The neat part: **it runs two front ends off the exact same core backend**. You can manage everything through an interactive terminal interface (CLI) or launch a clean web portal directly in your browser using Java’s built-in HTTP server 

```
      
```

No matter which interface you use, the business rules, validation, doctor-level concurrency locks, and background tasks run identically behind the scenes.

---


 What Each Role Can Do

* **Patients:** Filter doctors by specialty, see real-time open slots (Free / Booked / Expired), book consultations, reschedule, cancel (free before 2 hours; 50% fee after), and view past visit notes/prescriptions.


Doctors:** Check daily schedules and room occupancy, mark visits as completed, issue digital prescriptions (diagnosis, medicines, instructions), cancel visits on a patient's behalf, and view earned consultation fees.


* **Administrators:** Set up doctor profiles (fees, consulting rooms, schedules, slot durations), enable or disable accounts, track clinic-wide status dashboards, monitor cancellation ratios, tail audit logs, and export appointment histories to CSV.



---

Background Automation

* **`reminder-daemon`:** A dedicated background thread that wakes up every 60 seconds to queue and log reminders for consultations starting within the next 24 hours.


* **`auto-expiry`:** A companion worker thread that checks for and closes out elapsed unconfirmed slots so clinic reports stay accurate.



---

Tech Stack & Core Java Alignment

* **Runtime:** Java 17+ (built and validated on OpenJDK 21).


* **External Libraries:** **Zero** — operates purely on Java standard libraries.


* **Storage Options:** Pipe-delimited flat files in `data/`, with plug-and-play support for a relational SQL database via JDBC.


Concurrency Tools:** `Thread`, `Runnable`, `synchronized` blocks, `ConcurrentHashMap`, `AtomicInteger`, and `CountDownLatch`.


Security:** Salted SHA-256 cryptographic hashing through `java.security.MessageDigest`.


* **Custom Testing:** Hand-crafted assertion runner (`MiniTest`) running 64 automated tests without needing a JUnit JAR download.



---
Quick Start Guide

**Prerequisite:** Ensure you have JDK 17 or newer installed (`java -version`).

#### 1. Browser Interface (Recommended for demos)

```bash
./run_web.sh          # Linux/macOS (defaults to http://localhost:8080)
run_web.bat           # Windows

```

*Tip:* You can change the port easily: `./run_web.sh 9090`.

#### 2. Terminal Interface (CLI)

```bash
./run.sh              # Linux/macOS
run.bat               # Windows

```

Add `--verbose` if you want real-time application logs printed straight to your terminal screen.

#### Default Logins (Pre-seeded on initial launch)

---

### Automated Tests & Race Condition Proof

Run the verification test suite at any time:

```bash
./run_tests.sh

```

All tests run in an isolated directory (`build/testrun`) so your active app records stay untouched.

**The Key Test:** The suite includes a **25-thread concurrent race condition test** where 25 separate threads try to grab the exact same consultation slot at the same millisecond. Thanks to per-doctor synchronized locks, exactly 1 thread succeeds and 24 are rejected cleanly.

---

### Thoughtful Design Choices

* **Decoupled Architecture:** The core services communicate strictly through a generic `Repository<T>` interface, allowing seamless switching between local CSV files and relational SQL via JDBC without altering business logic.


* **Doctor-Level Locking:** Locks are isolated per doctor rather than globally across the entire hospital, meaning two patients booking different doctors never wait on one another.


* **Rollback on Reschedule:** When a patient reschedules, their original slot is freed first so they can reuse that time if needed; if the new booking attempt fails, the original slot immediately rolls back to booked.


* **Dynamic Slot Computation:** Doctor slots are calculated algorithmically rather than written out as thousands of static rows, so schedule updates take effect instantly.

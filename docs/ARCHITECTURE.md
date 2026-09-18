# Architecture & Design Diagrams

All diagrams are Mermaid, so they render directly on GitHub. For the PDF
report, screenshot the rendered versions or paste the source into
[mermaid.live](https://mermaid.live) and export PNG/SVG.

---

## 1. System architecture

A four-layer design. Each layer depends only on the one below it, and the
service layer depends on the `Repository<T>` *interface* rather than on any
concrete storage class.

```mermaid
flowchart TD
    subgraph P["Presentation layer — com.hospital.ui"]
        A1[ConsoleApp<br/>login and routing]
        A2[RoleMenu<br/>abstract template]
        A3[PatientMenu]
        A4[DoctorMenu]
        A5[AdminMenu]
        A2 --> A3
        A2 --> A4
        A2 --> A5
    end

    subgraph S["Service layer — com.hospital.service"]
        B1[AuthService]
        B2[SlotService]
        B3[BookingService<br/>per-doctor lock]
        B4[PrescriptionService]
        B5[ReportService]
        B6[ServiceRegistry<br/>composition root]
    end

    subgraph T["Background — com.hospital.concurrent"]
        C1[ReminderDaemon<br/>extends Thread]
        C2[AutoExpiryWorker<br/>implements Runnable]
    end

    subgraph D["Persistence layer — com.hospital.dao"]
        D0{{"Repository&lt;T&gt;<br/>interface"}}
        D1[CsvRepository&lt;T&gt;<br/>abstract]
        D2[UserRepository]
        D3[AppointmentRepository]
        D4[PrescriptionRepository]
        D5[JdbcAppointmentRepository]
        D0 --- D1
        D0 --- D5
        D1 --> D2
        D1 --> D3
        D1 --> D4
    end

    subgraph ST["Storage"]
        E1[(data/*.csv)]
        E2[(logs/hospital.log)]
        E3[(reports/*.csv)]
        E4[(RDBMS via JDBC<br/>optional)]
    end

    P --> S
    S --> D0
    T --> D0
    D2 --> E1
    D3 --> E1
    D4 --> E1
    D5 --> E4
    S --> E2
    B5 --> E3
```

**Why this shape.** The one rule that earns its keep is that no service ever
names a storage class. `BookingService` asks an `AppointmentRepository` for
appointments, but its only assumption is the `Repository<Appointment>`
contract, which is exactly why the JDBC implementation can be swapped in at
the `ServiceRegistry` without touching a line of business logic.

**Two front ends, one backend.** `com.hospital.ui` (console) and
`com.hospital.web` (browser) are both presentation layers that call the
identical `ServiceRegistry`. Neither knows the other exists. This is the same
reason `Repository<T>` lets the JDBC and CSV implementations swap freely —
programming to an interface (here, effectively the service layer's public
methods) is what makes a second front end a pure addition rather than a
rewrite.

```mermaid
flowchart LR
    subgraph Entry points
        M1[Main.java]
        M2[WebMain.java]
    end
    M1 --> UI[com.hospital.ui<br/>console menus]
    M2 --> WEB[com.hospital.web<br/>HttpServer + HTML]
    UI --> SR[(ServiceRegistry)]
    WEB --> SR
    SR --> SVC[Same service layer,<br/>same locks, same validation]
```

The web layer adds three small classes with a single responsibility each:

| Class | Responsibility |
|---|---|
| `WebServer` | Routes each HTTP request to a handler method that calls the service layer and renders a response |
| `SessionManager` | Maps a cookie token to a logged-in `User`, thread-safe via `ConcurrentHashMap` |
| `Html` | Builds and escapes HTML fragments — no templating engine, so the project stays dependency-free |

---

## 2. Use case diagram

```mermaid
flowchart LR
    Patient(("Patient"))
    Doctor(("Doctor"))
    Admin(("Administrator"))
    Sys(("System<br/>timer"))

    subgraph HOSP["Hospital Slot Booking System"]
        U1([Register account])
        U2([Login])
        U3([Browse doctors])
        U4([View slot availability])
        U5([Book appointment])
        U6([Cancel appointment])
        U7([Reschedule appointment])
        U8([View my appointments])
        U9([View prescriptions])
        U10([View day schedule])
        U11([Complete consultation])
        U12([Issue prescription])
        U13([View earnings])
        U14([Onboard doctor])
        U15([Enable / disable account])
        U16([View analytics dashboard])
        U17([Export report CSV])
        U18([View audit log])
        U19([Send due reminders])
        U20([Auto-expire stale slots])
    end

    Patient --> U1
    Patient --> U2
    Patient --> U3
    Patient --> U4
    Patient --> U5
    Patient --> U6
    Patient --> U7
    Patient --> U8
    Patient --> U9

    Doctor --> U2
    Doctor --> U10
    Doctor --> U11
    Doctor --> U12
    Doctor --> U13
    Doctor --> U6

    Admin --> U2
    Admin --> U14
    Admin --> U15
    Admin --> U16
    Admin --> U17
    Admin --> U18

    Sys --> U19
    Sys --> U20

    U5 -.->|includes| U4
    U7 -.->|includes| U5
    U12 -.->|requires| U11
```

---

## 3. Class diagram

```mermaid
classDiagram
    class User {
        <<abstract>>
        -String id
        -String username
        -String passwordHash
        -String fullName
        -String phone
        -boolean active
        +getRole()* Role
        +describe() String
    }
    class Patient {
        -int age
        -String gender
        -String bloodGroup
        +describe() String
    }
    class Doctor {
        -Specialization specialization
        -double consultationFee
        -String roomNo
        -LocalTime workStart
        -LocalTime workEnd
        -int slotMinutes
        +slotsPerDay() int
    }
    class Admin {
        -String designation
    }
    class Billable {
        <<interface>>
        +calculateBill() double
        +taxComponent() double
    }
    class Appointment {
        -String id
        -String patientId
        -String doctorId
        -LocalDate date
        -LocalTime startTime
        -LocalTime endTime
        -AppointmentStatus status
        -double fee
        +calculateBill() double
        +overlaps(date, start, end) boolean
        +startsAt() LocalDateTime
    }
    class Slot {
        -LocalDate date
        -LocalTime start
        -LocalTime end
        -boolean booked
        +isBookable() boolean
        +statusLabel() String
    }
    class Prescription {
        -String id
        -String appointmentId
        -String diagnosis
        -List~String~ medicines
        -String advice
    }

    User <|-- Patient
    User <|-- Doctor
    User <|-- Admin
    Billable <|.. Appointment

    class Repository~T~ {
        <<interface>>
        +save(T) T
        +update(T) void
        +deleteById(String) boolean
        +findById(String) Optional~T~
        +findAll() List~T~
    }
    class CsvRepository~T~ {
        <<abstract>>
        #Path file
        #toCsv(T)* String
        #fromCsv(String)* T
        #idOf(T)* String
        #load() List~T~
        #flush() void
    }
    class UserRepository
    class AppointmentRepository
    class PrescriptionRepository
    class JdbcAppointmentRepository

    Repository <|.. CsvRepository
    Repository <|.. JdbcAppointmentRepository
    CsvRepository <|-- UserRepository
    CsvRepository <|-- AppointmentRepository
    CsvRepository <|-- PrescriptionRepository

    class AuthService {
        +login(user, pass) User
        +registerPatient(...) Patient
        +registerDoctor(...) Doctor
        +changePassword(...) void
    }
    class SlotService {
        +generateGrid(Doctor, date) List~Slot~
        +getSlots(Doctor, date) List~Slot~
        +getAvailableSlots(...) List~Slot~
        +occupancyRate(...) double
    }
    class BookingService {
        -Map~String,Object~ doctorLocks
        +book(...) Appointment
        +cancel(...) Appointment
        +complete(...) Appointment
        +reschedule(...) Appointment
    }
    class PrescriptionService {
        +issue(...) Prescription
        +historyOfPatient(id) List~Prescription~
    }
    class ReportService {
        +revenueByDoctor() Map
        +statusBreakdown() Map
        +dailyLoad() Map
        +exportAppointmentsCsv() Path
    }

    AuthService --> UserRepository
    SlotService --> AppointmentRepository
    BookingService --> AppointmentRepository
    BookingService --> UserRepository
    BookingService --> SlotService
    PrescriptionService --> PrescriptionRepository
    ReportService --> AppointmentRepository

    class RoleMenu {
        <<abstract>>
        +show() final
        #title()* String
        #options()* String[]
        #handle(String)* boolean
    }
    RoleMenu <|-- PatientMenu
    RoleMenu <|-- DoctorMenu
    RoleMenu <|-- AdminMenu

    class ReminderDaemon {
        -volatile boolean running
        +run() void
        +scanOnce() int
        +shutdown() void
    }
    class AutoExpiryWorker {
        +run() void
        +expireOnce() int
    }
    Thread <|-- ReminderDaemon
    Runnable <|.. AutoExpiryWorker

    Appointment "1" --> "0..1" Prescription
    Doctor "1" --> "*" Appointment
    Patient "1" --> "*" Appointment
```

---

## 4. Workflow / process flow — booking an appointment

```mermaid
flowchart TD
    Start([Patient selects<br/>Book an appointment]) --> D1[Choose doctor]
    D1 --> D2[Enter date]
    D2 --> V1{Date valid<br/>and within 60 days?}
    V1 -- no --> E1[Show validation error] --> D2
    V1 -- yes --> GEN[SlotService generates grid<br/>and overlays bookings]
    GEN --> F{Any free slot?}
    F -- no --> SUG[Suggest next free date<br/>within 14 days] --> End1([Return to menu])
    F -- yes --> LIST[Display free slots] --> PICK[Patient picks a slot]

    PICK --> LOCK[[Acquire lock for this doctor]]
    LOCK --> C1{On the slot grid?}
    C1 -- no --> R1[SlotUnavailableException] --> REL
    C1 -- yes --> C2{Still in the future?}
    C2 -- no --> R2[SlotUnavailableException] --> REL
    C2 -- yes --> C3{Doctor free<br/>at that time?}
    C3 -- no --> R3[SlotUnavailableException] --> REL
    C3 -- yes --> C4{Patient free<br/>at that time?}
    C4 -- no --> R4[SlotUnavailableException] --> REL
    C4 -- yes --> C5{Under the cap<br/>of 5 active?}
    C5 -- no --> R5[ValidationException] --> REL
    C5 -- yes --> SAVE[Create appointment<br/>status BOOKED]
    SAVE --> PERSIST[Repository writes the row]
    PERSIST --> LOG[Audit line written]
    LOG --> REL[[Release lock]]
    REL --> OUT{Booked?}
    OUT -- yes --> OK([Show reference id, fee, room])
    OUT -- no --> ERR([Show reason, offer another slot])
```

Every rejection path sits *inside* the lock. If the checks ran before the
lock, another thread could book the slot in the gap between "is it free?" and
"write the row".

---

## 5. Sequence diagram — concurrent booking of the same slot

```mermaid
sequenceDiagram
    autonumber
    participant P1 as Patient A thread
    participant P2 as Patient B thread
    participant BS as BookingService
    participant L as Lock(DOC0001)
    participant AR as AppointmentRepository
    participant LOG as AppLogger

    P1->>BS: book(PAT0001, DOC0001, 20-09, 10:30)
    P2->>BS: book(PAT0002, DOC0001, 20-09, 10:30)

    BS->>L: acquire (thread A wins)
    activate L
    BS->>AR: findByDoctorAndDate(DOC0001, 20-09)
    AR-->>BS: no active appointment at 10:30
    BS->>AR: save(APT0007, BOOKED)
    AR-->>BS: persisted
    BS->>LOG: audit BOOK APT0007
    BS-->>P1: Appointment APT0007
    BS->>L: release
    deactivate L

    Note over P2,L: thread B was blocked here the whole time

    BS->>L: acquire (thread B proceeds)
    activate L
    BS->>AR: findByDoctorAndDate(DOC0001, 20-09)
    AR-->>BS: APT0007 occupies 10:30
    BS--xP2: SlotUnavailableException
    BS->>L: release
    deactivate L
```

The 25-thread version of exactly this scenario is asserted in
`TestRunner.testConcurrentBookingRace()`.

---

## 6. Sequence diagram — consultation and prescription

```mermaid
sequenceDiagram
    autonumber
    participant D as Doctor
    participant DM as DoctorMenu
    participant BS as BookingService
    participant PS as PrescriptionService
    participant AR as AppointmentRepository
    participant PR as PrescriptionRepository

    D->>DM: Mark appointment completed
    DM->>BS: complete(APT0007, DOC0001)
    BS->>AR: findById(APT0007)
    AR-->>BS: Appointment (BOOKED)
    BS->>BS: verify ownership and status
    BS->>AR: update(status = COMPLETED)
    BS-->>DM: Appointment (COMPLETED)

    D->>DM: Issue prescription
    DM->>PS: issue(APT0007, diagnosis, medicines, advice)
    PS->>AR: findById(APT0007)
    AR-->>PS: Appointment (COMPLETED)
    PS->>PR: findByAppointment(APT0007)
    PR-->>PS: empty — no duplicate
    PS->>PR: save(RX0003)
    PR-->>PS: persisted
    PS-->>DM: Prescription RX0003
    DM-->>D: confirmation with item count
```

---

## 7. Thread life cycle — ReminderDaemon

```mermaid
stateDiagram-v2
    [*] --> NEW: new ReminderDaemon(...)
    NEW --> RUNNABLE: start()
    RUNNABLE --> RUNNING: scheduler picks the thread
    RUNNING --> TIMED_WAITING: Thread.sleep(interval)
    TIMED_WAITING --> RUNNING: interval elapses
    RUNNING --> RUNNING: scanOnce() writes reminders
    TIMED_WAITING --> TERMINATED: shutdown() sets running=false<br/>and interrupts
    RUNNING --> TERMINATED: loop exits
    TERMINATED --> [*]
```

---

## 8. ER diagram / storage design

The default backend is file storage, but the records are modelled
relationally and the optional JDBC schema below mirrors them exactly.

```mermaid
erDiagram
    USER ||--o{ APPOINTMENT : "books / attends"
    DOCTOR ||--o{ APPOINTMENT : "serves"
    APPOINTMENT ||--o| PRESCRIPTION : "results in"
    DOCTOR ||--o{ SLOT : "exposes (derived)"

    USER {
        string id PK "PAT0001 / DOC0001 / ADM0001"
        string role "PATIENT | DOCTOR | ADMIN"
        string username UK
        string password_hash "base64(salt):base64(sha256)"
        string full_name
        string phone
        boolean active
    }
    PATIENT_FIELDS {
        int age
        string gender
        string blood_group
    }
    DOCTOR {
        string id PK
        string specialization
        double consultation_fee
        string room_no
        time work_start
        time work_end
        int slot_minutes
    }
    APPOINTMENT {
        string id PK
        string patient_id FK
        string doctor_id FK
        date appt_date
        time start_time
        time end_time
        string status "BOOKED|COMPLETED|CANCELLED|NO_SHOW"
        double fee
        string reason
        timestamp created_at
    }
    PRESCRIPTION {
        string id PK
        string appointment_id FK UK
        string diagnosis
        string medicines "semicolon separated"
        string advice
        timestamp issued_at
    }
    SLOT {
        string doctor_id FK
        date slot_date
        time start_time
        time end_time
        boolean booked "computed, never stored"
    }
```

### File layout

| File | Columns |
|---|---|
| `data/users.csv` | `id｜role｜username｜passwordHash｜fullName｜phone｜active｜f1..f7` — the `f` columns hold role-specific fields (single-table inheritance) |
| `data/appointments.csv` | `id｜patientId｜doctorId｜date｜startTime｜endTime｜status｜fee｜reason｜createdAt` |
| `data/prescriptions.csv` | `id｜appointmentId｜diagnosis｜medicines｜advice｜issuedAt` |
| `logs/hospital.log` | `timestamp [LEVEL] [thread] message` |
| `reports/appointments_*.csv` | Exported comma-separated dump with computed payable amounts |

Values are pipe-delimited; a literal `|` or newline inside a value is escaped
(`&#124;`, `&#10;`) so free-text fields cannot corrupt a row.

### Equivalent SQL schema (used by the JDBC backend)

```sql
CREATE TABLE appointments (
    id          VARCHAR(16) PRIMARY KEY,
    patient_id  VARCHAR(16) NOT NULL,
    doctor_id   VARCHAR(16) NOT NULL,
    appt_date   DATE        NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    status      VARCHAR(16) NOT NULL,
    fee         DOUBLE PRECISION NOT NULL,
    reason      VARCHAR(255),
    created_at  TIMESTAMP   NOT NULL
);

CREATE INDEX idx_doc_date ON appointments(doctor_id, appt_date);
```

The index matches the hottest query in the system — "what does this doctor
already have on this date?" — which every booking runs.

---

## 9. Non-functional requirements

| # | Requirement | How it is met | Where to verify |
|---|---|---|---|
| 1 | **Reliability / correctness under concurrency** | Per-doctor lock around the entire check-then-write sequence; `ConcurrentHashMap` for lock objects; `AtomicInteger` id generation; rollback on failed reschedule | `BookingService.book()`, `testConcurrentBookingRace` |
| 2 | **Security** | Salted SHA-256 hashing, no plain-text password stored or logged; login attempt limit of 3; role checks before every privileged action; `PreparedStatement` binding on the JDBC path | `PasswordUtil`, `AuthService`, `JdbcAppointmentRepository` |
| 3 | **Usability** | Numbered menus, sensible defaults on every prompt, specific error messages that state the rule that failed, next-free-date suggestion when a day is full | `Validator`, `PatientMenu` |
| 4 | **Maintainability** | Four layers with one-way dependencies, services coded against `Repository<T>`, template-method base classes remove duplication, Javadoc on every public type, 7 focused packages | project structure |
| 5 | **Error handling** | One checked exception hierarchy with error codes; low-level `IOException`/`SQLException` wrapped in `DataAccessException` with the cause preserved; corrupt rows skipped and logged rather than fatal; menu-level catch-all | `exception/`, `CsvRepository.load()` |
| 6 | **Logging / auditability** | Append-only log with timestamp, level and thread name; every login, booking, cancellation, completion and export recorded; admin can tail it in-app | `AppLogger`, admin option 8 |
| 7 | **Performance** | Lazy-loaded in-memory cache per repository; locks scoped per doctor so unrelated bookings run in parallel; indexed doctor+date query on the JDBC path | `CsvRepository`, `BookingService` |
| 8 | **Portability** | Pure JDK 17+, zero third-party jars, relative paths only, runs identically on Linux, macOS and Windows | `lib/README.md` |

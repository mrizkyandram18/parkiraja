# ParKing — Automated Parking Management System

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Tests](https://img.shields.io/badge/Tests-154%20passed-success.svg)](docs/testing.md)
[![JaCoCo](https://img.shields.io/badge/Line%20Coverage-100%25-brightgreen.svg)](docs/testing.md)

Backend RESTful service for parking lot providers developed for the **Bank Saqu Coding Test**.

---

## 1. Project Overview

**ParKing** is an automated parking management service designed to handle real-time vehicle entry, advance reservations, capacity management, bill calculation, and audit history reporting.

### Key Capabilities
- **Dynamic Capacity Management**: Configure and enforce facility capacity with thread-safe pessimistic row locking.
- **Advance Slot Reservation**: Reserve parking slots in advance; reserved slots are guaranteed and automatically deducted from remaining capacity.
- **Seamless Vehicle Check-In**: Supports direct check-in or reservation-backed check-in with automatic reservation consumption.
- **Fair Pricing Engine**: Automated hourly billing by vehicle type with fractional-hour ceiling rounding and minimum 1-hour charge.
- **Dual-Storage Resilience**: H2 relational database provides the ACID primary source of truth, synchronized post-commit with an external CSV audit report (`data/parking_history.csv`).
- **Production-Grade Security**: Defends against CSV Formula Injection (OWASP CWE-1236) and sanitizes HTTP 500 responses against Information Disclosure (OWASP CWE-209).
- **100% Test Coverage**: Verified with 154 unit and integration tests achieving 100% line coverage under JaCoCo.

---

## 2. Technology Stack

- **Language**: Java 17
- **Framework**: Spring Boot 4.1.2
- **Build Tool**: Apache Maven 3.9+
- **Persistence**: Spring Data JPA / Hibernate 6.x
- **Runtime Database**: H2 Database (File-persistent mode: `./data/parkiraja.mv.db`)
- **Test Database**: H2 Database (In-memory mode: `jdbc:h2:mem:parkirajatest`)
- **Validation**: Jakarta Bean Validation (Hibernate Validator 9.x)
- **Code Coverage**: JaCoCo Maven Plugin 0.8.16 (100% line coverage enforced)

---

## 3. Quick Start Guide

### Prerequisites
- **JDK 17** installed and configured (`java -version`).
- **Apache Maven 3.9+** installed and added to PATH (`mvn -version`).

### Build & Package
```bash
mvn clean package
```

### Run All Tests & Verify JaCoCo Coverage
```bash
mvn clean verify
```

### Start the Service
```bash
mvn spring-boot:run
```
The application will launch on `http://localhost:8080`.

---

## 4. End-to-End Walkthrough & cURL Demo

Follow this step-by-step walkthrough to test the entire parking workflow on a running instance:

### Step 1: Configure Parking Lot Capacity
Set total parking capacity to `50` slots:
```bash
curl -X PUT http://localhost:8080/api/v1/parking/capacity \
  -H "Content-Type: application/json" \
  -d '{"totalCapacity": 50}'
```
*Response (`200 OK`):*
```json
{"totalCapacity": 50}
```

### Step 2: Create Advance Reservation
Reserve a parking slot in advance for vehicle `B 1234 XYZ`:
```bash
curl -X POST http://localhost:8080/api/v1/parking/reservations \
  -H "Content-Type: application/json" \
  -d '{"licensePlate": "B 1234 XYZ"}'
```
*Response (`201 Created`):*
```json
{
  "licensePlate": "B 1234 XYZ",
  "reservedAt": "2026-11-13T10:00:00.123456"
}
```

### Step 3: Check In Vehicle
Vehicle `B 1234 XYZ` arrives and checks in as a Car (`vehicleType = 1`). The active reservation is automatically consumed:
```bash
curl -X POST http://localhost:8080/api/v1/parking/check-in \
  -H "Content-Type: application/json" \
  -d '{"licensePlate": "B 1234 XYZ", "vehicleType": 1}'
```
*Response (`201 Created`):*
```json
{
  "licensePlate": "B 1234 XYZ",
  "vehicleType": 1,
  "entryTime": "2026-11-13T10:15:00.654321"
}
```

### Step 4: Query Active Vehicle Session
Inspect active parking session for vehicle `B 1234 XYZ`:
```bash
curl -X GET "http://localhost:8080/api/v1/parking/vehicles/B%201234%20XYZ" \
  -H "Accept: application/json"
```
*Response (`200 OK`):*
```json
{
  "licensePlate": "B 1234 XYZ",
  "vehicleType": 1,
  "entryTime": "2026-11-13T10:15:00.654321"
}
```

### Step 5: Check Out Vehicle & Generate Invoice
Check out vehicle `B 1234 XYZ` and compute final fee:
```bash
curl -X POST http://localhost:8080/api/v1/parking/check-out \
  -H "Content-Type: application/json" \
  -d '{"licensePlate": "B 1234 XYZ"}'
```
*Response (`200 OK`):*
```json
{
  "licensePlate": "B 1234 XYZ",
  "vehicleType": 1,
  "entryTime": "2026-11-13T10:15:00.654321",
  "exitTime": "2026-11-13T12:20:00.789123",
  "durationHours": 3,
  "hourlyRate": 5000,
  "totalFee": 15000
}
```

### Step 6: Verify CSV History Export
Examine the post-commit synchronized CSV audit report in `data/parking_history.csv`:
```csv
license_plate,vehicle_type,entry_time,exit_time,parking_fee
B 1234 XYZ,1,2026-11-13T10:15:00.654321,2026-11-13T12:20:00.789123,15000
```

---

## 5. API Reference Summary

For full request/response schemas, validation rules, and error codes, refer to the [Complete API Reference](docs/api.md).

| Method | Endpoint | Description | Success Status |
|---|---|---|---|
| `PUT` | `/api/v1/parking/capacity` | Configure or update total parking capacity | `200 OK` |
| `GET` | `/api/v1/parking/capacity` | Retrieve current total parking capacity | `200 OK` |
| `POST` | `/api/v1/parking/reservations` | Reserve a parking slot for a license plate | `201 Created` |
| `GET` | `/api/v1/parking/reservations/{licensePlate}` | Retrieve an active reservation | `200 OK` |
| `POST` | `/api/v1/parking/check-in` | Check in a vehicle (Car or Motorcycle) | `201 Created` |
| `GET` | `/api/v1/parking/vehicles/{licensePlate}` | Retrieve active parking session by license plate | `200 OK` |
| `POST` | `/api/v1/parking/check-out` | Check out vehicle, compute billing, record history | `200 OK` |

---

## 6. Business Logic & Pricing Engine

### 6.1 Vehicle Rates
- **CAR (`vehicleType = 1`)**: IDR 5,000 / hour
- **MOTORCYCLE (`vehicleType = 2`)**: IDR 3,000 / hour

### 6.2 Duration & Fractional-Hour Ceiling Rounding
- **Minimum Charge**: 1 full hour (even if parked for only 1 minute).
- **Ceiling Rounding**: Any partial hour parked rounds up to the next whole hour.
- **Formula**:
  $$\text{durationHours} = \max\left(1, \left\lfloor \frac{\text{seconds} + 3599}{3600} \right\rfloor\right)$$
- **Calculation Examples**:
  - 15 minutes $\rightarrow$ 1 billed hour
  - 60 minutes $\rightarrow$ 1 billed hour
  - 61 minutes $\rightarrow$ 2 billed hours
  - 2 hours 15 minutes $\rightarrow$ 3 billed hours

---

## 7. Persistence & Data Integrity Architecture

For in-depth architectural details, see the [Architecture Document](docs/architecture.md).

1. **Relational Database as Source of Truth**:
   The H2 file-persistent database (`./data/parkiraja.mv.db`) serves as the durable, transactional source of truth. Data survives application restarts and hardware interruptions.
2. **Post-Commit CSV Synchronization**:
   Checkout invoices are recorded in H2 first within an atomic `@Transactional` boundary. The CSV file (`data/parking_history.csv`) is generated post-commit via Spring's `TransactionSynchronization.afterCommit()` callback.
3. **Fault Isolation & Self-Healing**:
   If writing the CSV file fails (e.g. disk full), the error is logged without aborting the checkout transaction (`200 OK`). The database remains intact, and the subsequent checkout or application startup (`ApplicationReadyEvent`) reconstructs the CSV file completely from H2.
4. **Pessimistic Concurrency Locking**:
   All capacity-mutating workflows acquire a row lock on the singleton capacity record (`findByIdForUpdate` with `PESSIMISTIC_WRITE`), ensuring race conditions never permit overbooking.

---

## 8. Security Hardening

For detailed security threat models and test cases, see the [Security Hardening Guide](docs/security.md).

- **CSV Formula Injection Mitigation (OWASP CWE-1236)**:
  `ParkingHistoryWriter` sanitizes license plates and text fields starting with formula triggers (`=`, `+`, `-`, `@`, `\t`, `\r` or leading whitespace followed by triggers) by prepending a single quote (`'`), followed by RFC 4180 escaping. Protects spreadsheet software against arbitrary formula and DDE command execution.
- **Information Disclosure Prevention (OWASP CWE-209)**:
  `GlobalExceptionHandler` captures all generic unexpected server exceptions, logs full stack traces server-side via SLF4J, and returns a uniform, sanitized error response (`"An unexpected internal error occurred"`), hiding SQL syntax, table names, and server paths from HTTP clients.

---

## 9. Testing & Quality Assurance

For complete test breakdown and coverage reports, see the [Testing Documentation](docs/testing.md).

- **Total Test Cases**: **154** automated tests (0 failures, 0 errors, 0 skipped).
- **Line Coverage**: **100%** across all 32 classes in the application bundle.
- **Coverage Rules Enforced in `pom.xml`**:
  - `BUNDLE` Line Coverage: $\ge 90\%$
  - `SOURCEFILE` Line Coverage: $\ge 90\%$
- **Test Breakdown**:
  - 32 Controller slice tests (`@WebMvcTest`)
  - 48 Service & Writer unit tests (JUnit 5 + Mockito)
  - 53 End-to-end integration tests (`@SpringBootTest` with in-memory H2)
  - 21 Domain, Exception & Context tests (DTOs, Entities, Exception Handlers, Context)

---

## 10. Project Directory Structure

```text
parkiraja/
├── .github/
│   └── workflows/
│       └── ci.yml                      # GitHub Actions CI build & verification
├── data/
│   ├── parkiraja.mv.db                 # Durable H2 database file (runtime, git-ignored)
│   └── parking_history.csv             # Exported parking history CSV report
├── docs/
│   ├── api.md                          # Complete REST API reference
│   ├── architecture.md                 # Architecture, domain model & concurrency
│   ├── testing.md                      # Testing strategy & JaCoCo coverage metrics
│   └── security.md                     # Security hardening & vulnerability defenses
├── src/
│   ├── main/
│   │   ├── java/com/parkiraja/
│   │   │   ├── controller/             # REST controllers (Capacity, Reservation, Vehicle)
│   │   │   ├── dto/                    # Immutable Java records for request/response payloads
│   │   │   ├── entity/                 # JPA domain entities (Capacity, Reservation, Session, History)
│   │   │   ├── exception/              # Global exception handler & domain exceptions
│   │   │   ├── repository/             # Spring Data JPA repositories with pessimistic locks
│   │   │   ├── service/                # Business services & CSV history writer
│   │   │   └── ParkirajaApplication.java # Spring Boot main entry point
│   │   └── resources/
│   │       └── application.yml         # Application configuration (port, datasource)
│   └── test/
│       ├── java/com/parkiraja/         # 154 unit and integration test cases
│       └── resources/
│           └── application.yml         # In-memory test database configuration
├── pom.xml                             # Maven project descriptor & JaCoCo configuration
└── README.md                           # Main project documentation (this document)
```

---

## 11. Documentation Index

- [REST API Reference](docs/api.md): In-depth endpoint specifications, schemas, status codes, and curl commands.
- [Architecture & Design](docs/architecture.md): Layered architecture, domain model, lifecycle state machine, and concurrency controls.
- [Testing & JaCoCo Coverage](docs/testing.md): Test suite organization, test isolation, coverage report generation, and metrics.
- [Security Hardening](docs/security.md): Threat mitigation for CSV injection (CWE-1236) and error disclosure (CWE-209).

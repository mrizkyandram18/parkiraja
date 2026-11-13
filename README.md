# ParKing — ParkiRaja Parking Management System

Backend service for parking lot providers.

## Technology Stack

- **Java Version**: 17
- **Framework**: Spring Boot 4.1.2
- **Build Tool**: Apache Maven 3.9.x
- **Database**: H2 Database (File-persistent mode)
- **Persistence**: Spring Data JPA / Hibernate
- **Validation**: Jakarta Validation

## Dependencies

The project uses minimal Spring Boot 4 dependencies:
- `spring-boot-starter-webmvc` — Spring MVC REST web framework
- `spring-boot-starter-data-jpa` — Spring Data JPA & Hibernate ORM
- `spring-boot-starter-validation` — Jakarta Bean Validation
- `com.h2database:h2` — H2 database engine (runtime)
- `spring-boot-starter-test` — JUnit 5, Mockito, and Spring Test (test scope)

## H2 Database Configuration

- **Runtime Mode**: File-persistent storage
- **JDBC URL**: `jdbc:h2:file:./data/parkiraja`
- **Driver**: `org.h2.Driver`
- **User**: `sa` (empty password)
- **Data Persistence**: Database files are stored under `./data/` and persist across application restarts. Database files are excluded from Git version control.
- **Test Mode**: In-memory H2 (`jdbc:h2:mem:parkirajatest;DB_CLOSE_DELAY=-1`) is used during test execution to provide clean, isolated test runs.

## Building and Running

### Prerequisites

- Java Development Kit (JDK) 17 installed and configured.
- Apache Maven 3.9+ installed and available in PATH.

### Compile the Application

```bash
mvn clean compile
```

### Run Tests

```bash
mvn test
```

### Run the Application

```bash
mvn spring-boot:run
```

The application starts by default on port `8080`.

## Billing Rules & Business Assumptions

Parking fees are calculated at vehicle checkout based on vehicle type rates:
- **CAR (code 1)**: IDR 5,000 per hour
- **MOTORCYCLE (code 2)**: IDR 3,000 per hour

### Fractional-Hour Rounding Assumption
Because the initial assignment specification does not specify fractional-hour rate pro-rating or grace periods:
1. **Ceiling Rounding**: Any partial hour parked is rounded up to the next full hour.
2. **Minimum Charge**: Minimum billable duration is one hour (e.g., parking for 15 minutes is billed as 1 hour).
3. **Exact Calculation**:
   - 15 minutes $\rightarrow$ 1 billed hour
   - 60 minutes $\rightarrow$ 1 billed hour
   - 61 minutes $\rightarrow$ 2 billed hours
   - 2 hours 15 minutes $\rightarrow$ 3 billed hours

## Parking History & CSV Synchronization

1. **Atomic Database Persistence**: Every completed checkout atomically persists a `ParkingHistory` record to the H2 database and removes the active `ParkingSession` within a single `@Transactional` boundary. The durable H2 database serves as the authoritative source of truth.
2. **Post-Commit CSV Synchronization**: The `data/parking_history.csv` export is generated from committed H2 history via Spring's `TransactionSynchronization.afterCommit()` callback after the database transaction successfully commits. History retrieval uses an independent read transaction (`Propagation.REQUIRES_NEW`).
3. **Safe Error Isolation**: If CSV synchronization fails (e.g. disk I/O or permissions), the failure is logged via SLF4J without causing the checkout operation to fail (`HTTP 200 OK`). The committed database history remains safe, and subsequent checkouts return `HTTP 404 Not Found` rather than generating duplicate invoices.
4. **Startup & Ongoing Reconstruction**: On application startup (`ApplicationReadyEvent`), `ParkingHistoryWriter` rebuilds the CSV file from the durable H2 history. Startup export errors are caught and logged so the service stays running. Any later successful synchronization (startup or subsequent checkout) automatically rebuilds the complete CSV file without duplicating records.

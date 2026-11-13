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
- **JDBC URL**: `jdbc:h2:file:./data/parkiraja;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1`
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

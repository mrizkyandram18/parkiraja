# ParKing Security Hardening & Defenses

This document outlines the security controls, threat mitigations, and defensive design implemented in the **ParKing** (ParkiRaja) parking management service.

---

## 1. Security Architecture Summary

| Vulnerability Category | OWASP / CWE ID | Attack Vector | Implemented Mitigation | Primary File |
|---|---|---|---|---|
| **CSV Formula Injection** | [CWE-1236](https://cwe.mitre.org/data/definitions/1236.html) | Malicious spreadsheet execution via exported CSV data. | Prefix sanitization (`'`) and RFC 4180 escaping at CSV export boundary. | `ParkingHistoryWriter.java` |
| **Information Disclosure** | [CWE-209](https://cwe.mitre.org/data/definitions/209.html) | Server stack traces, SQL syntax, or filesystem paths leaked in HTTP 500. | Centralized exception handling, server-side SLF4J logging, masked client error message. | `GlobalExceptionHandler.java` |
| **SQL Injection** | [CWE-89](https://cwe.mitre.org/data/definitions/89.html) | Arbitrary SQL commands executed through dynamic input. | Parameterized queries via Spring Data JPA and Hibernate ORM. | Repositories |
| **Race Conditions / Overbooking** | [CWE-362](https://cwe.mitre.org/data/definitions/362.html) | Concurrent check-ins exceeding configured parking capacity. | Pessimistic write locking (`PESSIMISTIC_WRITE`) and DB unique constraints. | `ParkingCapacityRepository.java` |

---

## 2. CSV Formula Injection Mitigation (OWASP CWE-1236)

### 2.1 Threat Context
When parking history data is exported to `data/parking_history.csv` and subsequently opened in spreadsheet software (such as Microsoft Excel, LibreOffice Calc, or Google Sheets), untrusted user input that begins with formula trigger characters can be evaluated as executable formulas.

Attackers could submit license plates such as:
- `=cmd|' /C calc'!A0` (DDE execution)
- `@SUM(1,2)`
- `+1+2`
- `-cmd|...`
- `   =cmd|...` (leading spaces before formula trigger)
- `\t=cmd|...` or `\r=cmd|...` (control characters)

### 2.2 Defensive Implementation
Sanitization is enforced at the serialization boundary in `com.parkiraja.service.ParkingHistoryWriter`:

```java
public String sanitizeFormula(String value) {
    if (value == null || value.isEmpty()) {
        return value;
    }
    char first = value.charAt(0);
    if (isFormulaTrigger(first) || first == '\t' || first == '\r') {
        return "'" + value;
    }
    int i = 0;
    int len = value.length();
    while (i < len && isWhitespaceOrControl(value.charAt(i))) {
        i++;
    }
    if (i > 0 && i < len && isFormulaTrigger(value.charAt(i))) {
        return "'" + value;
    }
    return value;
}

private boolean isFormulaTrigger(char ch) {
    return ch == '=' || ch == '+' || ch == '-' || ch == '@';
}
```

### 2.3 Protection Mechanics
1. **Neutralization**: When an exported field starts with `=`, `+`, `-`, `@`, `\t`, `\r`, or starts with whitespace followed by one of these characters, a single quote (`'`) is prepended. In spreadsheet programs, a leading `'` forces the entire cell to be treated as plain literal text rather than an active executable expression.
2. **RFC 4180 Escaping**: After formula sanitization, if the resulting string contains commas (`,`), quotes (`"`), or newlines (`\n`, `\r`), it is appropriately wrapped in double quotes, with internal quotes escaped as `""`.
3. **Database Integrity Preserved**: This sanitization occurs exclusively at the CSV export boundary. The durable H2 database maintains the pristine raw license plate string, preserving database query fidelity.

---

## 3. Information Disclosure Sanitization (OWASP CWE-209)

### 3.1 Threat Context
Default web framework exception handlers often expose internal implementation details in HTTP 500 error responses:
- Full Java stack traces disclosing package names, versions, and third-party libraries.
- Database SQL syntax errors revealing schema design, table names, and column types.
- Operating system filesystem paths revealing host infrastructure.

### 3.2 Defensive Implementation
Centralized exception handling in `com.parkiraja.exception.GlobalExceptionHandler` sanitizes all unexpected exceptions:

```java
private static final String INTERNAL_SERVER_ERROR_MESSAGE = "An unexpected internal error occurred";

@ExceptionHandler(Exception.class)
public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
    log.error("An unexpected internal error occurred", ex);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR_MESSAGE);
}
```

### 3.3 Protection Mechanics
1. **Masked Client Response**: Regardless of what unhandled exception occurs (runtime exceptions, SQL errors, connection failures), the client receives a uniform, sanitized response:
   ```json
   {
     "status": 500,
     "error": "Internal Server Error",
     "message": "An unexpected internal error occurred",
     "timestamp": "2026-11-13T10:00:00.123456"
   }
   ```
2. **Server-Side Auditing**: The complete exception with full stack trace and contextual information is logged exclusively on the server side using SLF4J (`log.error(...)`).
3. **Targeted Domain Handlers**: Business exceptions (`ParkingLotFullException`, `DuplicateVehicleException`, `CapacityNotFoundException`, etc.) return specific, helpful business error messages mapped to appropriate HTTP status codes (`400`, `404`, `409`) without disclosing technical internals.

---

## 4. Input Validation & Concurrency Protection

### 4.1 Strict Input Validation
All incoming HTTP request bodies are validated using Jakarta Bean Validation:
- `@NotBlank(message = "License plate must not be blank")`: Rejects null, empty, or whitespace-only plates.
- `@NotNull(message = "Total capacity is required")` and `@Min(value = 1, message = "Total capacity must be greater than zero")`: Rejects invalid capacity definitions.
- `@Min(value = 1)` and `@Max(value = 2)` on vehicle types: Strictly restricts input to valid vehicle categories (CAR or MOTORCYCLE).

### 4.2 Parameterized Data Access
All database access is conducted through Spring Data JPA repository methods:
- Queries are executed using prepared statements, eliminating SQL injection risks.
- Data bindings use type-safe entities and explicit parameter bindings (`@Param`).

### 4.3 Race Condition Immunity
- Operations checking or modifying capacity use pessimistic locking (`PESSIMISTIC_WRITE`) on the database row.
- Unique constraints on `license_plate` at the database schema level prevent duplicate active sessions even under high concurrent load.

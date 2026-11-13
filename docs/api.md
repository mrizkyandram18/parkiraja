# ParKing REST API Specification

This document provides the definitive, comprehensive API reference for the **ParKing** (ParkiRaja) parking management backend service.

---

## 1. Global Conventions

### 1.1 Base URL & Content Type
- **Base URL**: `http://localhost:8080`
- **Default Media Type**: `application/json`
- **Date & Time Format**: ISO-8601 (`YYYY-MM-DDTHH:mm:ss`, e.g., `2026-11-13T10:15:30`)
- **Character Encoding**: UTF-8

### 1.2 Common HTTP Headers
| Header | Value | Description |
|---|---|---|
| `Content-Type` | `application/json` | Required for all `POST` and `PUT` requests carrying a request body. |
| `Accept` | `application/json` | Recommended for all requests. |

### 1.3 Standard Error Response Format
All error responses return a standardized JSON structure with appropriate HTTP status codes:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "License plate must not be blank",
  "timestamp": "2026-11-13T10:30:00"
}
```

| Field | Type | Description |
|---|---|---|
| `status` | integer | HTTP status code matching the HTTP response header (e.g., `400`, `404`, `409`, `500`). |
| `error` | string | Standard HTTP reason phrase (e.g., `"Bad Request"`, `"Not Found"`, `"Conflict"`). |
| `message` | string | Descriptive error message explaining the validation or business failure. |
| `timestamp` | string | ISO-8601 timestamp when the error occurred. |

### 1.4 Standard HTTP Status Codes
- `200 OK`: Request succeeded. Returned on successful updates, retrievals, and vehicle checkout.
- `201 Created`: Resource successfully created. Returned on successful check-in and reservation creation.
- `400 Bad Request`: Payload validation failure, malformed JSON, or invalid capacity argument.
- `404 Not Found`: Resource not found (e.g., parking capacity not initialized, vehicle or reservation not active).
- `409 Conflict`: Business rule conflict (e.g., parking lot is full, vehicle already actively parked, duplicate reservation).
- `500 Internal Server Error`: Generic unexpected server error. Technical details and stack traces are masked and logged server-side.

---

## 2. API Endpoints Overview

| Method | Endpoint | Description | Success Status |
|---|---|---|---|
| `PUT` | `/api/v1/parking/capacity` | Configure or update total parking capacity | `200 OK` |
| `GET` | `/api/v1/parking/capacity` | Retrieve current total parking capacity | `200 OK` |
| `POST` | `/api/v1/parking/reservations` | Reserve a parking slot for a license plate | `201 Created` |
| `GET` | `/api/v1/parking/reservations/{licensePlate}` | Retrieve an active reservation | `200 OK` |
| `POST` | `/api/v1/parking/check-in` | Check in a vehicle into the parking lot | `201 Created` |
| `GET` | `/api/v1/parking/vehicles/{licensePlate}` | Retrieve active parking session by license plate | `200 OK` |
| `POST` | `/api/v1/parking/check-out` | Check out a vehicle, compute bill, and record history | `200 OK` |

---

## 3. Detailed Endpoint Reference

### 3.1 Update Parking Capacity

Configures or updates the total parking capacity of the facility. Capacity is persisted as a singleton record in the database.

- **Method**: `PUT`
- **Path**: `/api/v1/parking/capacity`
- **Content-Type**: `application/json`

#### Request Body
```json
{
  "totalCapacity": 50
}
```

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `totalCapacity` | integer | Yes | `min = 1`, Not null | The new maximum number of total vehicles/reservations allowed. |

#### Business Rules
- Total capacity cannot be reduced below the sum of currently active parked vehicles and active reservations.
- If capacity is lower than `(activeVehicles + activeReservations)`, the service rejects the update with `400 Bad Request`.

#### Success Response: `200 OK`
```json
{
  "totalCapacity": 50
}
```

#### Error Responses
- **`400 Bad Request`** — Validation failed:
  ```json
  {
    "status": 400,
    "error": "Bad Request",
    "message": "Total capacity must be greater than zero",
    "timestamp": "2026-11-13T10:00:00"
  }
  ```
- **`400 Bad Request`** — Capacity smaller than active occupancy:
  ```json
  {
    "status": 400,
    "error": "Bad Request",
    "message": "Total capacity cannot be less than current active vehicle and reservation count: 12",
    "timestamp": "2026-11-13T10:00:00"
  }
  ```

#### Example cURL
```bash
curl -X PUT http://localhost:8080/api/v1/parking/capacity \
  -H "Content-Type: application/json" \
  -d '{"totalCapacity": 50}'
```

---

### 3.2 Get Parking Capacity

Retrieves the currently configured total parking capacity.

- **Method**: `GET`
- **Path**: `/api/v1/parking/capacity`
- **Accept**: `application/json`

#### Success Response: `200 OK`
```json
{
  "totalCapacity": 50
}
```

#### Error Responses
- **`404 Not Found`** — Capacity has not been initialized:
  ```json
  {
    "status": 404,
    "error": "Not Found",
    "message": "Parking capacity has not been configured",
    "timestamp": "2026-11-13T10:00:00"
  }
  ```

#### Example cURL
```bash
curl -X GET http://localhost:8080/api/v1/parking/capacity \
  -H "Accept: application/json"
```

---

### 3.3 Create Parking Reservation

Reserves a parking slot in advance for a specific license plate. Consumes one unit of total parking capacity.

- **Method**: `POST`
- **Path**: `/api/v1/parking/reservations`
- **Content-Type**: `application/json`

#### Request Body
```json
{
  "licensePlate": "B 1234 XYZ"
}
```

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `licensePlate` | string | Yes | Not blank | Vehicle license plate. Normalized internally (trimmed and uppercased). |

#### Business Rules
- Parking capacity must be configured prior to creating reservations.
- Total occupied slots `(activeVehicles + activeReservations)` cannot exceed configured `totalCapacity`.
- A vehicle cannot be reserved if it is already actively parked (`409 Conflict`).
- A vehicle cannot have more than one active reservation simultaneously (`409 Conflict`).

#### Success Response: `201 Created`
```json
{
  "licensePlate": "B 1234 XYZ",
  "reservedAt": "2026-11-13T10:15:30.123456"
}
```

#### Error Responses
- **`400 Bad Request`** — License plate is blank or missing:
  ```json
  {
    "status": 400,
    "error": "Bad Request",
    "message": "License plate must not be blank",
    "timestamp": "2026-11-13T10:00:00"
  }
  ```
- **`404 Not Found`** — Capacity not configured:
  ```json
  {
    "status": 404,
    "error": "Not Found",
    "message": "Parking capacity has not been configured",
    "timestamp": "2026-11-13T10:00:00"
  }
  ```
- **`409 Conflict`** — Parking lot is full:
  ```json
  {
    "status": 409,
    "error": "Conflict",
    "message": "Parking lot is full",
    "timestamp": "2026-11-13T10:00:00"
  }
  ```
- **`409 Conflict`** — Vehicle already has an active reservation:
  ```json
  {
    "status": 409,
    "error": "Conflict",
    "message": "Vehicle with license plate B 1234 XYZ already has an active reservation",
    "timestamp": "2026-11-13T10:00:00"
  }
  ```
- **`409 Conflict`** — Vehicle is already actively parked:
  ```json
  {
    "status": 409,
    "error": "Conflict",
    "message": "Vehicle with license plate B 1234 XYZ is already actively parked",
    "timestamp": "2026-11-13T10:00:00"
  }
  ```

#### Example cURL
```bash
curl -X POST http://localhost:8080/api/v1/parking/reservations \
  -H "Content-Type: application/json" \
  -d '{"licensePlate": "B 1234 XYZ"}'
```

---

### 3.4 Get Parking Reservation

Retrieves an active reservation by vehicle license plate.

- **Method**: `GET`
- **Path**: `/api/v1/parking/reservations/{licensePlate}`
- **Accept**: `application/json`

#### Path Parameters
| Parameter | Type | Required | Description |
|---|---|---|---|
| `licensePlate` | string | Yes | Vehicle license plate (e.g., `B 1234 XYZ` or URL-encoded `B%201234%20XYZ`). |

#### Success Response: `200 OK`
```json
{
  "licensePlate": "B 1234 XYZ",
  "reservedAt": "2026-11-13T10:15:30.123456"
}
```

#### Error Responses
- **`404 Not Found`** — No active reservation found:
  ```json
  {
    "status": 404,
    "error": "Not Found",
    "message": "No active reservation found for license plate B 1234 XYZ",
    "timestamp": "2026-11-13T10:00:00"
  }
  ```

#### Example cURL
```bash
curl -X GET "http://localhost:8080/api/v1/parking/reservations/B%201234%20XYZ" \
  -H "Accept: application/json"
```

---

### 3.5 Vehicle Check-In

Records vehicle entry into the parking facility and starts an active parking session.

- **Method**: `POST`
- **Path**: `/api/v1/parking/check-in`
- **Content-Type**: `application/json`

#### Request Body
```json
{
  "licensePlate": "B 1234 XYZ",
  "vehicleType": 1
}
```

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `licensePlate` | string | Yes | Not blank | Vehicle license plate. Normalized internally (trimmed and uppercased). |
| `vehicleType` | integer | Yes | `1` or `2` | Vehicle type code: `1` = CAR, `2` = MOTORCYCLE. |

#### Business Rules
- Parking capacity must be configured prior to check-in.
- If the vehicle holds an active reservation:
  - The reservation is consumed (deleted) atomically.
  - The vehicle is granted entry without re-evaluating remaining capacity (its slot was already secured).
- If the vehicle does **not** hold a reservation:
  - Available capacity is verified: `(activeVehicles + activeReservations) < totalCapacity`.
  - If lot is full, check-in is rejected with `409 Conflict`.
- A vehicle cannot check in if it is already actively parked (`409 Conflict`).

#### Success Response: `201 Created`
```json
{
  "licensePlate": "B 1234 XYZ",
  "vehicleType": 1,
  "entryTime": "2026-11-13T10:20:00.654321"
}
```

#### Error Responses
- **`400 Bad Request`** — Validation failed:
  ```json
  {
    "status": 400,
    "error": "Bad Request",
    "message": "Vehicle type must be 1 (Car) or 2 (Motorcycle)",
    "timestamp": "2026-11-13T10:00:00"
  }
  ```
- **`404 Not Found`** — Capacity not configured:
  ```json
  {
    "status": 404,
    "error": "Not Found",
    "message": "Parking capacity has not been configured",
    "timestamp": "2026-11-13T10:00:00"
  }
  ```
- **`409 Conflict`** — Parking lot is full:
  ```json
  {
    "status": 409,
    "error": "Conflict",
    "message": "Parking lot is full",
    "timestamp": "2026-11-13T10:00:00"
  }
  ```
- **`409 Conflict`** — Vehicle already parked:
  ```json
  {
    "status": 409,
    "error": "Conflict",
    "message": "Vehicle with license plate B 1234 XYZ is already actively parked",
    "timestamp": "2026-11-13T10:00:00"
  }
  ```

#### Example cURL
```bash
curl -X POST http://localhost:8080/api/v1/parking/check-in \
  -H "Content-Type: application/json" \
  -d '{"licensePlate": "B 1234 XYZ", "vehicleType": 1}'
```

---

### 3.6 Get Active Vehicle Session

Retrieves active parking session details for a currently parked vehicle.

- **Method**: `GET`
- **Path**: `/api/v1/parking/vehicles/{licensePlate}`
- **Accept**: `application/json`

#### Path Parameters
| Parameter | Type | Required | Description |
|---|---|---|---|
| `licensePlate` | string | Yes | Vehicle license plate (e.g., `B 1234 XYZ` or URL-encoded `B%201234%20XYZ`). |

#### Success Response: `200 OK`
```json
{
  "licensePlate": "B 1234 XYZ",
  "vehicleType": 1,
  "entryTime": "2026-11-13T10:20:00.654321"
}
```

#### Error Responses
- **`404 Not Found`** — Vehicle not parked:
  ```json
  {
    "status": 404,
    "error": "Not Found",
    "message": "Vehicle with license plate B 1234 XYZ is not actively parked",
    "timestamp": "2026-11-13T10:00:00"
  }
  ```

#### Example cURL
```bash
curl -X GET "http://localhost:8080/api/v1/parking/vehicles/B%201234%20XYZ" \
  -H "Accept: application/json"
```

---

### 3.7 Vehicle Check-Out

Processes vehicle exit, calculates parking duration and billing fee, records history in the H2 database, removes active session, and synchronizes the CSV report file.

- **Method**: `POST`
- **Path**: `/api/v1/parking/check-out`
- **Content-Type**: `application/json`

#### Request Body
```json
{
  "licensePlate": "B 1234 XYZ"
}
```

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `licensePlate` | string | Yes | Not blank | Vehicle license plate. Normalized internally (trimmed and uppercased). |

#### Billing & Fee Calculation Rules
1. **Rates**:
   - CAR (`vehicleType = 1`): IDR 5,000 / hour
   - MOTORCYCLE (`vehicleType = 2`): IDR 3,000 / hour
2. **Ceiling Rounding**:
   - Duration is rounded up to the nearest whole hour:
     $$\text{durationHours} = \left\lceil \frac{\text{durationInSeconds}}{3600} \right\rceil = \left\lfloor \frac{\text{durationInSeconds} + 3599}{3600} \right\rfloor$$
   - Minimum billable duration is 1 hour (e.g., 5 seconds parked $\rightarrow$ 1 billed hour).
3. **Total Fee Formula**:
   $$\text{totalFee} = \text{durationHours} \times \text{hourlyRate}$$

#### Persistence & CSV Export Side-Effects
- Atomically saves a `ParkingHistory` record to H2 and deletes the `ParkingSession` record.
- Registers a post-commit transaction hook (`TransactionSynchronization.afterCommit()`).
- On successful commit, regenerates `data/parking_history.csv` with formula injection sanitization.
- If CSV file writing fails (e.g. disk I/O), the error is logged and isolated; the database transaction remains committed and the user receives `200 OK`.

#### Success Response: `200 OK`
```json
{
  "licensePlate": "B 1234 XYZ",
  "vehicleType": 1,
  "entryTime": "2026-11-13T10:20:00",
  "exitTime": "2026-11-13T12:35:00",
  "durationHours": 3,
  "hourlyRate": 5000,
  "totalFee": 15000
}
```

#### Error Responses
- **`400 Bad Request`** — License plate is blank:
  ```json
  {
    "status": 400,
    "error": "Bad Request",
    "message": "License plate must not be blank",
    "timestamp": "2026-11-13T10:00:00"
  }
  ```
- **`404 Not Found`** — Capacity not configured:
  ```json
  {
    "status": 404,
    "error": "Not Found",
    "message": "Parking capacity has not been configured",
    "timestamp": "2026-11-13T10:00:00"
  }
  ```
- **`404 Not Found`** — Vehicle is not actively parked:
  ```json
  {
    "status": 404,
    "error": "Not Found",
    "message": "Vehicle with license plate B 1234 XYZ is not actively parked",
    "timestamp": "2026-11-13T10:00:00"
  }
  ```

#### Example cURL
```bash
curl -X POST http://localhost:8080/api/v1/parking/check-out \
  -H "Content-Type: application/json" \
  -d '{"licensePlate": "B 1234 XYZ"}'
```

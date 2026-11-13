package com.parkiraja.exception;

import com.parkiraja.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.io.FileNotFoundException;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void shouldReturn500WithGenericMessageWhenGenericExceptionOccurs() {
        Exception ex = new RuntimeException("Generic unexpected failure");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().error()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().message()).isEqualTo("An unexpected internal error occurred");
        assertThat(response.getBody().timestamp()).isNotNull();
    }

    @Test
    void shouldSanitizeSqlExceptionDetailsInGenericException() {
        SQLException sqlEx = new SQLException("Table 'PARKING_HISTORY' not found at jdbc:h2:file:./data/parkiraja", "42S02", 42102);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(sqlEx);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("An unexpected internal error occurred");
        // Ensure SQL details, table names, and JDBC URLs are NOT exposed
        assertThat(response.getBody().message()).doesNotContain("PARKING_HISTORY");
        assertThat(response.getBody().message()).doesNotContain("jdbc:h2");
        assertThat(response.getBody().message()).doesNotContain("42S02");
    }

    @Test
    void shouldSanitizeFilesystemPathDetailsInGenericException() {
        FileNotFoundException fnfEx = new FileNotFoundException("D:\\Personal\\Projects\\bank-saqu\\parkiraja\\data\\secret.key (The system cannot find the file specified)");

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(fnfEx);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("An unexpected internal error occurred");
        // Ensure filesystem paths are NOT exposed
        assertThat(response.getBody().message()).doesNotContain("D:\\Personal");
        assertThat(response.getBody().message()).doesNotContain("secret.key");
    }

    @Test
    void shouldHandleGenericExceptionWithNullMessage() {
        Exception ex = new RuntimeException((String) null);

        ResponseEntity<ErrorResponse> response = exceptionHandler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("An unexpected internal error occurred");
    }

    @Test
    void shouldPreserveBusinessExceptionResponses() {
        ResponseEntity<ErrorResponse> r1 = exceptionHandler.handleCapacityNotFound(new CapacityNotFoundException("Capacity missing"));
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r1.getBody().message()).isEqualTo("Capacity missing");

        ResponseEntity<ErrorResponse> r2 = exceptionHandler.handleVehicleNotFound(new VehicleNotFoundException("Vehicle missing"));
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r2.getBody().message()).isEqualTo("Vehicle missing");

        ResponseEntity<ErrorResponse> r3 = exceptionHandler.handleReservationNotFound(new ReservationNotFoundException("Reservation missing"));
        assertThat(r3.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r3.getBody().message()).isEqualTo("Reservation missing");

        ResponseEntity<ErrorResponse> r4 = exceptionHandler.handleDuplicateReservation(new DuplicateReservationException("Duplicate reservation"));
        assertThat(r4.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r4.getBody().message()).isEqualTo("Duplicate reservation");

        ResponseEntity<ErrorResponse> r5 = exceptionHandler.handleDuplicateVehicle(new DuplicateVehicleException("Duplicate vehicle"));
        assertThat(r5.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r5.getBody().message()).isEqualTo("Duplicate vehicle");

        ResponseEntity<ErrorResponse> r6 = exceptionHandler.handleParkingLotFull(new ParkingLotFullException("Lot full"));
        assertThat(r6.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r6.getBody().message()).isEqualTo("Lot full");

        ResponseEntity<ErrorResponse> r7 = exceptionHandler.handleInvalidCapacity(new InvalidCapacityException("Invalid capacity"));
        assertThat(r7.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r7.getBody().message()).isEqualTo("Invalid capacity");

        ResponseEntity<ErrorResponse> r8 = exceptionHandler.handleDataIntegrityViolation(new DataIntegrityViolationException("Unique constraint"));
        assertThat(r8.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r8.getBody().message()).isEqualTo("Vehicle with license plate is already parked");

        ResponseEntity<ErrorResponse> r9 = exceptionHandler.handleMessageNotReadable(
                new HttpMessageNotReadableException("Malformed JSON", (org.springframework.http.HttpInputMessage) null));
        assertThat(r9.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r9.getBody().message()).isEqualTo("Invalid request payload");

        ResponseEntity<ErrorResponse> r10 = exceptionHandler.handleParkingHistoryException(new ParkingHistoryException("CSV write failed"));
        assertThat(r10.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r10.getBody().message()).isEqualTo("CSV write failed");
    }
}

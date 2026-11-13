package com.parkiraja.service;

import com.parkiraja.dto.CheckInRequest;
import com.parkiraja.dto.CheckOutRequest;
import com.parkiraja.dto.CheckOutResponse;
import com.parkiraja.dto.VehicleResponse;
import com.parkiraja.entity.ParkingCapacity;
import com.parkiraja.entity.ParkingHistory;
import com.parkiraja.entity.ParkingReservation;
import com.parkiraja.entity.ParkingSession;
import com.parkiraja.entity.VehicleType;
import com.parkiraja.exception.CapacityNotFoundException;
import com.parkiraja.exception.DuplicateVehicleException;
import com.parkiraja.exception.ParkingHistoryException;
import com.parkiraja.exception.ParkingLotFullException;
import com.parkiraja.exception.VehicleNotFoundException;
import com.parkiraja.repository.ParkingCapacityRepository;
import com.parkiraja.repository.ParkingHistoryRepository;
import com.parkiraja.repository.ParkingReservationRepository;
import com.parkiraja.repository.ParkingSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private ParkingCapacityRepository parkingCapacityRepository;

    @Mock
    private ParkingSessionRepository parkingSessionRepository;

    @Mock
    private ParkingReservationRepository parkingReservationRepository;

    @Mock
    private ParkingHistoryRepository parkingHistoryRepository;

    @Mock
    private ParkingHistoryWriter parkingHistoryWriter;

    @InjectMocks
    private VehicleService vehicleService;

    @Test
    void shouldCheckInVehicleSuccessfullyWithoutReservationWhenSpaceAvailable() {
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now());
        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.existsByLicensePlate("B 1234 XYZ")).thenReturn(false);
        when(parkingReservationRepository.findByLicensePlate("B 1234 XYZ")).thenReturn(Optional.empty());
        when(parkingSessionRepository.count()).thenReturn(3L);
        when(parkingReservationRepository.count()).thenReturn(2L);

        LocalDateTime now = LocalDateTime.now();
        ParkingSession savedSession = new ParkingSession("B 1234 XYZ", VehicleType.CAR, now);
        when(parkingSessionRepository.save(any(ParkingSession.class))).thenReturn(savedSession);

        VehicleResponse response = vehicleService.checkIn(new CheckInRequest("  b 1234 xyz  ", 1));

        assertThat(response.licensePlate()).isEqualTo("B 1234 XYZ");
        assertThat(response.vehicleType()).isEqualTo(1);
        assertThat(response.entryTime()).isEqualTo(now);

        ArgumentCaptor<ParkingSession> captor = ArgumentCaptor.forClass(ParkingSession.class);
        verify(parkingSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getLicensePlate()).isEqualTo("B 1234 XYZ");
        assertThat(captor.getValue().getVehicleType()).isEqualTo(VehicleType.CAR);
    }

    @Test
    void shouldCheckInVehicleWithMatchingReservationAndFulfillReservation() {
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now());
        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.existsByLicensePlate("B 5678 ABC")).thenReturn(false);

        ParkingReservation reservation = new ParkingReservation("B 5678 ABC", LocalDateTime.now().minusHours(1));
        when(parkingReservationRepository.findByLicensePlate("B 5678 ABC")).thenReturn(Optional.of(reservation));

        LocalDateTime now = LocalDateTime.now();
        ParkingSession savedSession = new ParkingSession("B 5678 ABC", VehicleType.MOTORCYCLE, now);
        when(parkingSessionRepository.save(any(ParkingSession.class))).thenReturn(savedSession);

        VehicleResponse response = vehicleService.checkIn(new CheckInRequest("b 5678 abc", 2));

        assertThat(response.licensePlate()).isEqualTo("B 5678 ABC");
        assertThat(response.vehicleType()).isEqualTo(2);

        verify(parkingReservationRepository).delete(reservation);
        verify(parkingSessionRepository, never()).count();
        verify(parkingReservationRepository, never()).count();
        verify(parkingSessionRepository).save(any(ParkingSession.class));
    }

    @Test
    void shouldThrowDuplicateVehicleExceptionWhenVehicleAlreadyParked() {
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now());
        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.existsByLicensePlate("B 1234 XYZ")).thenReturn(true);

        DuplicateVehicleException exception = assertThrows(
                DuplicateVehicleException.class,
                () -> vehicleService.checkIn(new CheckInRequest("B 1234 XYZ", 1))
        );

        assertThat(exception.getMessage()).contains("is already actively parked");
        verify(parkingSessionRepository, never()).save(any());
    }

    @Test
    void shouldThrowParkingLotFullExceptionWhenActivePlusReservedReachesCapacity() {
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now());
        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.existsByLicensePlate("B 9999 XYZ")).thenReturn(false);
        when(parkingReservationRepository.findByLicensePlate("B 9999 XYZ")).thenReturn(Optional.empty());
        when(parkingSessionRepository.count()).thenReturn(3L);
        when(parkingReservationRepository.count()).thenReturn(2L); // 3 + 2 = 5 >= 5

        ParkingLotFullException exception = assertThrows(
                ParkingLotFullException.class,
                () -> vehicleService.checkIn(new CheckInRequest("B 9999 XYZ", 1))
        );

        assertThat(exception.getMessage()).isEqualTo("Parking lot is full");
        verify(parkingSessionRepository, never()).save(any());
    }

    @Test
    void shouldThrowCapacityNotFoundExceptionWhenCapacityNotConfigured() {
        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.empty());

        CapacityNotFoundException exception = assertThrows(
                CapacityNotFoundException.class,
                () -> vehicleService.checkIn(new CheckInRequest("B 1234 XYZ", 1))
        );

        assertThat(exception.getMessage()).isEqualTo("Parking capacity has not been configured");
    }

    @Test
    void shouldGetActivelyParkedVehicle() {
        LocalDateTime now = LocalDateTime.now();
        ParkingSession session = new ParkingSession("B 1234 XYZ", VehicleType.CAR, now);
        when(parkingSessionRepository.findByLicensePlate("B 1234 XYZ")).thenReturn(Optional.of(session));

        VehicleResponse response = vehicleService.getVehicle("  b 1234 xyz  ");

        assertThat(response.licensePlate()).isEqualTo("B 1234 XYZ");
        assertThat(response.vehicleType()).isEqualTo(1);
        assertThat(response.entryTime()).isEqualTo(now);
    }

    @Test
    void shouldThrowVehicleNotFoundExceptionWhenVehicleNotParked() {
        when(parkingSessionRepository.findByLicensePlate("B 9999 NON")).thenReturn(Optional.empty());

        VehicleNotFoundException exception = assertThrows(
                VehicleNotFoundException.class,
                () -> vehicleService.getVehicle("B 9999 NON")
        );

        assertThat(exception.getMessage()).isEqualTo("Vehicle with license plate B 9999 NON is not actively parked");
    }

    @Test
    void shouldThrowVehicleNotFoundExceptionWhenPlateIsBlankOrNull() {
        VehicleNotFoundException exNull = assertThrows(
                VehicleNotFoundException.class,
                () -> vehicleService.getVehicle(null)
        );
        assertThat(exNull.getMessage()).isEqualTo("Vehicle is not actively parked");

        VehicleNotFoundException exBlank = assertThrows(
                VehicleNotFoundException.class,
                () -> vehicleService.getVehicle("   ")
        );
        assertThat(exBlank.getMessage()).isEqualTo("Vehicle is not actively parked");
    }

    @Test
    void shouldCalculateDurationHoursCorrectly() {
        LocalDateTime base = LocalDateTime.of(2026, 11, 13, 10, 0, 0);

        // 15 minutes -> 1 hour
        assertThat(vehicleService.calculateDurationHours(base, base.plusMinutes(15))).isEqualTo(1L);
        // Exactly 60 minutes -> 1 hour
        assertThat(vehicleService.calculateDurationHours(base, base.plusMinutes(60))).isEqualTo(1L);
        // 61 minutes -> 2 hours
        assertThat(vehicleService.calculateDurationHours(base, base.plusMinutes(61))).isEqualTo(2L);
        // 2 hours 15 minutes -> 3 hours
        assertThat(vehicleService.calculateDurationHours(base, base.plusHours(2).plusMinutes(15))).isEqualTo(3L);
        // Exact 4 hours -> 4 hours
        assertThat(vehicleService.calculateDurationHours(base, base.plusHours(4))).isEqualTo(4L);
        // 0 seconds -> 1 hour minimum
        assertThat(vehicleService.calculateDurationHours(base, base)).isEqualTo(1L);
        // Negative / null cases -> 1 hour minimum
        assertThat(vehicleService.calculateDurationHours(base, base.minusMinutes(10))).isEqualTo(1L);
        assertThat(vehicleService.calculateDurationHours(null, base)).isEqualTo(1L);
        assertThat(vehicleService.calculateDurationHours(base, null)).isEqualTo(1L);
    }

    @Test
    void shouldCheckOutCarSuccessfullyAndPersistHistory() {
        LocalDateTime entryTime = LocalDateTime.now().minusHours(2);
        ParkingSession session = new ParkingSession("B 1234 XYZ", VehicleType.CAR, entryTime);
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now());

        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.findByLicensePlate("B 1234 XYZ")).thenReturn(Optional.of(session));

        CheckOutResponse response = vehicleService.checkOut(new CheckOutRequest("  b 1234 xyz  "));

        assertThat(response.licensePlate()).isEqualTo("B 1234 XYZ");
        assertThat(response.vehicleType()).isEqualTo(1);
        assertThat(response.entryTime()).isEqualTo(entryTime);
        assertThat(response.exitTime()).isNotNull();
        assertThat(response.durationHours()).isGreaterThanOrEqualTo(2L);
        assertThat(response.hourlyRate()).isEqualTo(5000L);
        assertThat(response.totalFee()).isEqualTo(response.durationHours() * 5000L);

        ArgumentCaptor<ParkingHistory> historyCaptor = ArgumentCaptor.forClass(ParkingHistory.class);
        verify(parkingHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getLicensePlate()).isEqualTo("B 1234 XYZ");
        assertThat(historyCaptor.getValue().getVehicleType()).isEqualTo(VehicleType.CAR);
        assertThat(historyCaptor.getValue().getTotalFee()).isEqualTo(response.totalFee());

        verify(parkingHistoryWriter).syncFromDatabase();
        verify(parkingSessionRepository).delete(session);
    }

    @Test
    void shouldRegisterTransactionSynchronizationWhenTransactionIsActive() {
        LocalDateTime entryTime = LocalDateTime.now().minusHours(1);
        ParkingSession session = new ParkingSession("B 5555 TX", VehicleType.CAR, entryTime);
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now());

        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.findByLicensePlate("B 5555 TX")).thenReturn(Optional.of(session));

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            CheckOutResponse response = vehicleService.checkOut(new CheckOutRequest("B 5555 TX"));
            assertThat(response.licensePlate()).isEqualTo("B 5555 TX");

            // Execute registered afterCommit synchronization
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            verify(parkingHistoryWriter).syncFromDatabase();
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void shouldCatchAndLogExceptionWhenCsvSyncFailsInAfterCommit() {
        LocalDateTime entryTime = LocalDateTime.now().minusHours(1);
        ParkingSession session = new ParkingSession("B 5555 ERR", VehicleType.CAR, entryTime);
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now());

        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.findByLicensePlate("B 5555 ERR")).thenReturn(Optional.of(session));
        doThrow(new ParkingHistoryException("Failed to record parking history to CSV", new RuntimeException("disk full")))
                .when(parkingHistoryWriter).syncFromDatabase();

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            CheckOutResponse response = vehicleService.checkOut(new CheckOutRequest("B 5555 ERR"));
            assertThat(response.licensePlate()).isEqualTo("B 5555 ERR");

            // Execute registered afterCommit synchronization - must not throw exception
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            verify(parkingHistoryWriter).syncFromDatabase();
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void shouldCatchAndLogExceptionWhenCsvSyncFailsWithoutActiveTransaction() {
        LocalDateTime entryTime = LocalDateTime.now().minusHours(1);
        ParkingSession session = new ParkingSession("B 6666 ERR", VehicleType.CAR, entryTime);
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now());

        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.findByLicensePlate("B 6666 ERR")).thenReturn(Optional.of(session));
        doThrow(new ParkingHistoryException("Failed to record parking history to CSV", new RuntimeException("disk full")))
                .when(parkingHistoryWriter).syncFromDatabase();

        CheckOutResponse response = vehicleService.checkOut(new CheckOutRequest("B 6666 ERR"));
        assertThat(response.licensePlate()).isEqualTo("B 6666 ERR");
        verify(parkingHistoryWriter).syncFromDatabase();
    }

    @Test
    void shouldCheckOutMotorcycleSuccessfully() {
        LocalDateTime entryTime = LocalDateTime.now().minusMinutes(45); // 45 mins -> 1 hour
        ParkingSession session = new ParkingSession("D 5678 ABC", VehicleType.MOTORCYCLE, entryTime);
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now());

        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.findByLicensePlate("D 5678 ABC")).thenReturn(Optional.of(session));

        CheckOutResponse response = vehicleService.checkOut(new CheckOutRequest("D 5678 ABC"));

        assertThat(response.licensePlate()).isEqualTo("D 5678 ABC");
        assertThat(response.vehicleType()).isEqualTo(2);
        assertThat(response.durationHours()).isEqualTo(1L);
        assertThat(response.hourlyRate()).isEqualTo(3000L);
        assertThat(response.totalFee()).isEqualTo(3000L);

        verify(parkingSessionRepository).delete(session);
        verify(parkingHistoryRepository).save(any(ParkingHistory.class));
        verify(parkingHistoryWriter).syncFromDatabase();
    }

    @Test
    void shouldThrowCapacityNotFoundExceptionOnCheckOutWhenCapacityNotConfigured() {
        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.empty());

        CapacityNotFoundException exception = assertThrows(
                CapacityNotFoundException.class,
                () -> vehicleService.checkOut(new CheckOutRequest("B 1234 XYZ"))
        );

        assertThat(exception.getMessage()).isEqualTo("Parking capacity has not been configured");
    }

    @Test
    void shouldThrowVehicleNotFoundExceptionOnCheckOutWhenSessionNotFound() {
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now());
        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.findByLicensePlate("B 9999 NON")).thenReturn(Optional.empty());

        VehicleNotFoundException exception = assertThrows(
                VehicleNotFoundException.class,
                () -> vehicleService.checkOut(new CheckOutRequest("B 9999 NON"))
        );

        assertThat(exception.getMessage()).isEqualTo("Vehicle with license plate B 9999 NON is not actively parked");
    }

    @Test
    void shouldThrowVehicleNotFoundExceptionOnCheckOutWhenRequestOrPlateIsBlank() {
        VehicleNotFoundException exNull = assertThrows(
                VehicleNotFoundException.class,
                () -> vehicleService.checkOut(null)
        );
        assertThat(exNull.getMessage()).isEqualTo("Vehicle is not actively parked");

        VehicleNotFoundException exPlateNull = assertThrows(
                VehicleNotFoundException.class,
                () -> vehicleService.checkOut(new CheckOutRequest(null))
        );
        assertThat(exPlateNull.getMessage()).isEqualTo("Vehicle is not actively parked");

        VehicleNotFoundException exBlank = assertThrows(
                VehicleNotFoundException.class,
                () -> vehicleService.checkOut(new CheckOutRequest("   "))
        );
        assertThat(exBlank.getMessage()).isEqualTo("Vehicle is not actively parked");
    }
}

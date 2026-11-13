package com.parkiraja.service;

import com.parkiraja.dto.ReservationRequest;
import com.parkiraja.dto.ReservationResponse;
import com.parkiraja.entity.ParkingCapacity;
import com.parkiraja.entity.ParkingReservation;
import com.parkiraja.exception.CapacityNotFoundException;
import com.parkiraja.exception.DuplicateReservationException;
import com.parkiraja.exception.DuplicateVehicleException;
import com.parkiraja.exception.ParkingLotFullException;
import com.parkiraja.exception.ReservationNotFoundException;
import com.parkiraja.repository.ParkingCapacityRepository;
import com.parkiraja.repository.ParkingReservationRepository;
import com.parkiraja.repository.ParkingSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ParkingCapacityRepository parkingCapacityRepository;

    @Mock
    private ParkingSessionRepository parkingSessionRepository;

    @Mock
    private ParkingReservationRepository parkingReservationRepository;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    void shouldCreateReservationSuccessfullyWithNormalizedPlate() {
        ReservationRequest request = new ReservationRequest("  b 5678 abc  ");
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now());

        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.count()).thenReturn(2L);
        when(parkingReservationRepository.count()).thenReturn(1L);
        when(parkingSessionRepository.existsByLicensePlate("B 5678 ABC")).thenReturn(false);
        when(parkingReservationRepository.existsByLicensePlate("B 5678 ABC")).thenReturn(false);
        when(parkingReservationRepository.save(any(ParkingReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.createReservation(request);

        assertThat(response.licensePlate()).isEqualTo("B 5678 ABC");
        assertThat(response.reservedAt()).isNotNull();

        ArgumentCaptor<ParkingReservation> captor = ArgumentCaptor.forClass(ParkingReservation.class);
        verify(parkingReservationRepository).save(captor.capture());
        ParkingReservation saved = captor.getValue();
        assertThat(saved.getLicensePlate()).isEqualTo("B 5678 ABC");
        assertThat(saved.getReservedAt()).isNotNull();
    }

    @Test
    void shouldAcquireLockBeforeCountingOccupancyAndPersistWithinTransaction() {
        ReservationRequest request = new ReservationRequest("B 5678 ABC");
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now());

        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.count()).thenReturn(2L);
        when(parkingReservationRepository.count()).thenReturn(1L);
        when(parkingSessionRepository.existsByLicensePlate("B 5678 ABC")).thenReturn(false);
        when(parkingReservationRepository.existsByLicensePlate("B 5678 ABC")).thenReturn(false);
        when(parkingReservationRepository.save(any(ParkingReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        reservationService.createReservation(request);

        InOrder inOrder = inOrder(parkingCapacityRepository, parkingSessionRepository, parkingReservationRepository);
        inOrder.verify(parkingCapacityRepository).findByIdForUpdate(ParkingCapacity.SINGLETON_ID);
        inOrder.verify(parkingSessionRepository).count();
        inOrder.verify(parkingReservationRepository).count();
        inOrder.verify(parkingReservationRepository).save(any(ParkingReservation.class));
    }

    @Test
    void shouldThrowCapacityNotFoundExceptionWhenCapacityNotConfigured() {
        ReservationRequest request = new ReservationRequest("B 5678 ABC");
        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.empty());

        CapacityNotFoundException exception = assertThrows(
                CapacityNotFoundException.class,
                () -> reservationService.createReservation(request)
        );

        assertThat(exception.getMessage()).isEqualTo("Parking capacity has not been configured");
    }

    @Test
    void shouldThrowParkingLotFullExceptionWhenActiveVehiclesPlusReservationsReachCapacity() {
        ReservationRequest request = new ReservationRequest("B 5678 ABC");
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now());

        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.count()).thenReturn(3L);
        when(parkingReservationRepository.count()).thenReturn(2L);

        ParkingLotFullException exception = assertThrows(
                ParkingLotFullException.class,
                () -> reservationService.createReservation(request)
        );

        assertThat(exception.getMessage()).isEqualTo("Parking lot is full");
    }

    @Test
    void shouldThrowDuplicateVehicleExceptionWhenPlateAlreadyParked() {
        ReservationRequest request = new ReservationRequest("B 5678 ABC");
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now());

        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.count()).thenReturn(2L);
        when(parkingReservationRepository.count()).thenReturn(1L);
        when(parkingSessionRepository.existsByLicensePlate("B 5678 ABC")).thenReturn(true);

        DuplicateVehicleException exception = assertThrows(
                DuplicateVehicleException.class,
                () -> reservationService.createReservation(request)
        );

        assertThat(exception.getMessage()).contains("B 5678 ABC is already actively parked");
    }

    @Test
    void shouldThrowDuplicateReservationExceptionWhenPlateAlreadyReserved() {
        ReservationRequest request = new ReservationRequest("B 5678 ABC");
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now());

        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.count()).thenReturn(2L);
        when(parkingReservationRepository.count()).thenReturn(1L);
        when(parkingSessionRepository.existsByLicensePlate("B 5678 ABC")).thenReturn(false);
        when(parkingReservationRepository.existsByLicensePlate("B 5678 ABC")).thenReturn(true);

        DuplicateReservationException exception = assertThrows(
                DuplicateReservationException.class,
                () -> reservationService.createReservation(request)
        );

        assertThat(exception.getMessage()).contains("B 5678 ABC already has an active reservation");
    }

    @Test
    void shouldGetReservationSuccessfullyWhenFound() {
        LocalDateTime reservedAt = LocalDateTime.now().minusMinutes(15);
        ParkingReservation reservation = new ParkingReservation("B 5678 ABC", reservedAt);
        when(parkingReservationRepository.findByLicensePlate("B 5678 ABC")).thenReturn(Optional.of(reservation));

        ReservationResponse response = reservationService.getReservation("  b 5678 abc  ");

        assertThat(response.licensePlate()).isEqualTo("B 5678 ABC");
        assertThat(response.reservedAt()).isEqualTo(reservedAt);
    }

    @Test
    void shouldThrowReservationNotFoundExceptionWhenNotFound() {
        when(parkingReservationRepository.findByLicensePlate("B 9999 NON")).thenReturn(Optional.empty());

        ReservationNotFoundException exception = assertThrows(
                ReservationNotFoundException.class,
                () -> reservationService.getReservation("B 9999 NON")
        );

        assertThat(exception.getMessage()).contains("No active reservation found for license plate B 9999 NON");
    }

    @Test
    void shouldThrowReservationNotFoundExceptionWhenPlateIsBlankOrNull() {
        ReservationNotFoundException exNull = assertThrows(
                ReservationNotFoundException.class,
                () -> reservationService.getReservation(null)
        );
        assertThat(exNull.getMessage()).isEqualTo("Reservation not found");

        ReservationNotFoundException exBlank = assertThrows(
                ReservationNotFoundException.class,
                () -> reservationService.getReservation("   ")
        );
        assertThat(exBlank.getMessage()).isEqualTo("Reservation not found");
    }
}

package com.parkiraja.service;

import com.parkiraja.dto.CheckInRequest;
import com.parkiraja.dto.VehicleResponse;
import com.parkiraja.entity.ParkingCapacity;
import com.parkiraja.entity.ParkingReservation;
import com.parkiraja.entity.ParkingSession;
import com.parkiraja.entity.VehicleType;
import com.parkiraja.exception.CapacityNotFoundException;
import com.parkiraja.exception.DuplicateVehicleException;
import com.parkiraja.exception.ParkingLotFullException;
import com.parkiraja.exception.VehicleNotFoundException;
import com.parkiraja.repository.ParkingCapacityRepository;
import com.parkiraja.repository.ParkingReservationRepository;
import com.parkiraja.repository.ParkingSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private VehicleService vehicleService;

    @Test
    void shouldCheckInCarSuccessfullyWithNormalizedPlate() {
        CheckInRequest request = new CheckInRequest("  b 1234 xyz  ", 1);
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now());

        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.existsByLicensePlate("B 1234 XYZ")).thenReturn(false);
        when(parkingReservationRepository.findByLicensePlate("B 1234 XYZ")).thenReturn(Optional.empty());
        when(parkingSessionRepository.count()).thenReturn(0L);
        when(parkingReservationRepository.count()).thenReturn(0L);
        when(parkingSessionRepository.save(any(ParkingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VehicleResponse response = vehicleService.checkIn(request);

        assertThat(response.licensePlate()).isEqualTo("B 1234 XYZ");
        assertThat(response.vehicleType()).isEqualTo(1);
        assertThat(response.entryTime()).isNotNull();

        ArgumentCaptor<ParkingSession> captor = ArgumentCaptor.forClass(ParkingSession.class);
        verify(parkingSessionRepository).save(captor.capture());
        ParkingSession saved = captor.getValue();
        assertThat(saved.getLicensePlate()).isEqualTo("B 1234 XYZ");
        assertThat(saved.getVehicleType()).isEqualTo(VehicleType.CAR);
        assertThat(saved.getEntryTime()).isNotNull();
    }

    @Test
    void shouldCheckInMotorcycleSuccessfully() {
        CheckInRequest request = new CheckInRequest("D 5678 ABC", 2);
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now());

        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.existsByLicensePlate("D 5678 ABC")).thenReturn(false);
        when(parkingReservationRepository.findByLicensePlate("D 5678 ABC")).thenReturn(Optional.empty());
        when(parkingSessionRepository.count()).thenReturn(2L);
        when(parkingReservationRepository.count()).thenReturn(0L);
        when(parkingSessionRepository.save(any(ParkingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VehicleResponse response = vehicleService.checkIn(request);

        assertThat(response.licensePlate()).isEqualTo("D 5678 ABC");
        assertThat(response.vehicleType()).isEqualTo(2);
        assertThat(response.entryTime()).isNotNull();
    }

    @Test
    void shouldCheckInSuccessfullyUsingMatchingReservationAndFulfillReservation() {
        CheckInRequest request = new CheckInRequest("B 5678 ABC", 1);
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now());
        ParkingReservation reservation = new ParkingReservation("B 5678 ABC", LocalDateTime.now().minusMinutes(30));

        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.existsByLicensePlate("B 5678 ABC")).thenReturn(false);
        when(parkingReservationRepository.findByLicensePlate("B 5678 ABC")).thenReturn(Optional.of(reservation));
        when(parkingSessionRepository.save(any(ParkingSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VehicleResponse response = vehicleService.checkIn(request);

        assertThat(response.licensePlate()).isEqualTo("B 5678 ABC");
        assertThat(response.vehicleType()).isEqualTo(1);
        assertThat(response.entryTime()).isNotNull();

        verify(parkingReservationRepository).delete(reservation);
        verify(parkingSessionRepository, never()).count();
    }

    @Test
    void shouldThrowCapacityNotFoundExceptionWhenCapacityNotConfigured() {
        CheckInRequest request = new CheckInRequest("B 1234 XYZ", 1);
        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.empty());

        CapacityNotFoundException exception = assertThrows(
                CapacityNotFoundException.class,
                () -> vehicleService.checkIn(request)
        );

        assertThat(exception.getMessage()).isEqualTo("Parking capacity has not been configured");
    }

    @Test
    void shouldThrowParkingLotFullExceptionWhenLotIsFullForUnreservedVehicle() {
        CheckInRequest request = new CheckInRequest("B 1234 XYZ", 1);
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now());

        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.existsByLicensePlate("B 1234 XYZ")).thenReturn(false);
        when(parkingReservationRepository.findByLicensePlate("B 1234 XYZ")).thenReturn(Optional.empty());
        when(parkingSessionRepository.count()).thenReturn(3L);
        when(parkingReservationRepository.count()).thenReturn(2L);

        ParkingLotFullException exception = assertThrows(
                ParkingLotFullException.class,
                () -> vehicleService.checkIn(request)
        );

        assertThat(exception.getMessage()).isEqualTo("Parking lot is full");
    }

    @Test
    void shouldThrowDuplicateVehicleExceptionWhenPlateAlreadyParked() {
        CheckInRequest request = new CheckInRequest("B 1234 XYZ", 1);
        ParkingCapacity capacity = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now());

        when(parkingCapacityRepository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(capacity));
        when(parkingSessionRepository.existsByLicensePlate("B 1234 XYZ")).thenReturn(true);

        DuplicateVehicleException exception = assertThrows(
                DuplicateVehicleException.class,
                () -> vehicleService.checkIn(request)
        );

        assertThat(exception.getMessage()).contains("B 1234 XYZ is already actively parked");
    }

    @Test
    void shouldGetVehicleSuccessfullyWhenFound() {
        LocalDateTime entryTime = LocalDateTime.now().minusHours(1);
        ParkingSession session = new ParkingSession("B 1234 XYZ", VehicleType.CAR, entryTime);
        when(parkingSessionRepository.findByLicensePlate("B 1234 XYZ")).thenReturn(Optional.of(session));

        VehicleResponse response = vehicleService.getVehicle("  b 1234 xyz  ");

        assertThat(response.licensePlate()).isEqualTo("B 1234 XYZ");
        assertThat(response.vehicleType()).isEqualTo(1);
        assertThat(response.entryTime()).isEqualTo(entryTime);
    }

    @Test
    void shouldThrowVehicleNotFoundExceptionWhenPlateNotFound() {
        when(parkingSessionRepository.findByLicensePlate("B 9999 NON")).thenReturn(Optional.empty());

        VehicleNotFoundException exception = assertThrows(
                VehicleNotFoundException.class,
                () -> vehicleService.getVehicle("B 9999 NON")
        );

        assertThat(exception.getMessage()).contains("B 9999 NON is not actively parked");
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
}

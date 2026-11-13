package com.parkiraja.service;

import com.parkiraja.dto.CapacityRequest;
import com.parkiraja.dto.CapacityResponse;
import com.parkiraja.entity.ParkingCapacity;
import com.parkiraja.exception.CapacityNotFoundException;
import com.parkiraja.exception.InvalidCapacityException;
import com.parkiraja.repository.ParkingCapacityRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParkingCapacityServiceTest {

    @Mock
    private ParkingCapacityRepository repository;

    @Mock
    private ParkingSessionRepository parkingSessionRepository;

    @InjectMocks
    private ParkingCapacityService service;

    @Test
    void shouldCreateCapacityWhenNotExists() {
        CapacityRequest request = new CapacityRequest(50);
        when(repository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.empty());
        when(parkingSessionRepository.count()).thenReturn(0L);
        when(repository.save(any(ParkingCapacity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CapacityResponse response = service.updateCapacity(request);

        assertThat(response.totalCapacity()).isEqualTo(50);
        ArgumentCaptor<ParkingCapacity> captor = ArgumentCaptor.forClass(ParkingCapacity.class);
        verify(repository).save(captor.capture());
        ParkingCapacity saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(ParkingCapacity.SINGLETON_ID);
        assertThat(saved.getTotalCapacity()).isEqualTo(50);
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldUpdateCapacityWhenAlreadyExists() {
        ParkingCapacity existing = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 20, LocalDateTime.now().minusDays(1));
        CapacityRequest request = new CapacityRequest(50);
        when(repository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(existing));
        when(parkingSessionRepository.count()).thenReturn(10L);
        when(repository.save(existing)).thenReturn(existing);

        CapacityResponse response = service.updateCapacity(request);

        assertThat(response.totalCapacity()).isEqualTo(50);
        assertThat(existing.getTotalCapacity()).isEqualTo(50);
        verify(repository).save(existing);
    }

    @Test
    void shouldThrowInvalidCapacityExceptionWhenNewCapacityLessThanActiveVehicles() {
        CapacityRequest request = new CapacityRequest(5);
        when(repository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID))
                .thenReturn(Optional.of(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 20, LocalDateTime.now())));
        when(parkingSessionRepository.count()).thenReturn(10L);

        InvalidCapacityException exception = assertThrows(
                InvalidCapacityException.class,
                () -> service.updateCapacity(request)
        );

        assertThat(exception.getMessage()).contains("Total capacity cannot be less than current active vehicle count: 10");
    }

    @Test
    void shouldAcquireLockBeforeCountingActiveVehiclesAndPersistWithinTransaction() {
        ParkingCapacity existing = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 20, LocalDateTime.now().minusDays(1));
        CapacityRequest request = new CapacityRequest(30);
        when(repository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(existing));
        when(parkingSessionRepository.count()).thenReturn(5L);
        when(repository.save(existing)).thenReturn(existing);

        service.updateCapacity(request);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(repository, parkingSessionRepository);
        inOrder.verify(repository).findByIdForUpdate(ParkingCapacity.SINGLETON_ID);
        inOrder.verify(parkingSessionRepository).count();
        inOrder.verify(repository).save(existing);
    }

    @Test
    void shouldReturnCapacityWhenConfigured() {
        ParkingCapacity existing = new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 50, LocalDateTime.now());
        when(repository.findById(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(existing));

        CapacityResponse response = service.getCapacity();

        assertThat(response.totalCapacity()).isEqualTo(50);
        verify(repository).findById(ParkingCapacity.SINGLETON_ID);
    }

    @Test
    void shouldThrowCapacityNotFoundExceptionWhenNotConfigured() {
        when(repository.findById(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.empty());

        CapacityNotFoundException exception = assertThrows(
                CapacityNotFoundException.class,
                () -> service.getCapacity()
        );

        assertThat(exception.getMessage()).isEqualTo("Parking capacity has not been configured");
        verify(repository).findById(ParkingCapacity.SINGLETON_ID);
    }

    @Test
    void shouldReturnActiveVehicleCount() {
        when(parkingSessionRepository.count()).thenReturn(7L);

        long count = service.getActiveVehicleCount();

        assertThat(count).isEqualTo(7L);
        verify(parkingSessionRepository).count();
    }
}

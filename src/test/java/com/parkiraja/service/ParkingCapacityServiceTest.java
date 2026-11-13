package com.parkiraja.service;

import com.parkiraja.dto.CapacityRequest;
import com.parkiraja.dto.CapacityResponse;
import com.parkiraja.entity.ParkingCapacity;
import com.parkiraja.exception.CapacityNotFoundException;
import com.parkiraja.repository.ParkingCapacityRepository;
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

    @InjectMocks
    private ParkingCapacityService service;

    @Test
    void shouldCreateCapacityWhenNotExists() {
        CapacityRequest request = new CapacityRequest(50);
        when(repository.findById(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.empty());
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
        when(repository.findById(ParkingCapacity.SINGLETON_ID)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        CapacityResponse response = service.updateCapacity(request);

        assertThat(response.totalCapacity()).isEqualTo(50);
        assertThat(existing.getTotalCapacity()).isEqualTo(50);
        verify(repository).save(existing);
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
}

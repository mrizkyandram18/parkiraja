package com.parkiraja.service;

import com.parkiraja.dto.CapacityRequest;
import com.parkiraja.dto.CapacityResponse;
import com.parkiraja.entity.ParkingCapacity;
import com.parkiraja.exception.CapacityNotFoundException;
import com.parkiraja.exception.InvalidCapacityException;
import com.parkiraja.repository.ParkingCapacityRepository;
import com.parkiraja.repository.ParkingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ParkingCapacityService {

    private final ParkingCapacityRepository repository;
    private final ParkingSessionRepository parkingSessionRepository;

    public ParkingCapacityService(ParkingCapacityRepository repository, ParkingSessionRepository parkingSessionRepository) {
        this.repository = repository;
        this.parkingSessionRepository = parkingSessionRepository;
    }

    @Transactional
    public CapacityResponse updateCapacity(CapacityRequest request) {
        ParkingCapacity capacity = repository.findByIdForUpdate(ParkingCapacity.SINGLETON_ID)
                .orElseGet(() -> new ParkingCapacity(
                        ParkingCapacity.SINGLETON_ID,
                        request.totalCapacity(),
                        LocalDateTime.now()
                ));

        long activeVehicleCount = parkingSessionRepository.count();
        if (request.totalCapacity() < activeVehicleCount) {
            throw new InvalidCapacityException(
                    "Total capacity cannot be less than current active vehicle count: " + activeVehicleCount
            );
        }

        capacity.setTotalCapacity(request.totalCapacity());
        capacity.setUpdatedAt(LocalDateTime.now());

        ParkingCapacity saved = repository.save(capacity);
        return new CapacityResponse(saved.getTotalCapacity());
    }

    @Transactional(readOnly = true)
    public CapacityResponse getCapacity() {
        return repository.findById(ParkingCapacity.SINGLETON_ID)
                .map(capacity -> new CapacityResponse(capacity.getTotalCapacity()))
                .orElseThrow(() -> new CapacityNotFoundException("Parking capacity has not been configured"));
    }

    @Transactional(readOnly = true)
    public long getActiveVehicleCount() {
        return parkingSessionRepository.count();
    }
}

package com.parkiraja.service;

import com.parkiraja.dto.CapacityRequest;
import com.parkiraja.dto.CapacityResponse;
import com.parkiraja.entity.ParkingCapacity;
import com.parkiraja.exception.CapacityNotFoundException;
import com.parkiraja.repository.ParkingCapacityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ParkingCapacityService {

    private final ParkingCapacityRepository repository;

    public ParkingCapacityService(ParkingCapacityRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CapacityResponse updateCapacity(CapacityRequest request) {
        ParkingCapacity capacity = repository.findById(ParkingCapacity.SINGLETON_ID)
                .map(existing -> {
                    existing.setTotalCapacity(request.totalCapacity());
                    existing.setUpdatedAt(LocalDateTime.now());
                    return existing;
                })
                .orElseGet(() -> new ParkingCapacity(
                        ParkingCapacity.SINGLETON_ID,
                        request.totalCapacity(),
                        LocalDateTime.now()
                ));

        ParkingCapacity saved = repository.save(capacity);
        return new CapacityResponse(saved.getTotalCapacity());
    }

    @Transactional(readOnly = true)
    public CapacityResponse getCapacity() {
        return repository.findById(ParkingCapacity.SINGLETON_ID)
                .map(capacity -> new CapacityResponse(capacity.getTotalCapacity()))
                .orElseThrow(() -> new CapacityNotFoundException("Parking capacity has not been configured"));
    }
}

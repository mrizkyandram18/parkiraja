package com.parkiraja.controller;

import com.parkiraja.dto.CapacityRequest;
import com.parkiraja.dto.CapacityResponse;
import com.parkiraja.service.ParkingCapacityService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parking/capacity")
public class ParkingCapacityController {

    private final ParkingCapacityService capacityService;

    public ParkingCapacityController(ParkingCapacityService capacityService) {
        this.capacityService = capacityService;
    }

    @PutMapping
    public ResponseEntity<CapacityResponse> updateCapacity(@Valid @RequestBody CapacityRequest request) {
        CapacityResponse response = capacityService.updateCapacity(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<CapacityResponse> getCapacity() {
        CapacityResponse response = capacityService.getCapacity();
        return ResponseEntity.ok(response);
    }
}

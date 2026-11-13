package com.parkiraja.controller;

import com.parkiraja.dto.CheckInRequest;
import com.parkiraja.dto.CheckOutRequest;
import com.parkiraja.dto.CheckOutResponse;
import com.parkiraja.dto.VehicleResponse;
import com.parkiraja.service.VehicleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/parking")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping("/check-in")
    public ResponseEntity<VehicleResponse> checkIn(@Valid @RequestBody CheckInRequest request) {
        VehicleResponse response = vehicleService.checkIn(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/check-out")
    public ResponseEntity<CheckOutResponse> checkOut(@Valid @RequestBody CheckOutRequest request) {
        CheckOutResponse response = vehicleService.checkOut(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/vehicles/{licensePlate}")
    public ResponseEntity<VehicleResponse> getVehicle(@PathVariable String licensePlate) {
        VehicleResponse response = vehicleService.getVehicle(licensePlate);
        return ResponseEntity.ok(response);
    }
}

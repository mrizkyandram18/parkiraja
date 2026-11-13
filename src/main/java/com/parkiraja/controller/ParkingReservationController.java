package com.parkiraja.controller;

import com.parkiraja.dto.ReservationRequest;
import com.parkiraja.dto.ReservationResponse;
import com.parkiraja.service.ReservationService;
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
@RequestMapping("/api/v1/parking/reservations")
public class ParkingReservationController {

    private final ReservationService reservationService;

    public ParkingReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(@Valid @RequestBody ReservationRequest request) {
        ReservationResponse response = reservationService.createReservation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{licensePlate}")
    public ResponseEntity<ReservationResponse> getReservation(@PathVariable String licensePlate) {
        ReservationResponse response = reservationService.getReservation(licensePlate);
        return ResponseEntity.ok(response);
    }
}

package com.parkiraja.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleExceptionsTest {

    @Test
    void shouldCreateExceptionsWithMessage() {
        VehicleNotFoundException ex1 = new VehicleNotFoundException("Vehicle not found");
        assertThat(ex1.getMessage()).isEqualTo("Vehicle not found");

        DuplicateVehicleException ex2 = new DuplicateVehicleException("Duplicate vehicle");
        assertThat(ex2.getMessage()).isEqualTo("Duplicate vehicle");

        ParkingLotFullException ex3 = new ParkingLotFullException("Lot is full");
        assertThat(ex3.getMessage()).isEqualTo("Lot is full");

        InvalidCapacityException ex4 = new InvalidCapacityException("Invalid capacity");
        assertThat(ex4.getMessage()).isEqualTo("Invalid capacity");
    }
}

package com.parkiraja.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class VehicleResponseTest {

    @Test
    void shouldCreateRecord() {
        LocalDateTime now = LocalDateTime.now();
        VehicleResponse response = new VehicleResponse("B 1234 XYZ", 1, now);
        assertThat(response.licensePlate()).isEqualTo("B 1234 XYZ");
        assertThat(response.vehicleType()).isEqualTo(1);
        assertThat(response.entryTime()).isEqualTo(now);
    }
}

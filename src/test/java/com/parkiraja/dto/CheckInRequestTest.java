package com.parkiraja.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckInRequestTest {

    @Test
    void shouldCreateRecord() {
        CheckInRequest request = new CheckInRequest("B 1234 XYZ", 1);
        assertThat(request.licensePlate()).isEqualTo("B 1234 XYZ");
        assertThat(request.vehicleType()).isEqualTo(1);
    }
}

package com.parkiraja.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationRequestTest {

    @Test
    void shouldCreateRecord() {
        ReservationRequest request = new ReservationRequest("B 5678 ABC");
        assertThat(request.licensePlate()).isEqualTo("B 5678 ABC");
    }
}

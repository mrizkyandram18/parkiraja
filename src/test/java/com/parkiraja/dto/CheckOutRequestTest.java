package com.parkiraja.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckOutRequestTest {

    @Test
    void shouldCreateRecord() {
        CheckOutRequest request = new CheckOutRequest("B 1234 XYZ");
        assertThat(request.licensePlate()).isEqualTo("B 1234 XYZ");
    }
}

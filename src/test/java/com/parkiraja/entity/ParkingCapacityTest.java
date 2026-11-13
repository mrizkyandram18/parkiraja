package com.parkiraja.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ParkingCapacityTest {

    @Test
    void shouldTestEntityGettersAndSetters() {
        LocalDateTime now = LocalDateTime.now();
        ParkingCapacity entity = new ParkingCapacity();

        entity.setId(1L);
        entity.setTotalCapacity(50);
        entity.setUpdatedAt(now);

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getTotalCapacity()).isEqualTo(50);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void shouldTestParameterizedConstructor() {
        LocalDateTime now = LocalDateTime.now();
        ParkingCapacity entity = new ParkingCapacity(1L, 100, now);

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getTotalCapacity()).isEqualTo(100);
        assertThat(entity.getUpdatedAt()).isEqualTo(now);
    }
}

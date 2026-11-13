package com.parkiraja.dto;

import java.time.LocalDateTime;

public record ReservationResponse(
        String licensePlate,
        LocalDateTime reservedAt
) {
}

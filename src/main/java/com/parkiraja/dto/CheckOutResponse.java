package com.parkiraja.dto;

import java.time.LocalDateTime;

public record CheckOutResponse(
        String licensePlate,
        int vehicleType,
        LocalDateTime entryTime,
        LocalDateTime exitTime,
        long durationHours,
        long hourlyRate,
        long totalFee
) {
}

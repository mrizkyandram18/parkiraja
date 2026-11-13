package com.parkiraja.dto;

import java.time.LocalDateTime;

public record VehicleResponse(
        String licensePlate,
        int vehicleType,
        LocalDateTime entryTime
) {
}

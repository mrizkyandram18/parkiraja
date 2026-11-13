package com.parkiraja.entity;

public enum VehicleType {
    CAR(1, 5000L),
    MOTORCYCLE(2, 3000L);

    private final int code;
    private final long hourlyRate;

    VehicleType(int code, long hourlyRate) {
        this.code = code;
        this.hourlyRate = hourlyRate;
    }

    public int getCode() {
        return code;
    }

    public long getHourlyRate() {
        return hourlyRate;
    }

    public static VehicleType fromCode(int code) {
        for (VehicleType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid vehicle type code: " + code);
    }
}

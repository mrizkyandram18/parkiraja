package com.parkiraja.entity;

public enum VehicleType {
    CAR(1),
    MOTORCYCLE(2);

    private final int code;

    VehicleType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
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

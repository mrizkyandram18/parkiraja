package com.parkiraja.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "parking_capacity")
public class ParkingCapacity {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "total_capacity", nullable = false)
    private int totalCapacity;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ParkingCapacity() {
    }

    public ParkingCapacity(Long id, int totalCapacity, LocalDateTime updatedAt) {
        this.id = id;
        this.totalCapacity = totalCapacity;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public void setTotalCapacity(int totalCapacity) {
        this.totalCapacity = totalCapacity;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

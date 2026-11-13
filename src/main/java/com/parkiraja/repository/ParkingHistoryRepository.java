package com.parkiraja.repository;

import com.parkiraja.entity.ParkingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ParkingHistoryRepository extends JpaRepository<ParkingHistory, Long> {

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    List<ParkingHistory> findAllByOrderByIdAsc();
}

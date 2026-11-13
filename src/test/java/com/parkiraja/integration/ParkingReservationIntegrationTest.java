package com.parkiraja.integration;

import com.parkiraja.entity.ParkingCapacity;
import com.parkiraja.entity.ParkingReservation;
import com.parkiraja.entity.ParkingSession;
import com.parkiraja.entity.VehicleType;
import com.parkiraja.repository.ParkingCapacityRepository;
import com.parkiraja.repository.ParkingReservationRepository;
import com.parkiraja.repository.ParkingSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ParkingReservationIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ParkingCapacityRepository capacityRepository;

    @Autowired
    private ParkingSessionRepository sessionRepository;

    @Autowired
    private ParkingReservationRepository reservationRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        reservationRepository.deleteAll();
        sessionRepository.deleteAll();
        capacityRepository.deleteAll();
    }

    @Test
    void shouldCreateReservationSuccessfullyAndPersistInDatabase() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/parking/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 5678 ABC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licensePlate").value("B 5678 ABC"))
                .andExpect(jsonPath("$.reservedAt").isNotEmpty());

        Optional<ParkingReservation> saved = reservationRepository.findByLicensePlate("B 5678 ABC");
        assertThat(saved).isPresent();
        assertThat(saved.get().getLicensePlate()).isEqualTo("B 5678 ABC");
        assertThat(saved.get().getReservedAt()).isNotNull();
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldNormalizeLicensePlateOnReservation() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/parking/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"  b 5678 abc  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licensePlate").value("B 5678 ABC"));

        Optional<ParkingReservation> saved = reservationRepository.findByLicensePlate("B 5678 ABC");
        assertThat(saved).isPresent();
        assertThat(saved.get().getLicensePlate()).isEqualTo("B 5678 ABC");
    }

    @Test
    void shouldRejectBlankLicensePlate() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/parking/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("License plate must not be blank"));

        assertThat(reservationRepository.count()).isEqualTo(0);
    }

    @Test
    void shouldRejectReservationBeforeCapacityConfiguration() throws Exception {
        mockMvc.perform(post("/api/v1/parking/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 5678 ABC\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Parking capacity has not been configured"));

        assertThat(reservationRepository.count()).isEqualTo(0);
    }

    @Test
    void shouldRejectReservationWhenParkingLotIsFull() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 2, LocalDateTime.now()));

        // 1 parked vehicle, 1 reservation -> total allocated = 2
        sessionRepository.save(new ParkingSession("B 1001 AA", VehicleType.CAR, LocalDateTime.now()));
        reservationRepository.save(new ParkingReservation("B 1002 BB", LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/parking/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1003 CC\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Parking lot is full"));

        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectDuplicateActiveReservation() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/parking/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 5678 ABC\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/parking/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"  b 5678 abc  \"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Vehicle with license plate B 5678 ABC already has an active reservation"));

        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectReservationForAlreadyParkedVehicle() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));

        sessionRepository.save(new ParkingSession("B 5678 ABC", VehicleType.CAR, LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/parking/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"  b 5678 abc  \"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Vehicle with license plate B 5678 ABC is already actively parked"));

        assertThat(reservationRepository.count()).isEqualTo(0);
    }

    @Test
    void shouldLookupActiveReservationSuccessfullyWithNormalizedPlate() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));

        reservationRepository.save(new ParkingReservation("B 5678 ABC", LocalDateTime.now()));

        mockMvc.perform(get("/api/v1/parking/reservations/  b 5678 abc  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licensePlate").value("B 5678 ABC"))
                .andExpect(jsonPath("$.reservedAt").isNotEmpty());
    }

    @Test
    void shouldReturn404WhenLookingUpUnknownReservation() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));

        mockMvc.perform(get("/api/v1/parking/reservations/B 9999 NON"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No active reservation found for license plate B 9999 NON"));
    }

    @Test
    void shouldPreventRegularVehicleFromConsumingReservedSlots() throws Exception {
        // Capacity = 2: 1 parked, 1 reserved -> 0 available slots for unreserved
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 2, LocalDateTime.now()));
        sessionRepository.save(new ParkingSession("B 1111 AA", VehicleType.CAR, LocalDateTime.now()));
        reservationRepository.save(new ParkingReservation("B 2222 BB", LocalDateTime.now()));

        // Unreserved vehicle tries to check in -> rejected as full
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 3333 CC\", \"vehicleType\": 1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Parking lot is full"));

        assertThat(sessionRepository.count()).isEqualTo(1);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldAllowMatchingReservedVehicleToCheckInEvenWhenRegularSlotsZero() throws Exception {
        // Capacity = 2: 1 parked, 1 reserved -> 0 unreserved slots
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 2, LocalDateTime.now()));
        sessionRepository.save(new ParkingSession("B 1111 AA", VehicleType.CAR, LocalDateTime.now()));
        reservationRepository.save(new ParkingReservation("B 2222 BB", LocalDateTime.now()));

        // Matching reserved vehicle checks in -> succeeds!
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"b 2222 bb\", \"vehicleType\": 1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licensePlate").value("B 2222 BB"))
                .andExpect(jsonPath("$.vehicleType").value(1))
                .andExpect(jsonPath("$.entryTime").isNotEmpty());

        // Reservation fulfilled and removed; session created; capacity allocation unchanged (2)
        assertThat(reservationRepository.count()).isEqualTo(0);
        assertThat(sessionRepository.count()).isEqualTo(2);
        assertThat(sessionRepository.existsByLicensePlate("B 2222 BB")).isTrue();
    }

    @Test
    void shouldConsumeReservationExactlyOnce() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 2, LocalDateTime.now()));
        reservationRepository.save(new ParkingReservation("B 2222 BB", LocalDateTime.now()));

        // 1. Initial check-in consumes reservation
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 2222 BB\", \"vehicleType\": 1}"))
                .andExpect(status().isCreated());

        assertThat(reservationRepository.count()).isEqualTo(0);
        assertThat(sessionRepository.count()).isEqualTo(1);

        // 2. Duplicate check-in while parked is rejected as already parked
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 2222 BB\", \"vehicleType\": 1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Vehicle with license plate B 2222 BB is already actively parked"));

        // 3. Vehicle checks out (session deleted), another vehicle parks filling the lot (capacity 2)
        sessionRepository.deleteAll();
        sessionRepository.save(new ParkingSession("B 1001 XX", VehicleType.CAR, LocalDateTime.now()));
        sessionRepository.save(new ParkingSession("B 1002 YY", VehicleType.CAR, LocalDateTime.now()));
        assertThat(sessionRepository.count()).isEqualTo(2);

        // 4. Vehicle B 2222 BB cannot check in without new reservation when lot is full
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 2222 BB\", \"vehicleType\": 1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Parking lot is full"));
    }

    @Test
    void shouldRejectCapacityReductionBelowOccupiedPlusReservedCount() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));
        sessionRepository.save(new ParkingSession("B 1111 AA", VehicleType.CAR, LocalDateTime.now()));
        sessionRepository.save(new ParkingSession("B 2222 BB", VehicleType.CAR, LocalDateTime.now()));
        reservationRepository.save(new ParkingReservation("B 3333 CC", LocalDateTime.now()));
        reservationRepository.save(new ParkingReservation("B 4444 DD", LocalDateTime.now()));

        // Occupied = 2 active vehicles + 2 active reservations = 4
        // Reduction to 3 (< 4) must be rejected
        mockMvc.perform(put("/api/v1/parking/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalCapacity\": 3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Total capacity cannot be less than current active vehicle and reservation count: 4"));

        // Reduction to 4 (>= 4) succeeds
        mockMvc.perform(put("/api/v1/parking/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalCapacity\": 4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCapacity").value(4));

        Optional<ParkingCapacity> capacity = capacityRepository.findById(ParkingCapacity.SINGLETON_ID);
        assertThat(capacity).isPresent();
        assertThat(capacity.get().getTotalCapacity()).isEqualTo(4);
    }

    @Test
    void shouldAllowSamePlateToReserveAgainAfterFulfillmentAndCheckout() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));

        // 1. Reserve
        mockMvc.perform(post("/api/v1/parking/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 7777 XX\"}"))
                .andExpect(status().isCreated());
        assertThat(reservationRepository.count()).isEqualTo(1);

        // 2. Check in (fulfills reservation)
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 7777 XX\", \"vehicleType\": 1}"))
                .andExpect(status().isCreated());
        assertThat(reservationRepository.count()).isEqualTo(0);
        assertThat(sessionRepository.count()).isEqualTo(1);

        // 3. Check out (delete session)
        sessionRepository.deleteAll();
        assertThat(sessionRepository.count()).isEqualTo(0);

        // 4. Reserve again with same plate -> succeeds
        mockMvc.perform(post("/api/v1/parking/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 7777 XX\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licensePlate").value("B 7777 XX"));
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldHandleSimultaneousReservationsCompetingForFinalSlot() throws Exception {
        // Capacity = 1
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 1, LocalDateTime.now()));

        int threadCount = 5;
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            tasks.add(() -> {
                barrier.await();
                MvcResult result = mockMvc.perform(post("/api/v1/parking/reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"licensePlate\": \"B " + (5000 + index) + " RES\"}"))
                        .andReturn();
                return result.getResponse().getStatus();
            });
        }

        List<Future<Integer>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        int successCount = 0;
        int conflictCount = 0;
        for (Future<Integer> future : futures) {
            int status = future.get();
            if (status == 201) {
                successCount++;
            } else if (status == 409) {
                conflictCount++;
            }
        }

        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(threadCount - 1);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldSynchronizeConcurrentReservationAndOrdinaryVehicleCheckIn() throws Exception {
        // Capacity = 1, exactly 1 available slot
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 1, LocalDateTime.now()));

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Integer> reservationTask = () -> {
            barrier.await();
            MvcResult result = mockMvc.perform(post("/api/v1/parking/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"licensePlate\": \"B 1000 RES\"}"))
                    .andReturn();
            return result.getResponse().getStatus();
        };

        Callable<Integer> checkInTask = () -> {
            barrier.await();
            MvcResult result = mockMvc.perform(post("/api/v1/parking/check-in")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"licensePlate\": \"B 2000 REG\", \"vehicleType\": 1}"))
                    .andReturn();
            return result.getResponse().getStatus();
        };

        Future<Integer> resFuture = executor.submit(reservationTask);
        Future<Integer> checkInFuture = executor.submit(checkInTask);
        executor.shutdown();

        int resStatus = resFuture.get();
        int checkInStatus = checkInFuture.get();

        // Exactly one must succeed with 201, the other must fail with 409
        assertThat((resStatus == 201 && checkInStatus == 409) || (resStatus == 409 && checkInStatus == 201)).isTrue();

        // DB Invariant: total occupied cannot exceed capacity 1
        long totalOccupied = sessionRepository.count() + reservationRepository.count();
        assertThat(totalOccupied).isEqualTo(1);
        if (resStatus == 201) {
            assertThat(reservationRepository.existsByLicensePlate("B 1000 RES")).isTrue();
            assertThat(sessionRepository.count()).isEqualTo(0);
        } else {
            assertThat(sessionRepository.existsByLicensePlate("B 2000 REG")).isTrue();
            assertThat(reservationRepository.count()).isEqualTo(0);
        }
    }

    @Test
    void shouldSynchronizeConcurrentReservationFulfillmentAndCapacityUpdate() throws Exception {
        // Capacity = 5: 4 active vehicles, 1 reservation for B 5555 RES -> lot is fully allocated (5)
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));
        for (int i = 1; i <= 4; i++) {
            sessionRepository.save(new ParkingSession("B " + (4000 + i) + " ACT", VehicleType.CAR, LocalDateTime.now()));
        }
        reservationRepository.save(new ParkingReservation("B 5555 RES", LocalDateTime.now()));
        assertThat(sessionRepository.count() + reservationRepository.count()).isEqualTo(5);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread A: fulfillment (reserved check-in). Keeps total allocated at 5 (sessions: 4->5, res: 1->0)
        Callable<Integer> fulfillmentTask = () -> {
            barrier.await();
            MvcResult result = mockMvc.perform(post("/api/v1/parking/check-in")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"licensePlate\": \"B 5555 RES\", \"vehicleType\": 1}"))
                    .andReturn();
            return result.getResponse().getStatus();
        };

        // Thread B: attempt capacity reduction to 4. Since total occupied is 5 throughout, this MUST be rejected with 400
        Callable<Integer> capacityUpdateTask = () -> {
            barrier.await();
            MvcResult result = mockMvc.perform(put("/api/v1/parking/capacity")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"totalCapacity\": 4}"))
                    .andReturn();
            return result.getResponse().getStatus();
        };

        Future<Integer> fulfillmentFuture = executor.submit(fulfillmentTask);
        Future<Integer> capacityFuture = executor.submit(capacityUpdateTask);
        executor.shutdown();

        int fulfillmentStatus = fulfillmentFuture.get();
        int capacityStatus = capacityFuture.get();

        assertThat(fulfillmentStatus).isEqualTo(201);
        assertThat(capacityStatus).isEqualTo(400);

        // Verify final DB invariants
        Optional<ParkingCapacity> capacity = capacityRepository.findById(ParkingCapacity.SINGLETON_ID);
        assertThat(capacity).isPresent();
        assertThat(capacity.get().getTotalCapacity()).isEqualTo(5);
        assertThat(sessionRepository.count()).isEqualTo(5);
        assertThat(reservationRepository.count()).isEqualTo(0);
        assertThat(sessionRepository.existsByLicensePlate("B 5555 RES")).isTrue();
    }

    @Test
    void shouldKeepDatabaseStateConsistentAfterRejectedOperations() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 2, LocalDateTime.now()));
        sessionRepository.save(new ParkingSession("B 1000 ACT", VehicleType.CAR, LocalDateTime.now()));

        // Rejected: blank plate (400)
        mockMvc.perform(post("/api/v1/parking/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"licensePlate\": \"\"}")).andExpect(status().isBadRequest());

        // Rejected: vehicle already parked (409)
        mockMvc.perform(post("/api/v1/parking/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"licensePlate\": \"B 1000 ACT\"}")).andExpect(status().isConflict());

        // Rejected: lookup unknown (404)
        mockMvc.perform(get("/api/v1/parking/reservations/B 9999 NON")).andExpect(status().isNotFound());

        // Verify DB state is pristine
        assertThat(reservationRepository.count()).isEqualTo(0);
        assertThat(sessionRepository.count()).isEqualTo(1);
        assertThat(sessionRepository.existsByLicensePlate("B 1000 ACT")).isTrue();
    }
}

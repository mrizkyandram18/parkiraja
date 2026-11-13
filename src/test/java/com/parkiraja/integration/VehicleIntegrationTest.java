package com.parkiraja.integration;

import com.parkiraja.entity.ParkingCapacity;
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
class VehicleIntegrationTest {

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
    void shouldRegisterCarAndMotorcycleSuccessfully() throws Exception {
        // Configure capacity
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now()));

        // 1. Car registration
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"  b 1234 xyz  \", \"vehicleType\": 1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licensePlate").value("B 1234 XYZ"))
                .andExpect(jsonPath("$.vehicleType").value(1))
                .andExpect(jsonPath("$.entryTime").isNotEmpty());

        // 2. Motorcycle registration
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"D 5678 ABC\", \"vehicleType\": 2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licensePlate").value("D 5678 ABC"))
                .andExpect(jsonPath("$.vehicleType").value(2))
                .andExpect(jsonPath("$.entryTime").isNotEmpty());

        // Verify in H2 directly
        assertThat(sessionRepository.count()).isEqualTo(2);
        Optional<ParkingSession> car = sessionRepository.findByLicensePlate("B 1234 XYZ");
        assertThat(car).isPresent();
        assertThat(car.get().getVehicleType()).isEqualTo(VehicleType.CAR);
        assertThat(car.get().getEntryTime()).isNotNull();

        Optional<ParkingSession> motorcycle = sessionRepository.findByLicensePlate("D 5678 ABC");
        assertThat(motorcycle).isPresent();
        assertThat(motorcycle.get().getVehicleType()).isEqualTo(VehicleType.MOTORCYCLE);
    }

    @Test
    void shouldRejectRegistrationBeforeCapacityConfiguration() throws Exception {
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1234 XYZ\", \"vehicleType\": 1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Parking capacity has not been configured"));

        assertThat(sessionRepository.count()).isEqualTo(0);
    }

    @Test
    void shouldRejectBlankPlateAndInvalidVehicleType() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now()));

        // Blank plate
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"   \", \"vehicleType\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("License plate must not be blank"));

        // Invalid vehicle type (code 3)
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1234 XYZ\", \"vehicleType\": 3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Invalid vehicle type (code 0)
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1234 XYZ\", \"vehicleType\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Missing vehicle type
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1234 XYZ\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertThat(sessionRepository.count()).isEqualTo(0);
    }

    @Test
    void shouldRejectDuplicateActivePlate() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now()));

        // Initial check-in
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1234 XYZ\", \"vehicleType\": 1}"))
                .andExpect(status().isCreated());

        // Duplicate check-in with same plate (different casing/spacing)
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"  b 1234 xyz  \", \"vehicleType\": 1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Vehicle with license plate B 1234 XYZ is already actively parked"));

        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectWhenParkingLotIsFull() throws Exception {
        // Capacity = 2
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 2, LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1001 A\", \"vehicleType\": 1}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1002 B\", \"vehicleType\": 2}"))
                .andExpect(status().isCreated());

        // 3rd vehicle exceeds capacity
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1003 C\", \"vehicleType\": 1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Parking lot is full"));

        assertThat(sessionRepository.count()).isEqualTo(2);
    }

    @Test
    void shouldLookupActiveVehicleSuccessfullyAndReturn404WhenNotFound() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 7777 WX\", \"vehicleType\": 1}"))
                .andExpect(status().isCreated());

        // Lookup with trimmed / lower case plate
        mockMvc.perform(get("/api/v1/parking/vehicles/b 7777 wx"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licensePlate").value("B 7777 WX"))
                .andExpect(jsonPath("$.vehicleType").value(1))
                .andExpect(jsonPath("$.entryTime").isNotEmpty());

        // Lookup unknown vehicle
        mockMvc.perform(get("/api/v1/parking/vehicles/UNKNOWN-PLATE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Vehicle with license plate UNKNOWN-PLATE is not actively parked"));
    }

    @Test
    void shouldAllowVehicleReRegistrationAfterPreviousSessionCheckedOut() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now()));

        // Session 1
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 8888 ZZ\", \"vehicleType\": 1}"))
                .andExpect(status().isCreated());

        // Simulate checkout (deletion from active parking session table)
        sessionRepository.deleteAll();
        assertThat(sessionRepository.count()).isEqualTo(0);

        // Re-registration of same license plate
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 8888 ZZ\", \"vehicleType\": 1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licensePlate").value("B 8888 ZZ"));

        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRejectCapacityReductionBelowActiveVehicleCount() throws Exception {
        // Initial capacity = 5
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));

        // Register 3 vehicles
        mockMvc.perform(post("/api/v1/parking/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"licensePlate\": \"B 1111 AA\", \"vehicleType\": 1}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/parking/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"licensePlate\": \"B 2222 BB\", \"vehicleType\": 1}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/parking/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"licensePlate\": \"B 3333 CC\", \"vehicleType\": 1}")).andExpect(status().isCreated());

        assertThat(sessionRepository.count()).isEqualTo(3);

        // Valid update: capacity = 4 (>= 3)
        mockMvc.perform(put("/api/v1/parking/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalCapacity\": 4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCapacity").value(4));

        // Invalid update: capacity = 2 (< 3 active vehicles)
        mockMvc.perform(put("/api/v1/parking/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalCapacity\": 2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Total capacity cannot be less than current active vehicle count: 3"));

        // Verify capacity remains 4
        Optional<ParkingCapacity> capacity = capacityRepository.findById(ParkingCapacity.SINGLETON_ID);
        assertThat(capacity).isPresent();
        assertThat(capacity.get().getTotalCapacity()).isEqualTo(4);
    }

    @Test
    void shouldHandleConcurrentCheckInsForLastSlotWithoutOverAllocating() throws Exception {
        // Set capacity to 1
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 1, LocalDateTime.now()));

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            tasks.add(() -> {
                MvcResult result = mockMvc.perform(post("/api/v1/parking/check-in")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"licensePlate\": \"B " + (1000 + index) + " CON\", \"vehicleType\": 1}"))
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
        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldMaintainCapacityInvariantWhenVehicleCheckInAndCapacityReductionRaceAtLimit() throws Exception {
        // Initial capacity = 10, with 9 active vehicles parked
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now()));
        for (int i = 1; i <= 9; i++) {
            sessionRepository.save(new ParkingSession("B " + (1000 + i) + " INIT", VehicleType.CAR, LocalDateTime.now()));
        }
        assertThat(sessionRepository.count()).isEqualTo(9);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Integer> checkInTask = () -> {
            barrier.await();
            MvcResult result = mockMvc.perform(post("/api/v1/parking/check-in")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"licensePlate\": \"B 9999 CON\", \"vehicleType\": 1}"))
                    .andReturn();
            return result.getResponse().getStatus();
        };

        Callable<Integer> capacityTask = () -> {
            barrier.await();
            MvcResult result = mockMvc.perform(put("/api/v1/parking/capacity")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"totalCapacity\": 9}"))
                    .andReturn();
            return result.getResponse().getStatus();
        };

        Future<Integer> checkInFuture = executor.submit(checkInTask);
        Future<Integer> capacityFuture = executor.submit(capacityTask);
        executor.shutdown();

        int checkInStatus = checkInFuture.get();
        int capacityStatus = capacityFuture.get();

        // One must succeed, the other must fail depending on transaction serialization order
        // Case A: check-in wins (201 Created), capacity reduction fails because count is now 10 (400 Bad Request)
        // Case B: capacity reduction wins (200 OK), check-in fails because capacity is now 9 (409 Conflict)
        assertThat((checkInStatus == 201 && capacityStatus == 400)
                || (checkInStatus == 409 && capacityStatus == 200)).isTrue();

        // Verify the final persisted database state: must NEVER have 10 vehicles with capacity 9
        ParkingCapacity persistedCapacity = capacityRepository.findById(ParkingCapacity.SINGLETON_ID).orElseThrow();
        long persistedCount = sessionRepository.count();

        assertThat(persistedCapacity.getTotalCapacity()).isGreaterThanOrEqualTo((int) persistedCount);

        if (checkInStatus == 201) {
            assertThat(persistedCapacity.getTotalCapacity()).isEqualTo(10);
            assertThat(persistedCount).isEqualTo(10);
            assertThat(sessionRepository.existsByLicensePlate("B 9999 CON")).isTrue();
        } else {
            assertThat(persistedCapacity.getTotalCapacity()).isEqualTo(9);
            assertThat(persistedCount).isEqualTo(9);
            assertThat(sessionRepository.existsByLicensePlate("B 9999 CON")).isFalse();
        }
    }

    @Test
    void shouldMaintainCapacityInvariantWhenCapacityUpdateAndMultipleVehicleCheckInsRace() throws Exception {
        // Initial capacity = 10, with 5 active vehicles parked (5 slots available)
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now()));
        for (int i = 1; i <= 5; i++) {
            sessionRepository.save(new ParkingSession("B " + (2000 + i) + " INIT", VehicleType.CAR, LocalDateTime.now()));
        }
        assertThat(sessionRepository.count()).isEqualTo(5);

        // 6 threads: 5 competing to check in vehicles, 1 attempting to reduce capacity to 5
        int checkInThreads = 5;
        int totalThreads = checkInThreads + 1;
        CyclicBarrier barrier = new CyclicBarrier(totalThreads);
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        List<Callable<Integer>> tasks = new ArrayList<>();

        for (int i = 0; i < checkInThreads; i++) {
            final int index = i;
            tasks.add(() -> {
                barrier.await();
                MvcResult result = mockMvc.perform(post("/api/v1/parking/check-in")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"licensePlate\": \"B " + (3000 + index) + " MULTI\", \"vehicleType\": 1}"))
                        .andReturn();
                return result.getResponse().getStatus();
            });
        }

        tasks.add(() -> {
            barrier.await();
            MvcResult result = mockMvc.perform(put("/api/v1/parking/capacity")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"totalCapacity\": 5}"))
                    .andReturn();
            return result.getResponse().getStatus();
        });

        List<Future<Integer>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        // Verify all tasks returned valid HTTP status codes
        for (int i = 0; i < checkInThreads; i++) {
            int status = futures.get(i).get();
            assertThat(status == 201 || status == 409).isTrue();
        }
        int capacityStatus = futures.get(checkInThreads).get();
        assertThat(capacityStatus == 200 || capacityStatus == 400).isTrue();

        // Invariant check on persisted database state:
        // Under no circumstances can active vehicles exceed total capacity
        ParkingCapacity persistedCapacity = capacityRepository.findById(ParkingCapacity.SINGLETON_ID).orElseThrow();
        long persistedCount = sessionRepository.count();

        assertThat(persistedCapacity.getTotalCapacity()).isGreaterThanOrEqualTo((int) persistedCount);

        // If capacity reduction succeeded (totalCapacity = 5), active vehicles must be exactly 5
        if (capacityStatus == 200) {
            assertThat(persistedCapacity.getTotalCapacity()).isEqualTo(5);
            assertThat(persistedCount).isEqualTo(5);
        }
    }
}

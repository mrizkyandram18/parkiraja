package com.parkiraja.integration;

import com.parkiraja.entity.ParkingCapacity;
import com.parkiraja.entity.ParkingReservation;
import com.parkiraja.entity.ParkingSession;
import com.parkiraja.entity.VehicleType;
import com.parkiraja.repository.ParkingCapacityRepository;
import com.parkiraja.repository.ParkingReservationRepository;
import com.parkiraja.repository.ParkingSessionRepository;
import com.parkiraja.service.ParkingHistoryWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class VehicleCheckoutIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ParkingCapacityRepository capacityRepository;

    @Autowired
    private ParkingSessionRepository sessionRepository;

    @Autowired
    private ParkingReservationRepository reservationRepository;

    @Autowired
    private ParkingHistoryWriter historyWriter;

    private MockMvc mockMvc;
    private Path csvPath;

    @BeforeEach
    void setUp() throws IOException {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        reservationRepository.deleteAll();
        sessionRepository.deleteAll();
        capacityRepository.deleteAll();

        csvPath = historyWriter.getCsvFilePath();
        if (Files.exists(csvPath)) {
            Files.deleteIfExists(csvPath);
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        if (csvPath != null && Files.exists(csvPath)) {
            Files.deleteIfExists(csvPath);
        }
    }

    @Test
    void shouldCalculateCarCheckoutFeeWithPartialHourRounding() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now()));

        // Parked 2 hours and 15 minutes ago -> rounded to 3 billable hours
        LocalDateTime entryTime = LocalDateTime.now().minusHours(2).minusMinutes(15);
        sessionRepository.save(new ParkingSession("B 1234 CAR", VehicleType.CAR, entryTime));

        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"  b 1234 car  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licensePlate").value("B 1234 CAR"))
                .andExpect(jsonPath("$.vehicleType").value(1))
                .andExpect(jsonPath("$.durationHours").value(3))
                .andExpect(jsonPath("$.hourlyRate").value(5000))
                .andExpect(jsonPath("$.totalFee").value(15000))
                .andExpect(jsonPath("$.entryTime").isNotEmpty())
                .andExpect(jsonPath("$.exitTime").isNotEmpty());

        assertThat(sessionRepository.existsByLicensePlate("B 1234 CAR")).isFalse();
        assertThat(sessionRepository.count()).isEqualTo(0);

        // Verify CSV file
        assertThat(Files.exists(csvPath)).isTrue();
        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).isEqualTo("license_plate,vehicle_type,entry_time,exit_time,parking_fee");
        assertThat(lines.get(1)).contains("B 1234 CAR,1,");
        assertThat(lines.get(1)).endsWith(",15000");
    }

    @Test
    void shouldCalculateMotorcycleCheckoutFeeWithMinimumOneHourBilling() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now()));

        // Parked 15 minutes ago -> minimum 1 hour (IDR 3,000)
        LocalDateTime entryTime = LocalDateTime.now().minusMinutes(15);
        sessionRepository.save(new ParkingSession("D 5678 MOT", VehicleType.MOTORCYCLE, entryTime));

        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"D 5678 MOT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licensePlate").value("D 5678 MOT"))
                .andExpect(jsonPath("$.vehicleType").value(2))
                .andExpect(jsonPath("$.durationHours").value(1))
                .andExpect(jsonPath("$.hourlyRate").value(3000))
                .andExpect(jsonPath("$.totalFee").value(3000));

        assertThat(sessionRepository.existsByLicensePlate("D 5678 MOT")).isFalse();
    }

    @Test
    void shouldCalculateExactHourBillingForMultipleHours() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now()));

        // Exactly 2 hours ago -> 2 billed hours (CAR: 10,000)
        LocalDateTime entryTime = LocalDateTime.now().minusHours(2);
        sessionRepository.save(new ParkingSession("B 2222 EXT", VehicleType.CAR, entryTime));

        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 2222 EXT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationHours").value(2))
                .andExpect(jsonPath("$.hourlyRate").value(5000))
                .andExpect(jsonPath("$.totalFee").value(10000));
    }

    @Test
    void shouldRejectCheckoutWhenPlateIsBlankOrVehicleNotFound() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now()));

        // Blank plate -> 400 Bad Request
        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("License plate must not be blank"));

        // Unknown vehicle -> 404 Not Found
        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 9999 NON\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Vehicle with license plate B 9999 NON is not actively parked"));
    }

    @Test
    void shouldAllowSamePlateToCheckInAgainAfterCheckout() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));

        // 1. Initial check-in
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 7777 RE\", \"vehicleType\": 1}"))
                .andExpect(status().isCreated());
        assertThat(sessionRepository.count()).isEqualTo(1);

        // 2. Checkout
        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 7777 RE\"}"))
                .andExpect(status().isOk());
        assertThat(sessionRepository.count()).isEqualTo(0);

        // 3. Re-check-in with same plate succeeds
        mockMvc.perform(post("/api/v1/parking/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 7777 RE\", \"vehicleType\": 1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licensePlate").value("B 7777 RE"));
        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldPreventDoubleCheckoutFromProducingSecondInvoice() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));
        sessionRepository.save(new ParkingSession("B 8888 DBL", VehicleType.CAR, LocalDateTime.now().minusHours(1)));

        // 1. First checkout succeeds
        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 8888 DBL\"}"))
                .andExpect(status().isOk());

        // 2. Second checkout attempt fails with 404
        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 8888 DBL\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Exactly one history record written to CSV
        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines).hasSize(2); // Header + 1 record
    }

    @Test
    void shouldKeepActiveReservationsOfOtherVehiclesUntouchedDuringCheckout() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));
        sessionRepository.save(new ParkingSession("B 1111 ACT", VehicleType.CAR, LocalDateTime.now().minusHours(1)));
        reservationRepository.save(new ParkingReservation("B 9999 RES", LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1111 ACT\"}"))
                .andExpect(status().isOk());

        assertThat(sessionRepository.count()).isEqualTo(0);
        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(reservationRepository.existsByLicensePlate("B 9999 RES")).isTrue();
    }

    @Test
    void shouldAppendMultipleTransactionsToCsvWithCorrectFormatAndEscaping() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now()));
        LocalDateTime entry = LocalDateTime.of(2026, 11, 13, 9, 0, 0);

        sessionRepository.save(new ParkingSession("B 1001 AA", VehicleType.CAR, entry));
        sessionRepository.save(new ParkingSession("D 2002 BB", VehicleType.MOTORCYCLE, entry));

        mockMvc.perform(post("/api/v1/parking/check-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"licensePlate\": \"B 1001 AA\"}")).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/parking/check-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"licensePlate\": \"D 2002 BB\"}")).andExpect(status().isOk());

        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo("license_plate,vehicle_type,entry_time,exit_time,parking_fee");
        assertThat(lines.get(1)).startsWith("B 1001 AA,1,2026-11-13T09:00");
        assertThat(lines.get(2)).startsWith("D 2002 BB,2,2026-11-13T09:00");
    }

    @Test
    void shouldHandleTwoSimultaneousCheckoutRequestsForSamePlateWithoutDuplicateHistory() throws Exception {
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));
        sessionRepository.save(new ParkingSession("B 3333 SIM", VehicleType.CAR, LocalDateTime.now().minusHours(1)));

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Integer> checkoutTask = () -> {
            barrier.await();
            MvcResult result = mockMvc.perform(post("/api/v1/parking/check-out")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"licensePlate\": \"B 3333 SIM\"}"))
                    .andReturn();
            return result.getResponse().getStatus();
        };

        Future<Integer> f1 = executor.submit(checkoutTask);
        Future<Integer> f2 = executor.submit(checkoutTask);
        executor.shutdown();

        int s1 = f1.get();
        int s2 = f2.get();

        // Exactly one must succeed (200), the conflicting one must fail with 404
        assertThat((s1 == 200 && s2 == 404) || (s1 == 404 && s2 == 200)).isTrue();

        // Exactly one history record in CSV
        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines).hasSize(2);
        assertThat(sessionRepository.count()).isEqualTo(0);
    }

    @Test
    void shouldSynchronizeConcurrentCheckoutAndCheckInAllocation() throws Exception {
        // Capacity = 1. Lot is full (1 session parked).
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 1, LocalDateTime.now()));
        sessionRepository.save(new ParkingSession("B 1000 OUT", VehicleType.CAR, LocalDateTime.now().minusHours(1)));

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread 1: Check out vehicle
        Callable<Integer> checkoutTask = () -> {
            barrier.await();
            MvcResult result = mockMvc.perform(post("/api/v1/parking/check-out")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"licensePlate\": \"B 1000 OUT\"}"))
                    .andReturn();
            return result.getResponse().getStatus();
        };

        // Thread 2: Check in a new vehicle competing for the slot
        Callable<Integer> checkInTask = () -> {
            barrier.await();
            MvcResult result = mockMvc.perform(post("/api/v1/parking/check-in")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"licensePlate\": \"B 2000 IN\", \"vehicleType\": 1}"))
                    .andReturn();
            return result.getResponse().getStatus();
        };

        Future<Integer> checkoutFuture = executor.submit(checkoutTask);
        Future<Integer> checkInFuture = executor.submit(checkInTask);
        executor.shutdown();

        int checkoutStatus = checkoutFuture.get();
        int checkInStatus = checkInFuture.get();

        assertThat(checkoutStatus).isEqualTo(200);
        // If checkout committed before check-in, check-in gets 201; if check-in was evaluated first, check-in gets 409
        assertThat(checkInStatus == 201 || checkInStatus == 409).isTrue();

        // Database invariant: active sessions must NEVER exceed capacity 1
        long activeCount = sessionRepository.count();
        assertThat(activeCount).isLessThanOrEqualTo(1L);
        if (checkInStatus == 201) {
            assertThat(sessionRepository.existsByLicensePlate("B 2000 IN")).isTrue();
            assertThat(activeCount).isEqualTo(1L);
        } else {
            assertThat(activeCount).isEqualTo(0L);
        }
    }
}

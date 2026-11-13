package com.parkiraja.integration;

import com.parkiraja.dto.CheckOutRequest;
import com.parkiraja.entity.ParkingCapacity;
import com.parkiraja.entity.ParkingHistory;
import com.parkiraja.entity.ParkingReservation;
import com.parkiraja.entity.ParkingSession;
import com.parkiraja.entity.VehicleType;
import com.parkiraja.repository.ParkingCapacityRepository;
import com.parkiraja.repository.ParkingHistoryRepository;
import com.parkiraja.repository.ParkingReservationRepository;
import com.parkiraja.repository.ParkingSessionRepository;
import com.parkiraja.service.ParkingHistoryWriter;
import com.parkiraja.service.VehicleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
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
    private ParkingHistoryRepository historyRepository;

    @Autowired
    private ParkingHistoryWriter historyWriter;

    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MockMvc mockMvc;
    private Path csvPath;

    @BeforeEach
    void setUp() throws IOException {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        historyRepository.deleteAll();
        reservationRepository.deleteAll();
        sessionRepository.deleteAll();
        capacityRepository.deleteAll();

        csvPath = historyWriter.getCsvFilePath();
        if (Files.exists(csvPath)) {
            if (Files.isDirectory(csvPath)) {
                Files.delete(csvPath);
            } else {
                Files.deleteIfExists(csvPath);
            }
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        if (csvPath != null && Files.exists(csvPath)) {
            if (Files.isDirectory(csvPath)) {
                Files.delete(csvPath);
            } else {
                Files.deleteIfExists(csvPath);
            }
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

        // Verify database history
        List<ParkingHistory> historyList = historyRepository.findAll();
        assertThat(historyList).hasSize(1);
        assertThat(historyList.get(0).getLicensePlate()).isEqualTo("B 1234 CAR");
        assertThat(historyList.get(0).getTotalFee()).isEqualTo(15000L);

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
        assertThat(historyRepository.count()).isEqualTo(1);
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
        assertThat(historyRepository.count()).isEqualTo(1);
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
        assertThat(historyRepository.count()).isEqualTo(1);
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

    // --- PHASE 6.1 DATABASE & CSV CONSISTENCY TESTS ---

    @Test
    void shouldNotLeaveInconsistentCsvWhenDatabaseTransactionRollsBack() {
        // Requirement 2 & 5.3: Prove database/CSV dual-write problem is resolved.
        // Active parking session exists in H2
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now()));
        sessionRepository.save(new ParkingSession("B 9999 RB", VehicleType.CAR, LocalDateTime.now().minusHours(1)));

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        assertThrows(RuntimeException.class, () -> {
            template.execute(status -> {
                vehicleService.checkOut(new CheckOutRequest("B 9999 RB"));
                // Simulate an unexpected runtime exception prior to commit
                throw new RuntimeException("Simulated unexpected failure before transaction commit");
            });
        });

        // H2 state: ParkingSession must STILL be active because transaction rolled back
        assertThat(sessionRepository.existsByLicensePlate("B 9999 RB")).isTrue();
        assertThat(historyRepository.findAll()).isEmpty();

        // CSV state: CSV must NOT contain any record for "B 9999 RB"
        if (Files.exists(csvPath)) {
            try {
                String content = Files.readString(csvPath);
                assertThat(content).doesNotContain("B 9999 RB");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void shouldHandlePostCommitCsvExportFailureGracefully(CapturedOutput output) throws Exception {
        // Requirement 4: Simulate CSV synchronization failure AFTER database commit
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now()));
        LocalDateTime entryTime = LocalDateTime.now().minusHours(2);
        sessionRepository.save(new ParkingSession("B 4040 EXP", VehicleType.CAR, entryTime));

        // Create a directory at csvPath to induce an IOException when writing to the CSV file
        if (Files.exists(csvPath)) {
            Files.deleteIfExists(csvPath);
        }
        Files.createDirectories(csvPath);

        // 1. Checkout response remains HTTP 200
        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 4040 EXP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licensePlate").value("B 4040 EXP"))
                .andExpect(jsonPath("$.durationHours").value(2))
                .andExpect(jsonPath("$.hourlyRate").value(5000))
                .andExpect(jsonPath("$.totalFee").value(10000));

        // 2. Active parking session is removed
        assertThat(sessionRepository.existsByLicensePlate("B 4040 EXP")).isFalse();
        assertThat(sessionRepository.count()).isEqualTo(0);

        // 3. Exactly one ParkingHistory record exists in H2
        List<ParkingHistory> historyList = historyRepository.findAll();
        assertThat(historyList).hasSize(1);
        assertThat(historyList.get(0).getLicensePlate()).isEqualTo("B 4040 EXP");
        assertThat(historyList.get(0).getTotalFee()).isEqualTo(10000L);

        // 4. CSV synchronization failure is logged
        assertThat(output.getAll()).contains("Failed to synchronize parking history CSV after transaction commit");

        // 5. No second checkout invoice is generated on retry (returns 404 Not Found)
        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 4040 EXP\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Vehicle with license plate B 4040 EXP is not actively parked"));
        assertThat(historyRepository.count()).isEqualTo(1);

        // 6. A later successful synchronization reconstructs the correct CSV from H2
        Files.delete(csvPath);
        assertThat(Files.exists(csvPath)).isFalse();

        historyWriter.syncFromDatabase();

        assertThat(Files.exists(csvPath)).isTrue();
        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).isEqualTo("license_plate,vehicle_type,entry_time,exit_time,parking_fee");
        assertThat(lines.get(1)).contains("B 4040 EXP,1,");
        assertThat(lines.get(1)).endsWith(",10000");

        // Subsequent checkout operation also maintains correct synchronization
        sessionRepository.save(new ParkingSession("D 5050 NXT", VehicleType.MOTORCYCLE, LocalDateTime.now().minusHours(1)));
        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"D 5050 NXT\"}"))
                .andExpect(status().isOk());

        lines = Files.readAllLines(csvPath);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(1)).contains("B 4040 EXP");
        assertThat(lines.get(2)).contains("D 5050 NXT");
    }

    @Test
    void shouldPersistDurableDatabaseHistoryAndMatchCsvExport() throws Exception {
        // Requirement 5.1 & 5.2: Durable database history matches CSV export
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));
        sessionRepository.save(new ParkingSession("B 1111 MAT", VehicleType.CAR, LocalDateTime.now().minusHours(1)));
        sessionRepository.save(new ParkingSession("D 2222 MAT", VehicleType.MOTORCYCLE, LocalDateTime.now().minusHours(2)));

        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 1111 MAT\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"D 2222 MAT\"}"))
                .andExpect(status().isOk());

        List<ParkingHistory> dbHistories = historyRepository.findAllByOrderByIdAsc();
        assertThat(dbHistories).hasSize(2);

        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines).hasSize(3); // 1 header + 2 records
        assertThat(lines.get(0)).isEqualTo("license_plate,vehicle_type,entry_time,exit_time,parking_fee");
        assertThat(lines.get(1)).startsWith("B 1111 MAT,1,");
        assertThat(lines.get(2)).startsWith("D 2222 MAT,2,");
    }

    @Test
    void shouldRecoverCsvExportFromH2History() throws IOException {
        // Requirement 5.4: CSV export failure can be recovered from H2 history
        ParkingHistory h1 = new ParkingHistory("B 3333 REC", VehicleType.CAR, LocalDateTime.now().minusHours(2), LocalDateTime.now(), 10000L);
        ParkingHistory h2 = new ParkingHistory("D 4444 REC", VehicleType.MOTORCYCLE, LocalDateTime.now().minusHours(1), LocalDateTime.now(), 3000L);
        historyRepository.saveAll(List.of(h1, h2));

        // Delete the CSV to simulate lost / interrupted export
        Files.deleteIfExists(csvPath);
        assertThat(Files.exists(csvPath)).isFalse();

        // Trigger recovery from durable H2 history
        historyWriter.syncFromDatabase();

        assertThat(Files.exists(csvPath)).isTrue();
        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(1)).contains("B 3333 REC,1,");
        assertThat(lines.get(2)).contains("D 4444 REC,2,");
    }

    @Test
    void shouldRecreateCorrectCsvOnStartupSynchronization() throws IOException {
        // Requirement 5.5: Startup synchronization recreates the correct CSV
        ParkingHistory history = new ParkingHistory("B 5555 START", VehicleType.CAR, LocalDateTime.now().minusHours(1), LocalDateTime.now(), 5000L);
        historyRepository.save(history);

        Files.deleteIfExists(csvPath);

        // Fire application ready event
        historyWriter.onApplicationReady();

        assertThat(Files.exists(csvPath)).isTrue();
        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(1)).contains("B 5555 START,1,");
    }

    @Test
    void shouldHandleStartupCsvExportFailureGracefullyAndRecoverOnSubsequentSync(CapturedOutput output) throws IOException {
        // Requirements 3.1 - 3.6: Startup CSV failure handling, durability, and recovery
        ParkingHistory history = new ParkingHistory("B 8888 STARTFAIL", VehicleType.CAR, LocalDateTime.now().minusHours(2), LocalDateTime.now(), 10000L);
        historyRepository.save(history);

        // 1. Simulate CSV write failure during startup by creating a directory at csvPath
        if (Files.exists(csvPath)) {
            if (Files.isDirectory(csvPath)) {
                Files.delete(csvPath);
            } else {
                Files.deleteIfExists(csvPath);
            }
        }
        Files.createDirectories(csvPath);

        // 2 & 3. Startup listener does not propagate a CSV export failure
        historyWriter.onApplicationReady();

        // 2. CSV write failure during startup is logged with SLF4J
        assertThat(output.getAll()).contains("Failed to synchronize parking history CSV during application startup");

        // 4. Database history remains completely intact after failed startup export
        List<ParkingHistory> dbHistories = historyRepository.findAll();
        assertThat(dbHistories).hasSize(1);
        assertThat(dbHistories.get(0).getLicensePlate()).isEqualTo("B 8888 STARTFAIL");
        assertThat(dbHistories.get(0).getTotalFee()).isEqualTo(10000L);

        // 5. A subsequent successful synchronization rebuilds the CSV correctly
        Files.delete(csvPath);
        assertThat(Files.exists(csvPath)).isFalse();

        historyWriter.syncFromDatabase();

        assertThat(Files.exists(csvPath)).isTrue();
        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).isEqualTo("license_plate,vehicle_type,entry_time,exit_time,parking_fee");
        assertThat(lines.get(1)).contains("B 8888 STARTFAIL,1,");
        assertThat(lines.get(1)).endsWith(",10000");

        // 6. Repeated synchronization does not duplicate records
        historyWriter.onApplicationReady();
        lines = Files.readAllLines(csvPath);
        assertThat(lines).hasSize(2);
    }

    @Test
    void shouldNotDuplicateTransactionsOnRepeatedSynchronization() {
        // Requirement 5.6: Repeated synchronization does not duplicate transactions
        ParkingHistory h1 = new ParkingHistory("B 6666 REP", VehicleType.CAR, LocalDateTime.now().minusHours(1), LocalDateTime.now(), 5000L);
        ParkingHistory h2 = new ParkingHistory("D 7777 REP", VehicleType.MOTORCYCLE, LocalDateTime.now().minusHours(1), LocalDateTime.now(), 3000L);
        historyRepository.saveAll(List.of(h1, h2));

        historyWriter.syncFromDatabase();
        historyWriter.syncFromDatabase();
        historyWriter.syncFromDatabase();

        try {
            List<String> lines = Files.readAllLines(csvPath);
            assertThat(lines).hasSize(3); // 1 header + 2 records, NO duplicates
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldPreserveCorrectOrderOfMultipleCheckoutRecords() throws Exception {
        // Requirement 5.7: Multiple checkout records remain correctly ordered
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 10, LocalDateTime.now()));
        sessionRepository.save(new ParkingSession("B 0001 ORD", VehicleType.CAR, LocalDateTime.now().minusHours(3)));
        sessionRepository.save(new ParkingSession("B 0002 ORD", VehicleType.CAR, LocalDateTime.now().minusHours(2)));
        sessionRepository.save(new ParkingSession("B 0003 ORD", VehicleType.CAR, LocalDateTime.now().minusHours(1)));

        mockMvc.perform(post("/api/v1/parking/check-out").contentType(MediaType.APPLICATION_JSON).content("{\"licensePlate\": \"B 0001 ORD\"}")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/parking/check-out").contentType(MediaType.APPLICATION_JSON).content("{\"licensePlate\": \"B 0002 ORD\"}")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/parking/check-out").contentType(MediaType.APPLICATION_JSON).content("{\"licensePlate\": \"B 0003 ORD\"}")).andExpect(status().isOk());

        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines).hasSize(4);
        assertThat(lines.get(1)).contains("B 0001 ORD");
        assertThat(lines.get(2)).contains("B 0002 ORD");
        assertThat(lines.get(3)).contains("B 0003 ORD");
    }

    @Test
    void shouldVerifyNoActiveSessionRemainsAfterSuccessfulCheckout() throws Exception {
        // Requirement 5.9: No active session remains after successful checkout
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));
        sessionRepository.save(new ParkingSession("B 8888 CLR", VehicleType.CAR, LocalDateTime.now().minusHours(1)));

        assertThat(sessionRepository.existsByLicensePlate("B 8888 CLR")).isTrue();

        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"B 8888 CLR\"}"))
                .andExpect(status().isOk());

        assertThat(sessionRepository.existsByLicensePlate("B 8888 CLR")).isFalse();
        assertThat(sessionRepository.count()).isEqualTo(0);
        assertThat(historyRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldPreserveActiveParkingStateWhenCheckoutFails() throws Exception {
        // Requirement 5.10: Failed checkout preserves active parking state
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));
        sessionRepository.save(new ParkingSession("B 9999 STAY", VehicleType.CAR, LocalDateTime.now().minusHours(1)));

        // Call checkout with blank plate (fails 400)
        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"\"}"))
                .andExpect(status().isBadRequest());

        // Vehicle session is still active
        assertThat(sessionRepository.existsByLicensePlate("B 9999 STAY")).isTrue();
        assertThat(sessionRepository.count()).isEqualTo(1);
        assertThat(historyRepository.count()).isEqualTo(0);
    }

    @Test
    void shouldPreventCsvInjectionWhilePreservingOriginalLicensePlateInH2Database() throws Exception {
        // Setup parking capacity and session with formula-like license plate
        capacityRepository.save(new ParkingCapacity(ParkingCapacity.SINGLETON_ID, 5, LocalDateTime.now()));
        String formulaPlate = "=CMD|' /C CALC'!A0";
        sessionRepository.save(new ParkingSession(formulaPlate, VehicleType.CAR, LocalDateTime.now().minusHours(2)));

        // Perform checkout
        mockMvc.perform(post("/api/v1/parking/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licensePlate\": \"" + formulaPlate + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licensePlate").value(formulaPlate))
                .andExpect(jsonPath("$.durationHours").value(2))
                .andExpect(jsonPath("$.totalFee").value(10000));

        // 1. Verify H2 database remains the durable source of truth with original unmodified plate
        List<ParkingHistory> histories = historyRepository.findAll();
        assertThat(histories).hasSize(1);
        assertThat(histories.get(0).getLicensePlate()).isEqualTo(formulaPlate);

        // 2. Verify CSV export is sanitized at the export boundary with single quote prefix
        List<String> lines = Files.readAllLines(csvPath);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).isEqualTo("license_plate,vehicle_type,entry_time,exit_time,parking_fee");
        assertThat(lines.get(1)).startsWith("'" + formulaPlate + ",1,");

        // 3. Verify startup CSV regeneration from H2 preserves formula defense and H2 truth
        historyWriter.syncFromDatabase();
        List<String> resyncedLines = Files.readAllLines(csvPath);
        assertThat(resyncedLines).hasSize(2);
        assertThat(resyncedLines.get(1)).startsWith("'" + formulaPlate + ",1,");
        assertThat(historyRepository.findAll().get(0).getLicensePlate()).isEqualTo(formulaPlate);
    }
}

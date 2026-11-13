package com.parkiraja.integration;

import com.parkiraja.entity.ParkingCapacity;
import com.parkiraja.repository.ParkingCapacityRepository;
import com.parkiraja.repository.ParkingReservationRepository;
import com.parkiraja.repository.ParkingSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ParkingCapacityIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ParkingCapacityRepository repository;

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
        repository.deleteAll();
    }

    @Test
    void shouldReturn404WhenCapacityNotConfigured() throws Exception {
        mockMvc.perform(get("/api/v1/parking/capacity"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Parking capacity has not been configured"));
    }

    @Test
    void shouldConfigureAndRetrieveCapacitySuccessfully() throws Exception {
        // 1. Initial PUT configuration
        mockMvc.perform(put("/api/v1/parking/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalCapacity\": 50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCapacity").value(50));

        // Verify in H2 database directly
        Optional<ParkingCapacity> saved = repository.findById(ParkingCapacity.SINGLETON_ID);
        assertThat(saved).isPresent();
        assertThat(saved.get().getTotalCapacity()).isEqualTo(50);
        assertThat(repository.count()).isEqualTo(1);

        // 2. GET returns the configured capacity
        mockMvc.perform(get("/api/v1/parking/capacity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCapacity").value(50));

        // 3. Update existing capacity
        mockMvc.perform(put("/api/v1/parking/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalCapacity\": 100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCapacity").value(100));

        // Verify database still has exactly one record with updated value
        Optional<ParkingCapacity> updated = repository.findById(ParkingCapacity.SINGLETON_ID);
        assertThat(updated).isPresent();
        assertThat(updated.get().getTotalCapacity()).isEqualTo(100);
        assertThat(repository.count()).isEqualTo(1);

        // 4. GET returns the updated capacity
        mockMvc.perform(get("/api/v1/parking/capacity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCapacity").value(100));
    }

    @Test
    void shouldRejectInvalidCapacityInputs() throws Exception {
        // Zero capacity
        mockMvc.perform(put("/api/v1/parking/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalCapacity\": 0}"))
                .andExpect(status().isBadRequest());

        // Negative capacity
        mockMvc.perform(put("/api/v1/parking/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"totalCapacity\": -5}"))
                .andExpect(status().isBadRequest());

        // Missing field
        mockMvc.perform(put("/api/v1/parking/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // Database remains empty
        assertThat(repository.count()).isEqualTo(0);
    }
}

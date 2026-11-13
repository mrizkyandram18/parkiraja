package com.parkiraja;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ParkirajaApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void mainStartsApplication() {
        try (MockedStatic<SpringApplication> mockedSpring = Mockito.mockStatic(SpringApplication.class)) {
            mockedSpring.when(() -> SpringApplication.run(ParkirajaApplication.class, new String[]{}))
                    .thenReturn(null);

            ParkirajaApplication.main(new String[]{});

            mockedSpring.verify(() -> SpringApplication.run(ParkirajaApplication.class, new String[]{}));
        }
    }
}

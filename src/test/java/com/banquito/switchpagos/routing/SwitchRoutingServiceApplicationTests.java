package com.banquito.switchpagos.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.banquito.switchpagos.routing.controller.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SwitchRoutingServiceApplicationTests {

    @Autowired
    private HealthController healthController;

    @Test
    void contextLoads() {
        assertThat(healthController).isNotNull();
    }
}
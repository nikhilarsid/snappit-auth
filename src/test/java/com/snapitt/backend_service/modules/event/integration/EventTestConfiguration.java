package com.snapitt.backend_service.modules.event.integration;

import com.snapitt.backend_service.modules.event.handler.EventHandler;
import com.snapitt.backend_service.modules.event.model.EventEntity;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@TestConfiguration
@EnableScheduling
public class EventTestConfiguration {

    @Bean
    public EventHandler testEventHandler() {
        return new EventHandler() {
            @Override
            public void handle(EventEntity event) throws Exception {
                
            }
        };
    }
}

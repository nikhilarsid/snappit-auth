package com.snapitt.backend_service.modules.event.handler;

import com.snapitt.backend_service.modules.event.model.EventEntity;

public interface EventHandler {

    void handle(EventEntity event) throws Exception;
}

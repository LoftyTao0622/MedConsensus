package com.zyt.medconsensus.service;

import com.zyt.medconsensus.config.WebSocketUserNames;
import com.zyt.medconsensus.dto.PipelineEvent;
import java.time.LocalDateTime;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Publishes workflow progress without coupling the workflow service to STOMP details. */
@Component
public class PipelineEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public PipelineEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(Long userId, String sessionId, String stage, String message, int progress) {
        messagingTemplate.convertAndSendToUser(
                WebSocketUserNames.doctor(userId),
                "/queue/pipeline",
                new PipelineEvent(userId, sessionId, stage, message, progress, LocalDateTime.now().toString())
        );
    }
}

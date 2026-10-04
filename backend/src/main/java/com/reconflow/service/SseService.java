package com.reconflow.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class SseService implements MessageListener {

    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private static final String CHANNEL_NAME = "job:events";

    @PostConstruct
    public void init() {
        redisMessageListenerContainer.addMessageListener(this, new ChannelTopic(CHANNEL_NAME));
        log.info("Subscribed to Redis channel: {}", CHANNEL_NAME);
    }

    @PreDestroy
    public void destroy() {
        redisMessageListenerContainer.removeMessageListener(this);
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(300000L); // 5 minutes timeout
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError((e) -> emitters.remove(emitter));

        // Send an initial connected event so the frontend knows it's alive
        try {
            emitter.send(SseEmitter.event().name("connected").data("SSE connection established"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String eventData = new String(message.getBody());
        log.info("Received Pub/Sub message on {}: {}", CHANNEL_NAME, eventData);

        // Broadcast to all connected clients
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("job_event").data(eventData));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}

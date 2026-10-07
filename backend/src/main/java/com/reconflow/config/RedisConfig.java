package com.reconflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // We will dynamically add listeners via SseService
        return container;
    }

    @Bean
    public org.springframework.data.redis.core.script.DefaultRedisScript<java.util.List> rateLimitScript() {
        org.springframework.data.redis.core.script.DefaultRedisScript<java.util.List> script = new org.springframework.data.redis.core.script.DefaultRedisScript<>();
        try {
            org.springframework.core.io.ClassPathResource resource = new org.springframework.core.io.ClassPathResource("scripts/token_bucket.lua");
            byte[] bytes = org.springframework.util.FileCopyUtils.copyToByteArray(resource.getInputStream());
            String scriptContent = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            script.setScriptText(scriptContent);
            script.setResultType(java.util.List.class);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to load rate limiting script", e);
        }
        return script;
    }
}

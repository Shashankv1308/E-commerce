package com.spring.ecommerce.config;

import java.time.Duration;
import java.util.Map;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer
{
    /**
     * Logs cache errors and swallows them so Redis failures
     * degrade to "no caching" instead of causing 500 errors.
     */
    @Override
    public CacheErrorHandler errorHandler()
    {
        return new LoggingCacheErrorHandler();
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory)
    {
        ObjectMapper mapper = new ObjectMapper();
        // Explicit module registration — findAndRegisterModules() fails in Jackson 2.18+
        // because REQUIRE_HANDLERS_FOR_JAVA8_TIMES blocks LocalDateTime before ServiceLoader runs
        mapper.registerModule(new JavaTimeModule());
        // Serialize LocalDateTime as ISO string "2026-03-09T15:30:24" instead of array [2026,3,9,...]
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // NON_FINAL adds @class type info to non-final types (DTOs, collections, etc.)
        // but skips final classes (String, Long, enums) — avoids the fragile
        // wrapping that EVERYTHING causes
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        // Custom serializer — no deprecated Spring classes (GenericJackson2JsonRedisSerializer
        // and Jackson2JsonRedisSerializer are both marked for removal in Spring Data Redis 4.0)
        JacksonRedisSerializer jsonSerializer = new JacksonRedisSerializer(mapper);

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .withInitialCacheConfigurations(Map.of(
                        // TTL-only for products:
                        // stock drift is acceptable — real check is in decreaseStockIfAvailable
                        // only manually evicted when admin changes product metadata (name/price/active)
                        "products", defaults.entryTtl(Duration.ofMinutes(5)),

                        // orders evicted on every status change (infrequent per order)
                        // TTL is just a safety net
                        "orders", defaults.entryTtl(Duration.ofMinutes(30))
                ))
                .cacheDefaults(defaults.entryTtl(Duration.ofMinutes(10)))
                .build();
    }
}

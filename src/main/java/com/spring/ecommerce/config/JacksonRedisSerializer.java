package com.spring.ecommerce.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.io.IOException;

/**
 * Custom Redis serializer using Jackson JSON.
 *
 * Replaces GenericJackson2JsonRedisSerializer and Jackson2JsonRedisSerializer,
 * both deprecated (marked for removal) in Spring Data Redis 4.0.
 *
 * Implements RedisSerializer<Object> directly — no deprecated classes involved.
 */
public class JacksonRedisSerializer implements RedisSerializer<Object>
{
    private final ObjectMapper mapper;

    public JacksonRedisSerializer(ObjectMapper mapper)
    {
        this.mapper = mapper;
    }

    @Override
    public byte[] serialize(Object value) throws SerializationException
    {
        if (value == null) return new byte[0];
        try
        {
            return mapper.writeValueAsBytes(value);
        }
        catch (JsonProcessingException e)
        {
            throw new SerializationException("JSON serialization error: " + e.getMessage(), e);
        }
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializationException
    {
        if (bytes == null || bytes.length == 0) return null;
        try
        {
            return mapper.readValue(bytes, Object.class);
        }
        catch (IOException e)
        {
            throw new SerializationException("JSON deserialization error: " + e.getMessage(), e);
        }
    }
}

package com.spring.ecommerce.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Jackson configuration — provides an ObjectMapper bean.
 * 
 * Spring Boot 4's spring-boot-starter-webmvc does not auto-configure
 * JacksonAutoConfiguration, so we define it explicitly.
 */
@Configuration
public class JacksonConfig 
{
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() 
    {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.findAndRegisterModules();
        return mapper;
    }
}

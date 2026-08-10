package com.elatusdev.pokedex.catalog.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheAdapter redisCacheAdapter(StringRedisTemplate redis) {
        return new RedisCacheAdapter(redis);
    }
}

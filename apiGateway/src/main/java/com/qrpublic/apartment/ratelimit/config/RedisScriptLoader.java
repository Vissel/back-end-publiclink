package com.qrpublic.apartment.ratelimit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;

@Configuration
public class RedisScriptLoader {

    @Bean
    RedisScript<List<Long>> tokenBucketScript() {
        DefaultRedisScript<List<Long>> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("ratelimiter/token_bucket.lua")));
        Class<List<Long>> resultType = (Class<List<Long>>) (Class<?>) List.class;
        script.setResultType(resultType);
        return script;
    }
}

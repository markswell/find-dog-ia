package com.markswell.infraestructure.config;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "redis.keys")
public interface RedisConfig {

    String inputTokens();

    String outputTokens();

    String totalTokens();

}

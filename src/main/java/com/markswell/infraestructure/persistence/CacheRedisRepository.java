package com.markswell.infraestructure.persistence;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CacheRedisRepository implements CacheRepository {

    private final ValueCommands<String, Long> counters;

    public CacheRedisRepository(RedisDataSource redisDataSource) {
        this.counters = redisDataSource.value(Long.class);
    }

    @Override
    public long get(String key) {
        return counters.get(key);
    }

    @Override
    public long incrementBy(String key, long amount) {
        return counters.incrby(key, amount);
    }
}

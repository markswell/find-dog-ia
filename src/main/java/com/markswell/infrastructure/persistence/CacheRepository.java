package com.markswell.infrastructure.persistence;

public interface CacheRepository {

    long get(String key);

    long incrementBy(String key, long amount);

}

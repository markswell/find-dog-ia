package com.markswell.infraestructure.persistence;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.neo4j.driver.*;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class GraphRepository {

    @Inject
    private Driver driver;

    public List<Map<String, Object>> query(String cypher, Map<String, Object> params) {

        try (Session session = driver.session()) {

            return session.run(cypher, params)
                    .list(record -> record.asMap());
        }
    }

    @PreDestroy
    public void close() {
        driver.close();
    }
}
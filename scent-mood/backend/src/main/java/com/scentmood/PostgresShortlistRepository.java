package com.scentmood;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
class PostgresShortlistRepository implements ShortlistRepository {
    private final JdbcClient jdbc;

    PostgresShortlistRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> saved(UUID visitor) {
        return jdbc.sql("""
            SELECT fragrance_id FROM shortlist_item
            WHERE visitor_id = :visitor ORDER BY saved_at DESC, fragrance_id
            """).param("visitor", visitor).query(String.class).list();
    }

    @Override
    public void save(UUID visitor, String id) {
        jdbc.sql("""
            INSERT INTO shortlist_item (visitor_id, fragrance_id)
            VALUES (:visitor, :id) ON CONFLICT (visitor_id, fragrance_id) DO NOTHING
            """).param("visitor", visitor).param("id", id).update();
    }

    @Override
    public void remove(UUID visitor, String id) {
        jdbc.sql("DELETE FROM shortlist_item WHERE visitor_id = :visitor AND fragrance_id = :id")
            .param("visitor", visitor).param("id", id).update();
    }

    @Override
    public boolean healthy() {
        return jdbc.sql("SELECT 1").query(Integer.class).single() == 1;
    }
}

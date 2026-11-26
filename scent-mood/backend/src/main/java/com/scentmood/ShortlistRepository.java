package com.scentmood;

import java.util.List;
import java.util.UUID;

interface ShortlistRepository {
    List<String> saved(UUID visitor);
    void save(UUID visitor, String id);
    void remove(UUID visitor, String id);
    boolean healthy();
}

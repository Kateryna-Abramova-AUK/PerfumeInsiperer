package com.scentmood;

import java.util.List;

record Fragrance(
    String id,
    String name,
    String brand,
    List<String> notes,
    List<String> accords,
    List<String> moods,
    String family,
    List<String> seasons,
    List<String> times,
    String sourceUrl
) {}

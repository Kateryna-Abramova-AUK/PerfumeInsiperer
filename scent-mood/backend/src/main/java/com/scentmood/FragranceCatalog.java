package com.scentmood;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
class FragranceCatalog {
    private final List<Fragrance> entries;
    private final Map<String, Fragrance> byId;

    FragranceCatalog(ObjectMapper mapper) throws IOException {
        try (var source = new ClassPathResource("fragrances.json").getInputStream()) {
            List<Fragrance> loaded = mapper.readValue(source, new TypeReference<>() {});
            entries = loaded.stream()
                .sorted(Comparator.comparing(Fragrance::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Fragrance::id))
                .toList();
        }
        if (entries.isEmpty()) throw new IllegalStateException("The local fragrance catalog is empty");
        byId = entries.stream().collect(Collectors.toUnmodifiableMap(Fragrance::id, Function.identity()));
    }

    List<Fragrance> all() {
        return entries;
    }

    Optional<Fragrance> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }
}

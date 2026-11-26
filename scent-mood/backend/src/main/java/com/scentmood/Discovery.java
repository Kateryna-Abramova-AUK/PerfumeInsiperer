package com.scentmood;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class Discovery {
    record Mood(String id, String label, String description, Set<String> signals) {}
    record Match(String mood, boolean curatedTag, List<String> signals, int score) {}
    record Recommendation(Fragrance fragrance, List<Match> matches, Boolean preferredNotePresent,
                          String matchedPreferredNote, int score) {}

    static final List<Mood> MOODS = List.of(
        new Mood("angelic", "Angelic", "Airy, soft, and luminous",
            Set.of("white musk", "iris", "orris butter", "white florals", "jasmine", "jasmine absolute", "orange blossom", "magnolia", "cotton flower", "honeysuckle", "neroli")),
        new Mood("joyful", "Joyful", "Bright, juicy, and playful",
            Set.of("mandarin", "bergamot", "apple", "peach", "peach nectar", "blood orange", "grapefruit", "lemon", "raspberry", "pear", "cassis", "nectarine")),
        new Mood("mysterious", "Mysterious", "Dark, smoky, or intriguing",
            Set.of("incense", "frankincense", "patchouli", "plum", "black cherry", "cade oil", "burnt match", "smoke", "leather", "oud", "labdanum")),
        new Mood("cozy", "Cozy", "Warm, comforting, and softly sweet",
            Set.of("vanilla", "madagascar vanilla", "vanilla accord", "tonka bean", "sandalwood", "honey", "benzoin", "cream", "whipped cream")),
        new Mood("candy-like", "Candy-like", "Sugary, creamy, or dessert-like",
            Set.of("caramel", "salted caramel", "praline", "marshmallow", "pistachio", "pink sugar", "candy floss", "whipped cream", "ice cream", "burnt sugar")),
        new Mood("forest-like", "Forest-like", "Green, woody, and earthy",
            Set.of("cedar", "cedarwood", "white cedarwood", "pine", "white pine", "pine resin", "oakmoss", "vetiver", "spruce", "douglas fir", "western hemlock", "silver fir", "lichen"))
    );

    static final List<String> FAMILIES = List.of("Floral", "Gourmand", "Woody", "Fresh", "Amber / Spiced");
    static final List<String> SEASONS = List.of("Spring", "Summer", "Autumn", "Winter");
    static final List<String> TIMES = List.of("Day", "Evening");
    private static final Set<String> CHERRY_NOTES = Set.of("cherry", "black cherry", "wild cherry");

    private Discovery() {}

    static List<Recommendation> recommend(List<Fragrance> catalog, List<Mood> moods, String note, String family, String time) {
        return catalog.stream()
            .filter(fragrance -> wears(fragrance, family, "", time))
            .map(fragrance -> score(fragrance, moods, note))
            .filter(result -> !result.matches().isEmpty())
            .sorted(Comparator.<Recommendation>comparingInt(result -> result.matches().size()).reversed()
                .thenComparing(Comparator.comparingInt(Recommendation::score).reversed())
                .thenComparing(result -> result.fragrance().name(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(result -> result.fragrance().id()))
            .limit(3)
            .toList();
    }

    private static Recommendation score(Fragrance fragrance, List<Mood> moods, String note) {
        var signals = Stream.concat(fragrance.notes().stream(), fragrance.accords().stream())
            .collect(Collectors.toMap(Discovery::key, Function.identity(), (first, duplicate) -> first,
                java.util.LinkedHashMap::new));
        var matches = moods.stream().map(mood -> {
            boolean curated = fragrance.moods().contains(mood.id());
            var supporting = signals.entrySet().stream()
                .filter(entry -> mood.signals().contains(entry.getKey()))
                .limit(2).map(java.util.Map.Entry::getValue).toList();
            return new Match(mood.label(), curated, supporting, (curated ? 3 : 0) + supporting.size());
        }).filter(match -> match.score() > 0).toList();

        var preferredMatch = fragrance.notes().stream()
            .filter(candidate -> matchesPreferredNote(candidate, note))
            .findFirst().orElse(null);
        Boolean preferred = note.isBlank() ? null : preferredMatch != null;
        int score = matches.stream().mapToInt(Match::score).sum() + (preferredMatch == null ? 0 : 2);
        return new Recommendation(fragrance, matches, preferred, preferredMatch, score);
    }

    static boolean matchesPreferredNote(String candidate, String preferred) {
        String normalized = key(preferred);
        String note = key(candidate);
        return normalized.equals("cherry") ? CHERRY_NOTES.contains(note) : note.equals(normalized);
    }

    static List<Fragrance> search(List<Fragrance> catalog, String query, List<String> notes, String family, String season, String time) {
        String term = key(query);
        return catalog.stream()
            .filter(fragrance -> key(fragrance.name()).contains(term) || key(fragrance.brand()).contains(term))
            .filter(fragrance -> notes.stream().allMatch(note -> contains(fragrance.notes(), note)))
            .filter(fragrance -> wears(fragrance, family, season, time))
            .toList();
    }

    private static boolean wears(Fragrance fragrance, String family, String season, String time) {
        return (family.isBlank() || key(fragrance.family()).equals(key(family)))
            && (season.isBlank() || contains(fragrance.seasons(), "Year-round") || contains(fragrance.seasons(), season))
            && (time.isBlank() || contains(fragrance.times(), time));
    }

    private static boolean contains(List<String> values, String candidate) {
        String normalized = key(candidate);
        return values.stream().map(Discovery::key).anyMatch(normalized::equals);
    }

    static String key(String value) {
        return value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}

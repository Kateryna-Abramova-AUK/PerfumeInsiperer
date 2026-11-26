package com.scentmood;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryTest {
    private static final Discovery.Mood MYSTERIOUS = Discovery.MOODS.get(2);
    private static final Discovery.Mood COZY = Discovery.MOODS.get(3);

    @Test
    void bothMoodMatchesStayAheadOfHigherScoringSingleMatches() {
        var both = fragrance("Both", List.of("Incense", "Vanilla"), List.of(), List.of());
        var single = fragrance("Single", List.of("Black Cherry", "Incense", "Plum"), List.of("incense"), List.of("mysterious"));
        var results = Discovery.recommend(List.of(single, both), List.of(MYSTERIOUS, COZY), "Cherry", "", "");
        assertThat(results).extracting(result -> result.fragrance().name()).containsExactly("Both", "Single");
        assertThat(results.getFirst().score()).isEqualTo(2);
        assertThat(results.get(1).score()).isEqualTo(7);
    }

    @Test
    void preferredNoteAloneCannotQualifyAFragrance() {
        var cherryOnly = fragrance("Cherry only", List.of("Wild Cherry"), List.of(), List.of());
        assertThat(Discovery.matchesPreferredNote("Wild Cherry", "Cherry")).isTrue();
        assertThat(Discovery.recommend(List.of(cherryOnly), List.of(MYSTERIOUS), "Cherry", "", "")).isEmpty();
    }

    @Test
    void eveningFilterExcludesDayOnlyAndKeepsFragrancesMarkedForBoth() {
        var dayOnly = new Fragrance("day", "Day only", "Brand", List.of("Black Cherry"),
            List.of("Incense"), List.of("mysterious"), "Woody", List.of("Year-round"),
            List.of("Day"), "https://example.com");
        var both = fragrance("Day and evening", List.of("Plum"), List.of(), List.of());

        var results = Discovery.recommend(List.of(dayOnly, both), List.of(MYSTERIOUS), "Cherry", "", "Evening");
        assertThat(results).extracting(Discovery.Recommendation::fragrance).containsExactly(both);
    }

    @Test
    void explanationNamesActualMatchingNoteAccordAndCuratedTag() {
        var fragrance = fragrance("Explained", List.of("Black Cherry", "Lemon"), List.of("Incense"), List.of("mysterious"));
        var result = Discovery.recommend(List.of(fragrance), List.of(MYSTERIOUS, COZY), "Cherry", "", "").getFirst();

        assertThat(result.matches()).singleElement().satisfies(match -> {
            assertThat(match.mood()).isEqualTo("Mysterious");
            assertThat(match.curatedTag()).isTrue();
            assertThat(match.signals()).containsExactly("Black Cherry", "Incense");
            assertThat(match.score()).isEqualTo(5);
        });
        assertThat(result.preferredNotePresent()).isTrue();
        assertThat(result.matchedPreferredNote()).isEqualTo("Black Cherry");
    }

    @Test
    void noteAndAccordDuplicatesCountOnceAndPreferredNoteBreaksScoreTie() {
        var duplicate = fragrance("A", List.of("Incense"), List.of("incense"), List.of());
        var preferred = fragrance("B", List.of("Incense", "Cherry"), List.of(), List.of());

        var results = Discovery.recommend(List.of(duplicate, preferred), List.of(MYSTERIOUS), "Cherry", "Woody", "Evening");
        assertThat(results).extracting(Discovery.Recommendation::score).containsExactly(3, 1);
        assertThat(results).extracting(Discovery.Recommendation::preferredNotePresent).containsExactly(true, false);
    }

    @Test
    void catalogCombinesAllNotesTextAndWearFilters() {
        var both = fragrance("A", List.of("Incense", "Cherry"), List.of(), List.of());
        var one = fragrance("B", List.of("Incense"), List.of(), List.of());

        assertThat(Discovery.search(List.of(one, both), "bRa", List.of("incense", "CHERRY"), "woody", "Winter", "Day"))
            .containsExactly(both);
        assertThat(Discovery.search(List.of(both), "", List.of(), "Floral", "", "")).isEmpty();
    }

    private Fragrance fragrance(String name, List<String> notes, List<String> accords, List<String> moods) {
        return new Fragrance(name, name, "Brand", notes, accords, moods, "Woody",
            List.of("Year-round"), List.of("Day", "Evening"), "https://example.com");
    }
}

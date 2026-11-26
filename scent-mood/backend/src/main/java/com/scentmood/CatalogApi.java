package com.scentmood;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api")
class CatalogApi {
    private static final String VISITOR_COOKIE = "scent_mood_visitor";

    record MoodOption(String id, String label, String description) {}
    record Options(List<MoodOption> moods, List<String> notes, List<String> preferredNotes,
                   List<String> families, List<String> seasons, List<String> times) {}
    record RecommendationRequest(List<String> moods, String preferredNote, String family, String timeOfDay) {}

    private final FragranceCatalog catalog;
    private final ShortlistRepository shortlist;
    private final boolean secureCookie;

    CatalogApi(FragranceCatalog catalog, ShortlistRepository shortlist,
               @Value("${scent-mood.secure-cookie}") boolean secureCookie) {
        this.catalog = catalog;
        this.shortlist = shortlist;
        this.secureCookie = secureCookie;
    }

    @GetMapping("/health")
    Map<String, String> health() {
        if (!shortlist.healthy()) throw new ResponseStatusException(SERVICE_UNAVAILABLE);
        return Map.of("status", "up");
    }

    @GetMapping("/options")
    Options options() {
        var notes = catalog.all().stream().flatMap(fragrance -> fragrance.notes().stream())
            .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        var preferredNotes = notes.stream().anyMatch(note -> Discovery.matchesPreferredNote(note, "Cherry"))
            ? Stream.concat(notes.stream(), Stream.of("Cherry"))
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList()
            : notes;
        var moods = Discovery.MOODS.stream()
            .map(mood -> new MoodOption(mood.id(), mood.label(), mood.description())).toList();
        return new Options(moods, notes, preferredNotes, Discovery.FAMILIES, Discovery.SEASONS, Discovery.TIMES);
    }

    @GetMapping("/catalog")
    List<Fragrance> catalog(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "") String notes,
        @RequestParam(defaultValue = "") String family,
        @RequestParam(defaultValue = "") String season,
        @RequestParam(defaultValue = "") String time
    ) {
        requireOption(family, Discovery.FAMILIES, "family");
        requireOption(season, Discovery.SEASONS, "season");
        requireOption(time, Discovery.TIMES, "time");
        return Discovery.search(catalog.all(), q, split(notes), family, season, time);
    }

    @GetMapping("/recommendations")
    List<Discovery.Recommendation> recommendationsByQuery(
        @RequestParam(defaultValue = "") String moods,
        @RequestParam(defaultValue = "") String note,
        @RequestParam(defaultValue = "") String family,
        @RequestParam(defaultValue = "") String time
    ) {
        return recommend(split(moods), note, family, time);
    }

    @PostMapping("/recommendations")
    List<Discovery.Recommendation> recommendations(@RequestBody RecommendationRequest request) {
        if (request.moods() == null || request.moods().stream().anyMatch(mood -> mood == null || mood.isBlank())) {
            throw new ResponseStatusException(BAD_REQUEST, "Choose one or two moods");
        }
        var selected = request.moods().stream().map(Discovery::key).toList();
        if (selected.stream().distinct().count() != selected.size()) {
            throw new ResponseStatusException(BAD_REQUEST, "Choose distinct moods");
        }
        return recommend(selected, optional(request.preferredNote()), optional(request.family()), optional(request.timeOfDay()));
    }

    private List<Discovery.Recommendation> recommend(List<String> selected, String note, String family, String time) {
        if (selected.isEmpty() || selected.size() > 2) {
            throw new ResponseStatusException(BAD_REQUEST, "Choose one or two moods");
        }
        var validMoods = selected.stream().map(id -> Discovery.MOODS.stream()
            .filter(mood -> mood.id().equals(id)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unknown mood"))).toList();
        requireOption(family, Discovery.FAMILIES, "family");
        String timeFilter = Discovery.key(time).equals("any time") ? "" : time;
        requireOption(timeFilter, Discovery.TIMES, "time");
        return Discovery.recommend(catalog.all(), validMoods, note, family, timeFilter);
    }

    private static String optional(String value) {
        return value == null ? "" : value.strip();
    }

    @GetMapping("/shortlist")
    ResponseEntity<List<Fragrance>> shortlist(@CookieValue(name = VISITOR_COOKIE, required = false) String cookie) {
        UUID visitor = visitorId(cookie);
        var response = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (visitor == null) {
            visitor = UUID.randomUUID();
            var newCookie = ResponseCookie.from(VISITOR_COOKIE, visitor.toString())
                .httpOnly(true).secure(secureCookie).sameSite("Lax").path("/api")
                .maxAge(Duration.ofDays(365)).build();
            response.header(HttpHeaders.SET_COOKIE, newCookie.toString());
        }
        var saved = shortlist.saved(visitor).stream().map(catalog::find).flatMap(Optional::stream).toList();
        return response.body(saved);
    }

    @PutMapping("/shortlist/{id}")
    ResponseEntity<Void> save(@PathVariable String id, @CookieValue(name = VISITOR_COOKIE, required = false) String cookie) {
        UUID visitor = requireVisitor(cookie);
        if (catalog.find(id).isEmpty()) throw new ResponseStatusException(NOT_FOUND, "Fragrance not found");
        shortlist.save(visitor, id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping("/shortlist/{id}")
    ResponseEntity<Void> remove(@PathVariable String id, @CookieValue(name = VISITOR_COOKIE, required = false) String cookie) {
        shortlist.remove(requireVisitor(cookie), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static List<String> split(String values) {
        return Arrays.stream(values.split(",")).map(Discovery::key).filter(value -> !value.isBlank()).distinct().toList();
    }

    private static void requireOption(String value, List<String> options, String name) {
        if (!value.isBlank() && options.stream().map(Discovery::key).noneMatch(Discovery.key(value)::equals)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unknown " + name);
        }
    }

    private static UUID requireVisitor(String cookie) {
        UUID visitor = visitorId(cookie);
        if (visitor == null) throw new ResponseStatusException(CONFLICT, "Open the shortlist first");
        return visitor;
    }

    private static UUID visitorId(String cookie) {
        if (cookie == null || cookie.length() != 36) return null;
        try {
            return UUID.fromString(cookie);
        } catch (IllegalArgumentException invalidCookie) {
            return null;
        }
    }
}

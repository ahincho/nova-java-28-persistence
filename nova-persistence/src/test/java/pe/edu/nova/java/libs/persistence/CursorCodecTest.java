package pe.edu.nova.java.libs.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.nova.java.libs.api.standard.error.ApplicationError;
import pe.edu.nova.java.libs.api.standard.error.FieldError;

class CursorCodecTest {

    private static final String SORT = "newest";
    private static final List<String> KEYS = List.of("createdAt", "id");
    private static final Instant CREATED = Instant.parse("2026-10-02T15:04:05.123456Z");
    private static final UUID ID = UUID.fromString("0199a7e2-1c3b-7d4e-8f00-123456789abc");

    @Test
    void aCursorReadsBackTheSameTypedKeysInTheirOrder() {
        String cursor = CursorCodec.encode(SORT, position(CREATED, ID));

        Map<String, Object> keys = CursorCodec.decode(cursor, SORT, KEYS);

        assertEquals(List.of("createdAt", "id"), List.copyOf(keys.keySet()));
        assertEquals(CREATED, keys.get("createdAt"));
        assertEquals(ID, keys.get("id"));
    }

    @Test
    void aCursorIsUrlSafeBase64WithoutPaddingOfVersionedJson() {
        String cursor = CursorCodec.encode(SORT, position(CREATED, ID));

        assertTrue(cursor.matches("[A-Za-z0-9_-]+"), cursor);
        String json = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        assertEquals(
                "{\"v\":1,\"s\":\"newest\",\"k\":[[\"createdAt\",\"instant\",\"2026-10-02T15:04:05.123456Z\"],"
                        + "[\"id\",\"uuid\",\"0199a7e2-1c3b-7d4e-8f00-123456789abc\"]]}",
                json);
    }

    @Test
    void everySupportedKeyTypeSurvivesTheRoundTrip() {
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("name", "Ñandú \"quoted\" \\ back\nslash");
        keys.put("rank", 7);
        keys.put("sequence", -9_000_000_000L);
        keys.put("price", new BigDecimal("12.50"));
        keys.put("id", ID);
        keys.put("at", CREATED);
        keys.put("day", LocalDate.of(2026, 10, 2));
        keys.put("local", LocalDateTime.of(2026, 10, 2, 15, 4, 5));
        keys.put("offset", OffsetDateTime.parse("2026-10-02T10:04:05-05:00"));

        String cursor = CursorCodec.encode("everything", keys);

        assertEquals(keys, CursorCodec.decode(cursor, "everything", List.copyOf(keys.keySet())));
    }

    @Test
    void aCursorOfAnotherSortIsRejectedOnTheCursorField() {
        String cursor = CursorCodec.encode("oldest", position(CREATED, ID));

        assertInvalid(cursor, SORT, KEYS);
    }

    @Test
    void aCursorWithOtherKeysIsRejected() {
        String cursor = CursorCodec.encode(SORT, position(CREATED, ID));

        assertInvalid(cursor, SORT, List.of("id", "createdAt"));
        assertInvalid(cursor, SORT, List.of("createdAt"));
    }

    @Test
    void aCursorOfAnotherVersionIsRejected() {
        assertInvalid(encodeJson("{\"v\":2,\"s\":\"newest\",\"k\":[[\"id\",\"uuid\",\"" + ID + "\"]]}"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "not base64!",
                "e30",
                "W10",
                "eyJ2Ijox",
            })
    void aMalformedCursorIsRejected(String cursor) {
        assertInvalid(cursor, SORT, KEYS);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"v\":1,\"s\":\"newest\",\"k\":[]}",
                "{\"v\":1,\"s\":\"newest\",\"k\":{}}",
                "{\"v\":1,\"s\":\"newest\",\"k\":[[\"id\",\"uuid\"]]}",
                "{\"v\":1,\"s\":\"newest\",\"k\":[\"id\"]}",
                "{\"v\":1,\"s\":\"newest\",\"k\":[[\"id\",\"color\",\"red\"]]}",
                "{\"v\":1,\"s\":\"newest\",\"k\":[[\"id\",\"uuid\",\"not-a-uuid\"]]}",
                "{\"v\":1,\"s\":\"newest\",\"k\":[[\"id\",\"uuid\",7]]}",
                "{\"v\":1,\"s\":\"newest\",\"k\":[[\"id\",\"uuid\",\"x\"],[\"id\",\"uuid\",\"y\"]]}",
                "{\"v\":1,\"s\":\"newest\",\"k\":[[\"id\",\"uuid\",\"x\"]],\"extra\":1}",
                "{\"v\":\"1\",\"s\":\"newest\",\"k\":[[\"id\",\"uuid\",\"x\"]]}",
            })
    void aCursorWhoseContentIsNotAPositionIsRejected(String json) {
        assertInvalid(encodeJson(json));
    }

    @Test
    void writingRejectsWhatACursorCannotCarry() {
        assertThrows(IllegalArgumentException.class, () -> CursorCodec.encode(SORT, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> CursorCodec.encode(" ", Map.of("id", ID)));
        assertThrows(IllegalArgumentException.class, () -> CursorCodec.encode(SORT, Map.of("", ID)));
        Map<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("createdAt", null);
        assertThrows(IllegalArgumentException.class, () -> CursorCodec.encode(SORT, withNull));
        IllegalArgumentException unsupported = assertThrows(
                IllegalArgumentException.class, () -> CursorCodec.encode(SORT, Map.of("flag", Boolean.TRUE)));
        assertTrue(unsupported.getMessage().contains("java.lang.Boolean"), unsupported.getMessage());
    }

    private static Map<String, Object> position(Instant createdAt, UUID id) {
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("createdAt", createdAt);
        keys.put("id", id);
        return keys;
    }

    private static String encodeJson(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertInvalid(String cursor) {
        assertInvalid(cursor, SORT, List.of("id"));
    }

    private static void assertInvalid(String cursor, String sort, List<String> keys) {
        ApplicationError error = assertThrows(ApplicationError.class, () -> CursorCodec.decode(cursor, sort, keys));
        assertEquals(ApplicationError.Type.INVALID_INPUT, error.type());
        List<FieldError> fields = error.fieldErrors();
        assertEquals(1, fields.size());
        assertEquals("cursor", fields.get(0).field());
        assertFalse(fields.get(0).message().isBlank());
    }
}

package pe.edu.nova.java.libs.persistence;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import pe.edu.nova.java.libs.api.standard.error.ApplicationError;
import pe.edu.nova.java.libs.api.standard.error.FieldError;

/**
 * Convierte la posición del último elemento de una página en un cursor opaco, y de vuelta (ADR-054).
 *
 * <p>El cursor es Base64 URL-safe sin relleno (RFC 4648, sección 5) de un JSON con la versión del formato, el
 * nombre del orden y los valores de su clave, cada uno con su tipo:
 *
 * <pre>{"v":1,"s":"newest","k":[["createdAt","instant","2026-10-02T15:04:05.123456Z"],["id","uuid","…"]]}</pre>
 *
 * <p>No se firma: los filtros de la consulta los pone el servidor y no viajan en el cursor, así que uno alterado
 * solo mueve la posición dentro de la misma consulta. Al leerlo se comprueba que sea de esta versión, de este
 * orden y con estas claves; si no, es un 400 con el campo {@code cursor}, y nunca una página que no corresponde.
 */
public final class CursorCodec {

    /** La versión del formato que se escribe y la única que se lee. */
    public static final int VERSION = 1;

    /** El campo del error cuando el cursor no sirve. */
    public static final String FIELD = "cursor";

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private CursorCodec() {}

    /**
     * Escribe el cursor de una posición.
     *
     * @param sort el nombre del orden de la consulta
     * @param keys los valores de la clave del orden del último elemento, en el orden del {@code ORDER BY}
     * @return el cursor
     * @throws IllegalArgumentException si no hay claves, o si un valor es nulo o de un tipo que el cursor no lleva
     */
    public static String encode(String sort, Map<String, ?> keys) {
        requireName(sort, "sort");
        Objects.requireNonNull(keys, "keys");
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("el cursor necesita al menos una clave");
        }
        StringBuilder json = new StringBuilder()
                .append("{\"v\":")
                .append(VERSION)
                .append(",\"s\":")
                .append(Json.quote(sort))
                .append(",\"k\":[");
        String separator = "";
        for (Map.Entry<String, ?> key : keys.entrySet()) {
            requireName(key.getKey(), "key");
            if (key.getValue() == null) {
                throw new IllegalArgumentException(
                        "la clave " + key.getKey() + " es nula; las columnas del orden no admiten nulos");
            }
            KeyType type = KeyType.of(key.getValue());
            json.append(separator)
                    .append('[')
                    .append(Json.quote(key.getKey()))
                    .append(',')
                    .append(Json.quote(type.label))
                    .append(',')
                    .append(Json.quote(key.getValue().toString()))
                    .append(']');
            separator = ",";
        }
        json.append("]}");
        return ENCODER.encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Lee un cursor, que tiene que ser de este orden y llevar exactamente estas claves.
     *
     * @param cursor el cursor, tal como llegó
     * @param sort   el nombre del orden de la consulta
     * @param keys   los nombres de la clave del orden, en el orden del {@code ORDER BY}
     * @return los valores de la clave, en ese orden y con su tipo
     * @throws ApplicationError de tipo {@code INVALID_INPUT} con el campo {@code cursor} si está mal formado, es
     *     de otra versión, de otro orden o con otras claves
     */
    public static Map<String, Object> decode(String cursor, String sort, List<String> keys) {
        Objects.requireNonNull(cursor, "cursor");
        requireName(sort, "sort");
        Objects.requireNonNull(keys, "keys");
        try {
            Map<String, Object> position = read(cursor, sort);
            if (!List.copyOf(position.keySet()).equals(keys)) {
                throw invalid();
            }
            return position;
        } catch (IllegalArgumentException | ClassCastException e) {
            throw invalid();
        }
    }

    private static Map<String, Object> read(String cursor, String sort) {
        String text = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
        if (!(Json.parse(text) instanceof Map<?, ?> document) || document.size() != 3) {
            throw invalid();
        }
        if (!Long.valueOf(VERSION).equals(document.get("v")) || !sort.equals(document.get("s"))) {
            throw invalid();
        }
        if (!(document.get("k") instanceof List<?> entries) || entries.isEmpty()) {
            throw invalid();
        }
        Map<String, Object> position = new LinkedHashMap<>();
        for (Object entry : entries) {
            if (!(entry instanceof List<?> triple) || triple.size() != 3) {
                throw invalid();
            }
            String name = (String) triple.get(0);
            Object value = KeyType.byLabel((String) triple.get(1)).parse((String) triple.get(2));
            if (position.put(name, value) != null) {
                throw invalid();
            }
        }
        return position;
    }

    private static ApplicationError invalid() {
        return ApplicationError.invalidInput(
                CursorRequest.INVALID, List.of(FieldError.of(FIELD, "El cursor no es válido para esta consulta")));
    }

    private static void requireName(String name, String what) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(what + " no puede estar vacío");
        }
    }

    /** Los tipos que puede llevar una clave del cursor, con su nombre en el JSON. */
    private enum KeyType {
        STRING("string", String.class, text -> text),
        INT("int", Integer.class, Integer::valueOf),
        LONG("long", Long.class, Long::valueOf),
        DECIMAL("decimal", BigDecimal.class, BigDecimal::new),
        UUID_KEY("uuid", UUID.class, UUID::fromString),
        INSTANT("instant", Instant.class, Instant::parse),
        DATE("date", LocalDate.class, LocalDate::parse),
        DATE_TIME("datetime", LocalDateTime.class, LocalDateTime::parse),
        OFFSET_DATE_TIME("offsetdatetime", OffsetDateTime.class, OffsetDateTime::parse);

        private final String label;
        private final Class<?> type;
        private final Function<String, Object> parser;

        KeyType(String label, Class<?> type, Function<String, Object> parser) {
            this.label = label;
            this.type = type;
            this.parser = parser;
        }

        static KeyType of(Object value) {
            return Arrays.stream(values())
                    .filter(candidate -> candidate.type == value.getClass())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("el cursor no lleva claves de tipo "
                            + value.getClass().getName() + "; lleva " + labels()));
        }

        static KeyType byLabel(String label) {
            return Arrays.stream(values())
                    .filter(candidate -> candidate.label.equals(label))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("tipo desconocido: " + label));
        }

        Object parse(String text) {
            try {
                return parser.apply(text);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("valor inválido para " + label, e);
            }
        }

        private static List<String> labels() {
            List<String> labels = new ArrayList<>();
            for (KeyType type : values()) {
                labels.add(type.label);
            }
            return labels;
        }
    }
}

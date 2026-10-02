package pe.edu.nova.java.libs.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonTest {

    @Test
    void itReadsObjectsArraysTextsAndIntegers() {
        Object value = Json.parse(" { \"a\" : [ 1 , -2 , \"x\" ] , \"b\" : { } , \"c\" : [ ] } ");

        assertEquals(Map.of("a", List.of(1L, -2L, "x"), "b", Map.of(), "c", List.of()), value);
    }

    @Test
    void itReadsEveryEscape() {
        assertEquals("\"\\/\n\t\r\b\fÑ", Json.parse("\"\\\"\\\\\\/\\n\\t\\r\\b\\f\\u00d1\""));
    }

    @Test
    void quotingIsReadBackAsTheSameText() {
        String text = "comillas \" barra \\ control \u0001 ñ";

        assertEquals(text, Json.parse(Json.quote(text)));
        assertEquals("\"\\u0001\"", Json.quote("\u0001"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "  ",
                "true",
                "1.5",
                "-",
                "\"open",
                "\"bad \\x escape\"",
                "\"short \\u12\"",
                "\"bad \\uzzzz\"",
                "\"trailing \\",
                "\"control \u0001\"",
                "{\"a\" 1}",
                "{\"a\":1,}",
                "{1:1}",
                "{\"a\":1,\"a\":2}",
                "{\"a\":1",
                "[1,]",
                "[1 2]",
                "[1",
                "{} {}",
                "99999999999999999999",
            })
    void anythingElseIsMalformed(String text) {
        assertThrows(IllegalArgumentException.class, () -> Json.parse(text));
    }
}

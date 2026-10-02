package pe.edu.nova.java.libs.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * El JSON justo para el cursor, sin dependencias: objetos, arreglos, textos y enteros.
 *
 * <p>El núcleo no trae una librería de JSON (ADR-015) y el cursor no necesita más. Lo que no es parte de ese
 * subconjunto, como un decimal o un {@code true}, se rechaza como un cursor mal formado.
 */
final class Json {

    private final String text;
    private int position;

    private Json(String text) {
        this.text = text;
    }

    /**
     * Lee un documento: un {@code Map}, una {@code List}, un {@code String} o un {@code Long} por valor.
     *
     * @param text el documento
     * @return su valor
     * @throws IllegalArgumentException si no es un documento del subconjunto
     */
    static Object parse(String text) {
        Json json = new Json(text);
        Object value = json.value();
        json.skipSpaces();
        if (json.position != text.length()) {
            throw json.malformed();
        }
        return value;
    }

    /**
     * Escribe un texto como literal de JSON, con sus comillas.
     *
     * @param value el texto
     * @return el literal
     */
    static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private Object value() {
        skipSpaces();
        if (position >= text.length()) {
            throw malformed();
        }
        char c = text.charAt(position);
        if (c == '{') {
            return object();
        }
        if (c == '[') {
            return array();
        }
        if (c == '"') {
            return string();
        }
        if (c == '-' || Character.isDigit(c)) {
            return integer();
        }
        throw malformed();
    }

    private Map<String, Object> object() {
        Map<String, Object> members = new LinkedHashMap<>();
        position++;
        skipSpaces();
        if (peek('}')) {
            position++;
            return members;
        }
        while (true) {
            skipSpaces();
            if (!peek('"')) {
                throw malformed();
            }
            String name = string();
            skipSpaces();
            expect(':');
            if (members.put(name, value()) != null) {
                throw malformed();
            }
            skipSpaces();
            if (peek(',')) {
                position++;
            } else {
                expect('}');
                return members;
            }
        }
    }

    private List<Object> array() {
        List<Object> elements = new ArrayList<>();
        position++;
        skipSpaces();
        if (peek(']')) {
            position++;
            return elements;
        }
        while (true) {
            elements.add(value());
            skipSpaces();
            if (peek(',')) {
                position++;
            } else {
                expect(']');
                return elements;
            }
        }
    }

    private String string() {
        position++;
        StringBuilder out = new StringBuilder();
        while (position < text.length()) {
            char c = text.charAt(position++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                if (c < 0x20) {
                    throw malformed();
                }
                out.append(c);
                continue;
            }
            if (position >= text.length()) {
                throw malformed();
            }
            char escaped = text.charAt(position++);
            switch (escaped) {
                case '"', '\\', '/' -> out.append(escaped);
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> out.append(unicode());
                default -> throw malformed();
            }
        }
        throw malformed();
    }

    private char unicode() {
        if (position + 4 > text.length()) {
            throw malformed();
        }
        try {
            char c = (char) Integer.parseInt(text.substring(position, position + 4), 16);
            position += 4;
            return c;
        } catch (NumberFormatException e) {
            throw malformed();
        }
    }

    private Long integer() {
        int start = position;
        if (peek('-')) {
            position++;
        }
        while (position < text.length() && Character.isDigit(text.charAt(position))) {
            position++;
        }
        try {
            return Long.valueOf(text.substring(start, position));
        } catch (NumberFormatException e) {
            throw malformed();
        }
    }

    private void skipSpaces() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }

    private boolean peek(char c) {
        return position < text.length() && text.charAt(position) == c;
    }

    private void expect(char c) {
        if (!peek(c)) {
            throw malformed();
        }
        position++;
    }

    private IllegalArgumentException malformed() {
        return new IllegalArgumentException("JSON mal formado en la posición " + position);
    }
}

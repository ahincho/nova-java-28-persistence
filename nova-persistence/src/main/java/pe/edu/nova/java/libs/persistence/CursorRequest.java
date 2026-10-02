package pe.edu.nova.java.libs.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import pe.edu.nova.java.libs.api.standard.error.ApplicationError;
import pe.edu.nova.java.libs.api.standard.error.FieldError;

/**
 * Lo que pide el cliente: cuántos elementos y desde dónde (ADR-054).
 *
 * <p>Por HTTP llega como {@code ?limit=} y {@code ?cursor=}. Sin cursor es la primera página. El cursor se
 * guarda tal como llegó: se decodifica recién contra el orden de la consulta, que es quien sabe si le
 * corresponde.
 *
 * @param limit  cuántos elementos trae la página, al menos 1
 * @param cursor el {@code nextCursor} de la página anterior, o {@code null} para la primera
 */
public record CursorRequest(int limit, String cursor) {

    /** El mensaje para la persona, el mismo del catálogo de Nova para un 400. */
    static final String INVALID = "La solicitud no es válida";

    /**
     * Crea la petición.
     *
     * @throws IllegalArgumentException si el límite es menor que 1 o el cursor está en blanco
     */
    public CursorRequest {
        if (limit < 1) {
            throw new IllegalArgumentException("limit debe ser al menos 1: " + limit);
        }
        if (cursor != null && cursor.isBlank()) {
            throw new IllegalArgumentException("cursor no puede estar en blanco");
        }
    }

    /**
     * La primera página.
     *
     * @param limit cuántos elementos
     * @return la petición
     */
    public static CursorRequest first(int limit) {
        return new CursorRequest(limit, null);
    }

    /**
     * La página que sigue a un cursor.
     *
     * @param cursor el {@code nextCursor} de la página anterior
     * @param limit  cuántos elementos
     * @return la petición
     */
    public static CursorRequest after(String cursor, int limit) {
        return new CursorRequest(limit, Objects.requireNonNull(cursor, "cursor"));
    }

    /**
     * Lee la petición de los parámetros de query, tal como llegaron.
     *
     * <p>Un parámetro vacío cuenta como ausente. Un límite que no es un número, o que está fuera de rango, es un
     * 400 con el campo {@code limit}.
     *
     * @param limit  el valor de {@code ?limit=}, o {@code null}
     * @param cursor el valor de {@code ?cursor=}, o {@code null}
     * @param limits el límite por defecto y el máximo
     * @return la petición
     * @throws ApplicationError de tipo {@code INVALID_INPUT} si el límite no es válido
     */
    public static CursorRequest from(String limit, String cursor, CursorLimits limits) {
        Objects.requireNonNull(limits, "limits");
        String cursorOrNull = cursor == null || cursor.isBlank() ? null : cursor.strip();
        return new CursorRequest(parseLimit(limit, limits), cursorOrNull);
    }

    /**
     * El cursor, si no es la primera página.
     *
     * @return el cursor, o vacío
     */
    public Optional<String> cursorIfAny() {
        return Optional.ofNullable(cursor);
    }

    private static int parseLimit(String raw, CursorLimits limits) {
        if (raw == null || raw.isBlank()) {
            return limits.defaultLimit();
        }
        int value;
        try {
            value = Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            throw invalidLimit(limits);
        }
        if (value < 1 || value > limits.maxLimit()) {
            throw invalidLimit(limits);
        }
        return value;
    }

    private static ApplicationError invalidLimit(CursorLimits limits) {
        return ApplicationError.invalidInput(
                INVALID, List.of(FieldError.of("limit", "Debe ser un número entre 1 y " + limits.maxLimit())));
    }
}

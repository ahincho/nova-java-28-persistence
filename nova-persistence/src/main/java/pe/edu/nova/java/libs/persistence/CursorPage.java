package pe.edu.nova.java.libs.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Una página de un scroll infinito (ADR-054): los elementos, el cursor de la siguiente y si hay más.
 *
 * <p>Es lo que va en {@code data} del sobre de Nova. En la última página, {@code hasNext} es {@code false} y
 * {@code nextCursor} es {@code null}. No trae total ni página anterior: un scroll infinito solo avanza.
 *
 * @param items      los elementos de la página, en el orden de la consulta
 * @param nextCursor el cursor con el que se pide la página siguiente, o {@code null} en la última
 * @param hasNext    si hay una página siguiente
 * @param <T>        el tipo de los elementos
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasNext) {

    /**
     * Crea la página.
     *
     * @throws IllegalArgumentException si {@code hasNext} no coincide con la presencia del cursor
     */
    public CursorPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (hasNext != (nextCursor != null)) {
            throw new IllegalArgumentException("hasNext es true si y solo si hay un nextCursor");
        }
    }

    /**
     * Una página con siguiente.
     *
     * @param items      los elementos
     * @param nextCursor el cursor de la siguiente
     * @param <T>        el tipo de los elementos
     * @return la página
     */
    public static <T> CursorPage<T> of(List<T> items, String nextCursor) {
        return new CursorPage<>(items, Objects.requireNonNull(nextCursor, "nextCursor"), true);
    }

    /**
     * La última página.
     *
     * @param items los elementos
     * @param <T>   el tipo de los elementos
     * @return la página, sin cursor
     */
    public static <T> CursorPage<T> last(List<T> items) {
        return new CursorPage<>(items, null, false);
    }

    /**
     * El cursor de la siguiente, si hay una.
     *
     * @return el cursor, o vacío en la última página
     */
    public Optional<String> nextCursorIfAny() {
        return Optional.ofNullable(nextCursor);
    }

    /**
     * La misma página con cada elemento convertido, como una entidad en su vista.
     *
     * @param mapper la conversión
     * @param <R>    el tipo de llegada
     * @return la página convertida, con el mismo cursor
     */
    public <R> CursorPage<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper");
        List<R> mapped = items.stream().<R>map(mapper).toList();
        return new CursorPage<>(mapped, nextCursor, hasNext);
    }
}

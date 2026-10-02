package pe.edu.nova.java.starters.persistence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import pe.edu.nova.java.libs.persistence.CursorCodec;
import pe.edu.nova.java.libs.persistence.CursorPage;
import pe.edu.nova.java.libs.persistence.CursorRequest;

/**
 * Pone el contrato de ADR-054 sobre el scroll por keyset de Spring Data.
 *
 * <p>Un repositorio declara una consulta que devuelve un {@code Window}, y el handler la llama así:
 *
 * <pre>{@code
 * Window<Order> window = orders.findByCustomerId(
 *         customerId, CursorPages.position(request, NEWEST), CursorPages.limit(request), NEWEST.sort());
 * return CursorPages.page(window, NEWEST, OrderViews::of);
 * }</pre>
 *
 * <p>La consulta pagina la raíz: nunca hace {@code fetch join} de una colección, porque Hibernate aplicaría el
 * límite en memoria. Las colecciones se cargan por lotes, con {@code @BatchSize}.
 */
public final class CursorPages {

    private CursorPages() {}

    /**
     * Desde dónde sigue la consulta.
     *
     * @param request lo que pidió el cliente
     * @param sort    el orden de la consulta
     * @return el inicio sin cursor, o la posición que guarda el cursor
     * @throws pe.edu.nova.java.libs.api.standard.error.ApplicationError de tipo {@code INVALID_INPUT} si el cursor
     *     no es de este orden
     */
    public static ScrollPosition position(CursorRequest request, CursorSort sort) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(sort, "sort");
        return request.cursorIfAny()
                .<ScrollPosition>map(
                        cursor -> ScrollPosition.forward(CursorCodec.decode(cursor, sort.name(), sort.keys())))
                .orElseGet(ScrollPosition::keyset);
    }

    /**
     * Cuántos elementos trae la consulta.
     *
     * @param request lo que pidió el cliente
     * @return el límite
     */
    public static Limit limit(CursorRequest request) {
        return Limit.of(Objects.requireNonNull(request, "request").limit());
    }

    /**
     * Convierte el resultado de la consulta en la página que recibe el cliente.
     *
     * @param window el resultado del repositorio, consultado con {@link #position} y {@link #limit}
     * @param sort   el mismo orden de la consulta
     * @param mapper la conversión de cada entidad en su vista
     * @param <E>    el tipo de la entidad
     * @param <R>    el tipo de la vista
     * @return la página, con el cursor del último elemento si hay más
     * @throws IllegalStateException si la consulta no fue por keyset o su orden no termina en la clave primaria
     */
    public static <E, R> CursorPage<R> page(
            Window<E> window, CursorSort sort, Function<? super E, ? extends R> mapper) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(sort, "sort");
        Objects.requireNonNull(mapper, "mapper");
        List<R> items = window.stream().<R>map(mapper).toList();
        if (!window.hasNext() || window.isEmpty()) {
            return CursorPage.last(items);
        }
        if (!(window.positionAt(window.size() - 1) instanceof KeysetScrollPosition keyset)) {
            throw new IllegalStateException("la consulta del orden " + sort.name() + " no es por keyset");
        }
        // Spring Data entrega las claves sin el orden del ORDER BY, y les suma la clave primaria si el orden no la
        // trae. El cursor las guarda en el orden de la consulta, y exige que sean exactamente las del orden.
        Map<String, Object> keys = keyset.getKeys();
        if (!keys.keySet().equals(Set.copyOf(sort.keys()))) {
            throw new IllegalStateException("el orden " + sort.name() + " tiene que terminar en la clave primaria: "
                    + sort.keys() + " frente a " + keys.keySet());
        }
        Map<String, Object> ordered = new LinkedHashMap<>();
        sort.keys().forEach(key -> ordered.put(key, keys.get(key)));
        return CursorPage.of(items, CursorCodec.encode(sort.name(), ordered));
    }
}

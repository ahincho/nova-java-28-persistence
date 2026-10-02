package pe.edu.nova.java.starters.persistence;

import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Sort;

/**
 * Un orden de scroll con nombre (ADR-054).
 *
 * <p>El nombre viaja en el cursor y lo ata a su orden: un cursor de {@code newest} no sirve para otro orden, y se
 * rechaza con un 400. El orden termina siempre en la clave primaria, para que dos filas con el mismo valor nunca se
 * repitan ni se salten, y sus columnas no admiten nulos. Se declara una vez por consulta, como constante:
 *
 * <pre>{@code
 * static final CursorSort NEWEST = CursorSort.of("newest", Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
 * }</pre>
 *
 * @param name el nombre que viaja en el cursor
 * @param sort el orden de Spring Data, con la clave primaria al final
 */
public record CursorSort(String name, Sort sort) {

    /**
     * Crea el orden.
     *
     * @throws IllegalArgumentException si el nombre está en blanco o el orden está vacío
     */
    public CursorSort {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("el orden necesita un nombre");
        }
        Objects.requireNonNull(sort, "sort");
        if (sort.isUnsorted()) {
            throw new IllegalArgumentException("el orden " + name + " no tiene propiedades");
        }
    }

    /**
     * Crea el orden.
     *
     * @param name el nombre que viaja en el cursor
     * @param sort el orden, con la clave primaria al final
     * @return el orden con nombre
     */
    public static CursorSort of(String name, Sort sort) {
        return new CursorSort(name, sort);
    }

    /**
     * Las propiedades del orden, que son las claves del cursor.
     *
     * @return sus nombres, en el orden del {@code ORDER BY}
     */
    public List<String> keys() {
        return sort.stream().map(Sort.Order::getProperty).toList();
    }
}

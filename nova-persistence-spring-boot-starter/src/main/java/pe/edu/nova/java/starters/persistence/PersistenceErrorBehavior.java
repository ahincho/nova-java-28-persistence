package pe.edu.nova.java.starters.persistence;

import pe.edu.nova.java.libs.api.standard.error.NovaError;
import pe.edu.nova.java.libs.cqrs.Command;
import pe.edu.nova.java.libs.cqrs.CommandBehavior;
import pe.edu.nova.java.libs.cqrs.Next;
import pe.edu.nova.java.libs.cqrs.Query;
import pe.edu.nova.java.libs.cqrs.QueryBehavior;

/**
 * Traduce lo que falla en la base, durante un comando o una consulta, a un error de ADR-031 (ADR-054).
 *
 * <p>Con él, una clave duplicada o una versión pisada se responden 409 y una base caída 503, en lugar de un 500
 * genérico con el que el cliente no sabe si reintentar. La traducción es la de {@link PersistenceErrors}; lo que
 * no es de la base sigue su camino sin cambios.
 */
public final class PersistenceErrorBehavior implements CommandBehavior, QueryBehavior {

    /** Crea el comportamiento. */
    public PersistenceErrorBehavior() {}

    @Override
    public <R> R handle(Command<R> command, Next<R> next) {
        return translating(next);
    }

    @Override
    public <R> R handle(Query<R> query, Next<R> next) {
        return translating(next);
    }

    private static <R> R translating(Next<R> next) {
        try {
            return next.proceed();
        } catch (RuntimeException e) {
            NovaError translated = PersistenceErrors.translate(e).orElse(null);
            if (translated == null) {
                throw e;
            }
            throw translated;
        }
    }
}

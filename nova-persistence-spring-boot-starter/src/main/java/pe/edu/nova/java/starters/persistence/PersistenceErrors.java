package pe.edu.nova.java.starters.persistence;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;
import java.util.Optional;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;
import pe.edu.nova.java.libs.api.standard.error.ApplicationError;
import pe.edu.nova.java.libs.api.standard.error.InfrastructureError;
import pe.edu.nova.java.libs.api.standard.error.NovaError;

/**
 * Traduce una excepción de la base a un error de ADR-031 (ADR-054).
 *
 * <table>
 *   <caption>La traducción</caption>
 *   <tr><th>Excepción</th><th>Error</th><th>HTTP</th></tr>
 *   <tr><td>bloqueo optimista, al pisar una versión</td><td>{@code CONFLICT} con {@value #CONCURRENT_MODIFICATION}</td><td>409</td></tr>
 *   <tr><td>una restricción única o de integridad</td><td>{@code CONFLICT} con {@value #DATA_CONFLICT}</td><td>409</td></tr>
 *   <tr><td>una base caída o una conexión que no llega</td><td>{@code UNAVAILABLE}</td><td>503</td></tr>
 *   <tr><td>una consulta que pasa su timeout</td><td>{@code TIMEOUT}</td><td>504</td></tr>
 * </table>
 *
 * <p>Reconoce tanto las excepciones que Spring ya tradujo como las de JPA y JDBC que llegan sin traducir, como las
 * de un {@code flush}: recorre las causas y, si no encuentra un tipo conocido, mira el SQLState del estándar. Lo
 * demás no se traduce y sigue siendo un 500.
 */
public final class PersistenceErrors {

    /** El código de un cambio que pisó otro. */
    public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";

    /** El código de un dato que choca con otro que ya existe. */
    public static final String DATA_CONFLICT = "DATA_CONFLICT";

    /** La dependencia que nombran los errores de infraestructura: sale en el log, nunca en el mensaje. */
    public static final String UPSTREAM = "database";

    private static final String JPA_OPTIMISTIC_LOCK = "jakarta.persistence.OptimisticLockException";
    private static final String JPA_QUERY_TIMEOUT = "jakarta.persistence.QueryTimeoutException";
    private static final String JPA_LOCK_TIMEOUT = "jakarta.persistence.LockTimeoutException";
    private static final int MAX_CAUSES = 16;

    private PersistenceErrors() {}

    /**
     * El error de ADR-031 que corresponde a una excepción.
     *
     * @param failure lo que se lanzó
     * @return el error, o vacío si no es una excepción de la base que se sepa traducir
     */
    public static Optional<NovaError> translate(Throwable failure) {
        if (failure == null || failure instanceof NovaError) {
            return Optional.empty();
        }
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSES; depth++, current = current.getCause()) {
            Optional<NovaError> known = byType(current, failure);
            if (known.isPresent()) {
                return known;
            }
        }
        return bySqlState(failure);
    }

    private static Optional<NovaError> byType(Throwable candidate, Throwable failure) {
        if (candidate instanceof OptimisticLockingFailureException || isA(candidate, JPA_OPTIMISTIC_LOCK)) {
            return Optional.of(concurrentModification());
        }
        if (candidate instanceof DataIntegrityViolationException
                || candidate instanceof SQLIntegrityConstraintViolationException) {
            return Optional.of(dataConflict());
        }
        if (candidate instanceof QueryTimeoutException
                || candidate instanceof SQLTimeoutException
                || isA(candidate, JPA_QUERY_TIMEOUT)
                || isA(candidate, JPA_LOCK_TIMEOUT)) {
            return Optional.of(InfrastructureError.timeout(UPSTREAM, failure));
        }
        if (candidate instanceof DataAccessResourceFailureException
                || candidate instanceof CannotCreateTransactionException
                || candidate instanceof SQLTransientConnectionException) {
            return Optional.of(InfrastructureError.unavailable(UPSTREAM, failure));
        }
        return Optional.empty();
    }

    private static Optional<NovaError> bySqlState(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSES; depth++, current = current.getCause()) {
            if (current instanceof SQLException sql
                    && sql.getSQLState() != null
                    && sql.getSQLState().length() == 5) {
                String state = sql.getSQLState();
                // La clase 23 es la de las restricciones de integridad, y la 08 la de la conexión.
                if (state.startsWith("23")) {
                    return Optional.of(dataConflict());
                }
                if (state.startsWith("08")) {
                    return Optional.of(InfrastructureError.unavailable(UPSTREAM, failure));
                }
                // 57014 es la consulta cancelada por su statement_timeout en PostgreSQL.
                if ("57014".equals(state)) {
                    return Optional.of(InfrastructureError.timeout(UPSTREAM, failure));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean isA(Throwable candidate, String className) {
        for (Class<?> type = candidate.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    private static ApplicationError concurrentModification() {
        return ApplicationError.conflict(
                CONCURRENT_MODIFICATION, "El recurso cambió mientras se procesaba la operación");
    }

    private static ApplicationError dataConflict() {
        return ApplicationError.conflict(DATA_CONFLICT, "La operación choca con datos que ya existen");
    }
}

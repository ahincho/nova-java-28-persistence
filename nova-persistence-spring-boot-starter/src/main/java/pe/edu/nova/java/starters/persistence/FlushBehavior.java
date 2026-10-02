package pe.edu.nova.java.starters.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.edu.nova.java.libs.cqrs.Command;
import pe.edu.nova.java.libs.cqrs.CommandBehavior;
import pe.edu.nova.java.libs.cqrs.Next;

/**
 * Escribe en la base lo pendiente de cada comando antes de que termine (ADR-054).
 *
 * <p>Sin él, un {@code save} llega a la base recién en el commit, y el commit puede estar fuera del bus: en pedidos lo
 * hace la idempotencia de ADR-047, alrededor del controlador. Con el {@code flush} al final del handler, una clave
 * duplicada o una versión pisada falla dentro del bus, donde {@link PersistenceErrorBehavior} la traduce a un 409.
 *
 * <p>Solo actúa dentro de una transacción de escritura que ya tiene su {@code EntityManager}; sin ella no hay nada
 * pendiente que escribir.
 */
public final class FlushBehavior implements CommandBehavior {

    private final ObjectProvider<EntityManagerFactory> factories;

    /**
     * Crea el comportamiento. La fábrica se busca en cada comando, así que no depende del orden de la configuración.
     *
     * @param factories la fábrica de {@code EntityManager} del servicio
     */
    public FlushBehavior(ObjectProvider<EntityManagerFactory> factories) {
        this.factories = Objects.requireNonNull(factories, "factories");
    }

    @Override
    public <R> R handle(Command<R> command, Next<R> next) {
        R result = next.proceed();
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            EntityManagerFactory factory = factories.getIfUnique();
            EntityManager entityManager =
                    factory == null ? null : EntityManagerFactoryUtils.getTransactionalEntityManager(factory);
            if (entityManager != null) {
                entityManager.flush();
            }
        }
        return result;
    }
}

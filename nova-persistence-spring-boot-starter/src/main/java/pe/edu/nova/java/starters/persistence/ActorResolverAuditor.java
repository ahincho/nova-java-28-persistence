package pe.edu.nova.java.starters.persistence;

import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.AuditorAware;
import pe.edu.nova.java.libs.cqrs.ActorResolver;

/**
 * El actor de la auditoría de las entidades, tomado del {@link ActorResolver} de CQRS (ADR-053): el mismo que
 * registra la auditoría de los buses. Sin un {@code ActorResolver}, usa el de respaldo.
 *
 * <p>Esta clase solo se carga si CQRS está en el classpath.
 */
final class ActorResolverAuditor implements AuditorAware<String> {

    private final ObjectProvider<ActorResolver> actors;
    private final AuditorAware<String> fallback;

    /**
     * El actor de los buses, o el de respaldo si el servicio no tiene un {@code ActorResolver}.
     *
     * @param context  el contexto, donde se busca el {@code ActorResolver} en cada cambio
     * @param fallback el actor de respaldo
     * @return el actor de la auditoría
     */
    static AuditorAware<String> of(ApplicationContext context, AuditorAware<String> fallback) {
        return new ActorResolverAuditor(context.getBeanProvider(ActorResolver.class), fallback);
    }

    ActorResolverAuditor(ObjectProvider<ActorResolver> actors, AuditorAware<String> fallback) {
        this.actors = Objects.requireNonNull(actors, "actors");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public Optional<String> getCurrentAuditor() {
        ActorResolver resolver = actors.getIfAvailable();
        return resolver == null ? fallback.getCurrentAuditor() : resolver.currentActor();
    }
}

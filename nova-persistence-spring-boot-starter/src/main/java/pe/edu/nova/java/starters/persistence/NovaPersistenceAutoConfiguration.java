package pe.edu.nova.java.starters.persistence;

import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pe.edu.nova.java.libs.cqrs.CommandBehavior;
import pe.edu.nova.java.libs.persistence.CursorLimits;

/**
 * Conecta la persistencia de Nova con Spring Boot (ADR-054).
 *
 * <p>Trae cuatro piezas, cada una con su condición y su interruptor bajo {@code nova.persistence.*}:
 *
 * <ul>
 *   <li>el {@code CursorRequest} como argumento de un controlador, con los límites de
 *       {@code nova.persistence.pagination.*};
 *   <li>la auditoría de Spring Data para {@link AuditableEntity}, con el reloj del servicio y el actor de CQRS o de
 *       Spring Security, si el servicio tiene una base y no declara su propio {@code @EnableJpaAuditing};
 *   <li>la traducción de los errores de la base en los buses de CQRS;
 *   <li>el {@code flush} al terminar cada comando.
 * </ul>
 *
 * <p>Cada bean se reemplaza declarando el propio: los límites, el actor y el reloj de la auditoría.
 */
// Después de Hibernate, para que la auditoría vea si el servicio tiene una EntityManagerFactory.
@AutoConfiguration(afterName = "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration")
@EnableConfigurationProperties(NovaPersistenceProperties.class)
public class NovaPersistenceAutoConfiguration {

    private static final String ACTOR_RESOLVER = "pe.edu.nova.java.libs.cqrs.ActorResolver";
    private static final String SPRING_SECURITY = "org.springframework.security.core.context.SecurityContextHolder";

    /** Crea la auto-configuración; la instancia Spring Boot. */
    public NovaPersistenceAutoConfiguration() {}

    /**
     * Los límites de una página, de {@code nova.persistence.pagination.*}.
     *
     * @param properties la configuración
     * @return los límites
     */
    @Bean
    @ConditionalOnMissingBean
    public CursorLimits novaCursorLimits(NovaPersistenceProperties properties) {
        return properties.getPagination().toLimits();
    }

    /** El {@code CursorRequest} como argumento de un controlador de Spring MVC. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(WebMvcConfigurer.class)
    static class WebConfiguration {

        @Bean
        WebMvcConfigurer novaCursorRequestConfigurer(CursorLimits limits) {
            return new WebMvcConfigurer() {
                @Override
                public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                    resolvers.add(new CursorRequestArgumentResolver(limits));
                }
            };
        }
    }

    /** La auditoría de las entidades, si el servicio no declara la suya. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({EntityManagerFactory.class, AuditingEntityListener.class})
    @ConditionalOnBean(EntityManagerFactory.class)
    @ConditionalOnMissingBean(name = "jpaAuditingHandler")
    @ConditionalOnProperty(prefix = "nova.persistence.auditing", name = "enabled", matchIfMissing = true)
    @EnableJpaAuditing
    static class AuditingConfiguration {

        @Bean
        @ConditionalOnMissingBean(AuditorAware.class)
        AuditorAware<String> novaPersistenceAuditor(ApplicationContext context) {
            ClassLoader classLoader = context.getClassLoader();
            AuditorAware<String> fallback =
                    ClassUtils.isPresent(SPRING_SECURITY, classLoader) ? new SpringSecurityAuditor() : Optional::empty;
            if (ClassUtils.isPresent(ACTOR_RESOLVER, classLoader)) {
                return ActorResolverAuditor.of(context, fallback);
            }
            return fallback;
        }

        // Postgres guarda microsegundos: con la misma precisión en memoria, lo que se lee es lo que se escribió.
        @Bean
        @ConditionalOnMissingBean(DateTimeProvider.class)
        DateTimeProvider novaPersistenceDateTimeProvider(ObjectProvider<Clock> clocks) {
            Clock clock = clocks.getIfAvailable(Clock::systemUTC);
            return () -> Optional.of(Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
        }
    }

    /** Los comportamientos de los buses de CQRS, si el servicio los usa. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(CommandBehavior.class)
    static class CqrsConfiguration {

        @Bean
        @Order(NovaPersistenceBehaviorOrder.ERROR_TRANSLATION)
        @ConditionalOnProperty(prefix = "nova.persistence.error-translation", name = "enabled", matchIfMissing = true)
        PersistenceErrorBehavior novaPersistenceErrorBehavior() {
            return new PersistenceErrorBehavior();
        }

        @Bean
        @Order(NovaPersistenceBehaviorOrder.FLUSH)
        @ConditionalOnClass({EntityManagerFactory.class, EntityManagerFactoryUtils.class})
        @ConditionalOnProperty(prefix = "nova.persistence.flush", name = "enabled", matchIfMissing = true)
        FlushBehavior novaPersistenceFlushBehavior(ObjectProvider<EntityManagerFactory> factories) {
            return new FlushBehavior(factories);
        }
    }
}

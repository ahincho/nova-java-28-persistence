package pe.edu.nova.java.starters.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pe.edu.nova.java.libs.api.standard.error.ApplicationError;
import pe.edu.nova.java.libs.cqrs.ActorResolver;
import pe.edu.nova.java.libs.cqrs.Command;
import pe.edu.nova.java.libs.persistence.CursorLimits;
import pe.edu.nova.java.libs.persistence.CursorRequest;
import pe.edu.nova.java.starters.persistence.it.Ticket;

class NovaPersistenceAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NovaPersistenceAutoConfiguration.class));

    // La auditoría necesita una EntityManagerFactory: la de H2, con las entidades del paquete de prueba.
    private final ApplicationContextRunner jpa = runner.withConfiguration(
                    AutoConfigurations.of(DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class))
            .withUserConfiguration(EntityPackage.class)
            .withPropertyValues("spring.datasource.generate-unique-name=true");

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage(basePackageClasses = Ticket.class)
    static class EntityPackage {}

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theLimitsAreNovasUnlessTheServiceSetsItsOwn() {
        runner.run(context -> assertEquals(CursorLimits.DEFAULT, context.getBean(CursorLimits.class)));
        runner.withPropertyValues(
                        "nova.persistence.pagination.default-limit=5", "nova.persistence.pagination.max-limit=10")
                .run(context -> assertEquals(new CursorLimits(5, 10), context.getBean(CursorLimits.class)));
        runner.withPropertyValues(
                        "nova.persistence.pagination.default-limit=50", "nova.persistence.pagination.max-limit=10")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void theBusesGetTheErrorTranslationAndTheFlush() {
        runner.run(context -> {
            assertTrue(context.containsBean("novaPersistenceErrorBehavior"));
            assertTrue(context.containsBean("novaPersistenceFlushBehavior"));
        });
    }

    @Test
    void eachBehaviorCanBeTurnedOff() {
        runner.withPropertyValues(
                        "nova.persistence.error-translation.enabled=false", "nova.persistence.flush.enabled=false")
                .run(context -> {
                    assertFalse(context.containsBean("novaPersistenceErrorBehavior"));
                    assertFalse(context.containsBean("novaPersistenceFlushBehavior"));
                });
    }

    @Test
    void withoutCqrsThereAreNoBehaviorsAndTheAuditorFallsBack() {
        jpa.withClassLoader(new FilteredClassLoader(Command.class.getPackageName()))
                .run(context -> {
                    assertFalse(context.containsBean("novaPersistenceErrorBehavior"));
                    assertTrue(context.getBean(AuditorAware.class) instanceof SpringSecurityAuditor);
                });
    }

    @Test
    void theAuditorIsTheActorOfTheBuses() {
        jpa.withBean(ActorResolver.class, () -> () -> Optional.of("ana"))
                .run(context -> assertEquals(Optional.of("ana"), auditor(context.getBean(AuditorAware.class))));
    }

    @Test
    void withoutAnActorResolverTheAuditorIsTheSpringSecurityUser() {
        jpa.run(context -> {
            AuditorAware<?> auditor = context.getBean(AuditorAware.class);
            assertEquals(Optional.empty(), auditor.getCurrentAuditor());

            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("luis", "x", "USER"));
            assertEquals(Optional.of("luis"), auditor.getCurrentAuditor());

            SecurityContextHolder.getContext()
                    .setAuthentication(new AnonymousAuthenticationToken(
                            "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
            assertEquals(Optional.empty(), auditor.getCurrentAuditor());

            TestingAuthenticationToken unauthenticated = new TestingAuthenticationToken("luis", "x");
            unauthenticated.setAuthenticated(false);
            SecurityContextHolder.getContext().setAuthentication(unauthenticated);
            assertEquals(Optional.empty(), auditor.getCurrentAuditor());
        });
    }

    @Test
    void withoutCqrsNorSpringSecurityThereIsNoAuditor() {
        jpa.withClassLoader(new FilteredClassLoader(Command.class.getPackageName(), "org.springframework.security"))
                .run(context -> assertEquals(Optional.empty(), auditor(context.getBean(AuditorAware.class))));
    }

    @Test
    void theAuditTimeIsTheServiceClockInMicroseconds() {
        Instant now = Instant.parse("2026-10-02T15:04:05.123456789Z");
        jpa.withBean(Clock.class, () -> Clock.fixed(now, ZoneOffset.UTC))
                .run(context -> assertEquals(
                        Optional.of(Instant.parse("2026-10-02T15:04:05.123456Z")),
                        context.getBean(DateTimeProvider.class).getNow()));
        jpa.run(context ->
                assertTrue(context.getBean(DateTimeProvider.class).getNow().isPresent()));
    }

    @Test
    void withoutADatabaseThereIsNoAuditing() {
        runner.run(context -> assertFalse(context.containsBean("novaPersistenceAuditor")));
    }

    @Test
    void aServiceWithItsOwnAuditingKeepsIt() {
        jpa.withBean("jpaAuditingHandler", Object.class, Object::new)
                .run(context -> assertFalse(context.containsBean("novaPersistenceAuditor")));
        jpa.withPropertyValues("nova.persistence.auditing.enabled=false")
                .run(context -> assertFalse(context.containsBean("novaPersistenceAuditor")));
        jpa.withBean(AuditorAware.class, () -> () -> Optional.of("propio"))
                .run(context -> assertFalse(context.containsBean("novaPersistenceAuditor")));
    }

    @Test
    void aServletApplicationReceivesTheCursorRequestAsAnArgument() throws Exception {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(NovaPersistenceAutoConfiguration.class))
                .run(context -> {
                    List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();
                    context.getBean(WebMvcConfigurer.class).addArgumentResolvers(resolvers);
                    CursorRequestArgumentResolver resolver = (CursorRequestArgumentResolver) resolvers.get(0);

                    MockHttpServletRequest request = new MockHttpServletRequest();
                    request.addParameter("limit", "7");
                    request.addParameter("cursor", "abc");
                    assertEquals(
                            CursorRequest.after("abc", 7),
                            resolver.resolveArgument(null, null, new ServletWebRequest(request), null));

                    MockHttpServletRequest tooMany = new MockHttpServletRequest();
                    tooMany.addParameter("limit", "101");
                    org.junit.jupiter.api.Assertions.assertThrows(
                            ApplicationError.class,
                            () -> resolver.resolveArgument(null, null, new ServletWebRequest(tooMany), null));
                });
        runner.run(context ->
                assertTrue(context.getBeansOfType(WebMvcConfigurer.class).isEmpty()));
    }

    @Test
    void theResolverOnlyTakesCursorRequests() throws Exception {
        CursorRequestArgumentResolver resolver = new CursorRequestArgumentResolver(CursorLimits.DEFAULT);
        java.lang.reflect.Method method = NovaPersistenceAutoConfigurationTest.class.getDeclaredMethod(
                "handler", CursorRequest.class, UUID.class);

        assertTrue(resolver.supportsParameter(new org.springframework.core.MethodParameter(method, 0)));
        assertFalse(resolver.supportsParameter(new org.springframework.core.MethodParameter(method, 1)));
    }

    @SuppressWarnings("unused")
    private void handler(CursorRequest page, UUID id) {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Optional<Object> auditor(AuditorAware auditor) {
        return auditor.getCurrentAuditor();
    }
}

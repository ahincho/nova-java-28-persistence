package pe.edu.nova.java.starters.persistence;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * El actor de la auditoría de las entidades, tomado de la autenticación de Spring Security: su nombre, si hay una
 * autenticación que no sea anónima.
 *
 * <p>Esta clase solo se carga si Spring Security está en el classpath.
 */
final class SpringSecurityAuditor implements AuditorAware<String> {

    SpringSecurityAuditor() {}

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.ofNullable(authentication.getName()).filter(name -> !name.isBlank());
    }
}

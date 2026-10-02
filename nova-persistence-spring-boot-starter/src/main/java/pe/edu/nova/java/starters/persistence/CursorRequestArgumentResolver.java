package pe.edu.nova.java.starters.persistence;

import java.util.Objects;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import pe.edu.nova.java.libs.persistence.CursorLimits;
import pe.edu.nova.java.libs.persistence.CursorRequest;

/**
 * Entrega un {@link CursorRequest} a un controlador, leído de {@code ?limit=} y {@code ?cursor=} (ADR-054).
 *
 * <p>Llega validado: un límite fuera de rango es un 400 con el campo {@code limit} antes de entrar al método. El
 * cursor se valida contra el orden de la consulta, en {@link CursorPages#position}.
 */
public final class CursorRequestArgumentResolver implements HandlerMethodArgumentResolver {

    /** El parámetro de query con el límite. */
    public static final String LIMIT = "limit";

    /** El parámetro de query con el cursor. */
    public static final String CURSOR = "cursor";

    private final CursorLimits limits;

    /**
     * Crea el resolvedor.
     *
     * @param limits el límite por defecto y el máximo
     */
    public CursorRequestArgumentResolver(CursorLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CursorRequest.class.equals(parameter.getParameterType());
    }

    @Override
    public CursorRequest resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        return CursorRequest.from(webRequest.getParameter(LIMIT), webRequest.getParameter(CURSOR), limits);
    }
}

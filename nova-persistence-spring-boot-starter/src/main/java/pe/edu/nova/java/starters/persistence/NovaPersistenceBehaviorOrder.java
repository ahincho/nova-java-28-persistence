package pe.edu.nova.java.starters.persistence;

/**
 * Dónde entran los comportamientos de persistencia en la cadena de los buses de ADR-053, cuyo orden va de 100, la
 * observación, a 500, la transacción.
 */
public final class NovaPersistenceBehaviorOrder {

    /**
     * La traducción de errores: por fuera de la transacción, para alcanzar también lo que falla al abrirla y al
     * confirmarla, y por dentro de la validación.
     */
    public static final int ERROR_TRANSLATION = 450;

    /** El {@code flush} de cada comando: por dentro de la transacción, justo alrededor del handler. */
    public static final int FLUSH = 600;

    private NovaPersistenceBehaviorOrder() {}
}

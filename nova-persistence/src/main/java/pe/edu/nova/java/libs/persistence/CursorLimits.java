package pe.edu.nova.java.libs.persistence;

/**
 * Cuántos elementos trae una página cuando el cliente no lo dice, y cuántos como máximo (ADR-054).
 *
 * @param defaultLimit el límite sin {@code ?limit=}
 * @param maxLimit     el límite más alto que se acepta
 */
public record CursorLimits(int defaultLimit, int maxLimit) {

    /** Los de Nova: 20 por defecto y 100 como máximo. */
    public static final CursorLimits DEFAULT = new CursorLimits(20, 100);

    /**
     * Crea los límites.
     *
     * @throws IllegalArgumentException si el máximo es menor que 1 o el de por defecto no está entre 1 y el
     *     máximo
     */
    public CursorLimits {
        if (maxLimit < 1) {
            throw new IllegalArgumentException("maxLimit debe ser al menos 1: " + maxLimit);
        }
        if (defaultLimit < 1 || defaultLimit > maxLimit) {
            throw new IllegalArgumentException("defaultLimit debe estar entre 1 y " + maxLimit + ": " + defaultLimit);
        }
    }
}

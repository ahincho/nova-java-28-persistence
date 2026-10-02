package pe.edu.nova.java.starters.persistence;

import org.springframework.boot.context.properties.ConfigurationProperties;
import pe.edu.nova.java.libs.persistence.CursorLimits;

/** La configuración de persistencia, bajo {@code nova.persistence.*} (ADR-054). Todo está encendido por defecto. */
@ConfigurationProperties("nova.persistence")
public class NovaPersistenceProperties {

    /** Los límites de una página. */
    private final Pagination pagination = new Pagination();

    /** La auditoría de las entidades que extienden {@code AuditableEntity}. */
    private final Toggle auditing = new Toggle();

    /** La traducción de los errores de la base en los buses de CQRS. */
    private final Toggle errorTranslation = new Toggle();

    /** El {@code flush} al terminar cada comando de CQRS. */
    private final Toggle flush = new Toggle();

    /** Crea la configuración con los valores por defecto. */
    public NovaPersistenceProperties() {}

    /**
     * Los límites de una página.
     *
     * @return su configuración
     */
    public Pagination getPagination() {
        return pagination;
    }

    /**
     * La auditoría de las entidades.
     *
     * @return su configuración
     */
    public Toggle getAuditing() {
        return auditing;
    }

    /**
     * La traducción de errores.
     *
     * @return su configuración
     */
    public Toggle getErrorTranslation() {
        return errorTranslation;
    }

    /**
     * El {@code flush} de cada comando.
     *
     * @return su configuración
     */
    public Toggle getFlush() {
        return flush;
    }

    /** Los límites de una página. */
    public static class Pagination {

        /** Cuántos elementos trae una página sin {@code ?limit=}. */
        private int defaultLimit = CursorLimits.DEFAULT.defaultLimit();

        /** El límite más alto que se acepta. */
        private int maxLimit = CursorLimits.DEFAULT.maxLimit();

        /** Crea los límites de Nova: 20 y 100. */
        public Pagination() {}

        /**
         * Cuántos elementos trae una página sin {@code ?limit=}.
         *
         * @return 20 por defecto
         */
        public int getDefaultLimit() {
            return defaultLimit;
        }

        /**
         * Cambia el límite por defecto.
         *
         * @param defaultLimit el límite, entre 1 y el máximo
         */
        public void setDefaultLimit(int defaultLimit) {
            this.defaultLimit = defaultLimit;
        }

        /**
         * El límite más alto que se acepta.
         *
         * @return 100 por defecto
         */
        public int getMaxLimit() {
            return maxLimit;
        }

        /**
         * Cambia el límite máximo.
         *
         * @param maxLimit el límite, al menos 1
         */
        public void setMaxLimit(int maxLimit) {
            this.maxLimit = maxLimit;
        }

        /**
         * Los límites, validados.
         *
         * @return los límites
         * @throws IllegalArgumentException si no son coherentes
         */
        public CursorLimits toLimits() {
            return new CursorLimits(defaultLimit, maxLimit);
        }
    }

    /** Una pieza que se puede apagar. */
    public static class Toggle {

        /** Si la pieza está encendida. */
        private boolean enabled = true;

        /** Crea la configuración encendida. */
        public Toggle() {}

        /**
         * Si la pieza está encendida.
         *
         * @return {@code true} por defecto
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Enciende o apaga la pieza.
         *
         * @param enabled si está encendida
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}

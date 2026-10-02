/**
 * El conector de persistencia de Nova con Spring Boot (ADR-054).
 *
 * <p>Trae lo reutilizable de JPA: la {@link pe.edu.nova.java.starters.persistence.AuditableEntity} con su auditoría y
 * su versión, el scroll por cursor sobre el keyset de Spring Data con
 * {@link pe.edu.nova.java.starters.persistence.CursorPages}, el {@code CursorRequest} como argumento de un
 * controlador, y la traducción de los errores de la base a los de ADR-031 en los buses de CQRS.
 */
package pe.edu.nova.java.starters.persistence;

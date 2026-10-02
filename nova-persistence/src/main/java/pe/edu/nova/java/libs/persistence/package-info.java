/**
 * El núcleo de persistencia de Nova, sin framework (ADR-054 y ADR-015).
 *
 * <p>Define el contrato del scroll infinito: un cliente pide con un {@link pe.edu.nova.java.libs.persistence.CursorRequest}
 * y recibe una {@link pe.edu.nova.java.libs.persistence.CursorPage}, cuyo cursor escribe y lee el
 * {@link pe.edu.nova.java.libs.persistence.CursorCodec}. Lo que se apoya en JPA vive en el starter.
 */
package pe.edu.nova.java.libs.persistence;

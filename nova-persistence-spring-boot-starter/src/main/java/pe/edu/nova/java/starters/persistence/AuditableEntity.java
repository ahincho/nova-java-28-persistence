package pe.edu.nova.java.starters.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * La base de una entidad con auditoría y versión (ADR-054).
 *
 * <p>La auditoría de Spring Data llena las cuatro columnas al guardar: cuándo y quién la creó, y cuándo y quién la
 * cambió por última vez. El momento sale del {@code Clock} del servicio, y el actor del mismo
 * {@code ActorResolver} que la auditoría de los buses (ADR-053). La versión es la del bloqueo optimista: dos
 * cambios sobre la misma versión no se pisan, el segundo es un 409.
 *
 * <p>Las columnas son {@code created_at}, {@code updated_at}, {@code created_by}, {@code updated_by} y
 * {@code version}. Las de fecha y la versión no admiten nulos; las de actor sí, porque un cambio puede no tener
 * actor, como el de una tarea programada.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private String updatedBy;

    @Version
    @Column(nullable = false)
    private Long version;

    /** Para las entidades que la extienden. */
    protected AuditableEntity() {}

    /**
     * Cuándo se creó.
     *
     * @return el instante, o {@code null} antes de guardarla por primera vez
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Cuándo cambió por última vez.
     *
     * @return el instante, o {@code null} antes de guardarla por primera vez
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Quién la creó.
     *
     * @return el actor, o {@code null} si no había uno
     */
    public String getCreatedBy() {
        return createdBy;
    }

    /**
     * Quién la cambió por última vez.
     *
     * @return el actor, o {@code null} si no había uno
     */
    public String getUpdatedBy() {
        return updatedBy;
    }

    /**
     * La versión del bloqueo optimista, que sube con cada cambio guardado.
     *
     * @return la versión, o {@code null} antes de guardarla por primera vez
     */
    public Long getVersion() {
        return version;
    }
}

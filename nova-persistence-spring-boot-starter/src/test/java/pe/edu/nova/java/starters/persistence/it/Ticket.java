package pe.edu.nova.java.starters.persistence.it;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import pe.edu.nova.java.starters.persistence.AuditableEntity;

/** Un ticket, con un código único. */
@Entity
@Table(name = "ticket")
public class Ticket extends AuditableEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    protected Ticket() {}

    Ticket(UUID id, String owner, String code, String title) {
        this.id = id;
        this.owner = owner;
        this.code = code;
        this.title = title;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    void rename(String title) {
        this.title = title;
    }
}

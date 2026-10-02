package pe.edu.nova.java.starters.persistence.it;

import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.JpaRepository;

/** Los tickets guardados, con la consulta por keyset de ADR-054. */
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Window<Ticket> findByOwner(String owner, ScrollPosition position, Limit limit, Sort sort);
}

package pe.edu.nova.java.starters.persistence.it;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.nova.java.libs.cqrs.ActorResolver;
import pe.edu.nova.java.libs.cqrs.Command;
import pe.edu.nova.java.libs.cqrs.CommandHandler;
import pe.edu.nova.java.libs.cqrs.Query;
import pe.edu.nova.java.libs.cqrs.QueryBus;
import pe.edu.nova.java.libs.cqrs.QueryHandler;
import pe.edu.nova.java.libs.persistence.CursorPage;
import pe.edu.nova.java.libs.persistence.CursorRequest;
import pe.edu.nova.java.starters.persistence.CursorPages;
import pe.edu.nova.java.starters.persistence.CursorSort;

/** Un servicio de prueba con tickets: una entidad auditable, sus comandos, su consulta paginada y su controlador. */
@SpringBootApplication
public class Tickets {

    /** El momento fijo del reloj del servicio: todos los tickets se crean en el mismo instante. */
    public static final Instant NOW = Instant.parse("2026-10-02T15:04:05.123456789Z");

    /** El actor de la auditoría, el mismo de los buses. */
    public static final String ACTOR = "ana";

    /** Del más nuevo al más viejo, y por id para desempatar. */
    public static final CursorSort NEWEST =
            CursorSort.of("newest", Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

    @Bean
    Clock clock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Bean
    ActorResolver actorResolver() {
        return () -> Optional.of(ACTOR);
    }

    /** La vista de un ticket. */
    public record TicketView(UUID id, String title) {}

    /** Abre un ticket. */
    public record OpenTicket(UUID id, String owner, String code, String title) implements Command<UUID> {}

    /** Cambia el título de un ticket después de que otro lo cambió. */
    public record RenameAfterAnotherChange(UUID id, String title) implements Command<Void> {}

    /** Los tickets de un dueño, de a una página. */
    public record ListTickets(String owner, CursorRequest page) implements Query<CursorPage<TicketView>> {}

    @Component
    static class OpenTicketHandler implements CommandHandler<OpenTicket, UUID> {

        private final TicketRepository tickets;

        OpenTicketHandler(TicketRepository tickets) {
            this.tickets = tickets;
        }

        @Override
        public UUID handle(OpenTicket command) {
            return tickets.save(new Ticket(command.id(), command.owner(), command.code(), command.title()))
                    .getId();
        }
    }

    /** Simula otra transacción que confirmó un cambio entre la lectura y la escritura de esta. */
    @Component
    static class RenameAfterAnotherChangeHandler implements CommandHandler<RenameAfterAnotherChange, Void> {

        private final TicketRepository tickets;
        private final JdbcTemplate jdbc;

        RenameAfterAnotherChangeHandler(TicketRepository tickets, JdbcTemplate jdbc) {
            this.tickets = tickets;
            this.jdbc = jdbc;
        }

        @Override
        public Void handle(RenameAfterAnotherChange command) {
            Ticket ticket = tickets.findById(command.id()).orElseThrow();
            jdbc.update("update ticket set version = version + 1 where id = ?", command.id());
            ticket.rename(command.title());
            return null;
        }
    }

    @Component
    static class ListTicketsHandler implements QueryHandler<ListTickets, CursorPage<TicketView>> {

        private final TicketRepository tickets;

        ListTicketsHandler(TicketRepository tickets) {
            this.tickets = tickets;
        }

        @Override
        public CursorPage<TicketView> handle(ListTickets query) {
            Window<Ticket> window = tickets.findByOwner(
                    query.owner(),
                    CursorPages.position(query.page(), NEWEST),
                    CursorPages.limit(query.page()),
                    NEWEST.sort());
            return CursorPages.page(window, NEWEST, ticket -> new TicketView(ticket.getId(), ticket.getTitle()));
        }
    }

    @RestController
    static class TicketController {

        private final QueryBus queries;

        TicketController(QueryBus queries) {
            this.queries = queries;
        }

        @GetMapping("/tickets")
        CursorPage<TicketView> list(@RequestParam String owner, CursorRequest page) {
            return queries.execute(new ListTickets(owner, page));
        }
    }
}

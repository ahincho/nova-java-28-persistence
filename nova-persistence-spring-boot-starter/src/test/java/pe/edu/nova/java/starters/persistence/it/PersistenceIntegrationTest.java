package pe.edu.nova.java.starters.persistence.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.nova.java.libs.api.standard.error.ApplicationError;
import pe.edu.nova.java.libs.api.standard.error.FieldError;
import pe.edu.nova.java.libs.cqrs.CommandBus;
import pe.edu.nova.java.libs.cqrs.QueryBus;
import pe.edu.nova.java.libs.persistence.CursorCodec;
import pe.edu.nova.java.libs.persistence.CursorPage;
import pe.edu.nova.java.libs.persistence.CursorRequest;
import pe.edu.nova.java.starters.persistence.PersistenceErrors;
import pe.edu.nova.java.starters.persistence.it.Tickets.ListTickets;
import pe.edu.nova.java.starters.persistence.it.Tickets.OpenTicket;
import pe.edu.nova.java.starters.persistence.it.Tickets.RenameAfterAnotherChange;
import pe.edu.nova.java.starters.persistence.it.Tickets.TicketView;

/** La capacidad entera sobre una base de verdad, H2, con los buses de CQRS y Spring MVC. */
@SpringBootTest(classes = Tickets.class)
class PersistenceIntegrationTest {

    @Autowired
    private CommandBus commands;

    @Autowired
    private QueryBus queries;

    @Autowired
    private TicketRepository tickets;

    @Autowired
    private WebApplicationContext context;

    @BeforeEach
    void startEmpty() {
        tickets.deleteAll();
    }

    @Test
    void anAuditableEntityIsStampedWithTheServiceClockAndTheBusActor() {
        UUID id = open("bob", "T-1");

        Ticket ticket = tickets.findById(id).orElseThrow();
        assertEquals(Tickets.NOW.truncatedTo(ChronoUnit.MICROS), ticket.getCreatedAt());
        assertEquals(ticket.getCreatedAt(), ticket.getUpdatedAt());
        assertEquals(Tickets.ACTOR, ticket.getCreatedBy());
        assertEquals(Tickets.ACTOR, ticket.getUpdatedBy());
        assertEquals(0L, ticket.getVersion());
    }

    @Test
    void scrollingWalksEveryRowOnceEvenWhenTheyShareTheSortValue() {
        List<UUID> opened =
                IntStream.range(0, 5).mapToObj(i -> open("bob", "T-" + i)).toList();
        open("eve", "E-1");

        List<UUID> seen = new ArrayList<>();
        CursorPage<TicketView> page = queries.execute(new ListTickets("bob", CursorRequest.first(2)));
        int pages = 1;
        seen.addAll(ids(page));
        while (page.hasNext()) {
            page = queries.execute(new ListTickets("bob", CursorRequest.after(page.nextCursor(), 2)));
            seen.addAll(ids(page));
            pages++;
        }

        // Los cinco se crearon en el mismo instante: el orden lo decide el id, que desempata, y de a dos se ve lo
        // mismo que de una sola vez, sin repetir ni saltar ninguno.
        assertEquals(Set.copyOf(opened), Set.copyOf(seen));
        assertEquals(ids(queries.execute(new ListTickets("bob", CursorRequest.first(5)))), seen);
        assertEquals(3, pages);
        assertNull(page.nextCursor());
    }

    @Test
    void aPageThatEndsExactlyAtTheLastRowSaysThereIsNoNext() {
        open("bob", "T-1");
        open("bob", "T-2");

        CursorPage<TicketView> page = queries.execute(new ListTickets("bob", CursorRequest.first(2)));

        assertEquals(2, page.items().size());
        assertFalse(page.hasNext());
    }

    @Test
    void aCursorOfAnotherSortIsAnInvalidCursor() {
        String foreign = CursorCodec.encode("oldest", Map.of("createdAt", Tickets.NOW, "id", UUID.randomUUID()));

        ApplicationError error = assertThrows(
                ApplicationError.class, () -> queries.execute(new ListTickets("bob", CursorRequest.after(foreign, 2))));

        assertEquals(
                List.of("cursor"),
                error.fieldErrors().stream().map(FieldError::field).toList());
    }

    @Test
    void aDuplicateIsAConflictInsideTheBusThanksToTheFlush() {
        open("bob", "T-1");

        ApplicationError error = assertThrows(ApplicationError.class, () -> open("bob", "T-1"));

        assertEquals(ApplicationError.Type.CONFLICT, error.type());
        assertEquals(PersistenceErrors.DATA_CONFLICT, error.code().orElseThrow());
        assertEquals(1, tickets.count());
    }

    @Test
    void aChangeOverAStaleVersionIsAConcurrentModification() {
        UUID id = open("bob", "T-1");

        ApplicationError error =
                assertThrows(ApplicationError.class, () -> commands.execute(new RenameAfterAnotherChange(id, "nuevo")));

        assertEquals(ApplicationError.Type.CONFLICT, error.type());
        assertEquals(PersistenceErrors.CONCURRENT_MODIFICATION, error.code().orElseThrow());
        assertEquals("primero", tickets.findById(id).orElseThrow().getTitle());
    }

    @Test
    void aControllerReceivesTheCursorRequestFromTheQueryString() throws Exception {
        open("bob", "T-1");
        open("bob", "T-2");
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

        mvc.perform(get("/tickets").param("owner", "bob").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").isString());
        mvc.perform(get("/tickets").param("owner", "bob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
    }

    @Test
    void aLimitOutOfRangeNeverReachesTheController() {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

        Exception failure = assertThrows(
                Exception.class,
                () -> mvc.perform(get("/tickets").param("owner", "bob").param("limit", "101")));

        Throwable cause = failure;
        while (cause != null && !(cause instanceof ApplicationError)) {
            cause = cause.getCause();
        }
        assertNotNull(cause, "the request should fail with the ApplicationError of the limit");
        assertEquals("limit", ((ApplicationError) cause).fieldErrors().get(0).field());
        assertTrue(tickets.findAll().isEmpty());
    }

    private UUID open(String owner, String code) {
        return commands.execute(new OpenTicket(UUID.randomUUID(), owner, code, "primero"));
    }

    private static List<UUID> ids(CursorPage<TicketView> page) {
        return page.items().stream().map(TicketView::id).toList();
    }
}

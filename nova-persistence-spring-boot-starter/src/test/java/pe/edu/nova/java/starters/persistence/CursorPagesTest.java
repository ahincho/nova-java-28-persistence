package pe.edu.nova.java.starters.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import pe.edu.nova.java.libs.api.standard.error.ApplicationError;
import pe.edu.nova.java.libs.persistence.CursorCodec;
import pe.edu.nova.java.libs.persistence.CursorPage;
import pe.edu.nova.java.libs.persistence.CursorRequest;

class CursorPagesTest {

    private static final CursorSort BY_RANK =
            CursorSort.of("rank", Sort.by(Sort.Order.asc("rank"), Sort.Order.asc("id")));

    @Test
    void withoutACursorTheQueryStartsFromTheBeginning() {
        ScrollPosition position = CursorPages.position(CursorRequest.first(10), BY_RANK);

        assertTrue(position.isInitial());
        assertTrue(position instanceof KeysetScrollPosition);
        assertEquals(Limit.of(10), CursorPages.limit(CursorRequest.first(10)));
    }

    @Test
    void aCursorContinuesForwardFromItsKeys() {
        String cursor = CursorCodec.encode("rank", keys(3, 30L));

        KeysetScrollPosition position =
                (KeysetScrollPosition) CursorPages.position(CursorRequest.after(cursor, 10), BY_RANK);

        assertEquals(keys(3, 30L), position.getKeys());
        assertEquals(ScrollPosition.Direction.FORWARD, position.getDirection());
    }

    @Test
    void aCursorOfAnotherSortIsRejected() {
        String cursor = CursorCodec.encode("other", keys(3, 30L));

        assertThrows(ApplicationError.class, () -> CursorPages.position(CursorRequest.after(cursor, 10), BY_RANK));
    }

    @Test
    void aPageWithMoreAheadCarriesTheCursorOfItsLastElementInTheOrderOfTheSort() {
        Map<String, Object> springOrder = new LinkedHashMap<>();
        springOrder.put("id", 20L);
        springOrder.put("rank", 2);
        Window<Integer> window = Window.from(
                List.of(1, 2), index -> ScrollPosition.forward(index == 1 ? springOrder : keys(1, 10L)), true);

        CursorPage<String> page = CursorPages.page(window, BY_RANK, String::valueOf);

        assertEquals(List.of("1", "2"), page.items());
        assertTrue(page.hasNext());
        assertEquals(CursorCodec.encode("rank", keys(2, 20L)), page.nextCursor());
    }

    @Test
    void theLastPageHasNoCursor() {
        Window<Integer> window = Window.from(List.of(1), index -> ScrollPosition.keyset(), false);

        CursorPage<Integer> page = CursorPages.page(window, BY_RANK, value -> value);

        assertFalse(page.hasNext());
        assertEquals(List.of(1), page.items());
    }

    @Test
    void aWindowThatIsNotByKeysetOrMissesTheKeyIsAProgrammingError() {
        Window<Integer> byOffset = Window.from(List.of(1), ScrollPosition::offset, true);
        Window<Integer> withoutId = Window.from(List.of(1), index -> ScrollPosition.forward(Map.of("rank", 1)), true);

        assertThrows(IllegalStateException.class, () -> CursorPages.page(byOffset, BY_RANK, value -> value));
        assertThrows(IllegalStateException.class, () -> CursorPages.page(withoutId, BY_RANK, value -> value));
    }

    @Test
    void aSortNeedsANameAndProperties() {
        assertEquals(List.of("rank", "id"), BY_RANK.keys());
        assertThrows(IllegalArgumentException.class, () -> CursorSort.of(" ", Sort.by("id")));
        assertThrows(IllegalArgumentException.class, () -> CursorSort.of(null, Sort.by("id")));
        assertThrows(IllegalArgumentException.class, () -> CursorSort.of("none", Sort.unsorted()));
        assertThrows(NullPointerException.class, () -> CursorSort.of("none", null));
    }

    private static Map<String, Object> keys(int rank, long id) {
        Map<String, Object> keys = new LinkedHashMap<>();
        keys.put("rank", rank);
        keys.put("id", id);
        return keys;
    }
}

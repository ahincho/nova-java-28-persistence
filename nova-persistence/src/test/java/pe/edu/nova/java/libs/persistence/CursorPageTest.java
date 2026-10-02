package pe.edu.nova.java.libs.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CursorPageTest {

    @Test
    void aPageWithANextOneCarriesItsCursor() {
        CursorPage<String> page = CursorPage.of(List.of("a", "b"), "next");

        assertTrue(page.hasNext());
        assertEquals("next", page.nextCursor());
        assertEquals(Optional.of("next"), page.nextCursorIfAny());
    }

    @Test
    void theLastPageHasNoCursor() {
        CursorPage<String> page = CursorPage.last(List.of("a"));

        assertFalse(page.hasNext());
        assertNull(page.nextCursor());
        assertEquals(Optional.empty(), page.nextCursorIfAny());
    }

    @Test
    void mappingKeepsTheCursorAndTheOrder() {
        CursorPage<Integer> page =
                CursorPage.of(List.of("a", "bb", "ccc"), "next").map(String::length);

        assertEquals(List.of(1, 2, 3), page.items());
        assertEquals("next", page.nextCursor());
        assertEquals(CursorPage.last(List.of(1)), CursorPage.last(List.of("a")).map(String::length));
    }

    @Test
    void theItemsCannotChangeAfterwards() {
        List<String> source = new ArrayList<>(List.of("a"));
        CursorPage<String> page = CursorPage.last(source);
        source.add("b");

        assertEquals(List.of("a"), page.items());
        assertThrows(UnsupportedOperationException.class, () -> page.items().add("c"));
    }

    @Test
    void hasNextAndTheCursorAlwaysAgree() {
        assertThrows(IllegalArgumentException.class, () -> new CursorPage<>(List.of(), "next", false));
        assertThrows(IllegalArgumentException.class, () -> new CursorPage<>(List.of(), null, true));
        assertThrows(NullPointerException.class, () -> CursorPage.of(List.of(), null));
        assertThrows(NullPointerException.class, () -> CursorPage.last(null));
        assertThrows(
                NullPointerException.class, () -> CursorPage.last(List.of()).map(null));
    }
}

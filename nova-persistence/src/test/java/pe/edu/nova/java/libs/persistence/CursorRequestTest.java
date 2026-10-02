package pe.edu.nova.java.libs.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.nova.java.libs.api.standard.error.ApplicationError;

class CursorRequestTest {

    @Test
    void withoutParametersItIsTheFirstPageWithTheDefaultLimit() {
        CursorRequest request = CursorRequest.from(null, null, CursorLimits.DEFAULT);

        assertEquals(20, request.limit());
        assertEquals(Optional.empty(), request.cursorIfAny());
    }

    @Test
    void emptyParametersCountAsAbsent() {
        CursorRequest request = CursorRequest.from(" ", "", CursorLimits.DEFAULT);

        assertEquals(CursorRequest.first(20), request);
    }

    @Test
    void theLimitAndTheCursorArriveAsTheyWereSent() {
        CursorRequest request = CursorRequest.from(" 100 ", " abc ", CursorLimits.DEFAULT);

        assertEquals(CursorRequest.after("abc", 100), request);
        assertEquals(Optional.of("abc"), request.cursorIfAny());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101", "ten", "2.5", "99999999999"})
    void aLimitOutOfRangeIsRejectedOnTheLimitField(String limit) {
        ApplicationError error =
                assertThrows(ApplicationError.class, () -> CursorRequest.from(limit, null, CursorLimits.DEFAULT));

        assertEquals(ApplicationError.Type.INVALID_INPUT, error.type());
        assertEquals("La solicitud no es válida", error.getMessage());
        assertEquals("limit", error.fieldErrors().get(0).field());
        assertEquals(
                "Debe ser un número entre 1 y 100", error.fieldErrors().get(0).message());
    }

    @Test
    void aServiceCanSetItsOwnLimits() {
        CursorLimits limits = new CursorLimits(5, 10);

        assertEquals(5, CursorRequest.from(null, null, limits).limit());
        assertThrows(ApplicationError.class, () -> CursorRequest.from("11", null, limits));
    }

    @Test
    void aRequestNeedsAPositiveLimitAndAMeaningfulCursor() {
        assertThrows(IllegalArgumentException.class, () -> CursorRequest.first(0));
        assertThrows(IllegalArgumentException.class, () -> new CursorRequest(1, " "));
        assertThrows(NullPointerException.class, () -> CursorRequest.after(null, 1));
        assertThrows(NullPointerException.class, () -> CursorRequest.from(null, null, null));
    }

    @Test
    void limitsKeepTheDefaultWithinTheMaximum() {
        assertThrows(IllegalArgumentException.class, () -> new CursorLimits(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CursorLimits(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new CursorLimits(11, 10));
        assertEquals(new CursorLimits(20, 100), CursorLimits.DEFAULT);
    }
}

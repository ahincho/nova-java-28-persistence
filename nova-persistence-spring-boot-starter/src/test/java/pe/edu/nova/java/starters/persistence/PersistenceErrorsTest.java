package pe.edu.nova.java.starters.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import pe.edu.nova.java.libs.api.standard.error.ApplicationError;
import pe.edu.nova.java.libs.api.standard.error.ErrorType;
import pe.edu.nova.java.libs.api.standard.error.InfrastructureError;
import pe.edu.nova.java.libs.api.standard.error.NovaError;
import pe.edu.nova.java.libs.cqrs.Command;
import pe.edu.nova.java.libs.cqrs.Query;

class PersistenceErrorsTest {

    static Stream<Arguments> translations() {
        return Stream.of(
                Arguments.of(new OptimisticLockingFailureException("stale"), ApplicationError.Type.CONFLICT),
                Arguments.of(
                        new ObjectOptimisticLockingFailureException(Object.class, 1), ApplicationError.Type.CONFLICT),
                Arguments.of(new OptimisticLockException("stale"), ApplicationError.Type.CONFLICT),
                Arguments.of(new DuplicateKeyException("dup"), ApplicationError.Type.CONFLICT),
                Arguments.of(new SQLIntegrityConstraintViolationException("dup"), ApplicationError.Type.CONFLICT),
                Arguments.of(
                        new PersistenceException(new SQLException("dup", "23505")), ApplicationError.Type.CONFLICT),
                Arguments.of(new QueryTimeoutException("slow"), InfrastructureError.Type.TIMEOUT),
                Arguments.of(new SQLTimeoutException("slow"), InfrastructureError.Type.TIMEOUT),
                Arguments.of(new jakarta.persistence.QueryTimeoutException("slow"), InfrastructureError.Type.TIMEOUT),
                Arguments.of(new LockTimeoutException("slow"), InfrastructureError.Type.TIMEOUT),
                Arguments.of(
                        new RuntimeException(new SQLException("cancel", "57014")), InfrastructureError.Type.TIMEOUT),
                Arguments.of(new DataAccessResourceFailureException("down"), InfrastructureError.Type.UNAVAILABLE),
                Arguments.of(new CannotCreateTransactionException("down"), InfrastructureError.Type.UNAVAILABLE),
                Arguments.of(new SQLTransientConnectionException("down"), InfrastructureError.Type.UNAVAILABLE),
                Arguments.of(
                        new RuntimeException(new SQLException("down", "08006")), InfrastructureError.Type.UNAVAILABLE));
    }

    @ParameterizedTest
    @MethodSource("translations")
    void aDatabaseFailureBecomesItsLayeredError(Throwable failure, ErrorType expected) {
        NovaError error = PersistenceErrors.translate(failure).orElseThrow();

        assertEquals(expected, error.type());
    }

    @Test
    void conflictsCarryTheirOwnCodeAndInfrastructureNamesTheDatabase() {
        assertEquals(
                Optional.of(PersistenceErrors.CONCURRENT_MODIFICATION),
                PersistenceErrors.translate(new OptimisticLockException())
                        .orElseThrow()
                        .code());
        assertEquals(
                Optional.of(PersistenceErrors.DATA_CONFLICT),
                PersistenceErrors.translate(new DuplicateKeyException("dup"))
                        .orElseThrow()
                        .code());
        NovaError down = PersistenceErrors.translate(new CannotCreateTransactionException("down"))
                .orElseThrow();
        assertEquals(Optional.of(PersistenceErrors.UPSTREAM), down.upstream());
    }

    @Test
    void theCauseChainIsFollowed() {
        RuntimeException wrapped = new RuntimeException(new IllegalStateException(new OptimisticLockException()));

        assertEquals(
                ApplicationError.Type.CONFLICT,
                PersistenceErrors.translate(wrapped).orElseThrow().type());
    }

    @Test
    void whatIsNotFromTheDatabaseIsLeftAlone() {
        assertEquals(Optional.empty(), PersistenceErrors.translate(null));
        assertEquals(Optional.empty(), PersistenceErrors.translate(new IllegalStateException("bug")));
        assertEquals(Optional.empty(), PersistenceErrors.translate(new CannotAcquireLockException("lock")));
        assertEquals(Optional.empty(), PersistenceErrors.translate(new RuntimeException(new SQLException("x"))));
        assertEquals(Optional.empty(), PersistenceErrors.translate(new RuntimeException(new SQLException("x", "42"))));
        assertEquals(
                Optional.empty(), PersistenceErrors.translate(new RuntimeException(new SQLException("x", "42P01"))));
        assertEquals(Optional.empty(), PersistenceErrors.translate(ApplicationError.conflict("already layered")));
    }

    @Test
    void theBehaviorTranslatesCommandsAndQueriesAndPassesTheRestThrough() {
        PersistenceErrorBehavior behavior = new PersistenceErrorBehavior();
        Command<String> command = new Command<>() {};
        Query<String> query = new Query<>() {};

        assertEquals("ok", behavior.handle(command, () -> "ok"));
        ApplicationError conflict = assertThrows(
                ApplicationError.class,
                () -> behavior.handle(command, () -> {
                    throw new DuplicateKeyException("dup");
                }));
        assertEquals(ApplicationError.Type.CONFLICT, conflict.type());
        InfrastructureError timeout = assertThrows(
                InfrastructureError.class,
                () -> behavior.handle(query, () -> {
                    throw new QueryTimeoutException("slow");
                }));
        assertEquals(InfrastructureError.Type.TIMEOUT, timeout.type());
        IllegalStateException bug = new IllegalStateException("bug");
        assertSame(
                bug,
                assertThrows(
                        IllegalStateException.class,
                        () -> behavior.handle(query, () -> {
                            throw bug;
                        })));
        assertTrue(behavior.handle(query, () -> "read").startsWith("read"));
    }
}

package com.nsu.capstone.identity.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nsu.capstone.global.exception.*;
import com.nsu.capstone.identity.domain.*;
import com.nsu.capstone.identity.oauth.*;
import com.nsu.capstone.identity.presentation.dto.ArtistSignupResponse;
import com.nsu.capstone.identity.repository.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ArtistOAuthSignupServiceTest {
    private final OAuthSignupStateStore states = mock(OAuthSignupStateStore.class);
    private final OAuthSignupPersistenceService persistence = mock(OAuthSignupPersistenceService.class);
    private final OAuthOpaqueValueService opaque = mock(OAuthOpaqueValueService.class);
    private final ArtistOAuthSignupService service = new ArtistOAuthSignupService(states, persistence, opaque);
    private final OAuthSignupStateStore.Claimed claimed = new OAuthSignupStateStore.Claimed(
        OAuthProvider.GOOGLE, "subject", "user@example.com", "01012345678");

    @Test
    void cleanupFailureAfterCommitKeepsSuccessAndDoesNotReleaseClaim() {
        when(opaque.generate()).thenReturn("owner");
        when(states.claim("session", "owner", UserRole.ARTIST)).thenReturn(claimed);
        var response = new ArtistSignupResponse(UUID.randomUUID(), "user@example.com", UserRole.ARTIST, UserStatus.ACTIVE);
        when(persistence.createArtist(eq(claimed), any())).thenReturn(response);
        doThrow(new IllegalStateException("sensitive detail")).when(states).cleanup("session", "owner");
        assertEquals(response, service.signup("session"));
        verify(states, never()).release(anyString(), anyString());
    }

    @Test
    void uncertainTransactionExceptionDoesNotImmediatelyReleaseClaim() {
        when(opaque.generate()).thenReturn("owner");
        when(states.claim("session", "owner", UserRole.ARTIST)).thenReturn(claimed);
        when(persistence.createArtist(eq(claimed), any())).thenThrow(new IllegalStateException("commit outcome unknown"));
        assertThrows(IllegalStateException.class, () -> service.signup("session"));
        verify(states, never()).release(anyString(), anyString());
        verify(states, never()).cleanup(anyString(), anyString());
    }

    @Test
    void confirmedRollbackCallbackReleasesOnlyItsOwnerEvenWhenReleaseFails() {
        when(opaque.generate()).thenReturn("owner");
        when(states.claim("session", "owner", UserRole.ARTIST)).thenReturn(claimed);
        doThrow(new IllegalStateException()).when(states).release("session", "owner");
        when(persistence.createArtist(eq(claimed), any())).thenAnswer(call -> {
            ((Runnable) call.getArgument(1)).run();
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        });
        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS,
            assertThrows(BusinessException.class, () -> service.signup("session")).getErrorCode());
        verify(states).release("session", "owner");
        verify(states, never()).cleanup(anyString(), anyString());
    }

    @Test
    void transactionCompletionReleasesOnlyOnConfirmedRollback() {
        for (int status : new int[] {TransactionSynchronization.STATUS_COMMITTED,
            TransactionSynchronization.STATUS_UNKNOWN, TransactionSynchronization.STATUS_ROLLED_BACK}) {
            UserRepository users = mock(UserRepository.class);
            OAuthAccountRepository accounts = mock(OAuthAccountRepository.class);
            UserIdGenerator ids = mock(UserIdGenerator.class);
            Runnable rollback = mock(Runnable.class);
            when(ids.generate()).thenReturn(UUID.randomUUID());
            when(users.save(any())).thenAnswer(call -> call.getArgument(0));
            TransactionSynchronizationManager.initSynchronization();
            try {
                new OAuthSignupPersistenceService(users, accounts, ids).createArtist(claimed, rollback);
                TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(status));
                verify(rollback, times(status == TransactionSynchronization.STATUS_ROLLED_BACK ? 1 : 0)).run();
            } finally { TransactionSynchronizationManager.clearSynchronization(); }
        }
    }
}

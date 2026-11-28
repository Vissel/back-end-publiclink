package com.qrpublic.apartment.user.service;

import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.user.entity.UserEntity;
import com.qrpublic.apartment.user.repository.UserEntityRepository;
import com.qrpublic.apartment.user.service.request.ChangeOwnPasswordRequest;
import com.qrpublic.apartment.user.service.response.ChangePasswordResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Own-password change at the place that actually holds the secret. The current
 * password must match the stored hash before anything is written: a valid
 * token, a replayed request or a guessed encrypted blob must all leave the
 * account untouched, because this is the only step standing between "logged in"
 * and "account taken over".
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplChangeOwnPasswordTest {

    private static final String USERNAME = "micro01";
    private static final String ENC_CURRENT = "enc-current";
    private static final String ENC_NEXT = "enc-next";
    private static final String CURRENT = "oldsecret123";
    private static final String NEXT = "newsecret456";
    private static final String STORED_HASH = "stored-hash";

    @Mock
    private UserEntityRepository userEntityRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RsaClient rsaClient;
    @Mock
    private PlatformTransactionManager transactionManager;

    private UserServiceImpl service;

    /**
     * Built by hand rather than with @InjectMocks: the service takes its
     * transaction manager through the constructor, and Mockito stops at
     * constructor injection, leaving the @Autowired collaborators null.
     */
    @BeforeEach
    void wireService() {
        service = new UserServiceImpl(transactionManager);
        ReflectionTestUtils.setField(service, "userEntityRepository", userEntityRepository);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(service, "rsaClient", rsaClient);
    }

    private ChangeOwnPasswordRequest request(String current, String next) {
        ChangeOwnPasswordRequest request = new ChangeOwnPasswordRequest();
        request.setUserName(USERNAME);
        request.setCurrentEncryptedPassword(current);
        request.setNewEncryptedPassword(next);
        return request;
    }

    private UserEntity storedAccount() {
        UserEntity existing = new UserEntity();
        existing.setUsername(USERNAME);
        existing.setPassword(STORED_HASH);
        existing.setMustChangePassword(true);
        return existing;
    }

    /** The browser encrypted both values, so both are handed back decrypted. */
    private void decryptsTo(String current, String next) {
        when(rsaClient.decrypt(ENC_CURRENT)).thenReturn(current);
        when(rsaClient.decrypt(ENC_NEXT)).thenReturn(next);
    }

    private Result<ChangePasswordResponse> change(ChangeOwnPasswordRequest request) {
        return service.doChangeOwnPassword(request);
    }

    @Test
    void changesThePasswordWhenTheCurrentOneIsProved() {
        UserEntity existing = storedAccount();
        decryptsTo(CURRENT, NEXT);
        when(userEntityRepository.findByUsername(USERNAME)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches(CURRENT, STORED_HASH)).thenReturn(true);
        when(passwordEncoder.matches(NEXT, STORED_HASH)).thenReturn(false);
        when(passwordEncoder.encode(NEXT)).thenReturn("new-hash");

        Result<ChangePasswordResponse> result = change(request(ENC_CURRENT, ENC_NEXT));

        assertThat(result.isSuccess()).isTrue();
        assertThat(existing.getPassword()).isEqualTo("new-hash");
        assertThat(result.getData().getUserName()).isEqualTo(USERNAME);
    }

    @Test
    void aProvedOwnChangeEndsTheForcedFirstLoginChange() {
        UserEntity existing = storedAccount();
        decryptsTo(CURRENT, NEXT);
        when(userEntityRepository.findByUsername(USERNAME)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches(CURRENT, STORED_HASH)).thenReturn(true);
        when(passwordEncoder.matches(NEXT, STORED_HASH)).thenReturn(false);
        when(passwordEncoder.encode(NEXT)).thenReturn("new-hash");

        change(request(ENC_CURRENT, ENC_NEXT));

        assertThat(existing.getMustChangePassword()).isFalse();
        verify(userEntityRepository).save(existing);
    }

    @Test
    void writesNothingWhenTheCurrentPasswordIsWrong() {
        UserEntity existing = storedAccount();
        decryptsTo("wrong-secret", NEXT);
        when(userEntityRepository.findByUsername(USERNAME)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("wrong-secret", STORED_HASH)).thenReturn(false);

        Result<ChangePasswordResponse> result = change(request(ENC_CURRENT, ENC_NEXT));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(403);
        assertThat(existing.getPassword()).isEqualTo(STORED_HASH);
        verify(userEntityRepository, never()).save(any());
    }

    @Test
    void anUndecryptableCurrentPasswordIsRejectedRatherThanAccepted() {
        decryptsTo(null, NEXT);
        when(userEntityRepository.findByUsername(USERNAME)).thenReturn(Optional.of(storedAccount()));

        Result<ChangePasswordResponse> result = change(request(ENC_CURRENT, ENC_NEXT));

        assertThat(result.getErrorCode()).isEqualTo(403);
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void refusesToReuseTheCurrentPasswordAsTheNewOne() {
        UserEntity existing = storedAccount();
        decryptsTo(CURRENT, CURRENT);
        when(userEntityRepository.findByUsername(USERNAME)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches(CURRENT, STORED_HASH)).thenReturn(true);

        Result<ChangePasswordResponse> result = change(request(ENC_CURRENT, ENC_NEXT));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(400);
        verify(userEntityRepository, never()).save(any());
    }

    @Test
    void enforcesTheMinimumLengthOnTheDecryptedValue() {
        decryptsTo(CURRENT, "short");

        Result<ChangePasswordResponse> result = change(request(ENC_CURRENT, ENC_NEXT));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(400);
        assertThat(result.getErrorMessage()).isEqualTo("New password must be at least 8 characters");
        // the length is settled before the account is even loaded
        verify(userEntityRepository, never()).findByUsername(any());
        verify(userEntityRepository, never()).save(any());
    }

    @Test
    void reportsAnUnknownAccountAsNotFound() {
        decryptsTo(CURRENT, NEXT);
        when(userEntityRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        Result<ChangePasswordResponse> result = change(request(ENC_CURRENT, ENC_NEXT));

        assertThat(result.getErrorCode()).isEqualTo(404);
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void refusesWhenAnyPartIsMissingAndTouchesNothing() {
        assertThat(change(null).getErrorCode()).isEqualTo(400);
        assertThat(change(request(null, ENC_NEXT)).getErrorCode()).isEqualTo(400);
        assertThat(change(request(ENC_CURRENT, "   ")).getErrorCode()).isEqualTo(400);

        ChangeOwnPasswordRequest anonymous = request(ENC_CURRENT, ENC_NEXT);
        anonymous.setUserName("  ");
        assertThat(change(anonymous).getErrorCode()).isEqualTo(400);

        verify(rsaClient, never()).decrypt(any());
        verify(userEntityRepository, never()).save(any());
    }
}

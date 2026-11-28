package com.qrpublic.apartment.service;

import com.qrpublic.apartment.adapter.authentication.response.FindUserResponse;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.entity.User;
import com.qrpublic.apartment.integration.OperatedUserClient;
import com.qrpublic.apartment.user.request.SelfRegisterRequest;
import com.qrpublic.apartment.user.response.SelfRegisterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Duplicate guard of the open registration: username first, then email, then
 * full name, and each refusal has to name the input so the form can mark the
 * field the visitor must change instead of only showing a banner.
 */
@ExtendWith(MockitoExtension.class)
class SelfRegisterServiceTest {

    @Mock
    private CoreUserService coreUserService;
    @Mock
    private OperatedUserClient operatedUserClient;
    @InjectMocks
    private SelfRegisterService service;

    private SelfRegisterRequest request;

    @BeforeEach
    void setUp() {
        request = new SelfRegisterRequest();
        request.setUsername("new.seller");
        request.setName("New Seller");
        request.setEmail("new@example.com");
        request.setPassword("encrypted-password");
    }

    private static User existingAccount() {
        User user = new User();
        user.setUserName("existing.seller");
        user.setName("Existing Seller");
        user.setEmail("existing@example.com");
        return user;
    }

    @Test
    void refusesAUsernameThatAlreadyExists() {
        when(coreUserService.findUserEntityByUsername("new.seller"))
                .thenReturn(Optional.of(existingAccount()));

        ResponseEntity<SelfRegisterResponse> response = service.selfRegister(request).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getField()).isEqualTo("username");
        assertThat(response.getBody().getMessage()).contains("username is already taken");
        // The first clash ends the check: no email/name query, no remote call.
        verify(coreUserService, never()).isEmailInUse(anyString());
        verify(operatedUserClient, never()).findUserByUsername(any());
    }

    @Test
    void refusesAnEmailThatAlreadyExists() {
        when(coreUserService.findUserEntityByUsername(anyString())).thenReturn(Optional.empty());
        when(coreUserService.isEmailInUse("new@example.com")).thenReturn(true);

        ResponseEntity<SelfRegisterResponse> response = service.selfRegister(request).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getField()).isEqualTo("email");
        assertThat(response.getBody().getMessage()).contains("email address is already registered");
        verify(coreUserService, never()).isNameInUse(anyString());
        verify(operatedUserClient, never()).findUserByUsername(any());
    }

    @Test
    void refusesAFullNameThatAlreadyExists() {
        when(coreUserService.findUserEntityByUsername(anyString())).thenReturn(Optional.empty());
        when(coreUserService.isEmailInUse(anyString())).thenReturn(false);
        when(coreUserService.isNameInUse("New Seller")).thenReturn(true);

        ResponseEntity<SelfRegisterResponse> response = service.selfRegister(request).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getField()).isEqualTo("name");
        assertThat(response.getBody().getMessage()).contains("full name already exists");
        verify(operatedUserClient, never()).findUserByUsername(any());
    }

    /**
     * A blank name is not a value to compare: the username stands in for it, and
     * the account service still has to be asked about the username itself.
     */
    @Test
    void aBlankNameIsNotComparedAndTheRemoteCheckStillRuns() {
        request.setName("   ");
        when(coreUserService.findUserEntityByUsername(anyString())).thenReturn(Optional.empty());
        when(coreUserService.isEmailInUse(anyString())).thenReturn(false);
        when(operatedUserClient.findUserByUsername(any())).thenReturn(Mono.just(new FindUserResponse()));

        ResponseEntity<SelfRegisterResponse> response = service.selfRegister(request).block();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getField()).isEqualTo("username");
        verify(coreUserService, never()).isNameInUse(anyString());
    }
}

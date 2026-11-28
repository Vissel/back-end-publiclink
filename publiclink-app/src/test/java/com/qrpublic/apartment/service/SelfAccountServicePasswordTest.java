package com.qrpublic.apartment.service;

import com.qrpublic.apartment.account.request.ChangePasswordRequest;
import com.qrpublic.apartment.adapter.user.request.OwnPasswordChangeRequest;
import com.qrpublic.apartment.adapter.user.response.UserChangePasswordResponse;
import com.qrpublic.apartment.core.service.CoreSellerPaymentPlanService;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.core.service.CoreYogaEnvironmentService;
import com.qrpublic.apartment.integration.OperatedUserClient;
import com.qrpublic.apartment.repository.UserRepository;
import com.qrpublic.apartment.template.model.Result;
import com.qrpublic.apartment.template.service.PublicLinkServiceTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Changing one's own password goes through the account service, so this layer
 * has exactly two jobs: bind the change to the caller's JWT identity (never a
 * name from the body) and carry the account service's answer back intact,
 * because the browser puts the reason on the field that caused it.
 */
@ExtendWith(MockitoExtension.class)
class SelfAccountServicePasswordTest {

    @Spy
    private PublicLinkServiceTemplate publicLinkServiceTemplate = new PublicLinkServiceTemplate();
    @Mock
    private CoreUserService coreUserService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CoreSellerPaymentPlanService coreSellerPaymentPlanService;
    @Mock
    private CoreYogaEnvironmentService coreYogaEnvironmentService;
    @Mock
    private OperatedUserClient operatedUserClient;
    @InjectMocks
    private SelfAccountService service;

    private ChangePasswordRequest request(String current, String next) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(next);
        return request;
    }

    private void remoteAnswer(com.qrpublic.apartment.adapter.template.Result<UserChangePasswordResponse> answer) {
        when(operatedUserClient.changeOwnPassword(any(OwnPasswordChangeRequest.class)))
                .thenReturn(Mono.justOrEmpty(answer));
    }

    private OwnPasswordChangeRequest sentRemote() {
        ArgumentCaptor<OwnPasswordChangeRequest> captor = ArgumentCaptor.forClass(OwnPasswordChangeRequest.class);
        verify(operatedUserClient).changeOwnPassword(captor.capture());
        return captor.getValue();
    }

    @Test
    void sendsTheCallerIdentityRatherThanAnythingFromTheBody() {
        remoteAnswer(com.qrpublic.apartment.adapter.template.Result.success(new UserChangePasswordResponse()));

        Result<Void> result = service.changePassword(request("enc-current", "enc-next"), "micro01");

        assertThat(result.isSuccess()).isTrue();
        OwnPasswordChangeRequest sent = sentRemote();
        assertThat(sent.getUserName()).isEqualTo("micro01");
        // the browser already encrypted both values with the gateway key
        assertThat(sent.getCurrentEncryptedPassword()).isEqualTo("enc-current");
        assertThat(sent.getNewEncryptedPassword()).isEqualTo("enc-next");
    }

    @Test
    void passesTheRejectionOfTheCurrentPasswordBackAsAPermissionError() {
        remoteAnswer(com.qrpublic.apartment.adapter.template.Result
                .<UserChangePasswordResponse>error(403, "Current password is not correct"));

        Result<Void> result = service.changePassword(request("enc-current", "enc-next"), "micro01");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(403);
        assertThat(result.getErrorMessage()).isEqualTo("Current password is not correct");
    }

    @Test
    void passesARejectedNewPasswordBackWithItsOwnCode() {
        remoteAnswer(com.qrpublic.apartment.adapter.template.Result
                .<UserChangePasswordResponse>error(400, "New password must be at least 8 characters"));

        Result<Void> result = service.changePassword(request("enc-current", "enc-next"), "micro01");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(400);
        assertThat(result.getErrorMessage()).isEqualTo("New password must be at least 8 characters");
    }

    @Test
    void anUnknownRemoteCodeStillReadsAsABadRequest() {
        remoteAnswer(com.qrpublic.apartment.adapter.template.Result
                .<UserChangePasswordResponse>error(0, "Rejected"));

        Result<Void> result = service.changePassword(request("enc-current", "enc-next"), "micro01");

        assertThat(result.getErrorCode()).isEqualTo(400);
        assertThat(result.getErrorMessage()).isEqualTo("Rejected");
    }

    @Test
    void aFailureWithoutAReasonStillSaysSomething() {
        remoteAnswer(com.qrpublic.apartment.adapter.template.Result
                .<UserChangePasswordResponse>error(400, null));

        Result<Void> result = service.changePassword(request("enc-current", "enc-next"), "micro01");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("Failed to change the password.");
    }

    @Test
    void anUnreachableAccountServiceIsABadGateway() {
        when(operatedUserClient.changeOwnPassword(any(OwnPasswordChangeRequest.class)))
                .thenReturn(Mono.error(new IllegalStateException("connection refused")));

        Result<Void> result = service.changePassword(request("enc-current", "enc-next"), "micro01");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(502);
        assertThat(result.getErrorMessage()).isEqualTo("Could not reach the account service.");
    }

    @Test
    void anEmptyAnswerFromTheAccountServiceIsAlsoUnreachable() {
        when(operatedUserClient.changeOwnPassword(any(OwnPasswordChangeRequest.class)))
                .thenReturn(Mono.empty());

        Result<Void> result = service.changePassword(request("enc-current", "enc-next"), "micro01");

        assertThat(result.getErrorCode()).isEqualTo(502);
    }

    @Test
    void refusesToSendWithoutTheCurrentPassword() {
        Result<Void> result = service.changePassword(request(null, "enc-next"), "micro01");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(400);
        verify(operatedUserClient, never()).changeOwnPassword(any());
    }

    @Test
    void refusesToSendWithoutANewPassword() {
        Result<Void> result = service.changePassword(request("enc-current", "  "), "micro01");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(400);
        verify(operatedUserClient, never()).changeOwnPassword(any());
    }

    @Test
    void refusesWithoutAnAuthenticatedIdentity() {
        Result<Void> result = service.changePassword(request("enc-current", "enc-next"), "");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(400);
        verify(operatedUserClient, never()).changeOwnPassword(any());
    }
}

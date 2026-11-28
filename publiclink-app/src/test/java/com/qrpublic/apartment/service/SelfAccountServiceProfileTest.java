package com.qrpublic.apartment.service;

import com.qrpublic.apartment.account.request.UpdateProfileRequest;
import com.qrpublic.apartment.account.response.SelfProfileResponse;
import com.qrpublic.apartment.core.service.CoreSellerPaymentPlanService;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.core.service.CoreYogaEnvironmentService;
import com.qrpublic.apartment.entity.User;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The account page may rename itself freely, but the email is a one-time
 * capture: setup and reset links are addressed to it, so letting it change
 * would let a typo lock an account out of its own password flow.
 */
@ExtendWith(MockitoExtension.class)
class SelfAccountServiceProfileTest {

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
    @InjectMocks
    private SelfAccountService service;

    private User user(String email) {
        User user = new User("micro01", null, "Micro One", "", "SELLER");
        user.setEmail(email);
        return user;
    }

    private UpdateProfileRequest request(String name, String email) {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setName(name);
        request.setEmail(email);
        return request;
    }

    @Test
    void addsAnEmailWhileTheAccountHasNone() {
        when(coreUserService.findUserEntityByUsername("micro01")).thenReturn(Optional.of(user(null)));

        Result<SelfProfileResponse> result = service.updateProfile(
                request("Micro One", "micro@example.com"), "micro01");

        assertThat(result.isSuccess()).isTrue();
        verify(coreUserService).updateProfile(any(User.class), eq("Micro One"), eq("micro@example.com"));
    }

    @Test
    void refusesToChangeAnEmailThatIsAlreadySet() {
        when(coreUserService.findUserEntityByUsername("micro01"))
                .thenReturn(Optional.of(user("micro@example.com")));

        Result<SelfProfileResponse> result = service.updateProfile(
                request("Micro One", "other@example.com"), "micro01");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(400);
        assertThat(result.getErrorMessage()).contains("cannot change");
    }

    @Test
    void resubmittingTheSameEmailIsNotAChange() {
        when(coreUserService.findUserEntityByUsername("micro01"))
                .thenReturn(Optional.of(user("micro@example.com")));

        Result<SelfProfileResponse> result = service.updateProfile(
                request("Micro Nguyen", "MICRO@example.com"), "micro01");

        assertThat(result.isSuccess()).isTrue();
        // the email column is never rewritten, only the name
        verify(coreUserService).updateProfile(any(User.class), eq("Micro Nguyen"), isNull());
    }

    @Test
    void refusesAnEmailAnotherAccountAlreadyUses() {
        when(coreUserService.findUserEntityByUsername("micro01")).thenReturn(Optional.of(user("")));
        when(userRepository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        Result<SelfProfileResponse> result = service.updateProfile(
                request("Micro One", "taken@example.com"), "micro01");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(409);
        verify(coreUserService, never()).updateProfile(any(), any(), any());
    }

    @Test
    void rejectsAMalformedEmail() {
        when(coreUserService.findUserEntityByUsername("micro01")).thenReturn(Optional.of(user(null)));

        Result<SelfProfileResponse> result = service.updateProfile(
                request("Micro One", "not-an-email"), "micro01");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(400);
    }

    @Test
    void rejectsStructurallyBrokenEmails() {
        String[] rejected = {
                "a..b@example.com", // empty label inside
                ".micro@example.com", // leading dot
                "micro@example.com.", // trailing dot
                "micro.@example.com", // dot right before @
                "micro@example", // no top-level domain
                "micro at example.com", // space inside the local part
        };

        for (String email : rejected) {
            when(coreUserService.findUserEntityByUsername("micro01")).thenReturn(Optional.of(user(null)));

            Result<SelfProfileResponse> result = service.updateProfile(
                    request("Micro One", email), "micro01");

            assertThat(result.isSuccess()).as("accepts " + email).isFalse();
            assertThat(result.getErrorCode()).as("status for " + email).isEqualTo(400);
            assertThat(result.getErrorMessage()).as("message for " + email)
                    .isEqualTo("Please enter a valid email address.");
        }
    }

    @Test
    void rejectsAnEmailLongerThanTheColumn() {
        String local = "a".repeat(96);
        when(coreUserService.findUserEntityByUsername("micro01")).thenReturn(Optional.of(user(null)));

        Result<SelfProfileResponse> result = service.updateProfile(
                request("Micro One", local + "@example.com"), "micro01");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("The email address must be at most 100 characters.");
    }

    @Test
    void storesTheEmailLowercased() {
        when(coreUserService.findUserEntityByUsername("micro01")).thenReturn(Optional.of(user(null)));

        service.updateProfile(request("Micro One", "Micro@Example.COM"), "micro01");

        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        verify(coreUserService).updateProfile(any(User.class), eq("Micro One"), email.capture());
        assertThat(email.getValue()).isEqualTo("micro@example.com");
    }

    @Test
    void blankNameIsRejected() {
        Result<SelfProfileResponse> result = service.updateProfile(request("  ", null), "micro01");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(400);
    }

    @Test
    void profileReadReportsWhetherTheEmailIsLocked() {
        when(coreUserService.findUserEntityByUsername("micro01"))
                .thenReturn(Optional.of(user("micro@example.com")));

        Result<SelfProfileResponse> result = service.profile("micro01");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().isEmailLocked()).isTrue();
        assertThat(result.getData().getRole()).isEqualTo("SELLER");
    }

    @Test
    void unknownAccountIsNotFoundRatherThanServerFailure() {
        when(coreUserService.findUserEntityByUsername("ghost")).thenReturn(Optional.empty());

        Result<SelfProfileResponse> result = service.profile("ghost");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo(404);
    }

    @Test
    void emailIsTrimmedBeforeItIsStored() {
        when(coreUserService.findUserEntityByUsername("micro01")).thenReturn(Optional.of(user(null)));

        service.updateProfile(request("Micro One", "  micro@example.com  "), "micro01");

        ArgumentCaptor<String> email = ArgumentCaptor.forClass(String.class);
        verify(coreUserService).updateProfile(any(User.class), eq("Micro One"), email.capture());
        assertThat(email.getValue()).isEqualTo("micro@example.com");
    }
}

package com.qrpublic.apartment.domain.badminton;

import com.qrpublic.apartment.constant.LinkConstant;
import com.qrpublic.apartment.core.model.YogaEnvModel;
import com.qrpublic.apartment.core.service.CoreTimezoneService;
import com.qrpublic.apartment.core.service.CoreYogaEnvironmentService;
import com.qrpublic.apartment.exception.ApplicationException;
import com.qrpublic.apartment.service.LinkService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BadmintonEnvAccessTest {
    @Mock
    private LinkService linkService;
    @Mock
    private CoreYogaEnvironmentService coreYogaEnvironmentService;
    @Mock
    private CoreTimezoneService coreTimezoneService;
    @InjectMocks
    private BadmintonEnvAccess access;

    @Test
    void rejectsSellerWhoDoesNotOwnEnvironment() {
        stubBadmintonEnvironment();

        assertThatThrownBy(() -> access.resolveForSeller("token", "other-seller"))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("another seller")
                .satisfies(error -> assertThat(((ApplicationException) error).getErrorCode()).isEqualTo(403));
    }

    @Test
    void acceptsOwningSeller() {
        stubBadmintonEnvironment();

        assertThat(access.resolveForSeller("token", "seller").getEnvId()).isEqualTo("env-1");
    }

    private void stubBadmintonEnvironment() {
        when(linkService.validateLinkButNotExpiredValidation("token")).thenReturn(true);
        when(linkService.extractClaimByKey("token", LinkConstant.CLAIM_SUBJECT)).thenReturn("request-1");
        when(coreYogaEnvironmentService.resolveByRequestUuid("request-1"))
                .thenReturn(YogaEnvModel.builder()
                        .envId("env-1")
                        .sellerUsername("seller")
                        .productType(CoreYogaEnvironmentService.TYPE_BADMINTON)
                        .active(true)
                        .build());
    }
}

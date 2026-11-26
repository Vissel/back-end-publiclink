package com.qrpublic.apartment.domain.badminton;

import com.qrpublic.apartment.core.model.BadmintonCourtModel;
import com.qrpublic.apartment.core.model.YogaEnvModel;
import com.qrpublic.apartment.core.service.CoreBadmintonService;
import com.qrpublic.apartment.exception.ApplicationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/** Seller court/schedule management with page ownership checks. */
@Component
public class BadmintonCourtDomain {
    @Autowired
    private BadmintonEnvAccess badmintonEnvAccess;

    @Autowired
    private CoreBadmintonService coreBadmintonService;

    public BadmintonCourtModel saveCourt(String token, String sellerUsername, BadmintonCourtModel draft) {
        YogaEnvModel env = badmintonEnvAccess.resolveForSeller(token, sellerUsername);
        if (draft.getCourtCode() == null || draft.getCourtCode().isBlank()) {
            return coreBadmintonService.createCourt(env.getEnvId(), draft);
        }
        BadmintonCourtModel existing = coreBadmintonService.findByCode(draft.getCourtCode());
        if (!env.getEnvId().equals(existing.getEnvId())) {
            throw new ApplicationException("Court does not belong to this page.", 403);
        }
        return coreBadmintonService.updateCourt(draft.getCourtCode(), draft);
    }

    public void deleteCourt(String token, String sellerUsername, String courtCode) {
        YogaEnvModel env = badmintonEnvAccess.resolveForSeller(token, sellerUsername);
        assertCourtInEnv(courtCode, env.getEnvId());
        coreBadmintonService.deactivateCourt(courtCode);
    }

    public List<BadmintonCourtModel> listCourts(String token, String sellerUsername) {
        YogaEnvModel env = badmintonEnvAccess.resolveForSeller(token, sellerUsername);
        return coreBadmintonService.findCourts(env.getEnvId(), true);
    }

    private void assertCourtInEnv(String courtCode, String envId) {
        BadmintonCourtModel existing = coreBadmintonService.findByCode(courtCode);
        if (!envId.equals(existing.getEnvId())) {
            throw new ApplicationException("Court does not belong to this page.", 403);
        }
    }
}

package com.qrpublic.apartment.domain.yoga;

import com.qrpublic.apartment.core.model.YogaClassModel;
import com.qrpublic.apartment.core.model.YogaEnvModel;
import com.qrpublic.apartment.core.service.CoreYogaClassService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seller-side management of the weekly class schedule. Every entry point
 * asserts the caller owns the environment behind the public link token.
 */
@Slf4j
@Component
public class YogaClassDomain {

    @Autowired
    private YogaEnvAccess yogaEnvAccess;

    @Autowired
    private CoreYogaClassService coreYogaClassService;

    public YogaClassModel createClass(String token, String sellerUsername, YogaClassModel draft) {
        YogaEnvModel env = yogaEnvAccess.resolveForSeller(token, sellerUsername);
        return coreYogaClassService.createClass(env.getEnvId(), draft);
    }

    public YogaClassModel updateClass(String token, String sellerUsername, String classCode, YogaClassModel draft) {
        YogaEnvModel env = yogaEnvAccess.resolveForSeller(token, sellerUsername);
        assertClassInEnv(classCode, env.getEnvId());
        return coreYogaClassService.updateClass(classCode, draft);
    }

    public void deleteClass(String token, String sellerUsername, String classCode) {
        YogaEnvModel env = yogaEnvAccess.resolveForSeller(token, sellerUsername);
        assertClassInEnv(classCode, env.getEnvId());
        coreYogaClassService.deactivateClass(classCode);
    }

    public List<YogaClassModel> listClasses(String token, String sellerUsername) {
        YogaEnvModel env = yogaEnvAccess.resolveForSeller(token, sellerUsername);
        return coreYogaClassService.findActiveByEnv(env.getEnvId());
    }

    private void assertClassInEnv(String classCode, String envId) {
        YogaClassModel existing = coreYogaClassService.findByCode(classCode);
        if (!envId.equals(existing.getEnvId())) {
            throw new com.qrpublic.apartment.exception.ApplicationException(
                    "Class does not belong to this page.", 403);
        }
    }
}

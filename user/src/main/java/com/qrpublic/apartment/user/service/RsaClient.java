package com.qrpublic.apartment.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.security.PrivateKey;
import java.util.Base64;

// TODO - will enhance to load the public key from a secure location
@Slf4j
@Service
public class RsaClient {
    private final PrivateKey privateKey;

    public RsaClient(PrivateKey loadedPrivateKey) {
        // In a real application, you would load this from a secure location
        this.privateKey = loadedPrivateKey;
    }

    /**
     * Decrypt password
     *
     * @param encryptedData
     * @return
     */
    public String decrypt(String encryptedData) {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
            return new String(decryptedBytes);
        } catch (Exception e) {
            log.error("Cannot decrypt the data.");
            return null;
        }
    }
}

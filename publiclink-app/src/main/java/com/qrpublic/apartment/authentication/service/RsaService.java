package com.qrpublic.apartment.authentication.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Slf4j
@Service
public class RsaService {
    @Value("${key.path}")
    private String privateKeyPath;

    @Value("${pubkey.path}")
    private String publicKeyPath;

    private static final int RSA_2048_BYTE_LENGTH = 256;

    public byte[] loadPublicKey() throws IOException {
        return Files.readAllBytes(Paths.get(publicKeyPath));
    }

    /**
     * Decrypt password by key pair
     * @param encryptedData
     * @return
     */
    public String decrypt(String encryptedData) {
        try {
            PrivateKey privateKey = loadPrivateKey();
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
            return new String(decryptedBytes);
        } catch (Exception e) {
            log.error("Cannot decrypt the data.");
           return new String();
        }
    }

    private PrivateKey loadPrivateKey() throws Exception {
        String keyContent = new String(Files.readAllBytes(Paths.get(privateKeyPath)));

        // 1. Remove headers/footers and all whitespace
        String cleanKey = keyContent
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        // 2. Decode the Base64 string
        byte[] keyBytes = Base64.getDecoder().decode(cleanKey);

        // 3. Handle PKCS#1 to PKCS#8 Conversion
        // If the key was PKCS#1, it needs a specific DER header for Java to read it as PKCS#8
        if (keyContent.contains("BEGIN RSA PRIVATE KEY")) {
            keyBytes = convertPkcs1ToPkcs8(keyBytes);
        }

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(keySpec);
    }
    // Helper to wrap PKCS#1 raw bytes into a PKCS#8 structure for Java
    private byte[] convertPkcs1ToPkcs8(byte[] pkcs1Bytes) {
        int pkcs1Length = pkcs1Bytes.length;
        int totalLength = pkcs1Length + 22; // Standard RSA PKCS#8 header length
        byte[] pkcs8Header = {
                0x30, (byte) 0x82, (byte) ((totalLength >> 8) & 0xff), (byte) (totalLength & 0xff), // Sequence
                0x02, 0x01, 0x00, // Version
                0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00, // Algorithm ID
                0x04, (byte) 0x82, (byte) ((pkcs1Length >> 8) & 0xff), (byte) (pkcs1Length & 0xff) // Octet String
        };
        byte[] pkcs8Bytes = new byte[pkcs8Header.length + pkcs1Bytes.length];
        System.arraycopy(pkcs8Header, 0, pkcs8Bytes, 0, pkcs8Header.length);
        System.arraycopy(pkcs1Bytes, 0, pkcs8Bytes, pkcs8Header.length, pkcs1Bytes.length);
        return pkcs8Bytes;
    }
    /**
     * Performs a pre-execution check to see if the string looks like
     * a valid RSA-2048 encrypted block.
     */
    public boolean isLikelyRsaEncrypted(String base64Data) {
        if (base64Data == null || base64Data.isEmpty()) {
            return false;
        }

        try {
            // 1. Check if it's valid Base64
            byte[] decoded = Base64.getDecoder().decode(base64Data);

            // 2. Check if the size matches the RSA Key Size
            // RSA 2048-bit keys always produce 256 bytes of ciphertext
            if (decoded.length < RSA_2048_BYTE_LENGTH) {
                return false;
            }

            // 3. Optional: Check for high entropy (encrypted data looks like noise)
            // (Advanced banking logic usually stops at length/format check)

            return true;
        } catch (IllegalArgumentException e) {
            // Not valid Base64
            return false;
        }
    }
}

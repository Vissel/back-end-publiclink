package com.qrpublic.apartment.user;

import com.qrpublic.apartment.user.service.RsaClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Configuration
public class UserConfig {
    @Value("${rsa-private-key.path}")
    private String privateKeyPath;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RsaClient rsaClient() throws Exception {
        // Load once, fail fast if key is invalid
        PrivateKey privateKey = loadPrivateKey();
        return new RsaClient(privateKey);
    }

    private PrivateKey loadPrivateKey() throws Exception {
        Path path = Paths.get(privateKeyPath);
        return doLoadPrivateKey(path);
    }

    private PrivateKey doLoadPrivateKey(Path path) throws Exception {
        String keyContent = new String(Files.readAllBytes(path));
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
}

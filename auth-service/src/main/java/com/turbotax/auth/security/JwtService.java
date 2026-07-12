package com.turbotax.auth.security;

import com.turbotax.auth.service.SecretsManagerService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private final SecretsManagerService secretsManagerService;

    @Value("${jwt.expiry-minutes:15}")
    private long expiryMinutes;

    public String generateToken(UUID userId, String email, String taxpayerType) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(userId.toString())
            .claims(Map.of("email", email, "taxpayer_type", taxpayerType))
            .issuedAt(new Date(now))
            .expiration(new Date(now + expiryMinutes * 60 * 1000))
            .signWith(loadPrivateKey())
            .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
            .verifyWith(loadPublicKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private PrivateKey loadPrivateKey() {
        try {
            String raw = secretsManagerService.getJwtPrivateKey();
            boolean isPkcs1 = raw.contains("BEGIN RSA PRIVATE KEY");

            String pem = raw
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

            byte[] decoded = Base64.getDecoder().decode(pem);

            if (isPkcs1) {
                // Wrap PKCS#1 bytes into a PKCS#8 envelope so Java's KeyFactory can parse it
                decoded = wrapPkcs1InPkcs8(decoded);
            }

            return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load JWT private key", e);
        }
    }

    /**
     * Java's KeyFactory only accepts PKCS#8 (BEGIN PRIVATE KEY).
     * openssl genrsa produces PKCS#1 (BEGIN RSA PRIVATE KEY).
     * This wraps PKCS#1 bytes in a minimal PKCS#8 ASN.1 envelope without external libs.
     */
    private byte[] wrapPkcs1InPkcs8(byte[] pkcs1) {
        // PKCS#8 PrivateKeyInfo (RFC 5208) is SEQUENCE { version INTEGER, algorithm SEQUENCE, privateKey OCTET STRING }.
        // The version field is mandatory -- omitting it (as this previously did) produces a
        // structure Java's KeyFactory rejects as an invalid key.
        byte[] version = buildDerTlv(0x02, new byte[]{0x00});
        // RSA OID: 1.2.840.113549.1.1.1
        byte[] rsaOid = {0x06, 0x09, 0x2a, (byte)0x86, 0x48, (byte)0x86, (byte)0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] algorithmSeq = buildDerTlv(0x30, rsaOid);
        byte[] keyOctetString = buildDerTlv(0x04, pkcs1);
        byte[] inner = concat(concat(version, algorithmSeq), keyOctetString);
        return buildDerTlv(0x30, inner);
    }

    private byte[] buildDerTlv(int tag, byte[] value) {
        int len = value.length;
        byte[] lengthBytes;
        if (len < 128) {
            lengthBytes = new byte[]{(byte) len};
        } else if (len < 256) {
            lengthBytes = new byte[]{(byte) 0x81, (byte) len};
        } else {
            lengthBytes = new byte[]{(byte) 0x82, (byte)(len >> 8), (byte)(len & 0xff)};
        }
        byte[] result = new byte[1 + lengthBytes.length + len];
        result[0] = (byte) tag;
        System.arraycopy(lengthBytes, 0, result, 1, lengthBytes.length);
        System.arraycopy(value, 0, result, 1 + lengthBytes.length, len);
        return result;
    }

    private byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private PublicKey loadPublicKey() {
        try {
            String pem = secretsManagerService.getJwtPublicKey()
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(pem);
            return KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load JWT public key", e);
        }
    }
}

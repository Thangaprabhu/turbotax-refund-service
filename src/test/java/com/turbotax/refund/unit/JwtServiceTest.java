package com.turbotax.refund.unit;

import com.turbotax.refund.security.JwtService;
import com.turbotax.refund.service.SecretsManagerService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock SecretsManagerService secretsManagerService;

    JwtService jwtService;
    KeyPair keyPair;

    @BeforeEach
    void setup() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keyPair = gen.generateKeyPair();

        jwtService = new JwtService(secretsManagerService);
        ReflectionTestUtils.setField(jwtService, "expiryMinutes", 15L);
    }

    private String pkcs8PrivateKeyPem() {
        String base64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }

    private String publicKeyPem() {
        String base64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }

    /** Builds a genuine PKCS#1 "BEGIN RSA PRIVATE KEY" PEM from the JDK-generated key, to exercise the wrap path. */
    private String pkcs1PrivateKeyPem() {
        RSAPrivateCrtKey crt = (RSAPrivateCrtKey) keyPair.getPrivate();
        byte[] der = derSequence(
            derInteger(BigInteger.ZERO),
            derInteger(crt.getModulus()),
            derInteger(crt.getPublicExponent()),
            derInteger(crt.getPrivateExponent()),
            derInteger(crt.getPrimeP()),
            derInteger(crt.getPrimeQ()),
            derInteger(crt.getPrimeExponentP()),
            derInteger(crt.getPrimeExponentQ()),
            derInteger(crt.getCrtCoefficient())
        );
        String base64 = Base64.getEncoder().encodeToString(der);
        return "-----BEGIN RSA PRIVATE KEY-----\n" + base64 + "\n-----END RSA PRIVATE KEY-----";
    }

    private byte[] derInteger(BigInteger value) {
        return derTlv(0x02, value.toByteArray());
    }

    private byte[] derSequence(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) total += p.length;
        byte[] joined = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, joined, pos, p.length);
            pos += p.length;
        }
        return derTlv(0x30, joined);
    }

    private byte[] derTlv(int tag, byte[] value) {
        int len = value.length;
        byte[] lengthBytes;
        if (len < 128) {
            lengthBytes = new byte[]{(byte) len};
        } else if (len < 256) {
            lengthBytes = new byte[]{(byte) 0x81, (byte) len};
        } else {
            lengthBytes = new byte[]{(byte) 0x82, (byte) (len >> 8), (byte) (len & 0xff)};
        }
        byte[] result = new byte[1 + lengthBytes.length + len];
        result[0] = (byte) tag;
        System.arraycopy(lengthBytes, 0, result, 1, lengthBytes.length);
        System.arraycopy(value, 0, result, 1 + lengthBytes.length, len);
        return result;
    }

    @Test
    void generateAndValidateToken_shouldRoundtrip_withPkcs8Key() {
        when(secretsManagerService.getJwtPrivateKey()).thenReturn(pkcs8PrivateKeyPem());
        when(secretsManagerService.getJwtPublicKey()).thenReturn(publicKeyPem());

        var userId = UUID.randomUUID();
        var token = jwtService.generateToken(userId, "user@example.com", "INDIVIDUAL");

        assertThat(token).isNotBlank();
        Claims claims = jwtService.validateToken(token);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
        assertThat(claims.get("taxpayer_type", String.class)).isEqualTo("INDIVIDUAL");
    }

    @Test
    void generateToken_shouldRoundtrip_withPkcs1Key() {
        when(secretsManagerService.getJwtPrivateKey()).thenReturn(pkcs1PrivateKeyPem());
        when(secretsManagerService.getJwtPublicKey()).thenReturn(publicKeyPem());

        var userId = UUID.randomUUID();
        var token = jwtService.generateToken(userId, "user@example.com", "BUSINESS");

        Claims claims = jwtService.validateToken(token);
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
    }

    @Test
    void validateToken_shouldThrow_forTamperedToken() {
        when(secretsManagerService.getJwtPrivateKey()).thenReturn(pkcs8PrivateKeyPem());
        when(secretsManagerService.getJwtPublicKey()).thenReturn(publicKeyPem());

        var token = jwtService.generateToken(UUID.randomUUID(), "user@example.com", "INDIVIDUAL");
        var tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> jwtService.validateToken(tampered))
            .isInstanceOf(SignatureException.class);
    }

    @Test
    void generateToken_shouldThrow_whenPrivateKeyIsMalformed() {
        when(secretsManagerService.getJwtPrivateKey()).thenReturn("not-a-real-key");

        assertThatThrownBy(() -> jwtService.generateToken(UUID.randomUUID(), "user@example.com", "INDIVIDUAL"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to load JWT private key");
    }

    @Test
    void validateToken_shouldThrow_whenPublicKeyIsMalformed() {
        when(secretsManagerService.getJwtPublicKey()).thenReturn("not-a-real-key");

        assertThatThrownBy(() -> jwtService.validateToken("anything"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Failed to load JWT public key");
    }

    /**
     * buildDerTlv's 128-255-byte-length branch (the single-byte 0x81 length form) is
     * structurally unreachable through wrapPkcs1InPkcs8's actual call sites for any real RSA
     * key: the OID is always tiny (<128) and the two blob wraps around real key material are
     * always >=256 bytes. It's still correct, general-purpose logic worth covering directly.
     */
    @Test
    void buildDerTlv_shouldUseSingleByteLengthForm_for128To255ByteValues() throws Exception {
        var method = JwtService.class.getDeclaredMethod("buildDerTlv", int.class, byte[].class);
        method.setAccessible(true);

        byte[] value = new byte[130];
        byte[] result = (byte[]) method.invoke(jwtService, 0x04, value);

        assertThat(result[0]).isEqualTo((byte) 0x04);
        assertThat(result[1]).isEqualTo((byte) 0x81);
        assertThat(result[2]).isEqualTo((byte) 130);
        assertThat(result.length).isEqualTo(3 + 130);
    }
}

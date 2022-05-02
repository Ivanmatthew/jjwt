package io.jsonwebtoken.impl.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwe
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.impl.lang.Services
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.io.Deserializer
import io.jsonwebtoken.security.EcPrivateJwk
import io.jsonwebtoken.security.EncryptionAlgorithms
import io.jsonwebtoken.security.Jwks
import io.jsonwebtoken.security.KeyRequest
import io.jsonwebtoken.security.KeyResult
import io.jsonwebtoken.security.SecurityException
import org.junit.Test

import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.spec.ECParameterSpec

import static org.junit.Assert.*

class RFC7518AppendixCTest {

    private static final String rfcString(String s) {
        return s.replaceAll('[\\s]', '')
    }

    private static final Map<String, ?> fromEncoded(String s) {
        byte[] json = Decoders.BASE64URL.decode(s)
        return Services.loadFirst(Deserializer.class).deserialize(json) as Map<String, ?>
    }

    private static final Map<String, ?> fromJson(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8)
        return Services.loadFirst(Deserializer.class).deserialize(bytes) as Map<String, ?>
    }

    private static EcPrivateJwk readJwk(String json) {
        Map<String, ?> m = fromJson(json)
        return Jwks.builder().putAll(m).build() as EcPrivateJwk
    }

    // https://datatracker.ietf.org/doc/html/rfc7517#appendix-C.1
    private static final String ALICE_EPHEMERAL_JWK_STRING = rfcString('''
    {"kty":"EC",
      "crv":"P-256",
      "x":"gI0GAILBdu7T53akrFmMyGcsF3n5dO7MmwNBHKW5SV0",
      "y":"SLW_xSffzlPWrHEVI30DHM_4egVwt3NQqeUD7nMFpps",
      "d":"0_NxaRPUMQoAJt50Gz8YiTr8gRTwyEaCumd-MToTmIo"
     }''')

    private static final String BOB_PRIVATE_JWK_STRING = rfcString('''
    {"kty":"EC",
      "crv":"P-256",
      "x":"weNJy2HscCSM6AEDTDg04biOvhFhyyWvOHQfeF_PxMQ",
      "y":"e8lnCO-AlStT-NJVX-crhB7QRYhiix03illJOVAOyck",
      "d":"VEmDZpDXXK8p8N0Cndsxs924q6nS1RXFASRl6BfUqdw"
     }''')

    private static final String RFC_HEADER_JSON_STRING = rfcString('''
    {"alg":"ECDH-ES",
      "enc":"A128GCM",
      "apu":"QWxpY2U",
      "apv":"Qm9i",
      "epk":
       {"kty":"EC",
        "crv":"P-256",
        "x":"gI0GAILBdu7T53akrFmMyGcsF3n5dO7MmwNBHKW5SV0",
        "y":"SLW_xSffzlPWrHEVI30DHM_4egVwt3NQqeUD7nMFpps"
       }
     }
    ''')

    private static final byte[] RFC_DERIVED_KEY =
            [86, 170, 141, 234, 248, 35, 109, 32, 92, 34, 40, 205, 113, 167, 16, 26] as byte[]

    @Test
    void test() {
        EcPrivateJwk aliceJwk = readJwk(ALICE_EPHEMERAL_JWK_STRING)
        EcPrivateJwk bobJwk = readJwk(BOB_PRIVATE_JWK_STRING)

        Map<String, ?> RFC_HEADER = fromJson(RFC_HEADER_JSON_STRING)

        byte[] derivedKey = null

        def alg = new EcdhKeyAlgorithm() {

            //ensure keypair reflects required RFC test value:
            @Override
            protected KeyPair generateKeyPair(KeyRequest request, ECParameterSpec spec) {
                return aliceJwk.toKeyPair()
            }

            @Override
            KeyResult getEncryptionKey(KeyRequest request) throws SecurityException {
                KeyResult result = super.getEncryptionKey(request)
                // save result derived key so we can compare with the RFC value:
                derivedKey = result.getKey().getEncoded()
                return result
            }
        }

        String jwe = Jwts.jweBuilder()
                .setHeader(Jwts.jweHeader()
                        .setAgreementPartyUInfo("Alice")
                        .setAgreementPartyVInfo("Bob"))
                .claim("Hello", "World")
                .encryptWith(EncryptionAlgorithms.A128GCM)
                .withKeyFrom(bobJwk.toPublicJwk().toKey(), alg)
                .compact()

        // Ensure the protected header produced by JJWT is identical to the one in the RFC:
        String encodedProtectedHeader = jwe.substring(0, jwe.indexOf('.'))
        Map<String, ?> protectedHeader = fromEncoded(encodedProtectedHeader)
        assertEquals RFC_HEADER, protectedHeader

        assertNotNull derivedKey
        assertArrayEquals RFC_DERIVED_KEY, derivedKey

        // now reverse the process and ensure it all works:
        Jwe<Claims> claimsJwe = Jwts.parserBuilder()
                .decryptWith(bobJwk.toKey())
                .build().parseClaimsJwe(jwe)

        assertEquals RFC_HEADER, claimsJwe.getHeader()
        assertEquals "World", claimsJwe.getBody().get("Hello")
    }
}
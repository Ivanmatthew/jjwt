package io.jsonwebtoken.impl.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.UnsupportedJwtException
import io.jsonwebtoken.security.JwsAlgorithms
import io.jsonwebtoken.security.SignatureAlgorithm
import io.jsonwebtoken.security.SignatureException
import org.junit.Test

import java.security.PrivateKey
import java.security.PublicKey

import static org.junit.Assert.*

class EdSignatureAlgorithmTest {

    static List<EdSignatureAlgorithm> algs = [JwsAlgorithms.EdDSA, JwsAlgorithms.Ed25519, JwsAlgorithms.Ed448] as List<EdSignatureAlgorithm>

    @Test
    void testJcaName() {
        assertEquals JwsAlgorithms.EdDSA.getId(), JwsAlgorithms.EdDSA.getJcaName()
        assertEquals EdwardsCurve.Ed25519.getId(), JwsAlgorithms.Ed25519.getJcaName()
        assertEquals EdwardsCurve.Ed448.getId(), JwsAlgorithms.Ed448.getJcaName()
    }

    @Test
    void testId() {
        //There is only one signature algorithm ID defined for Edwards curve keys per
        // https://www.rfc-editor.org/rfc/rfc8037#section-3.1 and
        // https://www.rfc-editor.org/rfc/rfc8037#section-5
        //
        // As such, the Ed25519 and Ed448 SignatureAlgorithm instances _must_ reflect the same ID since that's the
        // only one recognized by the spec.  They are effectively just aliases of EdDSA but have the added
        // functionality of generating Ed25519 and Ed448 keys, that's the only difference.
        for (EdSignatureAlgorithm alg : algs) {
            assertEquals JwsAlgorithms.EdDSA.getId(), alg.getId(); // all aliases of EdDSA per the RFC spec
        }
    }

    @Test
    void testKeyPairBuilder() {
        algs.each {
            def pair = it.keyPairBuilder().build()
            assertNotNull pair.public
            assertTrue pair.public instanceof PublicKey
            String alg = pair.public.getAlgorithm()
            assertTrue JwsAlgorithms.EdDSA.getId().equals(alg) || alg.equals(it.preferredCurve.getId())

            alg = pair.private.getAlgorithm()
            assertTrue JwsAlgorithms.EdDSA.getId().equals(alg) || alg.equals(it.preferredCurve.getId())
        }
    }

    /**
     * Likely when keys are from an HSM or PKCS key store
     */
    @Test
    void testGetAlgorithmJcaNameWhenCantFindCurve() {
        def key = new TestKey(algorithm: 'foo')
        algs.each {
            def payload = [0x00] as byte[]
            def req = new DefaultSecureRequest(payload, null , null, key)
            assertEquals it.getJcaName(), it.getJcaName(req)
        }
    }

    @Test
    void testEd25519SigVerifyWithEd448() {
        testIncorrectVerificationKey(JwsAlgorithms.Ed25519, TestKeys.Ed25519.pair.private, TestKeys.Ed448.pair.public)
    }

    @Test
    void testEd25519SigVerifyWithX25519() {
        testInvalidVerificationKey(JwsAlgorithms.Ed25519, TestKeys.Ed25519.pair.private, TestKeys.X25519.pair.public)
    }

    @Test
    void testEd25519SigVerifyWithX448() {
        testInvalidVerificationKey(JwsAlgorithms.Ed25519, TestKeys.Ed25519.pair.private, TestKeys.X448.pair.public)
    }

    @Test
    void testEd448SigVerifyWithEd25519() {
        testIncorrectVerificationKey(JwsAlgorithms.Ed448, TestKeys.Ed448.pair.private, TestKeys.Ed25519.pair.public)
    }

    @Test
    void testEd448SigVerifyWithX25519() {
        testInvalidVerificationKey(JwsAlgorithms.Ed448, TestKeys.Ed448.pair.private, TestKeys.X25519.pair.public)
    }

    @Test
    void testEd448SigVerifyWithX448() {
        testInvalidVerificationKey(JwsAlgorithms.Ed448, TestKeys.Ed448.pair.private, TestKeys.X448.pair.public)
    }

    static void testIncorrectVerificationKey(SignatureAlgorithm alg, PrivateKey priv, PublicKey pub) {
        try {
            testSig(alg, priv, pub)
            fail()
        } catch (SignatureException expected) {
            String expectedMsg = 'JWT signature does not match locally computed signature. JWT validity cannot be asserted and should not be trusted.'
            assertEquals expectedMsg, expected.getMessage()
        }
    }

    static void testInvalidVerificationKey(SignatureAlgorithm alg, PrivateKey priv, PublicKey pub) {
        try {
            testSig(alg, priv, pub)
            fail()
        } catch (UnsupportedJwtException expected) {
            def cause = expected.getCause()
            def keyCurve = EdwardsCurve.forKey(pub)
            String expectedMsg = "${keyCurve.getId()} keys may not be used with EdDSA digital signatures per https://www.rfc-editor.org/rfc/rfc8037#section-3.2"
            assertEquals expectedMsg, cause.getMessage()
        }
    }

    static void testSig(SignatureAlgorithm alg, PrivateKey signing, PublicKey verification) {
        String jwt = Jwts.builder().setIssuer('me').setAudience('you').signWith(signing, alg).compact()
        def token = Jwts.parserBuilder().verifyWith(verification).build().parseClaimsJws(jwt)
        assertEquals([alg: alg.getId()], token.header)
        assertEquals 'me', token.getPayload().getIssuer()
        assertEquals 'you', token.getPayload().getAudience()
    }
}

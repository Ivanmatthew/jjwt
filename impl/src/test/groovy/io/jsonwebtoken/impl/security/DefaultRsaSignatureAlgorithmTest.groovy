package io.jsonwebtoken.impl.security

import io.jsonwebtoken.security.InvalidKeyException
import io.jsonwebtoken.security.SignatureAlgorithms
import io.jsonwebtoken.security.WeakKeyException
import org.junit.Test

import javax.crypto.spec.SecretKeySpec
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

import static org.easymock.EasyMock.createMock
import static org.junit.Assert.*

class DefaultRsaSignatureAlgorithmTest {

    @Test
    void testKeyPairBuilder() {
        SignatureAlgorithms.values().findAll({it.id.startsWith("RS") || it.id.startsWith("PS")}).each {
            def pair = it.keyPairBuilder().build()
            assertNotNull pair.public
            assertTrue pair.public instanceof RSAPublicKey
            assertEquals it.preferredKeyBitLength, pair.public.modulus.bitLength()
            assertTrue pair.private instanceof RSAPrivateKey
            assertEquals it.preferredKeyBitLength, pair.private.modulus.bitLength()
        }
    }

    @Test(expected = IllegalArgumentException)
    void testWeakPreferredKeyLength() {
        new DefaultRsaSignatureAlgorithm(256, 1024) //must be >= 2048
    }

    @Test
    void testValidateKeyRsaKey() {
        def request = new DefaultSignatureRequest(null, null, new byte[1], new SecretKeySpec(new byte[1], 'foo'))
        try {
            SignatureAlgorithms.RS256.sign(request)
        } catch (InvalidKeyException e) {
            assertTrue e.getMessage().contains("must be an RSAKey")
        }
    }

    @Test
    void testValidateSigningKeyNotPrivate() {
        RSAPublicKey key = createMock(RSAPublicKey)
        def request = new DefaultSignatureRequest(null, null, new byte[1], key)
        try {
            SignatureAlgorithms.RS256.sign(request)
        } catch (InvalidKeyException e) {
            assertTrue e.getMessage().startsWith("Asymmetric key signatures must be created with PrivateKeys. The specified key is of type: ")
        }
    }

    @Test
    void testValidateSigningKeyWeakKey() {
        def gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(1024) //too week for any JWA RSA algorithm
        def pair = gen.generateKeyPair()

        def request = new DefaultSignatureRequest(null, null, new byte[1], pair.getPrivate())
        SignatureAlgorithms.values().findAll({it.id.startsWith('RS') || it.id.startsWith('PS')}).each {
            try {
                it.sign(request)
            } catch (WeakKeyException expected) {
            }
        }
    }
}

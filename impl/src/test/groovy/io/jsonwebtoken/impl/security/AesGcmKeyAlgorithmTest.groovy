package io.jsonwebtoken.impl.security

import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.impl.DefaultJweHeader
import io.jsonwebtoken.impl.lang.Bytes
import io.jsonwebtoken.impl.lang.CheckedFunction
import io.jsonwebtoken.io.Encoders
import io.jsonwebtoken.lang.Arrays
import io.jsonwebtoken.lang.RuntimeEnvironment
import io.jsonwebtoken.security.EncryptionAlgorithms
import io.jsonwebtoken.security.SecretKeyBuilder
import org.junit.Test

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import java.nio.charset.StandardCharsets
import java.security.NoSuchAlgorithmException

import static org.junit.Assert.*

class AesGcmKeyAlgorithmTest {

    // TODO: remove when we stop supporting JDK 7:
    static {
        // 'GCM' is available on Java 8 and later.  If we're on Java 7, we need to enable BC:
        try {
            Cipher.getInstance('AES/GCM/NoPadding')
        } catch (NoSuchAlgorithmException e) {
            RuntimeEnvironment.enableBouncyCastleIfPossible();
        }
    }

    /**
     * This tests asserts that our AeadAlgorithm implementation and the JCA 'AES/GCM/NoPadding' wrap algorithm
     * produce the exact same values.  This should be the case when the transformation is identical, even though
     * one uses Cipher.WRAP_MODE and the other uses a raw plaintext byte array.
     */
    @Test
    void testAesWrapProducesSameResultAsAesAeadEncryptionAlgorithm() {

        def alg = new GcmAesAeadAlgorithm(256)

        def iv = new byte[12];
        Randoms.secureRandom().nextBytes(iv);

        def kek = alg.keyBuilder().build()
        def cek = alg.keyBuilder().build()

        JcaTemplate template = new JcaTemplate("AES/GCM/NoPadding", null)
        byte[] jcaResult = template.execute(Cipher.class, new CheckedFunction<Cipher, byte[]>() {
            @Override
            byte[] apply(Cipher cipher) throws Exception {
                cipher.init(Cipher.WRAP_MODE, kek, new GCMParameterSpec(128, iv))
                return cipher.wrap(cek)
            }
        })

        //separate tag from jca ciphertext:
        int ciphertextLength = jcaResult.length - 16; //AES block size in bytes (128 bits)
        byte[] ciphertext = new byte[ciphertextLength]
        System.arraycopy(jcaResult, 0, ciphertext, 0, ciphertextLength)

        byte[] tag = new byte[16]
        System.arraycopy(jcaResult, ciphertextLength, tag, 0, 16)
        def resultA = new DefaultAeadResult(null, null, ciphertext, kek, null, tag, iv)

        def encRequest = new DefaultAeadRequest(null, null, cek.getEncoded(), kek, null, iv)
        def encResult = EncryptionAlgorithms.A256GCM.encrypt(encRequest)

        assertArrayEquals resultA.digest, encResult.digest
        assertArrayEquals resultA.initializationVector, encResult.initializationVector
        assertArrayEquals resultA.payload, encResult.payload
    }

    static void assertAlgorithm(int keyLength) {

        def alg = new AesGcmKeyAlgorithm(keyLength)
        assertEquals 'A' + keyLength + 'GCMKW', alg.getId()

        def template = new JcaTemplate('AES', null)

        def header = new DefaultJweHeader()
        def kek = template.generateSecretKey(keyLength)
        def cek = template.generateSecretKey(keyLength)
        def enc = new GcmAesAeadAlgorithm(keyLength) {
            @Override
            SecretKeyBuilder keyBuilder() {
                return new FixedSecretKeyBuilder(cek)
            }
        }

        def ereq = new DefaultKeyRequest(null, null, kek, header, enc)

        def result = alg.getEncryptionKey(ereq)

        byte[] encryptedKeyBytes = result.getPayload()
        assertFalse "encryptedKey must be populated", Arrays.length(encryptedKeyBytes) == 0

        def dcek = alg.getDecryptionKey(new DefaultDecryptionKeyRequest(null, null, kek, header, enc, encryptedKeyBytes))

        //Assert the decrypted key matches the original cek
        assertEquals cek.algorithm, dcek.algorithm
        assertArrayEquals cek.encoded, dcek.encoded
    }

    @Test
    void testResultSymmetry() {
        assertAlgorithm(128)
        assertAlgorithm(192)
        assertAlgorithm(256)
    }

    static void testDecryptionHeader(String headerName, Object value, String exmsg) {
        int keyLength = 128
        def alg = new AesGcmKeyAlgorithm(keyLength)
        def template = new JcaTemplate('AES', null)
        def header = new DefaultJweHeader()
        def kek = template.generateSecretKey(keyLength)
        def cek = template.generateSecretKey(keyLength)
        def enc = new GcmAesAeadAlgorithm(keyLength) {
            @Override
            SecretKeyBuilder keyBuilder() {
                return new FixedSecretKeyBuilder(cek)
            }
        }
        def ereq = new DefaultKeyRequest(null, null, kek, header, enc)
        def result = alg.getEncryptionKey(ereq)

        header.put(headerName, value) //null value will remove it

        byte[] encryptedKeyBytes = result.getPayload()

        try {
            alg.getDecryptionKey(new DefaultDecryptionKeyRequest(null, null, kek, header, enc, encryptedKeyBytes))
            fail()
        } catch (MalformedJwtException iae) {
            assertEquals exmsg, iae.getMessage()
        }
    }

    String missing(String name) {
        return "JWE header is missing required '${name}' value." as String
    }

    String type(String name) {
        return "JWE header '${name}' value must be a String. Actual type: java.lang.Integer" as String
    }

    String base64Url(String name) {
        return "JWE header '${name}' value is not a valid Base64URL String: Illegal base64url character: '#'"
    }

    String length(String name, int requiredBitLength) {
        return "JWE header '${name}' decoded byte array must be ${Bytes.bitsMsg(requiredBitLength)} long. Actual length: ${Bytes.bitsMsg(16)}."
    }

    @Test
    void testMissingHeaders() {
        testDecryptionHeader('iv', null, missing('iv'))
        testDecryptionHeader('tag', null, missing('tag'))
    }

    @Test
    void testIncorrectTypeHeaders() {
        testDecryptionHeader('iv', 14, type('iv'))
        testDecryptionHeader('tag', 14, type('tag'))
    }

    @Test
    void testInvalidBase64UrlHeaders() {
        testDecryptionHeader('iv', 'T#ZW@#', base64Url('iv'))
        testDecryptionHeader('tag', 'T#ZW@#', base64Url('tag'))
    }

    @Test
    void testIncorrectLengths() {
        def value = Encoders.BASE64URL.encode("hi".getBytes(StandardCharsets.US_ASCII))
        testDecryptionHeader('iv', value, length('iv', 96))
        testDecryptionHeader('tag', value, length('tag', 128))
    }
}

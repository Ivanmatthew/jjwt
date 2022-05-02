package io.jsonwebtoken.impl.security

import io.jsonwebtoken.Identifiable
import io.jsonwebtoken.lang.Collections
import io.jsonwebtoken.security.AsymmetricKeySignatureAlgorithm
import io.jsonwebtoken.security.EncryptionAlgorithms
import io.jsonwebtoken.security.KeyBuilderSupplier
import io.jsonwebtoken.security.SecretKeyBuilder
import io.jsonwebtoken.security.SignatureAlgorithms

import javax.crypto.SecretKey
import java.security.KeyPair
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * Test helper with cached keys to save time across tests (so we don't have to constantly dynamically generate keys)
 */
class TestKeys {

    // =======================================================
    // Secret Keys
    // =======================================================
    static SecretKey HS256 = SignatureAlgorithms.HS256.keyBuilder().build()
    static SecretKey HS384 = SignatureAlgorithms.HS384.keyBuilder().build()
    static SecretKey HS512 = SignatureAlgorithms.HS512.keyBuilder().build()

    static SecretKey A128GCM, A192GCM, A256GCM, A128KW, A192KW, A256KW, A128GCMKW, A192GCMKW, A256GCMKW
    static {
        A128GCM = A128KW = A128GCMKW = EncryptionAlgorithms.A128GCM.keyBuilder().build()
        A192GCM = A192KW = A192GCMKW = EncryptionAlgorithms.A192GCM.keyBuilder().build()
        A256GCM = A256KW = A256GCMKW = EncryptionAlgorithms.A256GCM.keyBuilder().build()
    }
    static SecretKey A128CBC_HS256 = EncryptionAlgorithms.A128CBC_HS256.keyBuilder().build()
    static SecretKey A192CBC_HS384 = EncryptionAlgorithms.A192CBC_HS384.keyBuilder().build()
    static SecretKey A256CBC_HS512 = EncryptionAlgorithms.A256CBC_HS512.keyBuilder().build()

    // =======================================================
    // Elliptic Curve Keys & Certificates
    // =======================================================
    static Bundle ES256 = TestCertificates.readAsymmetricBundle(SignatureAlgorithms.ES256)
    static Bundle ES384 = TestCertificates.readAsymmetricBundle(SignatureAlgorithms.ES384)
    static Bundle ES512 = TestCertificates.readAsymmetricBundle(SignatureAlgorithms.ES512)
    static Set<Bundle> EC = Collections.setOf(ES256, ES384, ES512)

    // =======================================================
    // RSA Keys & Certificates
    // =======================================================
    static Bundle RS256 = TestCertificates.readAsymmetricBundle(SignatureAlgorithms.RS256)
    static Bundle RS384 = TestCertificates.readAsymmetricBundle(SignatureAlgorithms.RS384)
    static Bundle RS512 = TestCertificates.readAsymmetricBundle(SignatureAlgorithms.RS512)
    static Set<Bundle> RSA = Collections.setOf(RS256, RS384, RS512)

    static <T extends KeyBuilderSupplier<SecretKey, SecretKeyBuilder> & Identifiable> SecretKey forAlgorithm(T alg) {
        String id = alg.getId()
        if (id.contains('-')) {
            id = id.replace('-', '_')
        }
        return TestKeys.metaClass.getAttribute(TestKeys, id) as SecretKey
    }

    static Bundle forAlgorithm(AsymmetricKeySignatureAlgorithm alg) {
        String id = alg.getId()
        if (id.startsWith('PS')) {
            id = 'R' + id.substring(1) //keys for PS* algs are the same as RS algs
        }
        return TestKeys.metaClass.getAttribute(TestKeys, id) as Bundle
    }

    static class Bundle {
        X509Certificate cert
        List<X509Certificate> chain
        KeyPair pair
        Bundle(X509Certificate cert, PrivateKey privateKey) {
            this.cert = cert
            this.chain = Collections.of(cert)
            this.pair = new KeyPair(cert.getPublicKey(), privateKey)
        }
    }
}
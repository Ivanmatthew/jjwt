package io.jsonwebtoken.impl.security

import io.jsonwebtoken.lang.Classes
import io.jsonwebtoken.security.SecureDigestAlgorithm
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter

import java.nio.charset.StandardCharsets
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate

/**
 * For test cases that need to read certificate and/or PEM files.  Encapsulates BouncyCastle API to
 * this class so it doesn't need to propagate across other test classes.
 *
 * MAINTAINERS NOTE:
 *
 * If this logic is ever needed in the impl or api modules, do not keep the
 * name of this class - it was quickly thrown together and it isn't appropriately named for exposure in a public
 * module.  Thought/design is necessary to see if/how cert/pem reading should be exposed in an easy-to-use and
 * maintain API (e.g. probably a builder).
 *
 * The only purpose of this class and its methods are to:
 *   1) be used in Test classes only, and
 *   2) encapsulate the BouncyCastle API so it is not exposed to other Test classes.
 */
class TestCertificates {

    private static JcaX509CertificateConverter X509_CERT_CONVERTER = new JcaX509CertificateConverter()
    private static JcaPEMKeyConverter PEM_KEY_CONVERTER = new JcaPEMKeyConverter()

    private static PEMParser getParser(String filename) {
        InputStream is = Classes.getResourceAsStream('io/jsonwebtoken/impl/security/' + filename)
        return new PEMParser(new BufferedReader(new InputStreamReader(is, StandardCharsets.ISO_8859_1)))
    }

    static X509Certificate readTestCertificate(SecureDigestAlgorithm alg) {
        PEMParser parser = getParser(alg.getId() + '.crt.pem')
        try {
            X509CertificateHolder holder = parser.readObject() as X509CertificateHolder
            return X509_CERT_CONVERTER.getCertificate(holder)
        } finally {
            parser.close()
        }
    }

    static PublicKey readTestPublicKey(SecureDigestAlgorithm alg) {
        return readTestCertificate(alg).getPublicKey()
    }

    static PrivateKey readTestPrivateKey(SecureDigestAlgorithm alg) {
        PEMParser parser = getParser(alg.getId() + '.key.pem')
        try {
            PrivateKeyInfo info
            Object object = parser.readObject()
            if (object instanceof PEMKeyPair) {
                info = ((PEMKeyPair)object).getPrivateKeyInfo()
            } else {
                info = (PrivateKeyInfo)object
            }
            return PEM_KEY_CONVERTER.getPrivateKey(info)
        } finally {
            parser.close()
        }
    }

    static TestKeys.Bundle readAsymmetricBundle(SecureDigestAlgorithm alg) {
        X509Certificate cert = readTestCertificate(alg)
        PrivateKey priv = readTestPrivateKey(alg)
        return new TestKeys.Bundle(cert, priv)
    }
}

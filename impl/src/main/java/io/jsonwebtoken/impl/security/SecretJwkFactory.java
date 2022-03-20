package io.jsonwebtoken.impl.security;

import io.jsonwebtoken.impl.lang.ValueGetter;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.lang.Arrays;
import io.jsonwebtoken.lang.Assert;
import io.jsonwebtoken.security.SecretJwk;
import io.jsonwebtoken.security.UnsupportedKeyException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * @since JJWT_RELEASE_VERSION
 */
class SecretJwkFactory extends AbstractFamilyJwkFactory<SecretKey, SecretJwk> {

    private static final String ENCODED_UNAVAILABLE_MSG = "SecretKey argument does not have any encoded bytes, or " +
            "the key's backing JCA Provider is preventing key.getEncoded() from returning any bytes.  It is not " +
            "possible to represent the SecretKey instance as a JWK.";

    SecretJwkFactory() {
        super(DefaultSecretJwk.TYPE_VALUE, SecretKey.class);
    }

    static byte[] getRequiredEncoded(SecretKey key) {
        Assert.notNull(key, "SecretKey argument cannot be null.");
        byte[] encoded = null;
        Exception cause = null;
        try {
            encoded = key.getEncoded();
        } catch (Exception e) {
            cause = e;
        }

        if (Arrays.length(encoded) == 0) {
            throw new IllegalArgumentException(ENCODED_UNAVAILABLE_MSG, cause);
        }

        return encoded;
    }

    @Override
    protected SecretJwk createJwkFromKey(JwkContext<SecretKey> ctx) {
        SecretKey key = Assert.notNull(ctx.getKey(), "JwkContext key cannot be null.");
        String k;
        try {
            byte[] encoded = getRequiredEncoded(key);
            k = Encoders.BASE64URL.encode(encoded);
            Assert.hasText(k, "k value cannot be null or empty.");
        } catch (Exception e) {
            String msg = "Unable to encode SecretKey to JWK: " + e.getMessage();
            throw new UnsupportedKeyException(msg, e);
        }

        ctx.put(DefaultSecretJwk.K.getId(), k);

        return new DefaultSecretJwk(ctx);
    }

    @Override
    protected SecretJwk createJwkFromValues(JwkContext<SecretKey> ctx) {
        ValueGetter getter = new DefaultValueGetter(ctx);
        byte[] bytes = getter.getRequiredBytes(DefaultSecretJwk.K.getId());
        SecretKey key = new SecretKeySpec(bytes, "AES");
        ctx.setKey(key);
        return new DefaultSecretJwk(ctx);
    }
}

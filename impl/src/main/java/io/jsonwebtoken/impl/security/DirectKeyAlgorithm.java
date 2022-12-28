package io.jsonwebtoken.impl.security;

import io.jsonwebtoken.lang.Assert;
import io.jsonwebtoken.security.DecryptionKeyRequest;
import io.jsonwebtoken.security.KeyAlgorithm;
import io.jsonwebtoken.security.KeyRequest;
import io.jsonwebtoken.security.KeyResult;
import io.jsonwebtoken.security.SecurityException;

import javax.crypto.SecretKey;

/**
 * @since JJWT_RELEASE_VERSION
 */
public class DirectKeyAlgorithm implements KeyAlgorithm<SecretKey, SecretKey> {

    static final String ID = "dir";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public KeyResult getEncryptionKey(KeyRequest<SecretKey> request) throws SecurityException {
        Assert.notNull(request, "request cannot be null.");
        SecretKey key = Assert.notNull(request.getPayload(), "Encryption key cannot be null.");
        return new DefaultKeyResult(key);
    }

    @Override
    public SecretKey getDecryptionKey(DecryptionKeyRequest<SecretKey> request) throws SecurityException {
        Assert.notNull(request, "request cannot be null.");
        return Assert.notNull(request.getKey(), "Decryption key cannot be null.");
    }
}

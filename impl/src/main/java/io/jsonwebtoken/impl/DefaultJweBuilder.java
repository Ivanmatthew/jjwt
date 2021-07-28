package io.jsonwebtoken.impl;

import io.jsonwebtoken.JweBuilder;
import io.jsonwebtoken.JweHeader;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.impl.lang.Function;
import io.jsonwebtoken.impl.lang.PropagatingExceptionFunction;
import io.jsonwebtoken.impl.lang.Services;
import io.jsonwebtoken.impl.security.DefaultKeyRequest;
import io.jsonwebtoken.impl.security.DefaultSymmetricAeadRequest;
import io.jsonwebtoken.io.SerializationException;
import io.jsonwebtoken.security.EncryptedKeyAlgorithm;
import io.jsonwebtoken.io.Serializer;
import io.jsonwebtoken.lang.Arrays;
import io.jsonwebtoken.lang.Assert;
import io.jsonwebtoken.lang.Collections;
import io.jsonwebtoken.lang.Strings;
import io.jsonwebtoken.security.KeyRequest;
import io.jsonwebtoken.security.SecurityException;
import io.jsonwebtoken.security.SymmetricAeadAlgorithm;
import io.jsonwebtoken.security.KeyAlgorithm;
import io.jsonwebtoken.security.KeyAlgorithms;
import io.jsonwebtoken.security.KeyResult;
import io.jsonwebtoken.security.AeadResult;
import io.jsonwebtoken.security.SymmetricAeadRequest;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Map;

public class DefaultJweBuilder extends DefaultJwtBuilder<JweBuilder> implements JweBuilder {

    private static final SecretKey EMPTY_SECRET_KEY = new SecretKeySpec("NONE".getBytes(StandardCharsets.UTF_8), "NONE");

    private SymmetricAeadAlgorithm enc; // MUST be Symmetric AEAD per https://tools.ietf.org/html/rfc7516#section-4.1.2
    private Function<SymmetricAeadRequest, AeadResult> encFunction;

    private KeyAlgorithm<Key,?> alg;
    private Function<KeyRequest<SecretKey,Key>,KeyResult> algFunction;

    private Key key;

    protected <I,O> Function<I,O> wrap(String msg, Function<I,O> fn) {
        return new PropagatingExceptionFunction<>(SecurityException.class, msg, fn);
    }

    //TODO for 1.0: delete this method when the parent class's implementation has changed to SerializationException
    @Override
    protected Function<Map<String, ?>, byte[]> wrap(final Serializer<Map<String, ?>> serializer, String which) {
        return new PropagatingExceptionFunction<>(SerializationException.class,
            "Unable to serialize " + which + " to JSON.", new Function<Map<String, ?>, byte[]>() {
                @Override
                public byte[] apply(Map<String, ?> map) {
                    return serializer.serialize(map);
                }
            }
        );
    }

    @Override
    public JweBuilder setPayload(String payload) {
        Assert.hasLength(payload, "payload cannot be null or empty."); //allowed for JWS, but not JWE
        return super.setPayload(payload);
    }

    @Override
    public JweBuilder encryptWith(final SymmetricAeadAlgorithm enc) {
        this.enc = Assert.notNull(enc, "EncryptionAlgorithm cannot be null.");
        Assert.hasText(enc.getId(), "EncryptionAlgorithm id cannot be null or empty.");
        String encMsg = enc.getId() + " encryption failed.";
        this.encFunction = wrap(encMsg, new Function<SymmetricAeadRequest, AeadResult>() {
            @Override
            public AeadResult apply(SymmetricAeadRequest request) {
                return enc.encrypt(request);
            }
        });
        return this;
    }

    @Override
    public JweBuilder withKey(SecretKey key) {
        return withKeyFrom(key, KeyAlgorithms.DIRECT);
    }

    @Override
    public <K extends Key> JweBuilder withKeyFrom(K key, final KeyAlgorithm<K, ?> alg) {
        this.key = Assert.notNull(key, "key cannot be null.");
        //noinspection unchecked
        this.alg = (KeyAlgorithm<Key, ?>) Assert.notNull(alg, "KeyAlgorithm cannot be null.");
        final KeyAlgorithm<Key,?> keyAlg = this.alg;
        Assert.hasText(alg.getId(), "KeyAlgorithm id cannot be null or empty.");

        String cekMsg = "Unable to obtain content encryption key from key management algorithm '" + alg.getId() + "'.";
        this.algFunction = wrap(cekMsg, new Function<KeyRequest<SecretKey, Key>, KeyResult>() {
            @Override
            public KeyResult apply(KeyRequest<SecretKey, Key> request) {
                return keyAlg.getEncryptionKey(request);
            }
        });

        return this;
    }

    @Override
    public String compact() {

        if (!Strings.hasLength(payload) && Collections.isEmpty(claims)) {
            throw new IllegalStateException("Either 'claims' or a non-empty 'payload' must be specified.");
        }

        if (Strings.hasLength(payload) && !Collections.isEmpty(claims)) {
            throw new IllegalStateException("Both 'payload' and 'claims' cannot both be specified. Choose either one.");
        }

        Assert.state(key != null, "Key is required.");
        Assert.state(enc != null, "EncryptionAlgorithm is required.");
        assert alg != null : "KeyAlgorithm is required."; //always set by withKey calling withKeyFrom

        if (this.serializer == null) { // try to find one based on the services available
            //noinspection unchecked
            serializeToJsonWith(Services.loadFirst(Serializer.class));
        }

        header = ensureHeader();

        JweHeader jweHeader;
        if (header instanceof JweHeader) {
            jweHeader = (JweHeader) header;
        } else {
            header = jweHeader = new DefaultJweHeader(header);
        }

        byte[] plaintext = this.payload != null ? payload.getBytes(Strings.UTF_8) : claimsSerializer.apply(claims);
        Assert.state(Arrays.length(plaintext) > 0, "Payload bytes cannot be empty."); // JWE invariant (JWS can be empty however)

        if (compressionCodec != null) {
            plaintext = compressionCodec.compress(plaintext);
            jweHeader.setCompressionAlgorithm(compressionCodec.getAlgorithmName());
        }

        SecretKey cek = alg instanceof EncryptedKeyAlgorithm ? enc.generateKey() : EMPTY_SECRET_KEY; //for algorithms that don't need one
        KeyRequest<SecretKey, Key> keyRequest = new DefaultKeyRequest<>(this.provider, this.secureRandom, cek, this.key, jweHeader);
        KeyResult keyResult = algFunction.apply(keyRequest);

        Assert.state(keyResult != null, "KeyAlgorithm must return a KeyResult.");
        cek = Assert.notNull(keyResult.getKey(), "KeyResult must return a content encryption key.");
        byte[] encryptedCek = Assert.notNull(keyResult.getPayload(), "KeyResult must return an encrypted key byte array, even if empty.");

        jweHeader.putAll(keyResult.getHeaderParams());
        jweHeader.setEncryptionAlgorithm(enc.getId());
        jweHeader.setAlgorithm(alg.getId());

        byte[] headerBytes = this.headerSerializer.apply(jweHeader);

        SymmetricAeadRequest encRequest = new DefaultSymmetricAeadRequest(provider, secureRandom, plaintext, cek, headerBytes);
        AeadResult encResult = encFunction.apply(encRequest);

        byte[] iv = Assert.notEmpty(encResult.getInitializationVector(), "Encryption result must have a non-empty initialization vector.");
        byte[] ciphertext = Assert.notEmpty(encResult.getPayload(), "Encryption result must have non-empty ciphertext (result.getData()).");
        byte[] tag = Assert.notEmpty(encResult.getAuthenticationTag(), "Encryption result must have a non-empty authentication tag.");

        String base64UrlEncodedHeader = base64UrlEncoder.encode(headerBytes);
        String base64UrlEncodedEncryptedKey = base64UrlEncoder.encode(encryptedCek);
        String base64UrlEncodedIv = base64UrlEncoder.encode(iv);
        String base64UrlEncodedCiphertext = base64UrlEncoder.encode(ciphertext);
        String base64UrlEncodedAad = base64UrlEncoder.encode(tag);

        return
            base64UrlEncodedHeader + JwtParser.SEPARATOR_CHAR +
            base64UrlEncodedEncryptedKey + JwtParser.SEPARATOR_CHAR +
            base64UrlEncodedIv + JwtParser.SEPARATOR_CHAR +
            base64UrlEncodedCiphertext + JwtParser.SEPARATOR_CHAR +
            base64UrlEncodedAad;
    }
}
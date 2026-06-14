package gq.yozakura.util.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;

/**
 * Crypto helpers backed by JDK providers.
 * 基于 JDK 标准 Provider 的加密/编码工具类。
 */
public final class CryptoUtil {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int AES_GCM_TAG_BITS = 128;
    private static final int AES_GCM_IV_BYTES = 12;
    private static final int AES_CBC_IV_BYTES = 16;
    private static final String AES = "AES";
    private static final String RSA = "RSA";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String AES_CBC = "AES/CBC/PKCS5Padding";
    private static final String RSA_OAEP = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String RSA_PKCS1 = "RSA/ECB/PKCS1Padding";
    private static final String RSA_SIGN_SHA256 = "SHA256withRSA";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private CryptoUtil() {
    }

    /**
     * Hash UTF-8 text and return lowercase hex.
     * 对 UTF-8 文本做哈希，返回小写十六进制。
     */
    public static String hashHex(String algorithm, String text) {
        return hex(hash(algorithm, text.getBytes(StandardCharsets.UTF_8)));
    }

    public static byte[] hash(String algorithm, byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return digest.digest(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Unsupported hash algorithm: " + algorithm, e);
        }
    }

    public static String md5(String text) {
        return hashHex("MD5", text);
    }

    public static String sha1(String text) {
        return hashHex("SHA-1", text);
    }

    public static String sha256(String text) {
        return hashHex("SHA-256", text);
    }

    public static String sha512(String text) {
        return hashHex("SHA-512", text);
    }

    /**
     * HMAC-SHA256 for message authentication.
     * HMAC-SHA256 消息认证码。
     */
    public static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 failed", e);
        }
    }

    public static String hmacSha256Hex(byte[] key, byte[] data) {
        return hex(hmacSha256(key, data));
    }

    public static String base64Encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] base64Decode(String data) {
        return Base64.getDecoder().decode(data);
    }

    public static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public static byte[] base64UrlDecode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }

    public static String base64EncodeUtf8(String text) {
        return base64Encode(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String base64DecodeUtf8(String encoded) {
        return new String(base64Decode(encoded), StandardCharsets.UTF_8);
    }

    /**
     * Generate an AES key with 128/192/256 bits.
     * 生成 128/192/256 位 AES 密钥。
     */
    public static byte[] generateAesKey(int bits) {
        if (bits != 128 && bits != 192 && bits != 256) {
            throw new IllegalArgumentException("AES key size must be 128, 192 or 256 bits");
        }
        try {
            KeyGenerator generator = KeyGenerator.getInstance(AES);
            generator.init(bits, SECURE_RANDOM);
            SecretKey key = generator.generateKey();
            return key.getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES key generation failed", e);
        }
    }

    /**
     * AES-GCM encrypt. Output layout: iv(12) + ciphertext + tag(16).
     * AES-GCM 加密。输出格式：iv(12) + 密文 + tag(16)。
     */
    public static byte[] aesGcmEncrypt(byte[] key, byte[] plaintext) {
        byte[] iv = randomBytes(AES_GCM_IV_BYTES);
        byte[] encrypted = aesGcmEncrypt(key, iv, plaintext, null);
        return concat(iv, encrypted);
    }

    public static byte[] aesGcmDecrypt(byte[] key, byte[] packed) {
        if (packed.length < AES_GCM_IV_BYTES + 1) {
            throw new IllegalArgumentException("AES-GCM payload is too short");
        }
        byte[] iv = slice(packed, 0, AES_GCM_IV_BYTES);
        byte[] encrypted = slice(packed, AES_GCM_IV_BYTES, packed.length - AES_GCM_IV_BYTES);
        return aesGcmDecrypt(key, iv, encrypted, null);
    }

    public static byte[] aesGcmEncrypt(byte[] key, byte[] iv, byte[] plaintext, byte[] aad) {
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(key), new GCMParameterSpec(AES_GCM_TAG_BITS, iv));
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(plaintext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    public static byte[] aesGcmDecrypt(byte[] key, byte[] iv, byte[] encrypted, byte[] aad) {
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, aesKey(key), new GCMParameterSpec(AES_GCM_TAG_BITS, iv));
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(encrypted);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("AES-GCM decryption failed", e);
        }
    }

    /**
     * AES-CBC encrypt. Output layout: iv(16) + ciphertext.
     * AES-CBC 加密。输出格式：iv(16) + 密文。
     */
    public static byte[] aesCbcEncrypt(byte[] key, byte[] plaintext) {
        byte[] iv = randomBytes(AES_CBC_IV_BYTES);
        return concat(iv, aesCbcEncrypt(key, iv, plaintext));
    }

    public static byte[] aesCbcDecrypt(byte[] key, byte[] packed) {
        if (packed.length < AES_CBC_IV_BYTES + 1) {
            throw new IllegalArgumentException("AES-CBC payload is too short");
        }
        byte[] iv = slice(packed, 0, AES_CBC_IV_BYTES);
        byte[] encrypted = slice(packed, AES_CBC_IV_BYTES, packed.length - AES_CBC_IV_BYTES);
        return aesCbcDecrypt(key, iv, encrypted);
    }

    public static byte[] aesCbcEncrypt(byte[] key, byte[] iv, byte[] plaintext) {
        return cipher(AES_CBC, Cipher.ENCRYPT_MODE, aesKey(key), new IvParameterSpec(iv), plaintext);
    }

    public static byte[] aesCbcDecrypt(byte[] key, byte[] iv, byte[] encrypted) {
        return cipher(AES_CBC, Cipher.DECRYPT_MODE, aesKey(key), new IvParameterSpec(iv), encrypted);
    }

    public static KeyPair generateRsaKeyPair(int bits) {
        if (bits < 2048) {
            throw new IllegalArgumentException("RSA key size should be at least 2048 bits");
        }
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA);
            generator.initialize(bits, SECURE_RANDOM);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA key generation failed", e);
        }
    }

    public static byte[] rsaEncrypt(PublicKey key, byte[] plaintext) {
        return cipher(RSA_OAEP, Cipher.ENCRYPT_MODE, key, null, plaintext);
    }

    public static byte[] rsaDecrypt(PrivateKey key, byte[] encrypted) {
        return cipher(RSA_OAEP, Cipher.DECRYPT_MODE, key, null, encrypted);
    }

    public static byte[] rsaEncryptPkcs1(PublicKey key, byte[] plaintext) {
        return cipher(RSA_PKCS1, Cipher.ENCRYPT_MODE, key, null, plaintext);
    }

    public static byte[] rsaDecryptPkcs1(PrivateKey key, byte[] encrypted) {
        return cipher(RSA_PKCS1, Cipher.DECRYPT_MODE, key, null, encrypted);
    }

    public static byte[] rsaSignSha256(PrivateKey key, byte[] data) {
        try {
            Signature signature = Signature.getInstance(RSA_SIGN_SHA256);
            signature.initSign(key, SECURE_RANDOM);
            signature.update(data);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA signing failed", e);
        }
    }

    public static boolean rsaVerifySha256(PublicKey key, byte[] data, byte[] signed) {
        try {
            Signature signature = Signature.getInstance(RSA_SIGN_SHA256);
            signature.initVerify(key);
            signature.update(data);
            return signature.verify(signed);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("RSA verification failed", e);
        }
    }

    public static PublicKey rsaPublicKeyFromBase64(String base64) {
        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(base64Decode(stripPem(base64)));
            return KeyFactory.getInstance(RSA).generatePublic(spec);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid RSA public key", e);
        }
    }

    public static PrivateKey rsaPrivateKeyFromBase64(String base64) {
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(base64Decode(stripPem(base64)));
            return KeyFactory.getInstance(RSA).generatePrivate(spec);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid RSA private key", e);
        }
    }

    public static String publicKeyBase64(PublicKey key) {
        return base64Encode(key.getEncoded());
    }

    public static String privateKeyBase64(PrivateKey key) {
        return base64Encode(key.getEncoded());
    }

    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    public static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0, j = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            out[j++] = HEX[value >>> 4];
            out[j++] = HEX[value & 0x0F];
        }
        return new String(out);
    }

    public static byte[] fromHex(String hex) {
        String normalized = hex.trim().toLowerCase(Locale.ROOT);
        if ((normalized.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex length must be even");
        }
        byte[] out = new byte[normalized.length() / 2];
        for (int i = 0; i < normalized.length(); i += 2) {
            int hi = Character.digit(normalized.charAt(i), 16);
            int lo = Character.digit(normalized.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex character");
            }
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }

    private static SecretKeySpec aesKey(byte[] key) {
        int length = key.length;
        if (length != 16 && length != 24 && length != 32) {
            throw new IllegalArgumentException("AES key must be 16, 24 or 32 bytes");
        }
        return new SecretKeySpec(key, AES);
    }

    private static byte[] cipher(String transformation, int mode, java.security.Key key,
                                 java.security.spec.AlgorithmParameterSpec params, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance(transformation);
            if (params == null) {
                cipher.init(mode, key);
            } else {
                cipher.init(mode, key, params);
            }
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(transformation + " failed", e);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        ByteBuffer buffer = ByteBuffer.allocate(a.length + b.length);
        buffer.put(a);
        buffer.put(b);
        return buffer.array();
    }

    private static byte[] slice(byte[] data, int offset, int length) {
        byte[] out = new byte[length];
        System.arraycopy(data, offset, out, 0, length);
        return out;
    }

    private static String stripPem(String value) {
        return value.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
    }
}

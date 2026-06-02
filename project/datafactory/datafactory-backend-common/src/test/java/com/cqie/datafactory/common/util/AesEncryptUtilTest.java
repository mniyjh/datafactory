package com.cqie.datafactory.common.util;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class AesEncryptUtilTest {

    private static final String KEY_B64 = Base64.getEncoder().encodeToString(
            "12345678901234567890123456789012".getBytes());
    private final AesEncryptUtil util = new AesEncryptUtil(KEY_B64);

    @Test
    void shouldEncryptAndDecryptRoundtrip() {
        String plain = "mySecretPassword123";
        String encrypted = util.encrypt(plain);
        assertNotEquals(plain, encrypted);
        assertEquals(plain, util.decrypt(encrypted));
    }

    @Test
    void shouldReturnNullAsIs() {
        assertNull(util.encrypt(null));
        assertNull(util.decrypt(null));
    }

    @Test
    void shouldReturnEmptyAsIs() {
        assertEquals("", util.encrypt(""));
        assertEquals("", util.decrypt(""));
    }

    @Test
    void shouldProduceDifferentCiphertextEachTime() {
        String plain = "test";
        String e1 = util.encrypt(plain);
        String e2 = util.encrypt(plain);
        assertNotEquals(e1, e2, "每次加密应使用不同IV产生不同密文");
    }

    @Test
    void shouldRejectInvalidKeyLength() {
        String shortKey = Base64.getEncoder().encodeToString("short".getBytes());
        assertThrows(IllegalArgumentException.class, () -> new AesEncryptUtil(shortKey));
    }

    @Test
    void shouldDecryptChineseText() {
        String plain = "中文密码测试";
        assertEquals(plain, util.decrypt(util.encrypt(plain)));
    }
}

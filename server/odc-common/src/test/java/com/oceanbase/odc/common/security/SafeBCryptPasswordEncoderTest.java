/*
 * Copyright (c) 2023 OceanBase.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.oceanbase.odc.common.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Unit tests for {@link SafeBCryptPasswordEncoder}.
 * Uses parameterized tests (map case style) to cover normal, boundary, and exception scenarios.
 */
@RunWith(Parameterized.class)
public class SafeBCryptPasswordEncoderTest {

    private static final SafeBCryptPasswordEncoder encoder = new SafeBCryptPasswordEncoder();

    private final String testName;
    private final CharSequence password;
    private final boolean expectEncodeSuccess;
    private final boolean expectMatchesSuccess;
    private final Class<? extends Exception> expectedException;

    public SafeBCryptPasswordEncoderTest(String testName, CharSequence password,
            boolean expectEncodeSuccess, boolean expectMatchesSuccess,
            Class<? extends Exception> expectedException) {
        this.testName = testName;
        this.password = password;
        this.expectEncodeSuccess = expectEncodeSuccess;
        this.expectMatchesSuccess = expectMatchesSuccess;
        this.expectedException = expectedException;
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> testCases() {
        // 72 ASCII 'A' characters = 72 bytes in UTF-8
        char[] chars72 = new char[72];
        Arrays.fill(chars72, 'A');
        String ascii72 = new String(chars72);

        // 73 ASCII 'A' characters = 73 bytes in UTF-8
        char[] chars73 = new char[73];
        Arrays.fill(chars73, 'A');
        String ascii73 = new String(chars73);

        // 24 Chinese characters, each 3 bytes in UTF-8 = 72 bytes
        StringBuilder cn24 = new StringBuilder();
        for (int i = 0; i < 24; i++) {
            cn24.append('\u4e2d'); // '中'
        }
        String utf8_72bytes = cn24.toString();

        // 25 Chinese characters, each 3 bytes in UTF-8 = 75 bytes
        StringBuilder cn25 = new StringBuilder();
        for (int i = 0; i < 25; i++) {
            cn25.append('\u4e2d'); // '中'
        }
        String utf8_75bytes = cn25.toString();

        return Arrays.asList(new Object[][] {
                // {testName, password, expectEncodeSuccess, expectMatchesSuccess, expectedException}
                {"normalPassword_encodeAndMatchSucceed", "Test1234!", true, true, null},
                {"boundary72ByteAscii_encodeSucceeds", ascii72, true, true, null},
                {"exceed72ByteAscii_encodeThrowsException", ascii73, false, false, IllegalArgumentException.class},
                {"utf8Boundary72Bytes_encodeSucceeds", utf8_72bytes, true, true, null},
                {"utf8Exceed72Bytes_encodeThrowsException", utf8_75bytes, false, false,
                        IllegalArgumentException.class},
                {"nullPassword_encodeThrowsException", null, false, false, IllegalArgumentException.class},
                {"emptyPassword_encodeSucceeds", "", true, false, null},
        });
    }

    @Test
    public void testEncode() {
        if (expectEncodeSuccess) {
            String encoded = encoder.encode(password);
            assertTrue(testName + ": encoded password should start with $2a$",
                    encoded.startsWith("$2a$"));
        } else {
            try {
                encoder.encode(password);
                // If we expected an exception but didn't get one, fail
                if (expectedException != null) {
                    throw new AssertionError(
                            testName + ": expected " + expectedException.getSimpleName() + " but none was thrown");
                }
            } catch (Exception e) {
                if (expectedException != null) {
                    assertTrue(testName + ": expected " + expectedException.getSimpleName()
                            + " but got " + e.getClass().getSimpleName(),
                            expectedException.isInstance(e));
                } else {
                    throw e;
                }
            }
        }
    }

    @Test
    public void testMatches() {
        if (expectMatchesSuccess) {
            // Encode first, then verify matches
            String encoded = encoder.encode(password);
            assertTrue(testName + ": matches should return true for correct password",
                    encoder.matches(password, encoded));
        } else if (expectedException != null && password != null) {
            // For overlong passwords, matches should also throw
            try {
                // Use a valid bcrypt hash for the matches call
                String dummyHash = encoder.encode("Test1234!");
                encoder.matches(password, dummyHash);
                throw new AssertionError(
                        testName + ": expected " + expectedException.getSimpleName() + " but none was thrown");
            } catch (Exception e) {
                assertTrue(testName + ": expected " + expectedException.getSimpleName()
                        + " but got " + e.getClass().getSimpleName(),
                        expectedException.isInstance(e));
            }
        }
    }

    /**
     * Non-parameterized test: wrong password should not match.
     */
    @Test
    public void testWrongPasswordDoesNotMatch() {
        if ("normalPassword_encodeAndMatchSucceed".equals(testName)) {
            String encoded = encoder.encode("Test1234!");
            assertFalse("Wrong password should not match",
                    encoder.matches("Wrong123!", encoded));
        }
    }

    /**
     * Non-parameterized test: null password in matches should be delegated to parent.
     */
    @Test
    public void testNullPasswordMatches() {
        if ("nullPassword_encodeThrowsException".equals(testName)) {
            String dummyHash = encoder.encode("Test1234!");
            // BCryptPasswordEncoder.matches(null, hash) returns false
            assertFalse("null password matches should return false",
                    encoder.matches(null, dummyHash));
        }
    }

    /**
     * Verify that byte length computation is based on UTF-8 encoding.
     */
    @Test
    public void testUtf8ByteLengthCalculation() {
        if ("utf8Boundary72Bytes_encodeSucceeds".equals(testName)) {
            // Verify our test data is actually 72 bytes
            int byteLength = password.toString().getBytes(StandardCharsets.UTF_8).length;
            assertTrue(testName + ": expected 72 bytes but got " + byteLength,
                    byteLength == 72);
        }
        if ("utf8Exceed72Bytes_encodeThrowsException".equals(testName)) {
            int byteLength = password.toString().getBytes(StandardCharsets.UTF_8).length;
            assertTrue(testName + ": expected 75 bytes but got " + byteLength,
                    byteLength == 75);
        }
    }
}

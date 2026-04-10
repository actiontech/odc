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

import java.nio.charset.StandardCharsets;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCryptPasswordEncoder wrapper that enforces the 72-byte password length limit. Addresses
 * CVE-2025-22228 by explicitly rejecting passwords exceeding BCrypt's maximum input length, rather
 * than silently truncating them.
 */
public class SafeBCryptPasswordEncoder extends BCryptPasswordEncoder {

    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    public SafeBCryptPasswordEncoder() {
        super();
    }

    public SafeBCryptPasswordEncoder(int strength) {
        super(strength);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        validatePasswordByteLength(rawPassword);
        return super.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        validatePasswordByteLength(rawPassword);
        return super.matches(rawPassword, encodedPassword);
    }

    private void validatePasswordByteLength(CharSequence password) {
        if (password == null) {
            return;
        }
        int byteLength = password.toString().getBytes(StandardCharsets.UTF_8).length;
        if (byteLength > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException(
                    "Password exceeds BCrypt maximum length of " + BCRYPT_MAX_PASSWORD_BYTES
                            + " bytes (actual: " + byteLength + " bytes)");
        }
    }
}


package com.example.ai_pet_agent.util;

import java.util.regex.Pattern;

public final class SessionIdValidator {

    private static final Pattern SESSION_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private SessionIdValidator() {
        // 工具类不需要创建对象
    }

    public static boolean isValid(String sessionId) {

        return sessionId != null
                && SESSION_ID_PATTERN
                        .matcher(sessionId)
                        .matches();
    }
}

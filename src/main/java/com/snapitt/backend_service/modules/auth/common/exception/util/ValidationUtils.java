package com.snapitt.backend_service.modules.auth.common.util;

import java.util.regex.Pattern;

public class ValidationUtils {

    private static final String PASSWORD_PATTERN =
            "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!?])(?=\\S+$).{8,20}$";

    private static final String USERNAME_PATTERN =
            "^[a-zA-Z0-9._]{3,20}$";

    private static final Pattern passwordPattern = Pattern.compile(PASSWORD_PATTERN);
    private static final Pattern usernamePattern = Pattern.compile(USERNAME_PATTERN);

    public static boolean isValidPassword(String password) {
        return password != null && passwordPattern.matcher(password).matches();
    }

    public static boolean isValidUsername(String username) {
        return username != null && usernamePattern.matcher(username).matches();
    }
}
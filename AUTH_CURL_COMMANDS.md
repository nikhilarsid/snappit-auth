# Auth Module - Curl Test Commands Reference

## Base URL
```
http://localhost:8080
```

---

## HAPPY PATH - Success Cases

### 1. Signup
```bash
curl -X POST http://localhost:8080/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name": "Test User One", "username": "test_user_one", "email": "testuser1@example.com", "password": "TestPass@123"}'
```

### 2. Login with Email
```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail": "testuser1@example.com", "password": "TestPass@123"}'
```

### 3. Login with Username
```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail": "test_user_one", "password": "TestPass@123"}'
```

### 4. Logout
```bash
curl -X POST http://localhost:8080/v1/auth/logout
```

### 5. Forgot Password - Init OTP
```bash
curl -X POST http://localhost:8080/v1/auth/forgot-password/init \
  -H "Content-Type: application/json" \
  -d '{"email": "testuser1@example.com"}'
```

**Note: Copy the OTP code from console output**

### 6. Forgot Password - Verify OTP
```bash
curl -X POST http://localhost:8080/v1/auth/forgot-password/verify \
  -H "Content-Type: application/json" \
  -d '{"email": "testuser1@example.com", "code": "123456"}'
```

**Replace "123456" with actual OTP code**

### 7. Forgot Password - Reset Password
```bash
curl -X POST http://localhost:8080/v1/auth/forgot-password/reset \
  -H "Content-Type: application/json" \
  -d '{"email": "testuser1@example.com", "code": "123456", "newPassword": "NewPass@456"}'
```

**Replace "123456" with actual OTP code**

### 8. Verify Login with New Password
```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail": "testuser1@example.com", "password": "NewPass@456"}'
```

---

## ERROR CASES - Signup Errors

### 9. Signup - Weak Password
```bash
curl -X POST http://localhost:8080/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name": "Weak Pass User", "username": "weak_user1", "email": "weak1@example.com", "password": "weak"}'
```

Expected: 400 Bad Request - WEAK_PASSWORD

### 10. Signup - Invalid Username Format
```bash
curl -X POST http://localhost:8080/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name": "Invalid User", "username": "user@invalid", "email": "invalid@example.com", "password": "TestPass@123"}'
```

Expected: 400 Bad Request - INVALID_USERNAME_FORMAT

### 11. Signup - Username Already Exists
```bash
curl -X POST http://localhost:8080/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name": "Duplicate User", "username": "test_user_one", "email": "different@example.com", "password": "TestPass@123"}'
```

Expected: 409 Conflict - USERNAME_ALREADY_EXISTS

### 12. Signup - Email Already Registered
```bash
curl -X POST http://localhost:8080/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name": "Duplicate Email", "username": "different_user", "email": "testuser1@example.com", "password": "TestPass@123"}'
```

Expected: 409 Conflict - EMAIL_ALREADY_EXISTS

### 13. Signup - Missing Name (Required Field)
```bash
curl -X POST http://localhost:8080/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username": "test_user", "email": "test@example.com", "password": "TestPass@123"}'
```

Expected: 400 Bad Request - Validation Error

### 14. Signup - Missing Username (Required Field)
```bash
curl -X POST http://localhost:8080/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name": "Test User", "email": "test@example.com", "password": "TestPass@123"}'
```

Expected: 400 Bad Request - Validation Error

### 15. Signup - Missing Email (Required Field)
```bash
curl -X POST http://localhost:8080/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name": "Test User", "username": "test_user", "password": "TestPass@123"}'
```

Expected: 400 Bad Request - Validation Error

### 16. Signup - Missing Password (Required Field)
```bash
curl -X POST http://localhost:8080/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name": "Test User", "username": "test_user", "email": "test@example.com"}'
```

Expected: 400 Bad Request - Validation Error

---

## ERROR CASES - Login Errors

### 17. Login - User Not Found
```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail": "nonexistent@example.com", "password": "TestPass@123"}'
```

Expected: 401 Unauthorized - INVALID_CREDENTIALS

### 18. Login - Wrong Password
```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail": "test_user_one", "password": "WrongPassword@123"}'
```

Expected: 401 Unauthorized - INVALID_CREDENTIALS

### 19. Login - Missing Username/Email
```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"password": "TestPass@123"}'
```

Expected: 400 Bad Request - Validation Error

### 20. Login - Missing Password
```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail": "test_user_one"}'
```

Expected: 400 Bad Request - Validation Error

---

## ERROR CASES - Forgot Password Errors

### 21. Forgot Password Init - User Not Found
```bash
curl -X POST http://localhost:8080/v1/auth/forgot-password/init \
  -H "Content-Type: application/json" \
  -d '{"email": "nonexistent@example.com"}'
```

Expected: 404 Not Found - USER_NOT_FOUND

### 22. Forgot Password Init - Missing Email
```bash
curl -X POST http://localhost:8080/v1/auth/forgot-password/init \
  -H "Content-Type: application/json" \
  -d '{}'
```

Expected: 400 Bad Request - Validation Error

### 23. Forgot Password Verify - Invalid OTP
```bash
curl -X POST http://localhost:8080/v1/auth/forgot-password/verify \
  -H "Content-Type: application/json" \
  -d '{"email": "testuser1@example.com", "code": "999999"}'
```

Expected: 400 Bad Request - INVALID_OTP

### 24. Forgot Password Verify - Missing Email
```bash
curl -X POST http://localhost:8080/v1/auth/forgot-password/verify \
  -H "Content-Type: application/json" \
  -d '{"code": "123456"}'
```

Expected: 400 Bad Request - Validation Error

### 25. Forgot Password Verify - Missing Code
```bash
curl -X POST http://localhost:8080/v1/auth/forgot-password/verify \
  -H "Content-Type: application/json" \
  -d '{"email": "testuser1@example.com"}'
```

Expected: 400 Bad Request - Validation Error

### 26. Forgot Password Reset - Invalid OTP
```bash
curl -X POST http://localhost:8080/v1/auth/forgot-password/reset \
  -H "Content-Type: application/json" \
  -d '{"email": "testuser1@example.com", "code": "999999", "newPassword": "NewPass@456"}'
```

Expected: 400 Bad Request - INVALID_OTP

### 27. Forgot Password Reset - Weak New Password
```bash
curl -X POST http://localhost:8080/v1/auth/forgot-password/reset \
  -H "Content-Type: application/json" \
  -d '{"email": "testuser1@example.com", "code": "123456", "newPassword": "weak"}'
```

Expected: 400 Bad Request - WEAK_PASSWORD

### 28. Forgot Password Reset - User Not Found
```bash
curl -X POST http://localhost:8080/v1/auth/forgot-password/reset \
  -H "Content-Type: application/json" \
  -d '{"email": "nonexistent@example.com", "code": "123456", "newPassword": "NewPass@456"}'
```

Expected: 404 Not Found - USER_NOT_FOUND

### 29. Forgot Password Reset - Missing Email
```bash
curl -X POST http://localhost:8080/v1/auth/forgot-password/reset \
  -H "Content-Type: application/json" \
  -d '{"code": "123456", "newPassword": "NewPass@456"}'
```

Expected: 400 Bad Request - Validation Error

### 30. Forgot Password Reset - Missing Code
```bash
curl -X POST http://localhost:8080/v1/auth/forgot-password/reset \
  -H "Content-Type: application/json" \
  -d '{"email": "testuser1@example.com", "newPassword": "NewPass@456"}'
```

Expected: 400 Bad Request - Validation Error

### 31. Forgot Password Reset - Missing New Password
```bash
curl -X POST http://localhost:8080/v1/auth/forgot-password/reset \
  -H "Content-Type: application/json" \
  -d '{"email": "testuser1@example.com", "code": "123456"}'
```

Expected: 400 Bad Request - Validation Error

---

## Additional Test Users (for repeated testing)

### User 2 - Alice
```bash
curl -X POST http://localhost:8080/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice Johnson", "username": "alice.johnson", "email": "alice.johnson@example.com", "password": "AlicePass@456"}'
```

### User 3 - Bob
```bash
curl -X POST http://localhost:8080/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name": "Bob Smith", "username": "bob.smith", "email": "bob.smith@example.com", "password": "BobPass@789"}'
```

### User 4 - Charlie
```bash
curl -X POST http://localhost:8080/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name": "Charlie Brown", "username": "charlie_brown", "email": "charlie@example.com", "password": "CharliePass@999"}'
```

---

## Error Response Formats

### 400 Bad Request
```json
{
  "error": "Error message",
  "code": "ERROR_CODE",
  "status": 400
}
```

### 401 Unauthorized
```json
{
  "error": "Error message",
  "code": "ERROR_CODE",
  "status": 401
}
```

### 404 Not Found
```json
{
  "error": "User not found",
  "code": "USER_NOT_FOUND",
  "status": 404
}
```

### 409 Conflict
```json
{
  "error": "Error message",
  "code": "ERROR_CODE",
  "status": 409
}
```

---

## Testing Tips

1. **Always include `-H "Content-Type: application/json"`** for POST requests with bodies
2. **Copy OTP from console output** when testing forgot password flow
3. **Test error cases in order** to avoid side effects
4. **Use unique usernames/emails** for each test (**prepend timestamp or random number**)
5. **Save successful responses** to verify token structure
6. **Check HTTP status codes** - they're part of the API contract
7. **Test both happy and error paths** for complete coverage


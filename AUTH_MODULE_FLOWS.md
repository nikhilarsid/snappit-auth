# Auth Module - Complete Flow Documentation

## Table of Contents
1. [Happy Path Flows](#happy-path-flows)
2. [Error/Edge Case Flows](#erroredge-case-flows)
3. [Password Requirements](#password-requirements)
4. [Username Requirements](#username-requirements)
5. [Test Data](#test-data)

---

## Happy Path Flows

### Flow 1: User Signup (Create Account)
**Endpoint**: `POST /v1/auth/signup`
**Status Code**: 201 Created
**Request Headers**: `Content-Type: application/json`

**Request Body**:
```json
{
  "name": "John Doe",
  "username": "john_doe123",
  "email": "john@example.com",
  "password": "SecurePass@123"
}
```

**Success Response** (201):
```json
{
  "id": "user_id_123",
  "username": "john_doe123",
  "email": "john@example.com",
  "profile": {
    "name": "John Doe",
    "bio": "",
    "avatarUrl": ""
  },
  "followersCount": 0,
  "followingCount": 0,
  "createdAt": "2024-02-15T10:00:00Z",
  "updatedAt": "2024-02-15T10:00:00Z"
}
```

**Cookie Set**: `token=<JWT_TOKEN>` (HttpOnly, Secure, Path=/, MaxAge=7 days)

---

### Flow 2: User Login (Email/Password)
**Endpoint**: `POST /v1/auth/login`
**Status Code**: 200 OK
**Request Headers**: `Content-Type: application/json`

**Request Body (using email)**:
```json
{
  "usernameOrEmail": "john@example.com",
  "password": "SecurePass@123"
}
```

**Request Body (using username)**:
```json
{
  "usernameOrEmail": "john_doe123",
  "password": "SecurePass@123"
}
```

**Success Response** (200):
```json
{
  "id": "user_id_123",
  "username": "john_doe123",
  "email": "john@example.com",
  "profile": {
    "name": "John Doe",
    "bio": "",
    "avatarUrl": ""
  },
  "followersCount": 0,
  "followingCount": 0,
  "createdAt": "2024-02-15T10:00:00Z",
  "updatedAt": "2024-02-15T10:00:00Z"
}
```

**Cookie Set**: `token=<JWT_TOKEN>` (HttpOnly, Secure, Path=/, MaxAge=7 days)

---

### Flow 3: Google OAuth Login
**Endpoint**: `POST /v1/auth/google`
**Status Code**: 200 OK
**Request Headers**: `Content-Type: application/json`

**Request Body**:
```json
{
  "idToken": "<GOOGLE_ID_TOKEN>"
}
```

**Success Response** (200):
```json
{
  "id": "user_id_456",
  "username": "john.doe@example1234",
  "email": "john@example.com",
  "profile": {
    "name": "John Doe",
    "bio": "",
    "avatarUrl": "https://..."
  },
  "followersCount": 0,
  "followingCount": 0,
  "createdAt": "2024-02-15T10:00:00Z",
  "updatedAt": "2024-02-15T10:00:00Z"
}
```

**Cookie Set**: `token=<JWT_TOKEN>` (HttpOnly, Secure, Path=/, MaxAge=7 days)

---

### Flow 4: Forgot Password - Initiate
**Endpoint**: `POST /v1/auth/forgot-password/init`
**Status Code**: 200 OK
**Request Headers**: `Content-Type: application/json`

**Request Body**:
```json
{
  "email": "john@example.com"
}
```

**Success Response** (200):
Empty body (void)

**Side Effect**: 
- OTP generated (6 digits)
- Printed to console: `OTP for john@example.com: 123456`
- Stored in database with 5-minute expiry

---

### Flow 5: Forgot Password - Verify OTP
**Endpoint**: `POST /v1/auth/forgot-password/verify`
**Status Code**: 200 OK
**Request Headers**: `Content-Type: application/json`

**Request Body**:
```json
{
  "email": "john@example.com",
  "code": "123456"
}
```

**Success Response** (200):
Empty body (void)

**Meaning**: OTP is valid, user can now reset password

---

### Flow 6: Forgot Password - Reset Password
**Endpoint**: `POST /v1/auth/forgot-password/reset`
**Status Code**: 200 OK
**Request Headers**: `Content-Type: application/json`

**Request Body**:
```json
{
  "email": "john@example.com",
  "code": "123456",
  "newPassword": "NewSecurePass@456"
}
```

**Success Response** (200):
Empty body (void)

**Side Effects**:
- Password updated in database (bcrypt hashed)
- OTP deleted (prevents reuse)
- User can now login with new password

---

### Flow 7: Logout
**Endpoint**: `POST /v1/auth/logout`
**Status Code**: 200 OK
**Request Headers**: None required

**Request Body**: None (empty POST)

**Success Response** (200):
Empty body (void)

**Cookie Set**: `token=` (empty), MaxAge=0 (clears cookie)

---

## Error/Edge Case Flows

### Signup - Error Cases

#### Error 1.1: Weak Password
**Endpoint**: `POST /v1/auth/signup`
**Status Code**: 400 Bad Request

**Invalid Passwords** (must have: 8+ chars, uppercase, lowercase, digit, special char):
- "short" (too short)
- "noupppercase123!" (no uppercase)
- "NOLOWERCASE123!" (no lowercase)
- "NoDigits!" (no digit)
- "NoSpecial123" (no special char)

**Request Body**:
```json
{
  "name": "John Doe",
  "username": "john_doe123",
  "email": "john@example.com",
  "password": "weak"
}
```

**Error Response** (400):
```json
{
  "error": "Password is too weak",
  "code": "WEAK_PASSWORD",
  "status": 400
}
```

---

#### Error 1.2: Invalid Username Format
**Endpoint**: `POST /v1/auth/signup`
**Status Code**: 400 Bad Request

**Invalid Usernames**:
- "ab" (too short, min 3 chars)
- "username with spaces" (spaces not allowed)
- "user@name" (@ not allowed)
- "user#123" (# not allowed)
- Allowed: alphanumeric, underscore, dash, dot

**Request Body**:
```json
{
  "name": "John Doe",
  "username": "user@invalid",
  "email": "john@example.com",
  "password": "SecurePass@123"
}
```

**Error Response** (400):
```json
{
  "error": "Invalid username format",
  "code": "INVALID_USERNAME_FORMAT",
  "status": 400
}
```

---

#### Error 1.3: Username Already Exists
**Endpoint**: `POST /v1/auth/signup`
**Status Code**: 409 Conflict

**Request Body** (second signup with same username):
```json
{
  "name": "Jane Doe",
  "username": "john_doe123",
  "email": "jane@example.com",
  "password": "SecurePass@123"
}
```

**Error Response** (409):
```json
{
  "error": "Username already in use",
  "code": "USERNAME_ALREADY_EXISTS",
  "status": 409
}
```

---

#### Error 1.4: Email Already Registered
**Endpoint**: `POST /v1/auth/signup`
**Status Code**: 409 Conflict

**Request Body** (second signup with same email):
```json
{
  "name": "Jane Doe",
  "username": "jane_doe123",
  "email": "john@example.com",
  "password": "SecurePass@123"
}
```

**Error Response** (409):
```json
{
  "error": "Email already registered",
  "code": "EMAIL_ALREADY_EXISTS",
  "status": 409
}
```

---

#### Error 1.5: Missing Required Fields
**Endpoint**: `POST /v1/auth/signup`
**Status Code**: 400 Bad Request

**Request Body** (missing name):
```json
{
  "username": "john_doe123",
  "email": "john@example.com",
  "password": "SecurePass@123"
}
```

**Error Response** (400):
```json
{
  "error": "Validation failed",
  "message": "Name is required",
  "status": 400
}
```

---

### Login - Error Cases

#### Error 2.1: Invalid Credentials (User Not Found)
**Endpoint**: `POST /v1/auth/login`
**Status Code**: 401 Unauthorized

**Request Body**:
```json
{
  "usernameOrEmail": "nonexistent@example.com",
  "password": "SecurePass@123"
}
```

**Error Response** (401):
```json
{
  "error": "Incorrect credentials",
  "code": "INVALID_CREDENTIALS",
  "status": 401
}
```

---

#### Error 2.2: Invalid Credentials (Wrong Password)
**Endpoint**: `POST /v1/auth/login`
**Status Code**: 401 Unauthorized

**Request Body**:
```json
{
  "usernameOrEmail": "john_doe123",
  "password": "WrongPassword@123"
}
```

**Error Response** (401):
```json
{
  "error": "Incorrect credentials",
  "code": "INVALID_CREDENTIALS",
  "status": 401
}
```

---

#### Error 2.3: Google-Only Account (Cannot Login with Password)
**Endpoint**: `POST /v1/auth/login`
**Status Code**: 403 Forbidden

**Scenario**: User created account via Google OAuth only, trying to login with password

**Request Body**:
```json
{
  "usernameOrEmail": "google_only_user@example.com",
  "password": "SomePassword@123"
}
```

**Error Response** (403):
```json
{
  "error": "This account uses Google login",
  "code": "GOOGLE_AUTH_REQUIRED",
  "status": 403
}
```

---

#### Error 2.4: Missing Required Fields
**Endpoint**: `POST /v1/auth/login`
**Status Code**: 400 Bad Request

**Request Body** (missing password):
```json
{
  "usernameOrEmail": "john_doe123"
}
```

**Error Response** (400):
```json
{
  "error": "Validation failed",
  "message": "Password is required",
  "status": 400
}
```

---

### Google Login - Error Cases

#### Error 3.1: Invalid Google ID Token
**Endpoint**: `POST /v1/auth/google`
**Status Code**: 401 Unauthorized

**Request Body**:
```json
{
  "idToken": "invalid.token.here"
}
```

**Error Response** (401):
```json
{
  "error": "Invalid Google token",
  "code": "INVALID_TOKEN",
  "status": 401
}
```

---

#### Error 3.2: Missing ID Token
**Endpoint**: `POST /v1/auth/google`
**Status Code**: 400 Bad Request

**Request Body**:
```json
{
  "idToken": ""
}
```

**Error Response** (400):
```json
{
  "error": "Validation failed",
  "message": "ID token is required",
  "status": 400
}
```

---

### Forgot Password - Error Cases

#### Error 4.1: Forgot Password Init - User Not Found
**Endpoint**: `POST /v1/auth/forgot-password/init`
**Status Code**: 404 Not Found

**Request Body**:
```json
{
  "email": "nonexistent@example.com"
}
```

**Error Response** (404):
```json
{
  "error": "User not found",
  "code": "USER_NOT_FOUND",
  "status": 404
}
```

---

#### Error 4.2: Forgot Password Verify - Invalid OTP
**Endpoint**: `POST /v1/auth/forgot-password/verify`
**Status Code**: 400 Bad Request

**Request Body** (wrong OTP code):
```json
{
  "email": "john@example.com",
  "code": "999999"
}
```

**Error Response** (400):
```json
{
  "error": "Invalid OTP",
  "code": "INVALID_OTP",
  "status": 400
}
```

---

#### Error 4.3: Forgot Password Verify - OTP Expired
**Endpoint**: `POST /v1/auth/forgot-password/verify`
**Status Code**: 400 Bad Request

**Request Body** (OTP older than 5 minutes):
```json
{
  "email": "john@example.com",
  "code": "123456"
}
```

**Error Response** (400):
```json
{
  "error": "OTP Expired",
  "code": "OTP_EXPIRED",
  "status": 400
}
```

---

#### Error 4.4: Forgot Password Reset - Weak Password
**Endpoint**: `POST /v1/auth/forgot-password/reset`
**Status Code**: 400 Bad Request

**Request Body**:
```json
{
  "email": "john@example.com",
  "code": "123456",
  "newPassword": "weak"
}
```

**Error Response** (400):
```json
{
  "error": "Password is too weak",
  "code": "WEAK_PASSWORD",
  "status": 400
}
```

---

#### Error 4.5: Forgot Password Reset - Invalid OTP
**Endpoint**: `POST /v1/auth/forgot-password/reset`
**Status Code**: 400 Bad Request

**Request Body**:
```json
{
  "email": "john@example.com",
  "code": "999999",
  "newPassword": "NewSecurePass@456"
}
```

**Error Response** (400):
```json
{
  "error": "Invalid OTP",
  "code": "INVALID_OTP",
  "status": 400
}
```

---

#### Error 4.6: Forgot Password Reset - OTP Expired
**Endpoint**: `POST /v1/auth/forgot-password/reset`
**Status Code**: 400 Bad Request

**Request Body** (OTP older than 5 minutes):
```json
{
  "email": "john@example.com",
  "code": "123456",
  "newPassword": "NewSecurePass@456"
}
```

**Error Response** (400):
```json
{
  "error": "OTP Expired",
  "code": "OTP_EXPIRED",
  "status": 400
}
```

---

#### Error 4.7: Forgot Password Reset - User Not Found
**Endpoint**: `POST /v1/auth/forgot-password/reset`
**Status Code**: 404 Not Found

**Request Body**:
```json
{
  "email": "nonexistent@example.com",
  "code": "123456",
  "newPassword": "NewSecurePass@456"
}
```

**Error Response** (404):
```json
{
  "error": "User not found",
  "code": "USER_NOT_FOUND",
  "status": 404
}
```

---

## Password Requirements

Password must satisfy ALL of the following:
- **Minimum length**: 8 characters
- **Uppercase letter**: At least one A-Z
- **Lowercase letter**: At least one a-z
- **Digit**: At least one 0-9
- **Special character**: At least one of: `!@#$%^&*()_+-=[]{}|;:,.<>?`

### Valid Passwords
- `SecurePass@123`
- `MyPass!2024`
- `Test@Password123`
- `Ch@ng3Me123`

### Invalid Passwords
- `short` (too short, no uppercase, no digit, no special char)
- `SecurePass123` (no special char)
- `ALLUPPERCASE123!` (no lowercase)
- `alllowercase123!` (no uppercase)
- `NoDigits!@#` (no digit)
- `NoSpecial123` (no special char)

---

## Username Requirements

Username must satisfy ALL of the following:
- **Length**: 3-20 characters
- **Characters**: Alphanumeric (a-z, A-Z, 0-9), underscore (_), dash (-), dot (.)
- **Uniqueness**: Must not already exist in system

### Valid Usernames
- `john_doe123`
- `jane.smith`
- `user-123`
- `abc`
- `User_Name.1`

### Invalid Usernames
- `ab` (too short, min 3)
- `user with spaces` (spaces not allowed)
- `user@email` (@ not allowed)
- `user#123` (# not allowed)
- `user!` (! not allowed)

---

## Test Data

### Test User 1 (For Success Cases)
```
Name: Test User One
Username: test_user_one
Email: testuser1@example.com
Password: TestPass@123
```

### Test User 2 (For Success Cases Alternative)
```
Name: Alice Johnson
Username: alice.johnson
Email: alice.johnson@example.com
Password: AlicePass@456
```

### Test User 3 (Google-Only User)
```
Name: Bob Smith
Username: bob.smith.xyz
Email: bob.smith@example.com
Auth Type: GOOGLE (no password)
```

### Test User 4 (Weak Password Testing)
```
Username: weak_user
Email: weak@example.com
Passwords to try: weak, NoSpecial123, PASSWORD123, password123!, Pas@
```

### Invalid/Edge Case Data
```
Username Min: "ab" (1 char too short)
Username Max+: "thisusernameistoolongfortesting" (too long)
Email Invalid: "notanemail", "test@", "@example.com"
```

---

## Testing Order (Recommended)

1. **Happy Paths First** (Flows 1-7)
   - Signup new user
   - Login with username
   - Login with email
   - Logout
   - Forgot password (init → verify → reset flow)

2. **Signup Error Cases** (Errors 1.1-1.5)
   - Test each invalid scenario

3. **Login Error Cases** (Errors 2.1-2.4)
   - Test each invalid scenario

4. **Forgot Password Error Cases** (Errors 4.1-4.7)
   - Test each invalid scenario

---


# Profile Module - Complete Flow Documentation

## Table of Contents
1. [Overview](#overview)
2. [Happy Path Flows](#happy-path-flows)
3. [Error/Edge Case Flows](#erroredge-case-flows)
4. [Validation Rules](#validation-rules)
5. [Test Data](#test-data)

---

## Overview

The Profile Module handles user profile management including viewing public profiles, accessing own profile, and updating profile information.

### Endpoints Summary
```
GET    /v1/profile/{username}     - Get public profile by username
GET    /v1/profile/my             - Get own profile (authenticated)
PATCH  /v1/profile                - Update own profile (authenticated)
```

---

## Happy Path Flows

### Flow 1: Get Public Profile by Username
**Endpoint**: `GET /v1/profile/{username}`
**Authentication**: Not required
**Status Code**: 200 OK

**Request**:
```
GET /v1/profile/test_user_one
Content-Type: application/json
```

**Success Response** (200):
```json
{
  "username": "test_user_one",
  "avatarUrl": "",
  "bio": "",
  "name": "Test User One",
  "followersCount": 0,
  "followingCount": 0,
  "isFollowing": false,
  "createdAt": "2026-02-14T21:02:43.423Z"
}
```

**Notes**:
- Works for any existing username
- Returns public profile information
- isFollowing is false for non-followers
- No authentication required

---

### Flow 2: Get Own Profile (Authenticated)
**Endpoint**: `GET /v1/profile/my`
**Authentication**: Required (Bearer token)
**Status Code**: 200 OK

**Request**:
```
GET /v1/profile/my
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

**Success Response** (200):
```json
{
  "username": "test_user_one",
  "name": "Test User One",
  "avatarUrl": "",
  "bio": "",
  "followersCount": 0,
  "followingCount": 0,
  "isFollowing": false,
  "createdAt": "2026-02-14T21:02:43.423Z",
  "updatedAt": "2026-02-14T21:02:43.423Z"
}
```

**Notes**:
- Requires valid JWT token in Authorization header
- Returns full profile with all fields
- canViewFullProfile = true for own profile
- Shows updatedAt field (own profile only)

---

### Flow 3: Update Profile - Change Name
**Endpoint**: `PATCH /v1/profile`
**Authentication**: Required (Bearer token)
**Status Code**: 200 OK

**Request Body**:
```json
{
  "name": "Updated User Name"
}
```

**Success Response** (200):
```json
{
  "username": "test_user_one",
  "name": "Updated User Name",
  "avatarUrl": "",
  "bio": "",
  "followersCount": 0,
  "followingCount": 0,
  "createdAt": "2026-02-14T21:02:43.423Z",
  "updatedAt": "2026-02-14T21:02:43.423Z"
}
```

**Notes**:
- Only name field updated
- Other fields remain unchanged
- updatedAt timestamp is refreshed

---

### Flow 4: Update Profile - Change Bio
**Endpoint**: `PATCH /v1/profile`
**Authentication**: Required (Bearer token)
**Status Code**: 200 OK

**Request Body**:
```json
{
  "bio": "This is my bio"
}
```

**Success Response** (200):
```json
{
  "username": "test_user_one",
  "name": "Test User One",
  "avatarUrl": "",
  "bio": "This is my bio",
  "followersCount": 0,
  "followingCount": 0,
  "createdAt": "2026-02-14T21:02:43.423Z",
  "updatedAt": "2026-02-14T21:02:43.423Z"
}
```

**Notes**:
- Only bio field updated
- Max 160 characters allowed
- Other fields remain unchanged

---

### Flow 5: Update Profile - Change Avatar
**Endpoint**: `PATCH /v1/profile`
**Authentication**: Required (Bearer token)
**Status Code**: 200 OK

**Request Body**:
```json
{
  "avatarUrl": "https://example.com/avatar.jpg"
}
```

**Success Response** (200):
```json
{
  "username": "test_user_one",
  "name": "Test User One",
  "avatarUrl": "https://example.com/avatar.jpg",
  "bio": "",
  "followersCount": 0,
  "followingCount": 0,
  "createdAt": "2026-02-14T21:02:43.423Z",
  "updatedAt": "2026-02-14T21:02:43.423Z"
}
```

**Notes**:
- Only avatarUrl field updated
- No length restrictions
- Other fields remain unchanged

---

### Flow 6: Update Profile - Multiple Fields
**Endpoint**: `PATCH /v1/profile`
**Authentication**: Required (Bearer token)
**Status Code**: 200 OK

**Request Body**:
```json
{
  "name": "New Name",
  "bio": "New bio here",
  "avatarUrl": "https://example.com/new-avatar.jpg"
}
```

**Success Response** (200):
```json
{
  "username": "test_user_one",
  "name": "New Name",
  "avatarUrl": "https://example.com/new-avatar.jpg",
  "bio": "New bio here",
  "followersCount": 0,
  "followingCount": 0,
  "createdAt": "2026-02-14T21:02:43.423Z",
  "updatedAt": "2026-02-14T21:02:43.423Z"
}
```

**Notes**:
- All three updatable fields changed
- Order of fields doesn't matter
- All validations applied

---

## Error/Edge Case Flows

### Error 1: Invalid Username Format
**Endpoint**: `GET /v1/profile/{username}`
**Status Code**: 400 Bad Request

**Invalid Usernames**:
- "ab" (too short, min 3 chars)
- "user@name" (@ not allowed)
- "user#123" (# not allowed)
- Username must match: `^[a-zA-Z0-9_.-]{3,30}$`

**Request**:
```
GET /v1/profile/ab
```

**Error Response** (400):
```json
{
  "error": "INVALID_USERNAME",
  "message": "Username format is invalid"
}
```

**Notes**:
- Path variable validation happens before service call
- Regex: `^[a-zA-Z0-9_.-]{3,30}$`
- Allowed: alphanumeric, underscore, dash, dot
- Min 3 chars, max 30 chars (stricter than auth module's 20)

---

### Error 2: User Not Found
**Endpoint**: `GET /v1/profile/{username}`
**Status Code**: 404 Not Found

**Request**:
```
GET /v1/profile/nonexistent_user
```

**Error Response** (404):
```json
{
  "error": "USER_NOT_FOUND",
  "message": "Profile does not exist"
}
```

**Notes**:
- Username format is valid but user doesn't exist
- Common scenario when accessing unknown profiles

---

### Error 3: Unauthorized - Missing Token
**Endpoint**: `GET /v1/profile/my`
**Status Code**: 401 Unauthorized

**Request** (without token):
```
GET /v1/profile/my
```

**Error Response** (401):
```json
{
  "error": "UNAUTHORIZED",
  "message": "Authentication required"
}
```

**Notes**:
- GET /v1/profile/my requires authentication
- GET /v1/profile/{username} does NOT require authentication
- Missing or invalid token returns 401

---

### Error 4: Unauthorized - Invalid Token
**Endpoint**: `GET /v1/profile/my`
**Status Code**: 401 Unauthorized

**Request**:
```
GET /v1/profile/my
Authorization: Bearer invalid.token.here
```

**Error Response** (401):
```json
{
  "error": "UNAUTHORIZED",
  "message": "Authentication required"
}
```

**Notes**:
- Token validation fails in SecurityContextHolder
- Same response as missing token

---

### Error 5: Profile User Not Found (After Auth)
**Endpoint**: `GET /v1/profile/my`
**Status Code**: 404 Not Found

**Scenario**: JWT token is valid but references non-existent user

**Error Response** (404):
```json
{
  "error": "USER_NOT_FOUND",
  "message": "User not found"
}
```

**Notes**:
- Rare edge case (user deleted after login)
- Token is valid but user doesn't exist

---

### Error 6: Update - Unauthorized
**Endpoint**: `PATCH /v1/profile`
**Status Code**: 401 Unauthorized

**Request** (without token):
```
PATCH /v1/profile
Content-Type: application/json
{
  "name": "New Name"
}
```

**Error Response** (401):
```json
{
  "error": "UNAUTHORIZED",
  "message": "Authentication required"
}
```

**Notes**:
- PATCH /v1/profile always requires authentication
- No public profile updates allowed

---

### Error 7: Update - No Valid Fields
**Endpoint**: `PATCH /v1/profile`
**Status Code**: 400 Bad Request

**Request Body** (all null/blank):
```json
{
  "name": null,
  "bio": null,
  "avatarUrl": null
}
```

**or**:

```json
{
  "name": "",
  "bio": "",
  "avatarUrl": ""
}
```

**Error Response** (400):
```json
{
  "error": "NO_VALID_FIELDS",
  "message": "No updatable fields provided"
}
```

**Notes**:
- At least one field must be provided and non-blank
- Empty strings are treated as blank
- Null values are treated as not provided

---

### Error 8: Update - Name Too Long
**Endpoint**: `PATCH /v1/profile`
**Status Code**: 400 Bad Request

**Request Body**:
```json
{
  "name": "This is a very long name that exceeds the maximum limit of 100 characters because we want to test validation of the name field in our API"
}
```

**Error Response** (400):
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid request parameters",
  "details": {
    "name": "Name must be under 100 characters"
  }
}
```

**Notes**:
- Max 100 characters for name field
- Validation happens in DTO with @Size annotation
- Strict validation before service call

---

### Error 9: Update - Bio Too Long
**Endpoint**: `PATCH /v1/profile`
**Status Code**: 400 Bad Request

**Request Body**:
```json
{
  "bio": "This bio is way too long... Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua for testing"
}
```

**Error Response** (400):
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid request parameters",
  "details": {
    "bio": "Bio must be under 160 characters"
  }
}
```

**Notes**:
- Max 160 characters for bio field
- Validation happens in DTO with @Size annotation
- Typical social media bio limit

---

### Error 10: Update - User Not Found
**Endpoint**: `PATCH /v1/profile`
**Status Code**: 404 Not Found

**Scenario**: JWT token is valid but user was deleted

**Request Body**:
```json
{
  "name": "New Name"
}
```

**Error Response** (404):
```json
{
  "error": "USER_NOT_FOUND",
  "message": "User not found"
}
```

**Notes**:
- Rare edge case (user deleted after login)
- Token is valid but user doesn't exist in database

---

### Error 11: Update - Missing Authorization Header
**Endpoint**: `PATCH /v1/profile`
**Status Code**: 401 Unauthorized

**Request** (no token):
```
PATCH /v1/profile
Content-Type: application/json
```

**Error Response** (401):
```json
{
  "error": "UNAUTHORIZED",
  "message": "Authentication required"
}
```

---

## Validation Rules

### Name Field
```
- Optional field
- Maximum: 100 characters
- No minimum length requirement (1+ chars when provided)
- Any characters allowed
- Validation: @Size(max = 100)

Valid Examples:
  • "John Doe"
  • "李明" (international characters)
  • "user_123"
  
Invalid Examples:
  • Names exceeding 100 chars → VALIDATION_ERROR
```

### Bio Field
```
- Optional field
- Maximum: 160 characters
- No minimum length requirement (1+ chars when provided)
- Typical social media bio
- Validation: @Size(max = 160)

Valid Examples:
  • "Software engineer | Coffee lover"
  • "Just a person living life 🎵"
  • "生活，工作，热爱"

Invalid Examples:
  • Bios exceeding 160 chars → VALIDATION_ERROR
```

### AvatarUrl Field
```
- Optional field
- No length restrictions
- Any URL format accepted
- Typically image URL
- No validation

Valid Examples:
  • "https://example.com/avatar.jpg"
  • "https://gravatar.com/avatar/123abc"
  • "data:image/png;base64,..."
```

### Update Request Validation
```
- At least one field must be provided and non-blank
- Empty strings are treated as blank (ignored)
- Null values are ignored
- Must provide at least: name OR bio OR avatarUrl (non-blank)

Valid Requests:
  • {"name": "New Name"}
  • {"bio": "New bio"}
  • {"avatarUrl": "url"}
  • {"name": "Name", "bio": "Bio"}
  • Any combination with at least one non-blank field

Invalid Requests:
  • {} → NO_VALID_FIELDS
  • {"name": "", "bio": "", "avatarUrl": ""} → NO_VALID_FIELDS
  • {"name": null, "bio": null, "avatarUrl": null} → NO_VALID_FIELDS
```

### Restricted Fields (Cannot Update)
```
The following fields CANNOT be updated:
  • username (created at signup)
  • email (created at signup)
  • followersCount (managed by follow module)
  • followingCount (managed by follow module)
  • createdAt (immutable)
  • updatedAt (automatically managed)

Attempts to update these fields will be silently ignored.
```

---

## Test Data

### Existing Test Users
```
User 1:
  ID: 698fb32aa9713a57589ca933
  Username: hhoehunterr
  Email: kshitij2@gmail.com
  Followers: 0
  Following: 0

User 2:
  ID: 6990546938c4d33011af2045
  Username: vikrant
  Email: vikrant@example.com
  Followers: 100
  Following: 50

User 3:
  ID: 6990e2f390f9682cf02d917b
  Username: test_user_one
  Email: testuser1@example.com
  Followers: 0
  Following: 0

User 4:
  ID: 6990e34d90f9682cf02d917e
  Username: alice.johnson
  Email: alice.johnson@example.com
  Followers: 0
  Following: 0

User 5:
  ID: 6990e3b190f9682cf02d9180
  Username: bob.smith
  Email: bob.smith@example.com
  Followers: 0
  Following: 0
```

### Test Scenarios
```
Profile Updates to Test:
  1. Name update: "Test User One" → "Updated User Name"
  2. Bio update: "" → "This is my updated bio"
  3. Avatar update: "" → "https://example.com/avatar.jpg"
  4. All three fields: Update name, bio, and avatar together
  5. Revert changes: Update back to original values
```

---

## Password Requirements for Login

Since updating profiles requires authentication, users need JWT tokens from login. Use these credentials from earlier auth testing:

```
test_user_one:
  Email: testuser1@example.com
  Password: TestPass@123

alice.johnson:
  Email: alice.johnson@example.com
  Password: AlicePass@456

bob.smith:
  Email: bob.smith@example.com
  Password: BobPass@789
```

---

## Testing Order (Recommended)

**Phase 1: Public Profile Access**
1. Get profile by valid username
2. Get profile by invalid username format
3. Get profile for non-existent user

**Phase 2: Authenticated Access**
1. Login user and get JWT token
2. Get own profile with token
3. Get own profile without token (error)

**Phase 3: Profile Updates**
1. Update name field only
2. Update bio field only
3. Update avatar field only
4. Update multiple fields
5. Update with no valid fields (error)
6. Update with too-long name (error)
7. Update with too-long bio (error)

**Phase 4: Edge Cases**
1. Attempt to update restricted fields (should be ignored)
2. Character validation (international characters)
3. Empty string vs null handling

---


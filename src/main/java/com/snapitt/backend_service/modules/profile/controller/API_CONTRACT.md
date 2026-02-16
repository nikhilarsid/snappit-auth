# Profile Module API Contract

## Overview
The Profile Module provides endpoints for managing user profiles, including retrieving public profiles, retrieving own profile, and updating user profile information.

---

## Endpoints Summary

| Method | Endpoint | Auth Required | Description |
|--------|----------|---------------|-------------|
| GET | `/v1/profile/{username}` | Optional | Get public profile by username |
| GET | `/v1/profile/my` | Required | Get authenticated user's own profile |
| PATCH | `/v1/profile` | Required | Update authenticated user's profile |

---

## Endpoint: Get Public Profile

### Request
```http
GET /v1/profile/{username}
Host: api.example.com
Accept: application/json
Cookie: token=<optional-JWT>
```

### Path Parameters
| Parameter | Type | Validation | Description |
|-----------|------|-----------|-------------|
| `username` | String | Pattern: `^[a-zA-Z0-9_.-]{3,30}$` | Target user's username |

### Response: 200 OK
```json
{
  "username": "kshitij",
  "avatarUrl": "https://cdn.example.com/avatar.jpg",
  "bio": "Building scalable systems.",
  "followersCount": 120,
  "followingCount": 45,
  "isFollowing": false,
  "createdAt": "2026-02-13T10:00:00Z"
}
```

### Response Fields
| Field | Type | Visibility | Description |
|-------|------|-----------|-------------|
| `username` | String | Public | Username of the profile |
| `avatarUrl` | String | Public | Profile picture URL |
| `bio` | String | Own profile or Approved followers only | User biography |
| `followersCount` | Long | Public | Number of followers |
| `followingCount` | Long | Public | Number of users being followed |
| `isFollowing` | Boolean | Authenticated users | Follow status |
| `createdAt` | ISO 8601 | Public | Account creation timestamp |

### Error Responses

#### 400 Bad Request - Invalid Username Format
```json
{
  "error": "INVALID_USERNAME",
  "message": "Username format is invalid",
  "details": {
    "username": "Username format is invalid"
  }
}
```
**Trigger:** Username doesn't match pattern `^[a-zA-Z0-9_.-]{3,30}$`

#### 404 Not Found - User Not Found
```json
{
  "error": "USER_NOT_FOUND",
  "message": "Profile does not exist"
}
```
**Trigger:** Username does not exist in database

#### 500 Internal Server Error
```json
{
  "error": "INTERNAL_SERVER_ERROR",
  "message": "An unexpected error occurred"
}
```
**Trigger:** Unexpected database errors or server exceptions

---

## Endpoint: Get Own Profile

### Request
```http
GET /v1/profile/my
Host: api.example.com
Accept: application/json
Cookie: token=<valid-JWT>
Authorization: Bearer <JWT>
```

### Authentication
- **Required:** Yes
- **Method:** JWT in Cookie or Authorization header
- **Invalid/Expired Token Response:** 401 Unauthorized

### Response: 200 OK
```json
{
  "username": "kshitij",
  "avatarUrl": "https://cdn.example.com/avatar.jpg",
  "bio": "Building scalable systems.",
  "followersCount": 120,
  "followingCount": 45,
  "isFollowing": false,
  "createdAt": "2026-02-13T10:00:00Z"
}
```

### Response Fields
| Field | Type | Description |
|-------|------|-------------|
| `username` | String | Your username |
| `avatarUrl` | String | Your profile picture URL |
| `bio` | String | Your biography |
| `followersCount` | Long | Number of your followers |
| `followingCount` | Long | Number of users you follow |
| `isFollowing` | Boolean | Always false (own profile) |
| `createdAt` | ISO 8601 | Your account creation timestamp |

### Error Responses

#### 401 Unauthorized - Missing or Invalid Authentication
```json
{
  "error": "UNAUTHORIZED",
  "message": "Authentication required"
}
```
**Trigger:** No JWT token provided or token is invalid/expired

#### 404 Not Found - User Not Found
```json
{
  "error": "USER_NOT_FOUND",
  "message": "User not found"
}
```
**Trigger:** User account deleted but token still valid (rare edge case)

#### 500 Internal Server Error
```json
{
  "error": "INTERNAL_SERVER_ERROR",
  "message": "An unexpected error occurred"
}
```
**Trigger:** Unexpected database errors or server exceptions

---

## Endpoint: Update Profile

### Request
```http
PATCH /v1/profile
Host: api.example.com
Content-Type: application/json
Cookie: token=<valid-JWT>
Authorization: Bearer <JWT>

{
  "name": "Kshitij Singh",
  "bio": "Distributed systems enthusiast",
  "avatarUrl": "https://cdn.example.com/new-avatar.jpg"
}
```

### Authentication
- **Required:** Yes
- **Method:** JWT in Cookie or Authorization header
- **Invalid/Expired Token Response:** 401 Unauthorized

### Request Body Schema
```json
{
  "name": "string (optional, max 100 chars)",
  "bio": "string (optional, max 160 chars)",
  "avatarUrl": "string (optional)"
}
```

### Request Validation Rules
| Field | Constraint | Error Code |
|-------|-----------|-----------|
| `name` | Max 100 characters | `VALIDATION_ERROR` |
| `bio` | Max 160 characters | `VALIDATION_ERROR` |
| At least one field | Must provide at least one field | `NO_VALID_FIELDS` |

### Response: 200 OK
```json
{
  "username": "kshitij",
  "avatarUrl": "https://cdn.example.com/new-avatar.jpg",
  "bio": "Distributed systems enthusiast",
  "name": "Kshitij Singh",
  "updatedAt": "2026-02-13T11:00:00Z"
}
```

### Response Fields
| Field | Type | Description |
|-------|------|-------------|
| `username` | String | User's username (read-only) |
| `avatarUrl` | String | Updated avatar URL |
| `bio` | String | Updated bio |
| `name` | String | Updated name |
| `updatedAt` | ISO 8601 | Update timestamp |

### Error Responses

#### 400 Bad Request - Missing Authentication
```json
{
  "error": "UNAUTHORIZED",
  "message": "Authentication required"
}
```
**Trigger:** No JWT token provided in request

#### 400 Bad Request - No Valid Fields Provided
```json
{
  "error": "NO_VALID_FIELDS",
  "message": "No updatable fields provided"
}
```
**Trigger:** Request body is empty or all fields are blank
**Example:**
```json
{}
// or
{
  "name": "",
  "bio": "",
  "avatarUrl": ""
}
```

#### 400 Bad Request - Field Validation Error
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid request parameters",
  "details": {
    "bio": "Bio must be under 160 characters",
    "name": "Name must be between 1 and 100 characters"
  }
}
```
**Trigger:** Field size constraints violated

#### 401 Unauthorized - Invalid/Expired Token
```json
{
  "error": "UNAUTHORIZED",
  "message": "Authentication required"
}
```
**Trigger:** JWT token is invalid, expired, or malformed

#### 404 Not Found - User Not Found
```json
{
  "error": "USER_NOT_FOUND",
  "message": "User not found"
}
```
**Trigger:** User deleted but token still valid (rare edge case)

#### 500 Internal Server Error
```json
{
  "error": "INTERNAL_SERVER_ERROR",
  "message": "An unexpected error occurred"
}
```
**Trigger:** Database failures or unexpected server errors

---

## Restrictive Constraints

### Fields That CANNOT Be Updated
The following fields are immutable and will be silently ignored if provided in the request:
- `username` - Set at account creation
- `email` - Set at account creation
- `followersCount` - Updated only by follow service
- `followingCount` - Updated only by follow service
- `createdAt` - Account creation timestamp
- `id` - Unique identifier

**Behavior:** Any attempt to update these fields will be ignored (no error thrown)

---

## Authentication

### JWT Token Requirements
- Location: Cookie (`token=`) or `Authorization: Bearer` header
- Encoding: HS256 / RS256
- Contains: User ID claim
- Expiration: Check token validity

### Public Endpoints
- GET /v1/profile/{username} - No auth required (but can be authenticated)

### Protected Endpoints
- PATCH /v1/profile - Auth required

---

## Validation Rules Summary

### Username Path Parameter
- **Pattern:** `^[a-zA-Z0-9_.-]{3,30}$`
- **Length:** 3-30 characters
- **Allowed:** Alphanumeric, underscore, dot, dash
- **Error Code:** `INVALID_USERNAME` (400)

### Name Field
- **Max Length:** 100 characters
- **Min Length:** 1 character (if provided)
- **Allowed:** Any characters
- **Error Code:** `VALIDATION_ERROR` (400)

### Bio Field
- **Max Length:** 160 characters
- **Min Length:** 1 character (if provided)
- **Allowed:** Any characters
- **Error Code:** `VALIDATION_ERROR` (400)

### Avatar URL Field
- **Max Length:** Unlimited
- **Allowed:** Any URL format
- **Validation:** None

---

## Exception Hierarchy

### Request Validation (400)
- `INVALID_USERNAME` - Path parameter validation failed
- `NO_VALID_FIELDS` - Empty update request
- `VALIDATION_ERROR` - Field constraint violation

### Authentication (401)
- `UNAUTHORIZED` - Missing or invalid JWT

### Not Found (404)
- `USER_NOT_FOUND` - User does not exist

### Server Error (500)
- `INTERNAL_SERVER_ERROR` - Unexpected database/server errors

---

## Handler Layer Responsibility

### ProfileController
- Validates `@PathVariable` username format via `@Pattern` annotation
- Validates authentication status
- Throws `ConstraintViolationException` via `@Validated`
- Delegates business logic to service layer

### ProfileService
- Validates request for empty/blank fields (`NO_VALID_FIELDS`)
- Validates field sizes (`VALIDATION_ERROR`)
- Queries user repository (throws `USER_NOT_FOUND`)
- Catches generic exceptions, wraps as `INTERNAL_SERVER_ERROR`

### GlobalExceptionHandler
- Converts `ConstraintViolationException` → HTTP 400 with `INVALID_USERNAME`
- Converts `MethodArgumentNotValidException` → HTTP 400 with `VALIDATION_ERROR`
- Converts `AuthException` → Appropriate HTTP status based on error code
- Catches all other exceptions → HTTP 500 with `INTERNAL_SERVER_ERROR`

---

## Example Workflows

### Workflow 1: Get Own Profile (Authenticated)
```
GET /v1/profile/kshitij
Cookie: token=<JWT>

✓ Username validated
✓ User found
✓ isFollowing = false (own profile)
✓ Full profile returned including bio
```

### Workflow 2: Get Other User's Profile
```
GET /v1/profile/alice
Cookie: token=<JWT for user kshitij>

✓ Username validated
✓ User found
✓ Check follow status → isFollowing = true/false/pending
✓ Bio only visible if: own profile OR approved follower
✓ Return profile with bio hidden or shown
```

### Workflow 3: Update Profile - Success
```
PATCH /v1/profile
Content-Type: application/json
Cookie: token=<JWT>

{
  "bio": "New bio here"
}

✓ Auth validated
✓ At least one field provided ✓
✓ Bio length ≤ 160 ✓
✓ User found
✓ Save to database
✓ Return updated profile with updatedAt
```

### Workflow 4: Update Profile - Empty Request
```
PATCH /v1/profile
Cookie: token=<JWT>

{}

✗ No updatable fields provided
→ 400 NO_VALID_FIELDS
```

### Workflow 5: Update Profile - Bio Too Long
```
PATCH /v1/profile
Cookie: token=<JWT>

{
  "bio": "This is a very long bio that exceeds 160 characters..."
}

✗ Bio validation failed (> 160 chars)
→ 400 VALIDATION_ERROR
```

---

## Rate Limiting & Performance

- No rate limiting currently implemented
- Database indexes required:
  - `users: { username: 1 }` UNIQUE
  - `users: { email: 1 }` UNIQUE

---

## Implementation Notes

1. **Validation Layers:**
   - DTO level: `@Size` constraints on UpdateProfileRequest
   - Path parameter: `@Pattern` on @PathVariable
   - Service level: Business logic validation (empty check, field validation)

2. **Error Handling Chain:**
   - Annotations → ConstraintViolationException → GlobalExceptionHandler
   - DTO validation → MethodArgumentNotValidException → GlobalExceptionHandler
   - Business logic → AuthException → GlobalExceptionHandler
   - Unexpected errors → Exception → GlobalExceptionHandler

3. **Partial Updates:**
   - All fields optional
   - Client sends only fields to update
   - Service validates presence and size constraints

4. **Future Enhancements:**
   - Add rate limiting
   - Add audit logging for profile changes
   - Add soft delete for profiles
   - Add profile visibility settings (private/public)

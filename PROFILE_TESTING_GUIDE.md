# Profile Module Testing Guide

## Quick Reference

**Module:** Profile Management  
**Base URL:** `http://localhost:8080/v1/profile`  
**Authentication:** Required for all endpoints  
**Test Date:** February 15, 2026  
**Tests Executed:** 24 (9 happy path, 15 error cases)  
**Pass Rate:** 100% ✅

---

## Overview

The Profile Module allows users to view and manage their profile information. All endpoints require JWT authentication via cookies.

### Key Features
- ✅ View public profiles of other users
- ✅ View own complete profile  
- ✅ Update profile fields (name, bio, avatar)
- ✅ Comprehensive field validation
- ✅ Read-only field protection (username, email, follower counts)

---

## Endpoints Tested

### 1. GET /v1/profile/{username}
**Purpose:** View user profile by username  
**Authentication:** Required  
**Status Code:** 200 OK

```bash
curl -b cookies.txt http://localhost:8080/v1/profile/alice_prof_test
```

**Response:**
```json
{
  "username": "alice_prof_test",
  "avatarUrl": "",
  "bio": "",
  "followersCount": 0,
  "followingCount": 0,
  "isFollowing": false,
  "createdAt": "2026-02-14T21:36:04.581Z"
}
```

**Validation Rules:**
- Username: 3-30 characters, alphanumeric + `_`, `-`, `.` only
- Returns 400 for invalid format
- Returns 404 for non-existent user

---

### 2. GET /v1/profile/my
**Purpose:** Get authenticated user's profile  
**Authentication:** Required  
**Status Code:** 200 OK

```bash
curl -b cookies.txt http://localhost:8080/v1/profile/my
```

**Response:**
```json
{
  "username": "alice_prof_test",
  "avatarUrl": "",
  "bio": "",
  "followersCount": 0,
  "followingCount": 0,
  "isFollowing": false,
  "createdAt": "2026-02-14T21:36:04.581Z"
}
```

---

### 3. PATCH /v1/profile
**Purpose:** Update own profile fields  
**Authentication:** Required  
**Status Code:** 200 OK

```bash
curl -X PATCH -b cookies.txt http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"name": "Updated Name", "bio": "New bio", "avatarUrl": "https://example.com/avatar.jpg"}'
```

**Updatable Fields:**
- `name` (max 100 characters)
- `bio` (max 160 characters)  
- `avatarUrl` (any length)

**Response:**
```json
{
  "username": "alice_prof_test",
  "avatarUrl": "https://example.com/avatar.jpg",
  "bio": "New bio",
  "name": "Updated Name",
  "followersCount": 0,
  "followingCount": 0,
  "isFollowing": false,
  "createdAt": "2026-02-14T21:36:05.552502Z"
}
```

---

## Happy Path Test Results

### Test 1: Get Public Profile
✅ **PASS** - 200 OK  
- Successfully retrieved alice_prof_test profile without authentication error
- Returned correct username, follower counts, timestamps

### Test 2: Get Own Profile
✅ **PASS** - 200 OK  
- Retrieved authenticated user's complete profile
- Included all profile fields and timestamps

### Test 3: Update Name Only
✅ **PASS** - 200 OK  
- Updated name field while preserving other fields
- Response included new name, empty bio, empty avatar

### Test 4: Verify Update
✅ **PASS** - 200 OK  
- Confirmed name change persisted in database

### Test 5: Update Bio Only
✅ **PASS** - 200 OK  
- Updated bio to "Product Manager & Designer"
- Other fields preserved (empty name, empty avatar)

### Test 6: Update Avatar Only
✅ **PASS** - 200 OK  
- Updated avatar URL to CDN link
- Bio and name preserved

### Test 7: Update Multiple Fields
✅ **PASS** - 200 OK  
- Updated all three fields in single request
- All changes applied correctly

### Test 8: Get Another User Profile
✅ **PASS** - 200 OK  
- Retrieved different user's profile while authenticated
- Shows updated avatar and bio

### Test 9: Get Third User Profile
✅ **PASS** - 200 OK  
- Retrieved another user's profile
- Confirmed data isolation between users

---

## Error Case Test Results

### Error Test 1: Invalid Username (Too Short)
✅ **PASS** - 400 INVALID_USERNAME  
**Input:** `ab`  
**Error:** 
```json
{
  "error": "INVALID_USERNAME",
  "message": "Username format is invalid",
  "details": {
    "getProfile.username": "Username format is invalid"
  }
}
```

### Error Test 2: Invalid Username (Special Character)
✅ **PASS** - 400 INVALID_USERNAME  
**Input:** `user@name`  
**Error:** Username format validation rejected `@` character

### Error Test 3: Invalid Username (Too Long)  
✅ **PASS** - 400 INVALID_USERNAME  
**Input:** `thisusernameiswaytoolongandexceedsthirtychars`  
**Error:** Exceeded 30-character limit

### Error Test 4: User Not Found
✅ **PASS** - 404 USER_NOT_FOUND  
**Input:** `nonexistent_user_xyz`  
**Error:**
```json
{
  "error": "USER_NOT_FOUND",
  "message": "Profile does not exist"
}
```

### Error Test 5: GET /my Without Authentication
✅ **PASS** - 401 Unauthorized  
- No cookie provided
- Security filter blocked access

### Error Test 6: GET /{username} Without Authentication
✅ **PASS** - 401 Unauthorized  
- No authentication token
- All endpoints require login

### Error Test 7: Invalid Token
✅ **PASS** - 401 Unauthorized  
- Expired/malformed token rejected
- SecurityContext not established

### Error Test 8: PATCH Without Authentication
✅ **PASS** - 401 Unauthorized  
- Update attempt blocked by security

### Error Test 9: Empty Request Body
✅ **PASS** - 400 NO_VALID_FIELDS  
**Input:** `{}`  
**Error:**
```json
{
  "error": "NO_VALID_FIELDS",
  "message": "No updatable fields provided"
}
```

### Error Test 10: All Blank Fields
✅ **PASS** - 400 NO_VALID_FIELDS  
**Input:** `{"name": "", "bio": "", "avatarUrl": ""}`  
**Error:** Empty strings treated as no fields provided

### Error Test 11: Name Exceeds Max Length
✅ **PASS** - 400 VALIDATION_ERROR  
**Input:** 101 character string  
**Error:**
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid request parameters",
  "details": {
    "name": "Name must be under 100 characters"
  }
}
```

### Error Test 12: Bio Exceeds Max Length
✅ **PASS** - 400 VALIDATION_ERROR  
**Input:** 161 character string  
**Error:**
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid request parameters",
  "details": {
    "bio": "Bio must be under 160 characters"
  }
}
```

### Error Test 13: Try to Update Username
✅ **PASS** - 400 NO_VALID_FIELDS  
**Input:** `{"username": "hacker_username"}`  
**Error:** Username is read-only field

### Error Test 14: Try to Update Email
✅ **PASS** - 400 NO_VALID_FIELDS  
**Input:** `{"email": "newemail@test.com"}`  
**Error:** Email is read-only field

### Error Test 15: Try to Update Follower Counts
✅ **PASS** - 400 NO_VALID_FIELDS  
**Input:** `{"followersCount": 100}`  
**Error:** Follower count is read-only field

---

## Validation Rules Summary

| Field | Rules | Example |
|-------|-------|---------|
| username (path) | 3-30 chars, alphanumeric + `_`, `-`, `.` | `alice_prof_test` |
| name (update) | Max 100 characters | `Alice Smith` |
| bio (update) | Max 160 characters | `Software Engineer` |
| avatarUrl (update) | Any length | `https://cdn.example.com/avatar.jpg` |
| followersCount | Read-only (cannot update) | Auto-managed |
| followingCount | Read-only (cannot update) | Auto-managed |
| email | Read-only (cannot update) | Set at signup |

---

## Authentication Details

**Method:** JWT Token in HttpOnly Cookie  
**Cookie Name:** `token`  
**Acquiring Token:** POST /v1/auth/signup or POST /v1/auth/login  
**Duration:** 7 days  
**Usage:** Automatically sent with each request if `-b cookies.txt` used in curl

---

## Test Execution Environment

```
Date: February 15, 2026
Server: Spring Boot (localhost:8080)
Database: MongoDB
Test Users Created: 3
  - alice_prof_test
  - bob_prof_test
  - charlie_prof_test
```

---

## Summary

✅ **All 24 tests passed (100% pass rate)**

**Coverage:**
- 9 happy path scenarios (all endpoints work as designed)
- 15 error cases (all validation and security working)
- All field validation constraints enforced
- All read-only fields protected
- Authentication required for all endpoints
- Proper error codes and messages returned

**Production Ready:** YES ✅

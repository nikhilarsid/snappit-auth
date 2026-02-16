# Profile Module - Curl Test Commands Reference

## Base URL
```
http://localhost:8080
```

---

## HAPPY PATH - Success Cases

### 1. Get Public Profile - test_user_one
```bash
curl -X GET http://localhost:8080/v1/profile/test_user_one
```

### 2. Get Public Profile - alice.johnson
```bash
curl -X GET http://localhost:8080/v1/profile/alice.johnson
```

### 3. Get Public Profile - bob.smith
```bash
curl -X GET http://localhost:8080/v1/profile/bob.smith
```

### 4. Get Public Profile - vikrant
```bash
curl -X GET http://localhost:8080/v1/profile/vikrant
```

### 5. Get Own Profile (with token)
```bash
curl -X GET http://localhost:8080/v1/profile/my \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

### 6. Update Profile - Name Only
```bash
curl -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{"name": "Updated User Name"}'
```

### 7. Update Profile - Bio Only
```bash
curl -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{"bio": "This is my updated bio"}'
```

### 8. Update Profile - Avatar Only
```bash
curl -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{"avatarUrl": "https://example.com/avatar.jpg"}'
```

### 9. Update Profile - All Fields
```bash
curl -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{
    "name": "New Full Name",
    "bio": "My new bio here",
    "avatarUrl": "https://example.com/new-avatar.jpg"
  }'
```

### 10. Get Own Profile Again (verify updates)
```bash
curl -X GET http://localhost:8080/v1/profile/my \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

---

## ERROR CASES - Invalid Username Format

### 11. Username Too Short (2 chars)
```bash
curl -X GET http://localhost:8080/v1/profile/ab
```

Expected: 400 Bad Request - INVALID_USERNAME

### 12. Username with Invalid Character (@)
```bash
curl -X GET http://localhost:8080/v1/profile/user@name
```

Expected: 400 Bad Request - INVALID_USERNAME

### 13. Username with Invalid Character (#)
```bash
curl -X GET http://localhost:8080/v1/profile/user%23123
```

Expected: 400 Bad Request - INVALID_USERNAME

### 14. Username with Space
```bash
curl -X GET "http://localhost:8080/v1/profile/user%20name"
```

Expected: 400 Bad Request - INVALID_USERNAME

---

## ERROR CASES - User Not Found

### 15. Non-existent Username (valid format)
```bash
curl -X GET http://localhost:8080/v1/profile/nonexistent_user_xyz123
```

Expected: 404 Not Found - USER_NOT_FOUND

---

## ERROR CASES - Authentication

### 16. Get Own Profile - No Token
```bash
curl -X GET http://localhost:8080/v1/profile/my
```

Expected: 401 Unauthorized - UNAUTHORIZED

### 17. Get Own Profile - Invalid Token
```bash
curl -X GET http://localhost:8080/v1/profile/my \
  -H "Authorization: Bearer invalid.token.here"
```

Expected: 401 Unauthorized - UNAUTHORIZED

---

## ERROR CASES - Update Validation

### 18. Update - No Valid Fields (empty)
```bash
curl -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{}'
```

Expected: 400 Bad Request - NO_VALID_FIELDS

### 19. Update - All Blank Fields
```bash
curl -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{"name": "", "bio": "", "avatarUrl": ""}'
```

Expected: 400 Bad Request - NO_VALID_FIELDS

### 20. Update - All Null Fields
```bash
curl -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{"name": null, "bio": null, "avatarUrl": null}'
```

Expected: 400 Bad Request - NO_VALID_FIELDS

### 21. Update - Name Too Long (>100 chars)
```bash
curl -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{"name": "This is a very long name that exceeds the maximum limit of 100 characters because we want to test the validation properly here"}'
```

Expected: 400 Bad Request - VALIDATION_ERROR (name field)

### 22. Update - Bio Too Long (>160 chars)
```bash
curl -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{"bio": "This is a very long bio that exceeds the maximum limit of 160 characters which is the typical social media bio length and we want to test that validation works correctly here"}'
```

Expected: 400 Bad Request - VALIDATION_ERROR (bio field)

### 23. Update - No Token
```bash
curl -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"name": "New Name"}'
```

Expected: 401 Unauthorized - UNAUTHORIZED

### 24. Update - Invalid Token
```bash
curl -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer invalid.token.here" \
  -d '{"name": "New Name"}'
```

Expected: 401 Unauthorized - UNAUTHORIZED

---

## LOGIN COMMANDS (to get JWT tokens)

### Get Token - test_user_one
```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail": "test_user_one", "password": "TestPass@123"}'
```

Save JWT token from response

### Get Token - alice.johnson
```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail": "alice.johnson", "password": "AlicePass@456"}'
```

Save JWT token from response

### Get Token - bob.smith
```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail": "bob.smith", "password": "BobPass@789"}'
```

Save JWT token from response

---

## Profile Update Test Scenarios

### Test User: test_user_one

**Step 1: Get initial profile**
```bash
curl -X GET http://localhost:8080/v1/profile/test_user_one
```

**Step 2: Login and get token**
```bash
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail": "test_user_one", "password": "TestPass@123"}'
```

**Step 3: Get own profile (with token)**
```bash
curl -X GET http://localhost:8080/v1/profile/my \
  -H "Authorization: Bearer <TOKEN_FROM_STEP_2>"
```

**Step 4: Update name**
```bash
curl -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN_FROM_STEP_2>" \
  -d '{"name": "Updated Test User"}'
```

**Step 5: Update bio**
```bash
curl -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN_FROM_STEP_2>" \
  -d '{"bio": "Software engineer & tester"}'
```

**Step 6: Update avatar**
```bash
curl -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN_FROM_STEP_2>" \
  -d '{"avatarUrl": "https://api.example.com/avatars/test_user_one.jpg"}'
```

**Step 7: Get final profile (verify all changes)**
```bash
curl -X GET http://localhost:8080/v1/profile/my \
  -H "Authorization: Bearer <TOKEN_FROM_STEP_2>"
```

---

## Quick Copy-Paste: Test Flow for One User

```bash
# 1. Login
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail": "test_user_one", "password": "TestPass@123"}')

# Extract token (if using jq)
TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.token')

# 2. Get public profile
curl -s http://localhost:8080/v1/profile/test_user_one | jq .

# 3. Get own profile
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/v1/profile/my | jq .

# 4. Update profile
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name": "New Name", "bio": "New bio"}' | jq .

# 5. Verify update
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/v1/profile/my | jq .
```

---

## Error Response Formats

### 400 Bad Request - Validation Error
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid request parameters",
  "details": {
    "fieldName": "Field validation message"
  }
}
```

### 400 Bad Request - Business Logic Error
```json
{
  "error": "INVALID_USERNAME",
  "message": "Username format is invalid"
}
```

### 400 Bad Request - No Valid Fields
```json
{
  "error": "NO_VALID_FIELDS",
  "message": "No updatable fields provided"
}
```

### 401 Unauthorized
```json
{
  "error": "UNAUTHORIZED",
  "message": "Authentication required"
}
```

### 404 Not Found
```json
{
  "error": "USER_NOT_FOUND",
  "message": "Profile does not exist"
}
```

---

## Testing Tips

1. **Always include `-H "Content-Type: application/json"`** for PATCH requests
2. **Use `-H "Authorization: Bearer <TOKEN>"`** for authenticated endpoints
3. **Use `| jq .`** at the end for pretty formatting
4. **Use `-s`** flag in curl to suppress progress
5. **Save JWT tokens** from login responses
6. **Test public endpoints first** (no auth required)
7. **Test authenticated endpoints** after getting tokens
8. **Number of characters matters** for name (max 100) and bio (max 160)

---


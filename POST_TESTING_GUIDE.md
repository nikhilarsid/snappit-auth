# POST Module - Quick Testing Guide

## Overview
Complete guide to testing the Post Module REST API. This guide includes quick-start instructions, endpoint descriptions, test commands, and troubleshooting tips.

**Module**: Post Module  
**Base URL**: `http://localhost:8080`  
**Total Endpoints**: 6  
**Authentication**: JWT (HttpOnly cookies)  

---

## Quick Start

### 1. Start the Server
```bash
# Terminal 1: Start Spring Boot application
cd /Users/kshitijsingh/MyProjects/snappit-auth
./gradlew bootRun
# Wait for: "Started AuthServiceApplication in X seconds"
```

### 2. Run Automated Tests
```bash
# Terminal 2: Run full test suite
cd /Users/kshitijsingh/MyProjects/snappit-auth
chmod +x test_post_flows.sh
./test_post_flows.sh

# Expected output: "✓ All tests passed!" with 42/42 passing
```

### 3. Run Manual Tests
```bash
# Terminal 2: Manual curl testing

# Login first
COOKIES="cookies.txt"
curl -c $COOKIES -X POST "http://localhost:8080/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"kshitij@gmail.com","password":"Dettcmpw123?"}'

# Create a post
curl -b $COOKIES -X POST "http://localhost:8080/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{"mediaUrl":"https://example.com/image.jpg","caption":"Test post"}'

# Clean up
rm $COOKIES
```

---

## Test Users Available

| Username | Email | Password | Status |
|----------|-------|----------|--------|
| if.kshitij | kshitij@gmail.com | Dettcmpw123? | ✅ Ready |
| hhoehunterr | kshitij2@gmail.com | Dettcmpw123? | ✅ Ready |
| (Relationship) | hhoehunterr follows if.kshitij | (approved) | ✅ Set up |

---

## Endpoint Reference

### 1. GET /api/v1/posts/{postId}
**Get a single post by ID**

```bash
# Request
curl -b cookies.txt http://localhost:8080/api/v1/posts/{postId}

# Success Response (200 OK)
{
  "id": "507f1f77bcf86cd799439011",
  "authorUsername": "if.kshitij",
  "mediaUrl": "https://example.com/image.jpg",
  "caption": "Beautiful sunset",
  "likeCount": 5,
  "commentCount": 2,
  "createdAt": "2026-02-14T10:30:00Z",
  "canDelete": true
}

# Error: Not authenticated (403)
# Error: Not approved follower (403)
# Error: Post not found (404)
```

### 2. GET /api/v1/posts/user/{username}
**Get paginated posts from a user**

```bash
# Request
curl -b cookies.txt "http://localhost:8080/api/v1/posts/user/if.kshitij?limit=20&cursor=nextpage"

# Success Response (200 OK)
{
  "data": [
    {
      "id": "507f1f77bcf86cd799439011",
      "authorUsername": "if.kshitij",
      "mediaUrl": "https://example.com/image1.jpg",
      "caption": "First post",
      "likeCount": 10,
      "commentCount": 2,
      "createdAt": "2026-02-15T10:30:00Z",
      "canDelete": false
    }
  ],
  "nextCursor": "507f1f77bcf86cd799439011"
}

# Query Parameters
# - limit: 1-50 (default 20)
# - cursor: opaque string for pagination
```

### 3. POST /api/v1/posts
**Create a new post**

```bash
# Request
curl -b cookies.txt -X POST "http://localhost:8080/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "mediaUrl": "https://example.com/sunset.jpg",
    "caption": "Beautiful sunset #nature"
  }'

# Success Response (201 Created)
{
  "id": "507f1f77bcf86cd799439013",
  "authorUsername": "if.kshitij",
  "mediaUrl": "https://example.com/sunset.jpg",
  "caption": "Beautiful sunset #nature",
  "likeCount": 0,
  "commentCount": 0,
  "createdAt": "2026-02-15T12:45:30Z",
  "canDelete": true
}

# Required Fields
# - mediaUrl (string, required, max 2048 chars)

# Optional Fields
# - caption (string, optional, max 500 chars)
```

### 4. DELETE /api/v1/posts/{postId}
**Delete a post (author only)**

```bash
# Request
curl -b cookies.txt -X DELETE "http://localhost:8080/api/v1/posts/{postId}"

# Success Response (200 OK)
{
  "message": "POST_DELETED"
}

# Error cases:
# - 404: Post not found
# - 403: Not the post author
```

### 5. POST /api/v1/posts/{postId}/like
**Like a post**

```bash
# Request
curl -b cookies.txt -X POST "http://localhost:8080/api/v1/posts/{postId}/like"

# Success Response (200 OK)
{
  "message": "POST_LIKED"
}

# Error cases:
# - 404: Post not found
# - 409: Already liked
```

### 6. DELETE /api/v1/posts/{postId}/like
**Unlike a post**

```bash
# Request
curl -b cookies.txt -X DELETE "http://localhost:8080/api/v1/posts/{postId}/like"

# Success Response (200 OK)
{
  "message": "POST_UNLIKED"
}

# Error cases:
# - 404: Post not found
# - 409: Not liked
```

---

## Common Testing Scenarios

### Scenario 1: Create and Like a Post
```bash
#!/bin/bash

COOKIES="cookies.txt"

# Login
curl -s -c $COOKIES -X POST "http://localhost:8080/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"kshitij@gmail.com","password":"Dettcmpw123?"}' > /dev/null

# Create post
RESPONSE=$(curl -s -b $COOKIES -X POST "http://localhost:8080/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{"mediaUrl":"https://example.com/photo.jpg","caption":"Test"}')
POST_ID=$(echo $RESPONSE | grep -o '"id":"[^"]*' | cut -d'"' -f4)

# View post
echo "Post created: $POST_ID"
curl -s -b $COOKIES "http://localhost:8080/api/v1/posts/$POST_ID" | jq .

# Like post
curl -s -b $COOKIES -X POST "http://localhost:8080/api/v1/posts/$POST_ID/like"
echo "Post liked"

# Unlike post
curl -s -b $COOKIES -X DELETE "http://localhost:8080/api/v1/posts/$POST_ID/like"
echo "Post unliked"

# Delete post
curl -s -b $COOKIES -X DELETE "http://localhost:8080/api/v1/posts/$POST_ID"
echo "Post deleted"

rm $COOKIES
```

### Scenario 2: Access Control Testing
```bash
#!/bin/bash

# Setup: User 1 creates post, User 2 tries to access

# User 1 login
curl -s -c cookies1.txt -X POST "http://localhost:8080/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"kshitij@gmail.com","password":"Dettcmpw123?"}' > /dev/null

# User 1 creates post
RESPONSE=$(curl -s -b cookies1.txt -X POST "http://localhost:8080/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{"mediaUrl":"https://example.com/private.jpg","caption":"Private"}')
POST_ID=$(echo $RESPONSE | grep -o '"id":"[^"]*' | cut -d'"' -f4)

# User 2 login
curl -s -c cookies2.txt -X POST "http://localhost:8080/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"kshitij2@gmail.com","password":"Dettcmpw123?"}' > /dev/null

# User 2 tries to access (should succeed - they're approved followers)
echo "User 2 accessing User 1's post:"
curl -s -b cookies2.txt "http://localhost:8080/api/v1/posts/$POST_ID" | jq .

# Cleanup
rm cookies1.txt cookies2.txt
```

### Scenario 3: Pagination Testing
```bash
#!/bin/bash

COOKIES="cookies.txt"

# Login
curl -s -c $COOKIES -X POST "http://localhost:8080/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"kshitij@gmail.com","password":"Dettcmpw123?"}' > /dev/null

# Get first page (limit=5)
echo "=== First Page ==="
RESPONSE=$(curl -s -b $COOKIES "http://localhost:8080/api/v1/posts/user/if.kshitij?limit=5")
echo $RESPONSE | jq '.data | length, "posts"'
CURSOR=$(echo $RESPONSE | jq -r '.nextCursor')
echo "Next cursor: $CURSOR"

# Get second page if cursor exists
if [ "$CURSOR" != "null" ] && [ ! -z "$CURSOR" ]; then
  echo "=== Second Page ==="
  RESPONSE=$(curl -s -b $COOKIES "http://localhost:8080/api/v1/posts/user/if.kshitij?limit=5&cursor=$CURSOR")
  echo $RESPONSE | jq '.data | length, "posts"'
fi

rm $COOKIES
```

---

## Troubleshooting

### Issue: 403 Forbidden on all requests
**Symptoms**: All requests return 403 FORBIDDEN

**Cause**: Usually missing authentication cookie

**Solution**:
```bash
# Ensure you logged in first
curl -c cookies.txt -X POST "http://localhost:8080/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"kshitij@gmail.com","password":"Dettcmpw123?"}'

# Then use cookie in requests
curl -b cookies.txt "http://localhost:8080/api/v1/posts/user/if.kshitij"
```

### Issue: 404 User Not Found
**Symptoms**: GET /api/v1/posts/user/{username} returns 404

**Cause**: Username doesn't exist or wrong spelling

**Solution**:
- Check username spelling (case-insensitive but must match exactly)
- Verify username format: 3-30 chars, alphanumeric + _-. only
- Use test users: if.kshitij, hhoehunterr

### Issue: 400 Bad Request on POST
**Symptoms**: POST /api/v1/posts returns 400

**Cause**: Missing required fields or invalid format

**Solution**:
```bash
# Required: mediaUrl, non-empty
# Optional: caption (max 500 chars)

# Valid request:
{
  "mediaUrl": "https://example.com/image.jpg",
  "caption": "Optional caption"
}

# Invalid: missing mediaUrl
{
  "caption": "Unsupported - no media"
}

# Invalid: empty mediaUrl
{
  "mediaUrl": "",
  "caption": "Empty URL"
}
```

### Issue: 409 Conflict on Like
**Symptoms**: POST .../like returns 409

**Cause**: Already liked this post

**Solution**:
- First unlike the post: DELETE .../like
- Then like again: POST .../like

### Issue: 401 Unauthorized
**Symptoms**: POST /api/v1/posts returns 401

**Cause**: User not authenticated (very rare with cookies)

**Solution**:
```bash
# Re-login
curl -c cookies.txt -X POST "http://localhost:8080/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"kshitij@gmail.com","password":"Dettcmpw123?"}'
```

### Issue: Posts not appearing for user
**Symptoms**: GET /api/v1/posts/user/{username} returns empty array

**Cause**: Common for new users; may be waiting for background jobs

**Solution**:
1. Check user actually exists (not 404)
2. Check requester is approved follower of user
3. Wait a moment for background job to process events
4. Create a test post and verify it appears

---

## Advanced Testing

### Performance Testing
```bash
#!/bin/bash

COOKIES="cookies.txt"
curl -s -c $COOKIES -X POST "http://localhost:8080/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"kshitij@gmail.com","password":"Dettcmpw123?"}' > /dev/null

# Measure post creation time
echo "Testing post creation performance..."
for i in {1..10}; do
  curl -s -o /dev/null -w "Request $i: %{time_total}s\n" \
    -b $COOKIES -X POST "http://localhost:8080/api/v1/posts" \
    -H "Content-Type: application/json" \
    -d '{"mediaUrl":"https://example.com/perf'$i'.jpg","caption":"Performance test"}'
done

rm $COOKIES
```

### Load Testing (requires ApacheBench)
```bash
#!/bin/bash

COOKIES="cookies.txt"
curl -s -c $COOKIES -X POST "http://localhost:8080/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"kshitij@gmail.com","password":"Dettcmpw123?"}' > /dev/null

# 100 concurrent requests
ab -n 100 -c 10 -H "Cookie: jwtToken=$(cat $COOKIES | grep jwtToken | awk '{print $7}')" \
  http://localhost:8080/api/v1/posts/user/if.kshitij

rm $COOKIES
```

---

## Documentation Files

| File | Purpose |
|------|---------|
| POST_MODULE_FLOWS.md | Complete endpoint documentation and flows |
| POST_CURL_COMMANDS.md | cURL command reference for all endpoints |
| POST_TEST_RESULTS.md | Full test results and findings |
| POST_TESTING_GUIDE.md | This file - quick reference guide |
| test_post_flows.sh | Automated test suite (42 tests) |

---

## Running the Full Test Suite

### One-Command Test
```bash
cd /Users/kshitijsingh/MyProjects/snappit-auth
chmod +x test_post_flows.sh && ./test_post_flows.sh
```

### Expected Output
```
========================================
SETUP: Authentication
========================================
✓ User 1 logged in
✓ User 2 logged in

... (many tests) ...

========================================
TEST SUMMARY
========================================
Total Tests: 42
Passed: 42
Failed: 0
Pass Rate: 100%

Cleaning up test files...
✓ All tests passed!
```

### Test Coverage
- **Happy Path**: 14 tests covering normal operations
- **Error Scenarios**: 28 tests covering edge cases
- **Endpoints**: All 6 endpoints fully tested
- **Access Control**: Authorization rules verified
- **Validation**: Input validation tested

---

## Key Points

✅ **Always login first** before making requests  
✅ **Use -c flag** to save cookies: `curl -c cookies.txt ...`  
✅ **Use -b flag** to use cookies: `curl -b cookies.txt ...`  
✅ **Check postId format** - should be 24 hex characters  
✅ **Username validation** - 3-30 chars, alphanumeric + _-. only  
✅ **Caption optional** - but mediaUrl required  
✅ **Delete permanently** - hard delete, no recovery  

---

## Resources

- **API Contract**: `/src/main/java/com/snapitt/backend_service/modules/post/API_CONTRACT.md`
- **Module Design**: `/src/main/java/com/snapitt/backend_service/modules/post/POST_MODULE_DESIGN.md`
- **Controller**: `/src/main/java/com/snapitt/backend_service/modules/post/controller/PostController.java`
- **Service**: `/src/main/java/com/snapitt/backend_service/modules/post/service/PostService.java`

---

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review test results in POST_TEST_RESULTS.md
3. Check server logs: `./gradlew bootRun 2>&1 | grep -i error`
4. Verify test users are available: `curl http://localhost:8080/v1/profile/if.kshitij`


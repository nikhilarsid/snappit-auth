# POST Module - cURL Command Reference

## Test Setup

### Test Users
```bash
# User 1 (Post Creator)
USER1_USERNAME="if.kshitij"
USER1_EMAIL="kshitij@gmail.com"
USER1_PASSWORD="Dettcmpw123?"

# User 2 (Approved Follower)
USER2_USERNAME="hhoehunterr"
USER2_EMAIL="kshitij2@gmail.com"
USER2_PASSWORD="Dettcmpw123?"

# Base URL
BASE_URL="http://localhost:8080"
```

### Cookie Setup
```bash
# Login User 1 (saves cookie to cookies1.txt)
curl -c cookies1.txt -X POST "$BASE_URL/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"'$USER1_EMAIL'","password":"'$USER1_PASSWORD'"}'

# Login User 2 (saves cookie to cookies2.txt)
curl -c cookies2.txt -X POST "$BASE_URL/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"'$USER2_EMAIL'","password":"'$USER2_PASSWORD'"}'
```

---

## ENDPOINT 1: Get Single Post

### 1.1 Happy Path: Get Post as Author
```bash
# User views their own post
curl -b cookies1.txt \
  "$BASE_URL/api/v1/posts/{postId}" \
  -H "Accept: application/json"
```

### 1.2 Happy Path: Get Post as Approved Follower
```bash
# User 2 (approved follower) views User 1's post
curl -b cookies2.txt \
  "$BASE_URL/api/v1/posts/{postId}" \
  -H "Accept: application/json"
```

### 1.3 Error: Post Not Found (404)
```bash
curl -b cookies1.txt \
  "$BASE_URL/api/v1/posts/507f1f77bcf86cd799999999" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

### 1.4 Error: Not Authenticated (403)
```bash
# Access without authentication
curl "$BASE_URL/api/v1/posts/{postId}" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

### 1.5 Error: Not Approved Follower (403)
```bash
# User 3 (not following User 1) tries to view post
# First, create new test user and login
curl -c cookies3.txt -X POST "$BASE_URL/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"testuser@example.com","password":"TestPass123!"}'

# Try to view User 1's post
curl -b cookies3.txt \
  "$BASE_URL/api/v1/posts/{postId}" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

---

## ENDPOINT 2: Get User's Posts (Paginated)

### 2.1 Happy Path: Get Own Posts (First Page)
```bash
# User views their own posts with default limit
curl -b cookies1.txt \
  "$BASE_URL/api/v1/posts/user/if.kshitij" \
  -H "Accept: application/json"
```

### 2.2 Happy Path: Get Own Posts (Custom Limit)
```bash
# User views own posts with limit=5
curl -b cookies1.txt \
  "$BASE_URL/api/v1/posts/user/if.kshitij?limit=5" \
  -H "Accept: application/json"
```

### 2.3 Happy Path: Get Posts as Approved Follower
```bash
# User 2 (approved follower) views User 1's posts
curl -b cookies2.txt \
  "$BASE_URL/api/v1/posts/user/if.kshitij" \
  -H "Accept: application/json"
```

### 2.4 Happy Path: Pagination with Cursor
```bash
# Get first page
RESPONSE=$(curl -s -b cookies1.txt \
  "$BASE_URL/api/v1/posts/user/if.kshitij?limit=2")
NEXT_CURSOR=$(echo $RESPONSE | grep -o '"nextCursor":"[^"]*' | cut -d'"' -f4)

# Get second page using cursor
curl -b cookies1.txt \
  "$BASE_URL/api/v1/posts/user/if.kshitij?limit=2&cursor=$NEXT_CURSOR" \
  -H "Accept: application/json"
```

### 2.5 Error: Invalid Username Format (400)
```bash
# Username with invalid characters
curl -b cookies1.txt \
  "$BASE_URL/api/v1/posts/user/invalid@user!" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

### 2.6 Error: Username Too Short (400)
```bash
# Username less than 3 characters
curl -b cookies1.txt \
  "$BASE_URL/api/v1/posts/user/ab" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

### 2.7 Error: User Not Found (404)
```bash
curl -b cookies1.txt \
  "$BASE_URL/api/v1/posts/user/nonexistentuser12345" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

### 2.8 Error: Not Authenticated (403)
```bash
# Access without authentication
curl "$BASE_URL/api/v1/posts/user/if.kshitij" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

### 2.9 Error: Not Approved Follower (403)
```bash
# User 3 (not following User 1) tries to view posts
curl -b cookies3.txt \
  "$BASE_URL/api/v1/posts/user/if.kshitij" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

### 2.10 Error: Invalid Limit (400)
```bash
# Limit too high
curl -b cookies1.txt \
  "$BASE_URL/api/v1/posts/user/if.kshitij?limit=100" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

### 2.11 Error: Invalid Limit Zero (400)
```bash
# Limit zero
curl -b cookies1.txt \
  "$BASE_URL/api/v1/posts/user/if.kshitij?limit=0" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

---

## ENDPOINT 3: Create Post

### 3.1 Happy Path: Create Post with Caption
```bash
curl -b cookies1.txt -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "mediaUrl": "https://example.com/sunset.jpg",
    "caption": "Beautiful sunset at the beach #nature"
  }' | tee post_response.json
```

### 3.2 Happy Path: Create Post without Caption
```bash
curl -b cookies1.txt -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "mediaUrl": "https://example.com/landscape.jpg"
  }' | tee post_response2.json
```

### 3.3 Happy Path: Extract PostId from Response
```bash
# Create post and extract ID
RESPONSE=$(curl -s -b cookies1.txt -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "mediaUrl": "https://example.com/photo.jpg",
    "caption": "Test post"
  }')

POST_ID=$(echo $RESPONSE | grep -o '"id":"[^"]*' | cut -d'"' -f4)
echo "Created post ID: $POST_ID"
```

### 3.4 Error: Missing mediaUrl (400)
```bash
curl -b cookies1.txt -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "caption": "Post without media"
  }' \
  -w "\nStatus: %{http_code}\n"
```

### 3.5 Error: Empty mediaUrl (400)
```bash
curl -b cookies1.txt -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "mediaUrl": "",
    "caption": "Post with empty media URL"
  }' \
  -w "\nStatus: %{http_code}\n"
```

### 3.6 Error: mediaUrl Exceeds Max Length (400)
```bash
# Create 2049 character URL (exceeds 2048 limit)
LONG_URL=$(printf 'https://example.com/image%.0s' {1..210})
curl -b cookies1.txt -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "mediaUrl": "'$LONG_URL'",
    "caption": "Oversized URL"
  }' \
  -w "\nStatus: %{http_code}\n"
```

### 3.7 Error: Caption Exceeds Max Length (400)
```bash
# Create 501 character caption (exceeds 500 limit)
LONG_CAPTION=$(printf 'a%.0s' {1..501})
curl -b cookies1.txt -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "mediaUrl": "https://example.com/image.jpg",
    "caption": "'$LONG_CAPTION'"
  }' \
  -w "\nStatus: %{http_code}\n"
```

### 3.8 Error: Not Authenticated (401)
```bash
# Create post without authentication
curl -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "mediaUrl": "https://example.com/image.jpg",
    "caption": "Unauthenticated post"
  }' \
  -w "\nStatus: %{http_code}\n"
```

### 3.9 Error: Invalid JSON Format (400)
```bash
curl -b cookies1.txt -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d 'invalid json' \
  -w "\nStatus: %{http_code}\n"
```

---

## ENDPOINT 4: Delete Post

### 4.1 Happy Path: Delete Own Post
```bash
# Delete post as author
curl -b cookies1.txt -X DELETE \
  "$BASE_URL/api/v1/posts/{postId}" \
  -H "Accept: application/json"
```

### 4.2 Error: Post Not Found (404)
```bash
curl -b cookies1.txt -X DELETE \
  "$BASE_URL/api/v1/posts/507f1f77bcf86cd799999999" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

### 4.3 Error: Not Authenticated (401)
```bash
# Delete without authentication
curl -X DELETE \
  "$BASE_URL/api/v1/posts/{postId}" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

### 4.4 Error: Not Post Author (403)
```bash
# User 2 tries to delete User 1's post
curl -b cookies2.txt -X DELETE \
  "$BASE_URL/api/v1/posts/{postId}" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

---

## ENDPOINT 5: Like Post

### 5.1 Happy Path: Like Post
```bash
curl -b cookies1.txt -X POST \
  "$BASE_URL/api/v1/posts/{postId}/like" \
  -H "Accept: application/json"
```

### 5.2 Happy Path: Verify Like Count Incremented
```bash
# Get post to see likeCount
curl -b cookies1.txt \
  "$BASE_URL/api/v1/posts/{postId}" \
  -H "Accept: application/json"
```

### 5.3 Happy Path: Like as Different User
```bash
# User 2 likes User 1's post
curl -b cookies2.txt -X POST \
  "$BASE_URL/api/v1/posts/{postId}/like" \
  -H "Accept: application/json"
```

### 5.4 Error: Post Not Found (404)
```bash
curl -b cookies1.txt -X POST \
  "$BASE_URL/api/v1/posts/507f1f77bcf86cd799999999/like" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

### 5.5 Error: Not Authenticated (401)
```bash
# Like without authentication
curl -X POST \
  "$BASE_URL/api/v1/posts/{postId}/like" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

### 5.6 Error: Already Liked (409)
```bash
# User tries to like same post twice
curl -b cookies1.txt -X POST \
  "$BASE_URL/api/v1/posts/{postId}/like" \
  -H "Accept: application/json"

# Second like attempt
curl -b cookies1.txt -X POST \
  "$BASE_URL/api/v1/posts/{postId}/like" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

---

## ENDPOINT 6: Unlike Post

### 6.1 Happy Path: Unlike Post
```bash
curl -b cookies1.txt -X DELETE \
  "$BASE_URL/api/v1/posts/{postId}/like" \
  -H "Accept: application/json"
```

### 6.2 Happy Path: Verify Like Count Decremented
```bash
# Get post to verify likeCount decremented
curl -b cookies1.txt \
  "$BASE_URL/api/v1/posts/{postId}" \
  -H "Accept: application/json"
```

### 6.3 Error: Post Not Found (404)
```bash
curl -b cookies1.txt -X DELETE \
  "$BASE_URL/api/v1/posts/507f1f77bcf86cd799999999/like" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

### 6.4 Error: Not Authenticated (401)
```bash
# Unlike without authentication
curl -X DELETE \
  "$BASE_URL/api/v1/posts/{postId}/like" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

### 6.5 Error: Not Liked (409)
```bash
# User tries to unlike a post they didn't like
curl -b cookies1.txt -X DELETE \
  "$BASE_URL/api/v1/posts/{postId}/like" \
  -H "Accept: application/json" \
  -w "\nStatus: %{http_code}\n"
```

---

## Complete End-to-End Workflow

### Workflow 1: Create, View, Like, Unlike Cycle
```bash
# 1. User 1 creates post
CREATE_RESPONSE=$(curl -s -b cookies1.txt -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "mediaUrl": "https://example.com/e2e_test.jpg",
    "caption": "E2E test post"
  }')
POST_ID=$(echo $CREATE_RESPONSE | grep -o '"id":"[^"]*' | cut -d'"' -f4)
echo "Created post: $POST_ID"

# 2. User 1 views their post
echo "Viewing post as author..."
curl -s -b cookies1.txt "$BASE_URL/api/v1/posts/$POST_ID" | jq .

# 3. User 1 likes post
echo "User 1 likes post..."
curl -s -b cookies1.txt -X POST \
  "$BASE_URL/api/v1/posts/$POST_ID/like"

# 4. User 2 (approved follower) likes post
echo "User 2 likes post..."
curl -s -b cookies2.txt -X POST \
  "$BASE_URL/api/v1/posts/$POST_ID/like"

# 5. View post to see likeCount = 2
echo "Viewing post with 2 likes..."
curl -s -b cookies1.txt "$BASE_URL/api/v1/posts/$POST_ID" | jq '.likeCount'

# 6. User 1 unlikes
echo "User 1 unlikes..."
curl -s -b cookies1.txt -X DELETE \
  "$BASE_URL/api/v1/posts/$POST_ID/like"

# 7. User 2 unlikes
echo "User 2 unlikes..."
curl -s -b cookies2.txt -X DELETE \
  "$BASE_URL/api/v1/posts/$POST_ID/like"

# 8. View post to confirm likeCount = 0
echo "Verifying likeCount back to 0..."
curl -s -b cookies1.txt "$BASE_URL/api/v1/posts/$POST_ID" | jq '.likeCount'

# 9. User 1 deletes post
echo "User 1 deletes post..."
curl -s -b cookies1.txt -X DELETE \
  "$BASE_URL/api/v1/posts/$POST_ID"
```

### Workflow 2: Access Control Verification
```bash
# 1. User 1 creates post
POST_RESPONSE=$(curl -s -b cookies1.txt -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "mediaUrl": "https://example.com/access_test.jpg",
    "caption": "Access control test"
  }')
POST_ID=$(echo $POST_RESPONSE | grep -o '"id":"[^"]*' | cut -d'"' -f4)

# 2. User 2 (approved follower) can view
echo "User 2 accessing post (should succeed)..."
curl -s -b cookies2.txt "$BASE_URL/api/v1/posts/$POST_ID" | jq '.id'

# 3. User 3 (not following) gets 403
echo "User 3 accessing post (should fail with 403)..."
curl -s -b cookies3.txt "$BASE_URL/api/v1/posts/$POST_ID" -w "\nStatus: %{http_code}\n"

# 4. No auth gets 403
echo "No auth accessing post (should fail with 403)..."
curl -s "$BASE_URL/api/v1/posts/$POST_ID" -w "\nStatus: %{http_code}\n"
```

---

## Common Response Headers

All successful responses include:
- `Content-Type: application/json`
- `Set-Cookie: jwtToken=...; Secure; HttpOnly;` (on login)

---

## Notes

- Replace `{postId}` with actual post ID from response
- Cookie files (`cookies1.txt`, `cookies2.txt`, etc.) must be created by login requests first
- `jq` command used for pretty-printing JSON (optional, remove if not installed)
- All timestamps are ISO 8601 format with UTC timezone
- Background jobs process events asynchronously (like counts may take a moment to update)


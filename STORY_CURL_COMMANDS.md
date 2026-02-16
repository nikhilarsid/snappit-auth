# STORY Module - cURL Command Reference

## Preparation

### Set up credentials and base URL:
```bash
BASE_URL="http://localhost:8080"
COOKIES="cookies.txt"

# User 1: if.kshitij (password: Dettcmpw123?)
USER1_USERNAME="if.kshitij"
USER1_EMAIL="kshitij@gmail.com"
USER1_PASSWORD="Dettcmpw123?"

# User 2: hhoehunterr (password: Dettcmpw123?)
# (Already an approved follower of User 1 from Follow module tests)
USER2_USERNAME="hhoehunterr"
USER2_EMAIL="kshitij2@gmail.com"
USER2_PASSWORD="Dettcmpw123?"
```

### Login for User 1:
```bash
curl -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"usernameOrEmail\": \"$USER1_EMAIL\", \"password\": \"$USER1_PASSWORD\"}" \
  -c "$COOKIES" \
  -w "\nStatus: %{http_code}\n"
```

### Login for User 2:
```bash
curl -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"usernameOrEmail\": \"$USER2_EMAIL\", \"password\": \"$USER2_PASSWORD\"}" \
  -c "$COOKIES" \
  -w "\nStatus: %{http_code}\n"
```

---

## ENDPOINT 1: Create Story (Happy Path)

### Create Story - 1 day expiration
```bash
TOMORROW=$(date -u -v+1d +"%Y-%m-%dT%H:%M:%SZ")

curl -X POST "$BASE_URL/api/v1/stories" \
  -H "Content-Type: application/json" \
  -b "$COOKIES" \
  -d "{\"mediaUrl\": \"https://example.com/story1.jpg\", \"expiresAt\": \"$TOMORROW\"}" \
  -w "\nStatus: %{http_code}\n"

# Store the returned story ID for later tests:
# STORY_ID="507f1f77bcf86cd799439011"
```

### Create Story - 2 days expiration
```bash
TWO_DAYS=$(date -u -v+2d +"%Y-%m-%dT%H:%M:%SZ")

curl -X POST "$BASE_URL/api/v1/stories" \
  -H "Content-Type: application/json" \
  -b "$COOKIES" \
  -d "{\"mediaUrl\": \"https://cdn.example.com/story/media.jpg\", \"expiresAt\": \"$TWO_DAYS\"}" \
  -w "\nStatus: %{http_code}\n"
```

---

## ENDPOINT 1: Create Story (Error Cases)

### Create Story - Missing mediaUrl
```bash
TOMORROW=$(date -u -v+1d +"%Y-%m-%dT%H:%M:%SZ")

curl -X POST "$BASE_URL/api/v1/stories" \
  -H "Content-Type: application/json" \
  -b "$COOKIES" \
  -d "{\"expiresAt\": \"$TOMORROW\"}" \
  -w "\nStatus: %{http_code}\n"

# Expected: 400 VALIDATION_ERROR "Media URL is required"
```

### Create Story - Blank mediaUrl
```bash
TOMORROW=$(date -u -v+1d +"%Y-%m-%dT%H:%M:%SZ")

curl -X POST "$BASE_URL/api/v1/stories" \
  -H "Content-Type: application/json" \
  -b "$COOKIES" \
  -d "{\"mediaUrl\": \"\", \"expiresAt\": \"$TOMORROW\"}" \
  -w "\nStatus: %{http_code}\n"

# Expected: 400 VALIDATION_ERROR
```

### Create Story - Missing expiresAt
```bash
curl -X POST "$BASE_URL/api/v1/stories" \
  -H "Content-Type: application/json" \
  -b "$COOKIES" \
  -d "{\"mediaUrl\": \"https://example.com/story.jpg\"}" \
  -w "\nStatus: %{http_code}\n"

# Expected: 400 VALIDATION_ERROR "Expiration time cannot be null"
```

### Create Story - expiresAt in the past
```bash
YESTERDAY=$(date -u -v-1d +"%Y-%m-%dT%H:%M:%SZ")

curl -X POST "$BASE_URL/api/v1/stories" \
  -H "Content-Type: application/json" \
  -b "$COOKIES" \
  -d "{\"mediaUrl\": \"https://example.com/story.jpg\", \"expiresAt\": \"$YESTERDAY\"}" \
  -w "\nStatus: %{http_code}\n"

# Expected: 400 VALIDATION_ERROR "Expiration time must be in the future"
```

### Create Story - Not authenticated
```bash
TOMORROW=$(date -u -v+1d +"%Y-%m-%dT%H:%M:%SZ")

curl -X POST "$BASE_URL/api/v1/stories" \
  -H "Content-Type: application/json" \
  -d "{\"mediaUrl\": \"https://example.com/story.jpg\", \"expiresAt\": \"$TOMORROW\"}" \
  -w "\nStatus: %{http_code}\n"

# Expected: 403 FORBIDDEN (Spring Security unauthenticated access)
```

---

## ENDPOINT 2: Get Single Story (Happy Path)

### Get Story - As author
```bash
# First, create a story and store the ID in STORY_ID
TOMORROW=$(date -u -v+1d +"%Y-%m-%dT%H:%M:%SZ")

STORY_JSON=$(curl -s -X POST "$BASE_URL/api/v1/stories" \
  -H "Content-Type: application/json" \
  -b "$COOKIES" \
  -d "{\"mediaUrl\": \"https://example.com/story_author.jpg\", \"expiresAt\": \"$TOMORROW\"}")

STORY_ID=$(echo "$STORY_JSON" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

# View own story
curl -X GET "$BASE_URL/api/v1/stories/$STORY_ID" \
  -b "$COOKIES" \
  -w "\nStatus: %{http_code}\n"

# Expected: 200 OK with StoryResponse (canDelete: true)
```

### Get Story - As approved follower
```bash
# Use STORY_ID from above

# Check if User 2 is logged in (if not, login first)
curl -X GET "$BASE_URL/api/v1/stories/$STORY_ID" \
  -b "$COOKIES" \
  -w "\nStatus: %{http_code}\n"

# Expected: 200 OK with StoryResponse (canDelete: false)
# User 2 is approved follower of User 1 from Follow module tests
```

---

## ENDPOINT 2: Get Single Story (Error Cases)

### Get Story - Story not found
```bash
INVALID_ID="507f1f77bcf86cd799438888"

curl -X GET "$BASE_URL/api/v1/stories/$INVALID_ID" \
  -b "$COOKIES" \
  -w "\nStatus: %{http_code}\n"

# Expected: 404 STORY_NOT_FOUND
```

### Get Story - Access denied (not authenticated, not approved)
```bash
# Create story as User 1
TOMORROW=$(date -u -v+1d +"%Y-%m-%dT%H:%M:%SZ")

STORY_JSON=$(curl -s -X POST "$BASE_URL/api/v1/stories" \
  -H "Content-Type: application/json" \
  -b "$COOKIES" \
  -d "{\"mediaUrl\": \"https://example.com/story_private.jpg\", \"expiresAt\": \"$TOMORROW\"}")

STORY_ID=$(echo "$STORY_JSON" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

# Try to view without authentication
curl -X GET "$BASE_URL/api/v1/stories/$STORY_ID" \
  -w "\nStatus: %{http_code}\n"

# Expected: 403 FORBIDDEN
```

---

## ENDPOINT 3: Get User's Stories (Happy Path)

### Get user's stories - First page (as author)
```bash
curl -X GET "$BASE_URL/api/v1/stories/user/if.kshitij?limit=20" \
  -b "$COOKIES" \
  -w "\nStatus: %{http_code}\n"

# Expected: 200 OK with paginated stories array and optional nextCursor
```

### Get user's stories - As approved follower
```bash
# User 2 is approved follower of User 1

curl -X GET "$BASE_URL/api/v1/stories/user/if.kshitij?limit=20" \
  -b "$COOKIES" \
  -w "\nStatus: %{http_code}\n"

# Expected: 200 OK with paginated stories
```

### Get user's stories - With custom limit
```bash
curl -X GET "$BASE_URL/api/v1/stories/user/if.kshitij?limit=5" \
  -b "$COOKIES" \
  -w "\nStatus: %{http_code}\n"

# Expected: 200 OK with up to 5 stories
```

### Get user's stories - With cursor (pagination)
```bash
# First get a page to get the nextCursor
RESPONSE=$(curl -s -X GET "$BASE_URL/api/v1/stories/user/if.kshitij?limit=2" -b "$COOKIES")

NEXT_CURSOR=$(echo "$RESPONSE" | grep -o '"nextCursor":"[^"]*"' | cut -d'"' -f4)

# If nextCursor exists, use it for the next page
if [ ! -z "$NEXT_CURSOR" ]; then
  curl -X GET "$BASE_URL/api/v1/stories/user/if.kshitij?limit=2&cursor=$NEXT_CURSOR" \
    -b "$COOKIES" \
    -w "\nStatus: %{http_code}\n"
fi

# Expected: 200 OK with next batch of stories
```

---

## ENDPOINT 3: Get User's Stories (Error Cases)

### Get user's stories - Invalid username format
```bash
curl -X GET "$BASE_URL/api/v1/stories/user/ab?limit=20" \
  -b "$COOKIES" \
  -w "\nStatus: %{http_code}\n"

# Expected: 400 VALIDATION_ERROR (username must be 3-30 chars)
```

### Get user's stories - User not found
```bash
curl -X GET "$BASE_URL/api/v1/stories/user/nonexistent_user_12345?limit=20" \
  -b "$COOKIES" \
  -w "\nStatus: %{http_code}\n"

# Expected: 404 USER_NOT_FOUND
```

### Get user's stories - Access denied (not approved follower)
```bash
# Try to access User 1's stories without being approved follower
# (Assuming you're logged in as User 2 who IS approved)
# If not approved, would get:

curl -X GET "$BASE_URL/api/v1/stories/user/if.kshitij?limit=20" \
  -b "$COOKIES" \
  -w "\nStatus: %{http_code}\n"

# Expected: 403 FORBIDDEN if not approved follower
# Expected: 200 OK if approved follower
```

### Get user's stories - Invalid limit
```bash
curl -X GET "$BASE_URL/api/v1/stories/user/if.kshitij?limit=100" \
  -b "$COOKIES" \
  -w "\nStatus: %{http_code}\n"

# Expected: 400 VALIDATION_ERROR (limit must be 1-50)
```

### Get user's stories - Not authenticated (no cookie)
```bash
curl -X GET "$BASE_URL/api/v1/stories/user/if.kshitij?limit=20" \
  -w "\nStatus: %{http_code}\n"

# Expected: 403 FORBIDDEN
```

---

## ENDPOINT 4: Delete Story (Happy Path)

### Delete story - As author
```bash
# Create a story first
TOMORROW=$(date -u -v+1d +"%Y-%m-%dT%H:%M:%SZ")

STORY_JSON=$(curl -s -X POST "$BASE_URL/api/v1/stories" \
  -H "Content-Type: application/json" \
  -b "$COOKIES" \
  -d "{\"mediaUrl\": \"https://example.com/story_to_delete.jpg\", \"expiresAt\": \"$TOMORROW\"}")

STORY_ID=$(echo "$STORY_JSON" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

# Delete own story
curl -X DELETE "$BASE_URL/api/v1/stories/$STORY_ID" \
  -b "$COOKIES" \
  -w "\nStatus: %{http_code}\n"

# Expected: 200 OK { "message": "STORY_DELETED" }
```

---

## ENDPOINT 4: Delete Story (Error Cases)

### Delete story - Story not found
```bash
INVALID_ID="507f1f77bcf86cd799438888"

curl -X DELETE "$BASE_URL/api/v1/stories/$INVALID_ID" \
  -b "$COOKIES" \
  -w "\nStatus: %{http_code}\n"

# Expected: 404 STORY_NOT_FOUND
```

### Delete story - Not authenticated
```bash
INVALID_ID="507f1f77bcf86cd799438888"

curl -X DELETE "$BASE_URL/api/v1/stories/$INVALID_ID" \
  -w "\nStatus: %{http_code}\n"

# Expected: 403 FORBIDDEN (Spring Security unauthenticated access)
```

### Delete story - Not author (approved follower tries to delete)
```bash
# Create story as User 1
TOMORROW=$(date -u -v+1d +"%Y-%m-%dT%H:%M:%SZ")

STORY_JSON=$(curl -s -X POST "$BASE_URL/api/v1/stories" \
  -H "Content-Type: application/json" \
  -b "$COOKIES" \
  -d "{\"mediaUrl\": \"https://example.com/story_other_author.jpg\", \"expiresAt\": \"$TOMORROW\"}")

STORY_ID=$(echo "$STORY_JSON" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

# Switch to User 2 (clear cookies and login)
rm -f "$COOKIES"
curl -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"usernameOrEmail\": \"$USER2_EMAIL\", \"password\": \"$USER2_PASSWORD\"}" \
  -c "$COOKIES" \
  -w "\nStatus: %{http_code}\n" > /dev/null

# User 2 tries to delete User 1's story
curl -X DELETE "$BASE_URL/api/v1/stories/$STORY_ID" \
  -b "$COOKIES" \
  -w "\nStatus: %{http_code}\n"

# Expected: 403 FORBIDDEN "You are not the author of this story"
```

---

## Complete Workflow Examples

### Workflow 1: Create, View, Delete Story
```bash
# Step 1: Login as User 1
curl -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"usernameOrEmail\": \"$USER1_EMAIL\", \"password\": \"$USER1_PASSWORD\"}" \
  -c "$COOKIES" > /dev/null

# Step 2: Create a story
TOMORROW=$(date -u -v+1d +"%Y-%m-%dT%H:%M:%SZ")
STORY_JSON=$(curl -s -X POST "$BASE_URL/api/v1/stories" \
  -H "Content-Type: application/json" \
  -b "$COOKIES" \
  -d "{\"mediaUrl\": \"https://example.com/workflow1.jpg\", \"expiresAt\": \"$TOMORROW\"}")
STORY_ID=$(echo "$STORY_JSON" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "Created story: $STORY_ID"

# Step 3: View the story (as author, canDelete should be true)
curl -X GET "$BASE_URL/api/v1/stories/$STORY_ID" \
  -b "$COOKIES" -w "\nStatus: %{http_code}\n"

# Step 4: Delete the story
curl -X DELETE "$BASE_URL/api/v1/stories/$STORY_ID" \
  -b "$COOKIES" \
  -w "\nStatus: %{http_code}\n"

# Step 5: Try to view deleted story (should be 404)
curl -X GET "$BASE_URL/api/v1/stories/$STORY_ID" \
  -b "$COOKIES" \
  -w "\nStatus: %{http_code}\n"
```

### Workflow 2: Cross-User Story Access
```bash
# Step 1: Login as User 1 and create story
curl -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"usernameOrEmail\": \"$USER1_EMAIL\", \"password\": \"$USER1_PASSWORD\"}" \
  -c "$COOKIES" > /dev/null

TOMORROW=$(date -u -v+1d +"%Y-%m-%dT%H:%M:%SZ")
STORY_JSON=$(curl -s -X POST "$BASE_URL/api/v1/stories" \
  -H "Content-Type: application/json" \
  -b "$COOKIES" \
  -d "{\"mediaUrl\": \"https://example.com/crossuser.jpg\", \"expiresAt\": \"$TOMORROW\"}")
STORY_ID=$(echo "$STORY_JSON" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

# Step 2: Login as User 2 (approved follower)
rm -f "$COOKIES"
curl -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"usernameOrEmail\": \"$USER2_EMAIL\", \"password\": \"$USER2_PASSWORD\"}" \
  -c "$COOKIES" > /dev/null

# Step 3: User 2 views User 1's story (should work - approved follower)
curl -X GET "$BASE_URL/api/v1/stories/$STORY_ID" \
  -b "$COOKIES" \
  -w "\nStatus: %{http_code}\n"

# Step 4: User 2 tries to delete User 1's story (should fail)
curl -X DELETE "$BASE_URL/api/v1/stories/$STORY_ID" \
  -b "$COOKIES" \
  -w "\nStatus: %{http_code}\n"
```

### Workflow 3: Paginated Story Browsing
```bash
# Step 1: Login as User 1
curl -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"usernameOrEmail\": \"$USER1_EMAIL\", \"password\": \"$USER1_PASSWORD\"}" \
  -c "$COOKIES" > /dev/null

# Step 2: Get first page of stories (limit 5)
RESPONSE=$(curl -s -X GET "$BASE_URL/api/v1/stories/user/if.kshitij?limit=5" \
  -b "$COOKIES")

echo "First page stories:"
echo "$RESPONSE" | grep -o '"id":"[^"]*"' | head -5

# Step 3: Extract nextCursor for pagination
NEXT_CURSOR=$(echo "$RESPONSE" | grep -o '"nextCursor":"[^"]*"' | cut -d'"' -f4)

# Step 4: Get next page if cursor exists
if [ ! -z "$NEXT_CURSOR" ]; then
  echo "Getting page 2 with cursor: $NEXT_CURSOR"
  curl -X GET "$BASE_URL/api/v1/stories/user/if.kshitij?limit=5&cursor=$NEXT_CURSOR" \
    -b "$COOKIES" \
    -w "\nStatus: %{http_code}\n"
fi
```

---

## Notes for Testing

1. **Timestamp Format**: Use ISO 8601 format with 'Z' suffix for UTC
   - Example: `2026-02-14T10:30:00Z`

2. **Cookie Management**: 
   - `-c "$COOKIES"` saves cookies from login response
   - `-b "$COOKIES"` sends cookies in subsequent requests
   - Clear cookies between user switches: `rm -f "$COOKIES"`

3. **Response Parsing**:
   - Extract story ID: `grep -o '"id":"[^"]*"' | cut -d'"' -f4`
   - Extract nextCursor: `grep -o '"nextCursor":"[^"]*"' | cut -d'"' -f4`

4. **MacOS sed Issues**: 
   - Use `sed '$d'` to remove last line (not `head -n -1`)
   - Use `date -v+1d` for date arithmetic (not `date -d`)

5. **Error Response Format**:
   ```json
   {
     "error": "VALIDATION_ERROR",
     "message": "Media URL is required",
     "timestamp": "2026-02-14T10:30:00Z"
   }
   ```

6. **Story Response Format**:
   ```json
   {
     "id": "507f1f77bcf86cd799439011",
     "authorUsername": "alice",
     "mediaUrl": "https://example.com/story.jpg",
     "createdAt": "2026-02-14T10:30:00Z",
     "expiresAt": "2026-02-15T10:30:00Z",
     "canDelete": true/false
   }
   ```

7. **List Response Format**:
   ```json
   {
     "data": [
       { "id": "...", "authorUsername": "...", ... },
       { "id": "...", "authorUsername": "...", ... }
     ],
     "nextCursor": "507f1f77bcf86cd799439012"
   }
   ```


# POST Module - Complete Flow Documentation

## Overview
The Post Module provides complete CRUD operations for creating, viewing, liking, and deleting posts. It includes sophisticated access control (only authors and approved followers can view posts) and a cursor-based pagination system for efficient data retrieval.

**Total Endpoints**: 6
**Authentication Required**: 4/6 endpoints
**Pagination Support**: 1 endpoint

---

## Endpoint Summary

| # | Method | Endpoint | Auth | Purpose |
|---|--------|----------|------|---------|
| 1 | GET | `/api/v1/posts/{postId}` | Optional | Retrieve single post (with access control) |
| 2 | GET | `/api/v1/posts/user/{username}` | Optional | Retrieve paginated user posts (with access control) |
| 3 | POST | `/api/v1/posts` | Required | Create new post |
| 4 | DELETE | `/api/v1/posts/{postId}` | Required | Delete post (author only) |
| 5 | POST | `/api/v1/posts/{postId}/like` | Required | Like a post |
| 6 | DELETE | `/api/v1/posts/{postId}/like` | Required | Unlike a post |

---

## ENDPOINT 1: Get Single Post

### GET /api/v1/posts/{postId}

**Happy Path Flow:**
1. User (authenticated as alice) requests single post by ID
2. System verifies post exists in database
3. System performs access control check:
   - If user is post author → Allow
   - If user is approved follower of author → Allow
   - Otherwise → Deny
4. Post object returned with metadata (author, likes, comments, timestamp)

**Response (200 OK):**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "authorUsername": "alice",
  "mediaUrl": "https://example.com/image.jpg",
  "caption": "Amazing sunset",
  "likeCount": 42,
  "commentCount": 7,
  "createdAt": "2026-02-14T10:30:00Z",
  "canDelete": false
}
```

### Error Flows: Get Single Post

**1. Post Not Found (404)**
- User requests post with invalid/non-existent ID
- System cannot find post in database
- Returns 404 POST_NOT_FOUND

**2. Access Denied - Not Authenticated (403)**
- Anonymous user (no auth token) requests post
- System requires authentication for access control check
- Returns 403 FORBIDDEN "You don't have access to view this post"

**3. Access Denied - Not Approved Follower (403)**
- User is authenticated but NOT an approved follower of post author
- System enforces access control rule
- Returns 403 FORBIDDEN "You don't have access to view this post"

**4. Internal Server Error (500)**
- Unexpected error during retrieval
- Returns 500 INTERNAL_SERVER_ERROR

---

## ENDPOINT 2: Get User's Posts (Paginated)

### GET /api/v1/posts/user/{username}?limit=20&cursor=...

**Happy Path Flow:**
1. User requests paginated posts for specific user
2. System validates username format (3-30 chars, alphanumeric + _-.)
3. System verifies target user exists
4. System performs access control check:
   - If requester is the target user → Allow all posts
   - If requester is approved follower → Allow all posts
   - Otherwise → Deny
5. System fetches posts in reverse chronological order (newest first)
6. System returns paginated response with nextCursor for subsequent requests

**Query Parameters:**
- `limit`: 1-50 (default 20) - Posts per page
- `cursor`: Optional - Opaque token for pagination

**Response (200 OK):**
```json
{
  "data": [
    {
      "id": "507f1f77bcf86cd799439011",
      "authorUsername": "alice",
      "mediaUrl": "https://example.com/image1.jpg",
      "caption": "First post",
      "likeCount": 10,
      "commentCount": 2,
      "createdAt": "2026-02-15T10:30:00Z",
      "canDelete": false
    },
    {
      "id": "507f1f77bcf86cd799439012",
      "authorUsername": "alice",
      "mediaUrl": "https://example.com/image2.jpg",
      "caption": "Second post",
      "likeCount": 25,
      "commentCount": 5,
      "createdAt": "2026-02-14T09:15:00Z",
      "canDelete": false
    }
  ],
  "nextCursor": "507f1f77bcf86cd799439012"
}
```

### Error Flows: Get User's Posts

**1. Invalid Username Format (400)**
- Username doesn't match pattern (3-30 chars, alphanumeric + _-.)
- Returns 400 VALIDATION_ERROR

**2. User Not Found (404)**
- Target username doesn't exist in system
- Returns 404 USER_NOT_FOUND

**3. Access Denied - Not Authenticated (403)**
- Anonymous user (no auth token) requests posts
- System requires authentication to check approved follower status
- Returns 403 FORBIDDEN "You don't have access to view these posts"

**4. Access Denied - Not Approved Follower (403)**
- User is authenticated but NOT an approved follower of target user
- System enforces access control rule
- Returns 403 FORBIDDEN "You don't have access to view these posts"

**5. Invalid Limit (400)**
- Limit parameter outside valid range (1-50)
- Returns 400 VALIDATION_ERROR

**6. Internal Server Error (500)**
- Unexpected error during retrieval
- Returns 500 INTERNAL_SERVER_ERROR

---

## ENDPOINT 3: Create Post

### POST /api/v1/posts

**Happy Path Flow:**
1. Authenticated user submits request with mediaUrl and optional caption
2. System validates DTO:
   - mediaUrl: Required, non-blank, max 2048 chars
   - caption: Optional, max 500 chars
3. System creates PostEntity in database
   - Sets author to authenticated user
   - Sets likeCount = 0, commentCount = 0
   - Sets createdAt = current timestamp
4. System emits POST_CREATED event
5. Background job fanouts post to all approved followers' post_feed
6. New post returned with 201 Created response

**Request Body:**
```json
{
  "mediaUrl": "https://example.com/sunset.jpg",
  "caption": "Beautiful sunset at the beach #nature"
}
```

**Response (201 Created):**
```json
{
  "id": "507f1f77bcf86cd799439013",
  "authorUsername": "alice",
  "mediaUrl": "https://example.com/sunset.jpg",
  "caption": "Beautiful sunset at the beach #nature",
  "likeCount": 0,
  "commentCount": 0,
  "createdAt": "2026-02-15T12:45:30Z",
  "canDelete": true
}
```

### Error Flows: Create Post

**1. Missing mediaUrl (400)**
- mediaUrl field is blank or missing
- Returns 400 VALIDATION_ERROR "Media URL is required"

**2. mediaUrl Exceeds Max Length (400)**
- mediaUrl longer than 2048 characters
- Returns 400 VALIDATION_ERROR

**3. Caption Exceeds Max Length (400)**
- caption field longer than 500 characters
- Returns 400 VALIDATION_ERROR

**4. Not Authenticated (401)**
- No JWT token in cookie or token expired
- Returns 401 UNAUTHORIZED "Authentication required"

**5. Invalid DTO Format (400)**
- JSON body format invalid or other DTO field validation fails
- Returns 400 VALIDATION_ERROR

**6. Internal Server Error (500)**
- Unexpected error during post creation
- Returns 500 INTERNAL_SERVER_ERROR

---

## ENDPOINT 4: Delete Post

### DELETE /api/v1/posts/{postId}

**Happy Path Flow:**
1. Authenticated user requests to delete their post
2. System verifies post exists in database
3. System verifies user is the post author
4. System hard deletes post from database
5. System emits POST_DELETED event
6. Background job removes post from all post_feed entries and cleans up
7. Success message returned

**Response (200 OK):**
```json
{
  "message": "POST_DELETED"
}
```

### Error Flows: Delete Post

**1. Post Not Found (404)**
- Post ID doesn't exist
- System cannot find post to delete
- Returns 404 POST_NOT_FOUND

**2. Not Authenticated (401)**
- No JWT token in cookie or token expired
- Returns 401 UNAUTHORIZED "Authentication required"

**3. Not Post Author (403)**
- User is authenticated but NOT the post author
- System enforces authorization rule
- Returns 403 FORBIDDEN "You are not the author of this post"

**4. Internal Server Error (500)**
- Unexpected error during deletion
- Returns 500 INTERNAL_SERVER_ERROR

---

## ENDPOINT 5: Like Post

### POST /api/v1/posts/{postId}/like

**Happy Path Flow:**
1. Authenticated user requests to like a post
2. System verifies post exists in database
3. System checks if user has already liked post
4. System creates LikeEntity record
5. System emits POST_LIKED event
6. Background job increments post likeCount and notifies author
7. Success message returned

**Response (200 OK):**
```json
{
  "message": "POST_LIKED"
}
```

### Error Flows: Like Post

**1. Post Not Found (404)**
- Post ID doesn't exist
- Returns 404 POST_NOT_FOUND

**2. Not Authenticated (401)**
- No JWT token in cookie or token expired
- Returns 401 UNAUTHORIZED "Authentication required"

**3. Already Liked (409)**
- User has already liked this post
- Prevents duplicate like from same user
- Returns 409 ALREADY_LIKED "You have already liked this post"

**4. Internal Server Error (500)**
- Unexpected error during like operation
- Returns 500 INTERNAL_SERVER_ERROR

---

## ENDPOINT 6: Unlike Post

### DELETE /api/v1/posts/{postId}/like

**Happy Path Flow:**
1. Authenticated user requests to unlike a post they previously liked
2. System verifies post exists in database
3. System checks if user has liked post
4. System deletes LikeEntity record
5. System emits POST_UNLIKED event
6. Background job decrements post likeCount
7. Success message returned

**Response (200 OK):**
```json
{
  "message": "POST_UNLIKED"
}
```

### Error Flows: Unlike Post

**1. Post Not Found (404)**
- Post ID doesn't exist
- Returns 404 POST_NOT_FOUND

**2. Not Authenticated (401)**
- No JWT token in cookie or token expired
- Returns 401 UNAUTHORIZED "Authentication required"

**3. Not Liked (409)**
- User has NOT liked this post
- Cannot unlike a post that wasn't liked
- Returns 409 NOT_LIKED "You have not liked this post"

**4. Internal Server Error (500)**
- Unexpected error during unlike operation
- Returns 500 INTERNAL_SERVER_ERROR

---

## Complete Flow Scenarios

### Scenario 1: Create, View, Like, Unlike Post
```
1. alice creates post → 201 CREATED
2. alice views own post → 200 OK (canDelete: true)
3. bob (approved follower of alice) views alice's post → 200 OK
4. bob likes alice's post → 200 OK
5. alice's post now shows likeCount: 1
6. bob unlikes alice's post → 200 OK
7. alice's post likeCount decrements back to 0
```

### Scenario 2: Access Control Enforcement
```
1. alice creates post
2. charlie (NOT following alice) tries to view post:
   - GET /api/v1/posts/{postId} → 403 FORBIDDEN
3. charlie follows alice and is approved
4. charlie tries to view post again:
   - GET /api/v1/posts/{postId} → 200 OK
5. charlie tries to delete alice's post:
   - DELETE /api/v1/posts/{postId} → 403 FORBIDDEN (not author)
```

### Scenario 3: Pagination Workflow
```
1. alice has 100 posts
2. bob (approved follower) requests first page:
   - GET /api/v1/posts/user/alice?limit=20 → 200 OK with 20 posts + nextCursor
3. bob requests second page:
   - GET /api/v1/posts/user/alice?limit=20&cursor=<nextCursor> → 200 OK with next 20 posts
4. bob continues paginating until nextCursor is null (all posts retrieved)
```

### Scenario 4: Double Like Prevention
```
1. User likes post → 200 OK
2. Same user tries to like post again → 409 ALREADY_LIKED
3. User unlikes post → 200 OK
4. User tries to unlike again → 409 NOT_LIKED
```

---

## Access Control Rules Summary

| Scenario | GET Single Post | GET User Posts | POST Create | DELETE | LIKE | UNLIKE |
|----------|---|---|---|---|---|---|
| Post author (authenticated) | ✅ Allow | ✅ Allow | N/A | ✅ Allow | ✅ Allow | Conditional |
| Approved follower (authenticated) | ✅ Allow | ✅ Allow | N/A | ❌ Reject | ✅ Allow | Conditional |
| Not follower (authenticated) | ❌ Reject | ❌ Reject | N/A | ❌ Reject | ❌ Reject | ❌ Reject |
| Not authenticated | ❌ Reject | ❌ Reject | ❌ Reject | ❌ Reject | ❌ Reject | ❌ Reject |

---

## Data Validation Rules

### mediaUrl
- Required: YES
- Max Length: 2048 chars
- Must be non-blank
- Validation: VALIDATION_ERROR (400) if invalid

### caption
- Required: NO (optional)
- Max Length: 500 chars
- Validation: VALIDATION_ERROR (400) if exceeds max

### username (path parameter)
- Required: YES
- Pattern: `^[a-zA-Z0-9_.-]{3,30}$`
- Length: 3-30 chars
- Validation: VALIDATION_ERROR (400) if format invalid

### PostId (path parameter)
- Required: YES
- Format: MongoDB ObjectId (24 hex characters)
- Validation: POST_NOT_FOUND (404) if doesn't exist

### limit (query parameter)
- Range: 1-50
- Default: 20
- Validation: VALIDATION_ERROR (400) if out of range

---

## Response Status Codes Summary

| Status | Endpoint(s) Using | Meaning |
|--------|----------|---------|
| 200 OK | All endpoints | Successful operation |
| 201 Created | POST /api/v1/posts | Post successfully created |
| 400 Bad Request | GET user posts, POST create | Validation error (format, length, range) |
| 401 Unauthorized | POST, DELETE (all), LIKE, UNLIKE | Not authenticated |
| 403 Forbidden | GET endpoints, DELETE, LIKE, UNLIKE | Access control denied |
| 404 Not Found | GET, DELETE, LIKE, UNLIKE | Post/user not found |
| 409 Conflict | LIKE, UNLIKE | Like state conflict (duplicate or not liked) |
| 500 Internal Server Error | All endpoints | Unexpected server error |

---

## Testing Strategy

**Total Test Cases**: 33 (estimated)
- Happy Path: 13 cases
  - Create post: 2 cases (with/without caption)
  - Get single post: 2 cases (as author, as approved follower)
  - Get user posts: 2 cases (with/without pagination)
  - Delete post: 1 case
  - Like post: 2 cases (initial like, check count)
  - Unlike post: 1 case
  - Access control verification: 1 case

- Error Cases: 20 cases
  - Create post: 4 error flows
  - Get single post: 4 error flows
  - Get user posts: 6 error flows
  - Delete post: 3 error flows
  - Like post: 3 error flows
  - Unlike post: 3 error flows

**Test Users**:
- `if.kshitij` (User 1) - Post creator
- `hhoehunterr` (User 2) - Approved follower (from Follow module tests)
- Both have password: `Dettcmpw123?`

**Test Data**:
- Valid post with image and caption
- Valid post with image only (no caption)
- Invalid posts (missing mediaUrl, oversized fields)
- Pagination with cursor
- Like/unlike cycles

---

## Implementation Notes

1. **Access Control**: Enforced at application level (not database level)
2. **Hard Delete**: Posts use hard delete (no soft delete flag)
3. **Like Counts**: Maintained by background job listening to POST_LIKED/POST_UNLIKED events
4. **Pagination**: Cursor-based (opaque token), prevents offset vulnerabilities
5. **Atomicity**: Database update + event emission are atomic
6. **Background Jobs**: POST_CREATED fanouts to post_feed, POST_DELETED/LIKED/UNLIKED update aggregates


# Post Module API Contract

## Overview
The post module enables users to create, retrieve, and manage posts with support for likes/unlikes and pagination.

## Authentication & Access Control
- **JWT Authentication Filter**: All endpoints use the `JwtAuthenticationFilter` which extracts the token from cookies and populates the security context with `UserPrincipal`
- **Flexible Auth**: GET endpoints (retrieve posts) support optional authentication
  - If authenticated: Access control is enforced (must be post author or approved follower)
  - If not authenticated: Access is denied (403 FORBIDDEN)
- **Mandatory Auth**: POST/DELETE endpoints (create, delete, like, unlike) require authentication

## Follower Access Model
- Users can view their own posts (always allowed if authenticated as author)
- Users can view posts only if they are **approved followers** of the post author
- Unauthenticated users cannot view any posts (receive 403 FORBIDDEN)

---

## API Endpoints

### 1. Get Post by ID
**GET** `/api/v1/posts/{postId}`

**Auth**: Optional (access control enforced)

**Description**: Retrieve a single post by ID

**Access Control**:
- ✅ Viewer is the post author
- ✅ Viewer is an approved follower of the post author
- ❌ Viewer is not authenticated
- ❌ Viewer is not an approved follower

**Path Parameters**:
- `postId` (string, required): MongoDB ObjectId

**Response** (200 OK):
```json
{
  "id": "507f1f77bcf86cd799439011",
  "authorUsername": "john_doe",
  "mediaUrl": "https://example.com/image.jpg",
  "caption": "Beautiful sunset",
  "likeCount": 42,
  "commentCount": 5,
  "createdAt": "2026-02-14T10:30:00Z",
  "canDelete": false
}
```

**Error Responses**:
| Error Code | Status | Message | Reason |
|---|---|---|---|
| POST_NOT_FOUND | 404 | Post not found | Post ID doesn't exist |
| FORBIDDEN | 403 | You don't have access to view this post | User not authenticated or not an approved follower |
| INTERNAL_SERVER_ERROR | 500 | An error occurred while retrieving post | Unexpected server error |

---

### 2. Get Posts by Username (Paginated)
**GET** `/api/v1/posts/user/{username}?limit=20&cursor=...`

**Auth**: Optional (access control enforced)

**Description**: Retrieve paginated posts by a user (cursor-based pagination)

**Access Control**:
- ✅ Viewer is the post author (viewing own posts)
- ✅ Viewer is an approved follower of the author
- ❌ Viewer is not authenticated
- ❌ Viewer is not an approved follower

**Description**: Retrieve paginated posts by a user (cursor-based pagination)

**Path Parameters**:
- `username` (string, required): Author's username (3-30 chars, pattern: `^[a-zA-Z0-9_.-]{3,30}$`)

**Query Parameters**:
- `limit` (integer, optional, default=20): Items per page (1-50)
- `cursor` (string, optional): Opaque cursor for pagination (use `nextCursor` from previous response)

**Response** (200 OK):
```json
{
  "data": [
    {
      "id": "507f1f77bcf86cd799439011",
      "authorUsername": "john_doe",
      "mediaUrl": "https://example.com/image1.jpg",
      "caption": "First post",
      "likeCount": 10,
      "commentCount": 2,
      "createdAt": "2026-02-14T10:30:00Z",
      "canDelete": true
    },
    {
      "id": "507f1f77bcf86cd799439013",
      "authorUsername": "john_doe",
      "mediaUrl": "https://example.com/image2.jpg",
      "caption": "Second post",
      "likeCount": 5,
      "commentCount": 1,
      "createdAt": "2026-02-14T09:30:00Z",
      "canDelete": true
    }
  ],
  "nextCursor": "507f1f77bcf86cd799439013"
}
```

**Error Responses**:
| Error Code | Status | Message | Reason |
|---|---|---|---|
| INVALID_USERNAME | 400 | Username format is invalid | Invalid username pattern |
| USER_NOT_FOUND | 404 | Profile does not exist | User not found |
| FORBIDDEN | 403 | You don't have access to view these posts | User not authenticated or not an approved follower |
| INTERNAL_SERVER_ERROR | 500 | An error occurred while retrieving posts | Unexpected server error |

---

### 3. Create Post
**POST** `/api/v1/posts`

**Auth**: Required (user must be authenticated)

**Description**: Create a new post

**Request Body**:
```json
{
  "mediaUrl": "https://example.com/image.jpg",
  "caption": "Beautiful sunset at the beach"
}
```

**Request DTO Validation**:
- `mediaUrl` (string, required): Non-blank, max 2048 chars
- `caption` (string, optional): Max 500 chars

**Response** (201 Created):
```json
{
  "id": "507f1f77bcf86cd799439011",
  "authorUsername": "john_doe",
  "mediaUrl": "https://example.com/image.jpg",
  "caption": "Beautiful sunset at the beach",
  "likeCount": 0,
  "commentCount": 0,
  "createdAt": "2026-02-14T10:30:00Z",
  "canDelete": true
}
```

**Business Logic**:
1. Validates mediaUrl (required, not blank)
2. Creates PostEntity (likeCount=0, commentCount=0)
3. Saves post to database
4. Emits POST_CREATED event with payload: {postId, authorId, mediaUrl, caption, createdAt}
5. Background job processes event to fanout post to follower post_feed

**Error Responses**:
| Error Code | Status | Message | Reason |
|---|---|---|---|
| UNAUTHORIZED | 401 | Authentication required | User not authenticated |
| VALIDATION_ERROR | 400 | Media URL is required | mediaUrl blank or missing |
| VALIDATION_ERROR | 400 | [Field]: [constraint violated] | DTO field validation failed |
| INTERNAL_SERVER_ERROR | 500 | An error occurred while creating post | Unexpected server error |

---

### 4. Delete Post
**DELETE** `/api/v1/posts/{postId}`

**Auth**: Required (user must be post author)

**Description**: Delete a post (hard delete)

**Path Parameters**:
- `postId` (string, required): MongoDB ObjectId

**Response** (200 OK):
```json
{
  "message": "POST_DELETED"
}
```

**Business Logic**:
1. Verifies post exists (404 if not)
2. Verifies user is post author (403 if not)
3. Hard deletes post from database
4. Emits POST_DELETED event with payload: {postId, authorId, deletedAt}
5. Background job processes event to remove post from post_feed and cleanup

**Error Responses**:
| Error Code | Status | Message | Reason |
|---|---|---|---|
| UNAUTHORIZED | 401 | Authentication required | User not authenticated |
| POST_NOT_FOUND | 404 | Post not found | Post ID doesn't exist |
| FORBIDDEN | 403 | You are not the author of this post | User is not post author |
| INTERNAL_SERVER_ERROR | 500 | An error occurred while deleting post | Unexpected server error |

---

### 5. Like Post
**POST** `/api/v1/posts/{postId}/like`

**Auth**: Required (user must be authenticated)

**Description**: Like a post

**Path Parameters**:
- `postId` (string, required): MongoDB ObjectId

**Response** (200 OK):
```json
{
  "message": "POST_LIKED"
}
```

**Business Logic**:
1. Verifies post exists (404 if not)
2. Checks if user has already liked post (409 if yes)
3. Creates LikeEntity record (userId, postId, createdAt)
4. Saves like to database
5. Emits POST_LIKED event with payload: {postId, userId, authorId, likedAt}
6. Background job processes event to increment post likeCount and notify author

**Note**: likeCount is maintained by background job, not by this endpoint

**Error Responses**:
| Error Code | Status | Message | Reason |
|---|---|---|---|
| UNAUTHORIZED | 401 | Authentication required | User not authenticated |
| POST_NOT_FOUND | 404 | Post not found | Post ID doesn't exist |
| ALREADY_LIKED | 409 | You have already liked this post | User has already liked this post |
| INTERNAL_SERVER_ERROR | 500 | An error occurred while liking post | Unexpected server error |

---

### 6. Unlike Post
**DELETE** `/api/v1/posts/{postId}/like`

**Auth**: Required (user must be authenticated)

**Description**: Unlike a post

**Path Parameters**:
- `postId` (string, required): MongoDB ObjectId

**Response** (200 OK):
```json
{
  "message": "POST_UNLIKED"
}
```

**Business Logic**:
1. Verifies post exists (404 if not)
2. Checks if user has liked post (409 if not)
3. Deletes LikeEntity record
4. Emits POST_UNLIKED event with payload: {postId, userId, authorId, unlikedAt}
5. Background job processes event to decrement post likeCount

**Note**: likeCount is maintained by background job, not by this endpoint

**Error Responses**:
| Error Code | Status | Message | Reason |
|---|---|---|---|
| UNAUTHORIZED | 401 | Authentication required | User not authenticated |
| POST_NOT_FOUND | 404 | Post not found | Post ID doesn't exist |
| NOT_LIKED | 409 | You have not liked this post | User has not liked this post |
| INTERNAL_SERVER_ERROR | 500 | An error occurred while unliking post | Unexpected server error |

---

## Data Models

### PostEntity (MongoDB Collection: `posts`)
```json
{
  "_id": ObjectId,
  "authorId": ObjectId,
  "mediaUrl": string,
  "caption": string,
  "likeCount": number,
  "commentCount": number,
  "createdAt": ISODate
}
```

**Indexes**:
- `{ authorId: 1, createdAt: -1 }` - Fetch posts by user with newest first
- `{ createdAt: -1 }` - Fetch recent posts globally

**Notes**:
- Hard delete (no soft delete/isDeleted field)
- likeCount managed by background job via POST_LIKED/POST_UNLIKED events
- commentCount managed by comment service

### LikeEntity (MongoDB Collection: `likes`)
```json
{
  "_id": ObjectId,
  "userId": ObjectId,
  "postId": ObjectId,
  "createdAt": ISODate
}
```

**Indexes**:
- `{ userId: 1, postId: 1 }` - UNIQUE constraint to prevent duplicate likes
- `{ postId: 1 }` - Fetch all likes for a post

---

## Events

### POST_CREATED
**Payload**:
```json
{
  "postId": "507f1f77bcf86cd799439011",
  "authorId": "507f1f77bcf86cd799439012",
  "mediaUrl": "https://example.com/image.jpg",
  "caption": "Beautiful sunset",
  "createdAt": "2026-02-14T10:30:00Z"
}
```
**Background Job**: Fanout post to all followers' post_feed

### POST_DELETED
**Payload**:
```json
{
  "postId": "507f1f77bcf86cd799439011",
  "authorId": "507f1f77bcf86cd799439012",
  "deletedAt": "2026-02-14T11:30:00Z"
}
```
**Background Job**: Remove post from all post_feed entries

### POST_LIKED
**Payload**:
```json
{
  "postId": "507f1f77bcf86cd799439011",
  "userId": "507f1f77bcf86cd799439013",
  "authorId": "507f1f77bcf86cd799439012",
  "likedAt": "2026-02-14T10:35:00Z"
}
```
**Background Job**: Increment post likeCount, send notification to post author

### POST_UNLIKED
**Payload**:
```json
{
  "postId": "507f1f77bcf86cd799439011",
  "userId": "507f1f77bcf86cd799439013",
  "authorId": "507f1f77bcf86cd799439012",
  "unlikedAt": "2026-02-14T10:40:00Z"
}
```
**Background Job**: Decrement post likeCount

---

## Exception Handling Priority

### By HTTP Status:
- **400 Bad Request**: INVALID_USERNAME, VALIDATION_ERROR
- **401 Unauthorized**: UNAUTHORIZED
- **403 Forbidden**: FORBIDDEN
- **404 Not Found**: USER_NOT_FOUND, POST_NOT_FOUND
- **409 Conflict**: ALREADY_LIKED, NOT_LIKED
- **500 Internal Server Error**: INTERNAL_SERVER_ERROR

### Handler Chain:
1. `GlobalExceptionHandler.handleConstraintViolationException()` - Path/query param validation
2. `GlobalExceptionHandler.handleMethodArgumentNotValidException()` - DTO field validation
3. `GlobalExceptionHandler.handleAuthException()` - Business logic exceptions
4. `GlobalExceptionHandler.handleException()` - Generic fallback

---

## Example Workflows

### Workflow 1: Create and Like a Post
```
1. User (alice) authenticates and receives JWT token in cookie
2. POST /api/v1/posts with mediaUrl and caption
   → 201 Created with PostResponse
   → POST_CREATED event emitted
   → BG job fanouts to post_feed of approved followers
3. User sees post
4. POST /api/v1/posts/{postId}/like
   → 200 OK with POST_LIKED message
   → POST_LIKED event emitted
   → BG job increments likeCount
```

### Workflow 2: View User's Posts with Pagination (Access Control)
```
Scenario: bob wants to view alice's posts

Option A: bob is not authenticated
1. GET /api/v1/posts/user/alice?limit=10
   → 403 FORBIDDEN "You don't have access to view these posts"
   → No JWT token in request

Option B: bob is authenticated but not following alice
1. GET /api/v1/posts/user/alice?limit=10
   → 403 FORBIDDEN "You don't have access to view these posts"
   → JWT token present but bob not in alice's approved followers

Option C: bob is an approved follower of alice
1. GET /api/v1/posts/user/alice?limit=10
   → 200 OK with 10 posts and nextCursor
2. GET /api/v1/posts/user/alice?limit=10&cursor=<nextCursor>
   → 200 OK with next 10 posts
3. Continue until nextCursor is null (no more posts)

Option D: bob views his own posts (alice viewing alice's posts)
1. GET /api/v1/posts/user/alice?limit=10 (authenticated as alice)
   → 200 OK with all of alice's posts
   → Always allowed for own posts
```

### Workflow 3: Access Single Post (Access Control)
```
Scenario: bob wants to view a specific post by alice

1. bob_token = authenticate as bob
2. GET /api/v1/posts/{postId} (without token)
   → 403 FORBIDDEN "You don't have access to view this post"
3. GET /api/v1/posts/{postId} (with bob_token, bob not following alice)
   → 403 FORBIDDEN "You don't have access to view this post"
4. bob follows alice and is approved
5. GET /api/v1/posts/{postId} (with bob_token, bob is approved follower)
   → 200 OK with PostResponse
```

### Workflow 4: Delete Post and Unlike
```
1. User (post author) DELETE /api/v1/posts/{postId}
   → 200 OK with POST_DELETED message
   → POST_DELETED event emitted
   → BG job removes from post_feed
2. Another user who liked the post DELETE /api/v1/posts/{postId}/like
   → 404 NOT_FOUND "Post not found"
   → (Post was already deleted in step 1)
```

---

## Implementation Notes

### Access Control Logic
- **View own posts**: Always allowed (if authenticated as the post author)
- **View others' posts**: Only if authenticated AND an approved follower of the post author
- **Not authenticated**: Always denied (403 FORBIDDEN)
- **Not a follower**: Always denied (403 FORBIDDEN)
- **Not approved**: Denied (only approved follow status grants access, pending/rejected do not)

### Transactional Boundaries
- **Create Post**: Database save + event emission (atomic)
- **Delete Post**: Database delete + event emission (atomic)
- **Like Post**: Like save + event emission (atomic)
- **Unlike Post**: Like delete + event emission (atomic)
- **View Post**: Access check happens before retrieval, no transaction needed

### Field Size Constraints
- `mediaUrl`: Required, max 2048 chars (typical URL limit)
- `caption`: Optional, max 500 chars
- Username: 3-30 chars, pattern `^[a-zA-Z0-9_.-]{3,30}$`

### Pagination
- Cursor-based (opaque) for consistency across modules
- Limit: 1-50, default 20
- Next cursor taken from last post's ID
- Empty nextCursor indicates end of results

### Background Job Responsibilities
- POST_CREATED: Fanout to follower post_feed (only approved followers)
- POST_DELETED: Cleanup post_feed entries
- POST_LIKED: Update likeCount, notify author
- POST_UNLIKED: Update likeCount


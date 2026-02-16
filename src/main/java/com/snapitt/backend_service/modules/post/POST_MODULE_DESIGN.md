# Post Module - Deep Dive Analysis & API Design

## Database Collections

### Posts Collection
```
{
  _id: ObjectId,
  authorId: ObjectId,              // User who created the post
  mediaUrl: string,                 // URL to media (image/video)
  caption: string,                  // Post description
  likeCount: number,                // Updated by background job (POST_LIKED event)
  commentCount: number,             // Updated by comment service
  createdAt: ISODate                // Creation timestamp
}

Indexes:
- { authorId: 1, createdAt: -1 }   // Fetch posts by user
- { createdAt: -1 }                // Fetch recent posts

NOTE: No soft delete - posts are hard deleted. Background job handles post_feed cleanup via POST_DELETED event.
```

### Events Collection (shared)
```
{
  _id: ObjectId,
  type: "POST_CREATED" | "POST_LIKED" | "POST_UNLIKED" | "POST_DELETED",
  aggregateId: ObjectId,           // postId or commentId
  payload: {
    userId: ObjectId,
    postId: ObjectId,
    ...
  },
  status: "pending" | "processing" | "done" | "failed",
  retryCount: number,
  createdAt: ISODate,
  processedAt: ISODate,
  lockedBy: string | null,
  lockedAt: ISODate | null
}
```

## Endpoints Design

### 1. Get Post by ID
```
GET /api/v1/posts/{postId}

Path Parameters:
- postId: String (MongoDB ObjectId)

Query Parameters:
- (none)

Response: 200 OK
{
  "_id": "123...",
  "authorId": "456...",
  "mediaUrl": "https://...",
  "caption": "Post caption",
  "likeCount": 10,
  "commentCount": 5,
  "createdAt": "2026-02-13T10:00:00Z"
}

Errors:
- 400 Bad Request - Invalid postId format
- 404 Not Found - Post not found
- 500 Internal Server Error
```

### 2. Get Posts by Username
```
GET /api/v1/posts/user/{username}

Path Parameters:
- username: String (3-30 chars, pattern: ^[a-zA-Z0-9_.-]{3,30}$)

Query Parameters:
- limit: int (1-50, default 20)
- cursor: string (optional)

Response: 200 OK
{
  "data": [
    {
      "_id": "123...",
      "authorId": "456...",
      "mediaUrl": "https://...",
      "caption": "Post caption",
      "likeCount": 10,
      "commentCount": 5,
      "createdAt": "2026-02-13T10:00:00Z"
    }
  ],
  "nextCursor": "abc123..." or null
}

Errors:
- 400 Bad Request - Invalid username format
- 404 Not Found - User not found
- 500 Internal Server Error
```

### 3. Create Post
```
POST /api/v1/posts

Auth: Required (JWT)

Request Body:
{
  "mediaUrl": "https://cdn.example.com/image.jpg",
  "caption": "My awesome post"
}

Response: 201 Created
{
  "_id": "123...",
  "authorId": "456...",
  "mediaUrl": "https://...",
  "caption": "My awesome post",
  "likeCount": 0,
  "commentCount": 0,
  "createdAt": "2026-02-13T11:00:00Z"
}

Validation:
- mediaUrl: required, must be valid URL format
- caption: optional, max 500 chars

Events Emitted:
- POST_CREATED with payload: { postId, authorId, mediaUrl, caption, createdAt }
- Background job will write post to post_feed for all followers

Errors:
- 400 Bad Request - Validation failed
- 401 Unauthorized - Not authenticated
- 500 Internal Server Error
```

### 4. Delete Post
```
DELETE /api/v1/posts/{postId}

Auth: Required (JWT)

Path Parameters:
- postId: String (MongoDB ObjectId)

Response: 200 OK
{
  "message": "POST_DELETED"
}

Business Logic:
- Only post author can delete
- Hard delete the post from posts collection
- Emit POST_DELETED event with deletedAt timestamp
- Background job will handle post_feed cleanup

Events Emitted:
- POST_DELETED with payload: { postId, authorId, deletedAt }

Errors:
- 400 Bad Request - Invalid postId format
- 401 Unauthorized - Not authenticated
- 403 Forbidden - Not post author
- 404 Not Found - Post not found
- 500 Internal Server Error
```

### 5. Like Post
```
POST /api/v1/posts/{postId}/like

Auth: Required (JWT)

Path Parameters:
- postId: String (MongoDB ObjectId)

Response: 200 OK
{
  "message": "POST_LIKED"
}

Business Logic:
- Create entry in likes collection
- Do NOT update likeCount in posts (handled by background job)
- Emit POST_LIKED event
- Transaction: like created -> event emitted

Errors:
- 400 Bad Request - Invalid postId format
- 401 Unauthorized - Not authenticated
- 404 Not Found - Post not found
- 409 Conflict - Already liked
- 500 Internal Server Error
```

### 6. Unlike Post
```
DELETE /api/v1/posts/{postId}/like

Auth: Required (JWT)

Path Parameters:
- postId: String (MongoDB ObjectId)

Response: 200 OK
{
  "message": "POST_UNLIKE"
}

Business Logic:
- Delete entry from likes collection
- Do NOT update likeCount in posts (handled by background job)
- Emit POST_UNLIKED event

Errors:
- 400 Bad Request - Invalid postId format
- 401 Unauthorized - Not authenticated
- 404 Not Found - Post not found
- 409 Conflict - Not liked
- 500 Internal Server Error
```

## Module Structure

```
post/
├── controller/
│   └── PostController.java
├── service/
│   └── PostService.java
├── model/
│   └── PostEntity.java
├── repository/
│   └── PostRepository.java
├── dto/
│   ├── request/
│   │   ├── CreatePostRequest.java
│   │   └── PaginationQueryRequest.java (reuse from follow)
│   └── response/
│       ├── PostResponse.java
│       ├── PaginatedPostsResponse.java
│       └── PostActionResponse.java
└── API_CONTRACT.md
```

## DTOs Required

### Request DTOs
1. **CreatePostRequest**
   - `mediaUrl`: String (required, URL validation)
   - `caption`: String (optional, max 500 chars)

2. **PaginationQueryRequest** (reuse from follow module)
   - `limit`: Integer (1-50, default 20)
   - `cursor`: String (optional)

### Response DTOs
1. **PostResponse** (single post)
   - `_id`: String
   - `authorId`: String
   - `mediaUrl`: String
   - `caption`: String
   - `likeCount`: Long
   - `commentCount`: Long
   - `createdAt`: ISO 8601

2. **PaginatedPostsResponse**
   - `data`: List<PostResponse>
   - `nextCursor`: String (nullable)

3. **PostActionResponse**
   - `message`: String (e.g., "POST_DELETED", "POST_LIKED")

## Validation Rules

| Field | Constraint | Error Code |
|-------|-----------|-----------|
| postId | Valid MongoDB ObjectId | `INVALID_POST_ID` (400) |
| username | Pattern + length | `INVALID_USERNAME` (400) |
| mediaUrl | Required, URL format | `VALIDATION_ERROR` (400) |
| caption | Max 500 chars | `VALIDATION_ERROR` (400) |
| limit | 1-50 | `VALIDATION_ERROR` (400) |

## Error Codes & HTTP Status

| Error Code | Status | Scenario |
|-----------|--------|----------|
| `INVALID_POST_ID` | 400 | Invalid ObjectId format |
| `INVALID_USERNAME` | 400 | Invalid username format |
| `VALIDATION_ERROR` | 400 | Field validation failed |
| `UNAUTHORIZED` | 401 | Not authenticated |
| `FORBIDDEN` | 403 | Not post author |
| `USER_NOT_FOUND` | 404 | User/author not found |
| `POST_NOT_FOUND` | 404 | Post not found |
| `ALREADY_LIKED` | 409 | Already liked this post |
| `NOT_LIKED` | 409 | Not liked this post |
| `INTERNAL_SERVER_ERROR` | 500 | Unexpected errors |

## Business Logic Rules

1. **Create Post** (Transactional)
   - Validate mediaUrl (required), caption (optional, max 500)
   - Create PostEntity with authorId from JWT
   - Save to database
   - Emit POST_CREATED event
   - Return created post

2. **Delete Post** (Transactional)
   - Verify post exists
   - Verify current user is author
   - Hard delete the post from posts collection
   - Emit POST_DELETED event with deletedAt timestamp (background job will update post_feed)

3. **Like Post** (Transactional)
   - Verify post exists
   - Check if already liked (prevent duplicate)
   - Create entry in likes collection (with unique index on userId+postId)
   - Emit POST_LIKED event (background job will update likeCount)
   - Do NOT update likeCount here

4. **Unlike Post** (Transactional)
   - Verify post exists
   - Verify like exists
   - Delete from likes collection
   - Emit POST_UNLIKED event (background job will update likeCount)

## Exception Handling Strategy

### Controller Level
- Validate `@PathVariable` postId with custom validator (ObjectId format)
- Validate username with `@Pattern` annotation
- Throw `AuthException("UNAUTHORIZED")` if not authenticated
- Validate limit/cursor via `@Min/@Max` on query params

### Service Level
- Validate mediaUrl format + required field
- Validate caption length
- Check post existence (throw `POST_NOT_FOUND`)
- Check user existence (throw `USER_NOT_FOUND`)
- Check ownership for delete (throw `FORBIDDEN`)
- Check like status for unlike (throw `NOT_LIKED`)
- Wrap unexpected exceptions as `INTERNAL_SERVER_ERROR`

### GlobalExceptionHandler
- Convert `ConstraintViolationException` → `INVALID_POST_ID` or `INVALID_USERNAME`
- Convert `MethodArgumentNotValidException` → `VALIDATION_ERROR`
- Convert `AuthException` → appropriate HTTP status + error code
- Convert generic `Exception` → `INTERNAL_SERVER_ERROR`

## Event Payloads

### POST_CREATED
```json
{
  "postId": "ObjectId",
  "authorId": "ObjectId",
  "mediaUrl": "string",
  "caption": "string",
  "createdAt": "ISO 8601"
}
```

### POST_LIKED
```json
{
  "postId": "ObjectId",
  "userId": "ObjectId (liker)",
  "likedAt": "ISO 8601"
}
```

### POST_UNLIKED
```json
{
  "postId": "ObjectId",
  "userId": "ObjectId (unliker)",
  "unlikedAt": "ISO 8601"
}
```

### POST_DELETED
```json
{
  "postId": "ObjectId",
  "authorId": "ObjectId",
  "deletedAt": "ISO 8601"
}
```

---

## Implementation Checklist

- [ ] Create PostEntity model with MongoDB annotations
- [ ] Create PostRepository with proper query methods
- [ ] Create request DTOs (CreatePostRequest, reuse PaginationQueryRequest)
- [ ] Create response DTOs (PostResponse, PaginatedPostsResponse, PostActionResponse)
- [ ] Create PostService with all business logic
- [ ] Create PostController with validation
- [ ] Add ObjectId validation constraint
- [ ] Create API_CONTRACT.md for Post module
- [ ] Update GlobalExceptionHandler if needed for new error codes

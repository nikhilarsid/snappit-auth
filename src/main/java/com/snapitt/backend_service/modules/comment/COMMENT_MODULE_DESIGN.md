# Comment Module - Deep Dive Analysis & Design

## Database Collections

### Comments Collection
```
{
  _id: ObjectId,
  postId: ObjectId,              // Post being commented on
  authorId: ObjectId,            // User who created comment
  parentCommentId: ObjectId | null,  // For replies (null for top-level)
  text: string,                  // Comment content (1-5000 chars)
  tagged: [ObjectId],            // List of mentioned user IDs
  likeCount: number,             // Updated by background job
  replyCount: number,            // Incremented when replies added
  createdAt: ISODate,
  isDeleted: boolean             // Soft delete flag
}

Indexes:
- { postId: 1, parentCommentId: 1, createdAt: -1 }  // Fetch comments/replies
- { parentCommentId: 1, createdAt: -1 }            // Fetch replies by parent

NOTE: Soft delete (isDeleted flag). Comments are NOT hard deleted.
Background job handles notifications via COMMENT_CREATED event.
```

### Events Collection (shared)
```
{
  _id: ObjectId,
  type: "COMMENT_CREATED",
  aggregateId: ObjectId,           // commentId
  payload: {
    commentId: ObjectId,
    postId: ObjectId,
    authorId: ObjectId,
    text: string,
    parentCommentId: ObjectId | null,
    tagged: [ObjectId],
    createdAt: ISODate
  },
  status: "pending" | "processing" | "done" | "failed",
  retryCount: number,
  createdAt: ISODate,
  processedAt: ISODate,
  lockedBy: string | null,
  lockedAt: ISODate | null
}
```

## Module Structure

```
comment/
├── controller/
│   └── CommentController.java
├── service/
│   └── CommentService.java
├── model/
│   └── CommentEntity.java
├── repository/
│   └── CommentRepository.java
├── dto/
│   ├── request/
│   │   └── CreateCommentRequest.java
│   └── response/
│       ├── CommentResponse.java
│       ├── PaginatedCommentsResponse.java
│       └── CommentActionResponse.java
├── API_CONTRACT.md
└── COMMENT_MODULE_DESIGN.md (this file)
```

## API Endpoints Design

### 1. Get Comment by ID
```
GET /api/v1/posts/{postId}/comments/{commentId}

Path Parameters:
- postId: String (MongoDB ObjectId)
- commentId: String (MongoDB ObjectId)

Query Parameters:
- (none)

Response: 200 OK
{
  "id": "123...",
  "postId": "post123...",
  "authorUsername": "john_doe",
  "text": "Great post!",
  "tagged": [],
  "likeCount": 5,
  "replyCount": 2,
  "createdAt": "2026-02-14T10:30:00Z",
  "parentCommentId": null,
  "canDelete": false
}

Errors:
- 400 Bad Request - Invalid postId/commentId format
- 403 Forbidden - User not authenticated or not approved follower
- 404 Not Found - Comment or post not found
- 500 Internal Server Error
```

### 2. Get Comments by Post (Paginated)
```
GET /api/v1/posts/{postId}/comments?limit=20&cursor=...

Path Parameters:
- postId: String (MongoDB ObjectId)

Query Parameters:
- limit: int (1-50, default 20)
- cursor: string (optional)

Response: 200 OK
{
  "data": [
    {
      "id": "123...",
      "postId": "post123...",
      "authorUsername": "john_doe",
      "text": "Great post!",
      "tagged": [],
      "likeCount": 5,
      "replyCount": 2,
      "createdAt": "2026-02-14T10:30:00Z",
      "parentCommentId": null,
      "canDelete": true
    }
  ],
  "nextCursor": "abc123..." or null
}

Errors:
- 403 Forbidden - User not authenticated or not approved follower
- 404 Not Found - Post not found
- 500 Internal Server Error
```

### 3. Get Comment Replies (Paginated)
```
GET /api/v1/posts/{postId}/comments/{commentId}/replies?limit=20&cursor=...

Path Parameters:
- postId: String (MongoDB ObjectId)
- commentId: String (MongoDB ObjectId) - parent comment

Query Parameters:
- limit: int (1-50, default 20)
- cursor: string (optional)

Response: 200 OK
{
  "data": [
    {
      "id": "reply123...",
      "postId": "post123...",
      "authorUsername": "jane_doe",
      "text": "I agree!",
      "tagged": ["user456..."],
      "likeCount": 2,
      "replyCount": 0,
      "createdAt": "2026-02-14T11:00:00Z",
      "parentCommentId": "123...",
      "canDelete": true
    }
  ],
  "nextCursor": "def456..." or null
}

Errors:
- 403 Forbidden - User not authenticated or not approved follower
- 404 Not Found - Post or parent comment not found
- 500 Internal Server Error
```

### 4. Create Comment
```
POST /api/v1/posts/{postId}/comments

Auth: Required (JWT)

Path Parameters:
- postId: String (MongoDB ObjectId)

Request Body:
{
  "text": "Great post!",
  "parentCommentId": null,
  "tagged": ["userId1", "userId2"]
}

Response: 201 Created
{
  "id": "123...",
  "postId": "post123...",
  "authorUsername": "john_doe",
  "text": "Great post!",
  "tagged": ["userId1", "userId2"],
  "likeCount": 0,
  "replyCount": 0,
  "createdAt": "2026-02-14T10:30:00Z",
  "parentCommentId": null,
  "canDelete": true
}

Validation:
- text: required, 1-5000 characters
- parentCommentId: optional (for replies)
- tagged: optional list of user IDs

Access Control:
- User must be post author OR approved follower of post author
- If replying: parent comment must exist and not be deleted

Events Emitted:
- COMMENT_CREATED with payload
- Background job handles notifications to post author and mentioned users

Business Logic:
1. Validates text is not blank
2. Gets post (404 if not found)
3. Checks access (403 if denied)
4. If replying: verifies parent comment exists and is active
5. Creates CommentEntity with likeCount=0, replyCount=0, isDeleted=false
6. Saves to database
7. If reply: increments parent comment's replyCount
8. Emits COMMENT_CREATED event
9. Returns CommentResponse with canDelete=true

Errors:
- 400 Bad Request - Validation failed
- 401 Unauthorized - Not authenticated
- 403 Forbidden - Not authorized to comment
- 404 Not Found - Post or parent comment not found
- 500 Internal Server Error
```

### 5. Delete Comment
```
DELETE /api/v1/comments/{commentId}

Auth: Required (JWT)

Path Parameters:
- commentId: String (MongoDB ObjectId)

Response: 200 OK
{
  "message": "COMMENT_DELETED"
}

Authorization:
- Only comment author or post author can delete

Business Logic:
1. Gets comment (404 if not found)
2. Checks if already deleted (404 if deleted)
3. Gets associated post (404 if not found)
4. Checks authorization (403 if denied)
5. Soft deletes comment (isDeleted=true)
6. If reply: decrements parent comment's replyCount
7. Does NOT emit event

Errors:
- 401 Unauthorized - Not authenticated
- 403 Forbidden - Not authorized
- 404 Not Found - Comment or post not found
- 500 Internal Server Error
```

## Service Layer (CommentService)

### Read Operations

1. **getComment(commentId: String, viewerId: String?): CommentResponse**
   - Fetches single comment by ID
   - Verifies comment is not deleted
   - Checks post access (viewerId must be post author or approved follower)
   - Error handling: COMMENT_NOT_FOUND (404), FORBIDDEN (403)

2. **getCommentsByPost(postId: String, limit: int, cursor: String?, viewerId: String?): PaginatedCommentsResponse**
   - Fetches paginated top-level comments (parentCommentId is null)
   - Cursor-based pagination with limit (1-50, default 20)
   - Filters deleted comments (isDeleted=false)
   - Checks post access
   - Error handling: POST_NOT_FOUND (404), FORBIDDEN (403)

3. **getCommentsByReply(parentCommentId: String, postId: String, limit: int, cursor: String?, viewerId: String?): PaginatedCommentsResponse**
   - Fetches paginated replies to a comment
   - Verifies parent comment exists and is not deleted
   - Cursor-based pagination with limit
   - Filters deleted comments
   - Checks post access
   - Error handling: COMMENT_NOT_FOUND (404), POST_NOT_FOUND (404), FORBIDDEN (403)

### Write Operations

4. **createComment(postId: String, authorId: String, createRequest: CreateCommentRequest): CommentResponse** [@Transactional]
   - Validates text (required, not blank)
   - Gets post (404 if not found)
   - Checks access: User must be post author OR approved follower (403 if denied)
   - If replying: Verifies parent comment exists, is active, and belongs to same post
   - Creates CommentEntity with likeCount=0, replyCount=0, isDeleted=false
   - Saves to database
   - If reply: **Increments parent comment's replyCount**
   - **Emits COMMENT_CREATED event** with full payload
   - Returns CommentResponse with canDelete=true
   - Error handling: VALIDATION_ERROR (400), POST_NOT_FOUND (404), COMMENT_NOT_FOUND (404), FORBIDDEN (403)

5. **deleteComment(commentId: String, requesterId: String): void** [@Transactional]
   - Gets comment (404 if not found)
   - Checks if already deleted (404 if deleted)
   - Gets associated post (404 if not found)
   - Checks authorization: Must be comment author OR post author (403 if not)
   - Soft deletes: Sets isDeleted=true
   - If reply: **Decrements parent comment's replyCount**
   - **No event emitted**
   - Error handling: COMMENT_NOT_FOUND (404), POST_NOT_FOUND (404), FORBIDDEN (403)

### Helper Methods

6. **hasAccessToPost(postAuthorId: String, viewerId: String?): Boolean**
   - Returns false if viewerId is null (unauthenticated)
   - Returns true if viewerId == postAuthorId (own post)
   - Returns true if viewerId is approved follower
   - Otherwise returns false

7. **canCommentOnPost(postAuthorId: String, userId: String): Boolean**
   - Returns true if userId == postAuthorId (post author can always comment)
   - Returns true if userId is approved follower of postAuthorId
   - Otherwise returns false

8. **mapToResponse(comment: CommentEntity, post: PostEntity?, viewerId: String?): CommentResponse**
   - Fetches author's username from UserRepository
   - Sets canDelete = true only if viewerId is comment author OR post author
   - Returns CommentResponse with all fields

## Controller Layer (CommentController)

### Endpoints

| Method | Endpoint | Auth | Purpose |
|--------|----------|------|---------|
| GET | `/api/v1/posts/{postId}/comments/{commentId}` | Optional | Get single comment |
| GET | `/api/v1/posts/{postId}/comments` | Optional | Get post's top-level comments (paginated) |
| GET | `/api/v1/posts/{postId}/comments/{commentId}/replies` | Optional | Get comment replies (paginated) |
| POST | `/api/v1/posts/{postId}/comments` | Required | Create comment or reply |
| DELETE | `/api/v1/comments/{commentId}` | Required | Delete comment |

### Helper Methods

1. **getCurrentUserId(): String**
   - Extracts UserPrincipal from SecurityContextHolder
   - Throws UNAUTHORIZED (401) if not authenticated

2. **getOptionalUserId(): String?**
   - Returns userId if authenticated
   - Returns null if not authenticated

### Validation

- Path variables: postId, commentId (String type)
- Query parameters: limit (1-50, default 20), cursor (optional)
- Request body: @Valid on CreateCommentRequest
  - text: @NotBlank, @Size(1-5000)
  - parentCommentId: optional
  - tagged: optional

## DTOs

### Request DTOs

**CreateCommentRequest**:
```
- text: String (@NotBlank, @Size(1-5000))
- parentCommentId: String (optional)
- tagged: List<String> (optional)
```

### Response DTOs

**CommentResponse**:
```
- id: String
- postId: String
- authorUsername: String
- text: String
- tagged: List<String>
- likeCount: Long
- replyCount: Long
- createdAt: Instant
- parentCommentId: String (nullable)
- canDelete: Boolean
```

**PaginatedCommentsResponse**:
```
- data: List<CommentResponse>
- nextCursor: String (nullable)
```

**CommentActionResponse**:
```
- message: String ("COMMENT_DELETED")
```

## Comment Hierarchy

### Single-Level Nesting (Two-Level Comment Tree)

```
POST
├── Comment 1 (parentCommentId = null)
│   ├── Reply 1 (parentCommentId = Comment_1.id)
│   ├── Reply 2 (parentCommentId = Comment_1.id)
│   └── Reply 3 (parentCommentId = Comment_1.id)
├── Comment 2 (parentCommentId = null)
│   ├── Reply 1 (parentCommentId = Comment_2.id)
│   └── Reply 2 (parentCommentId = Comment_2.id)
└── Comment 3 (parentCommentId = null)
```

**Key Constraints**:
1. Only two levels: top-level comments and direct replies
2. Replies to replies are NOT supported
3. parentCommentId must belong to same post
4. Parent comment must not be deleted to create replies

## Access Control Summary

### Viewing Comments
- Unauthenticated users: ❌ FORBIDDEN (403)
- Authenticated, own post: ✅ ALLOWED
- Authenticated, approved follower: ✅ ALLOWED
- Authenticated, not follower: ❌ FORBIDDEN (403)

### Creating Comments
- Unauthenticated: ❌ UNAUTHORIZED (401)
- Authenticated, own post: ✅ ALLOWED
- Authenticated, approved follower: ✅ ALLOWED
- Authenticated, not follower: ❌ FORBIDDEN (403)

### Deleting Comments
- Unauthenticated: ❌ UNAUTHORIZED (401)
- Comment author: ✅ ALLOWED
- Post author: ✅ ALLOWED
- Other authenticated users: ❌ FORBIDDEN (403)

## Business Logic Details

### Comment Creation
1. Text validation (required, 1-5000 chars)
2. Post access check (author or approved follower)
3. For replies: parent comment validation (exists, active, same post)
4. Create with likeCount=0, replyCount=0, isDeleted=false
5. Increment parent replyCount if reply
6. Emit COMMENT_CREATED event

### Comment Deletion
1. Comment existence check (404 if not found)
2. Delete status check (404 if already deleted)
3. Post existence check (404 if not found)
4. Authorization check (comment author or post author)
5. Soft delete: Set isDeleted=true
6. Decrement parent replyCount if reply
7. No event emission

### Reply Count Management
- **Increment**: When a reply is created (parentCommentId != null)
- **Decrement**: When a reply is deleted (parentCommentId != null)
- Only applies to direct children (not recursive)

### Like Count Management
- **Not managed by service layer**
- Managed by background job via COMMENT_LIKED/COMMENT_UNLIKED events
- Only updated in response (fetched from database)

## Event Handling

### Events Emitted
- **COMMENT_CREATED**: When comment or reply created
  - Payload: {commentId, postId, authorId, text, parentCommentId, tagged, createdAt}
  - Responsibility: Background job sends notification to post author and tagged users

### Events NOT Emitted
- **COMMENT_DELETED**: No event (soft delete only, no cleanup needed)
- **COMMENT_LIKED/UNLIKED**: Not implemented in comment module

## Exception Handling Pattern

```
try {
  // Business logic
} catch (AuthException ex) {
  throw ex;  // Re-throw domain exceptions
} catch (Exception ex) {
  logger.error(...);
  throw new AuthException("...", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
}
```

## Pagination Strategy

### Cursor-Based Pagination
- Cursor is the document ID of the last item from previous page
- Query: `find by {postId, parentCommentId, id < cursor} order by {createdAt DESC}`
- Returns: List of CommentEntity + nextCursor (id of last item or null)
- Advantages: Handles insertions/deletions during iteration, efficient for large datasets

### Limits
- Default: 20 items per page
- Min: 1
- Max: 50
- Can be adjusted per request via `limit` query parameter

## Comparison: Comments vs Posts

| Feature | Posts | Comments |
|---------|-------|----------|
| Deletion | Hard delete | Soft delete |
| Like/Unlike | ✅ Yes | ❌ No (managed by BG job only) |
| Caption | ✅ Yes | ❌ No |
| Comments | N/A | ✅ Yes (this module) |
| Nesting | N/A | ✅ Yes (one level) |
| Tagging | ❌ No | ✅ Yes (tagged field) |
| Event on Delete | ✅ POST_DELETED | ❌ No event |
| Event on Create | ✅ POST_CREATED | ✅ COMMENT_CREATED |
| Parent Access Control | ❌ N/A | ✅ Via post access |

## Services Used from Other Modules

### PostRepository
- `findById(postId)` - Get post for access control and context

### FollowRepository
- `findByFollowerIdAndFollowingId()` - Check follow relationship for access control

### UserRepository
- `findById(userId).map(u -> u.getUsername())` - Get author username for response

### ProfileService
- Not directly used (could be used for future profile enrichment)

### EventService
- `emitEvent(EventType.COMMENT_CREATED, ...)` - Emit event for notifications

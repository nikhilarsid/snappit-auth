# POST MODULE DEEP DIVE ANALYSIS & STORY MODULE REPLICATION

## POST MODULE COMPREHENSIVE ANALYSIS

### 1. Data Models

#### PostEntity (MongoDB Collection: "posts")
- **Fields**:
  - `id` (ObjectId): Auto-generated primary key
  - `authorId` (ObjectId): Reference to post creator
  - `mediaUrl` (String): URL to image/video content
  - `caption` (String): Optional post description (max 500 chars)
  - `likeCount` (Long): Number of likes (managed by background jobs)
  - `commentCount` (Long): Number of comments (managed by comment service)
  - `createdAt` (Instant): Timestamp when post was created

- **Indexes**:
  - Compound: `{authorId: 1, createdAt: -1}` - Fetch user's posts in reverse chronological order
  - Compound: `{createdAt: -1}` - Fetch recent posts globally

- **Deletion**: Hard delete (immediate removal from database)

#### LikeEntity (MongoDB Collection: "likes")
- **Fields**:
  - `id` (ObjectId): Auto-generated primary key
  - `userId` (ObjectId): User who liked the post
  - `postId` (ObjectId): Post being liked
  - `createdAt` (Instant): When the like was created

- **Indexes**:
  - UNIQUE Compound: `{userId: 1, postId: 1}` - Prevent duplicate likes
  - Compound: `{postId: 1}` - Fetch all likes for a post

### 2. Repository Layer

#### PostRepository (extends MongoRepository<PostEntity, String>)
```
Methods:
- findByAuthorIdOrderByCreatedAtDesc(authorId, Pageable)
  → Returns all posts by author, sorted by createdAt descending
  → Used for paginated user post feeds

- findByAuthorIdAndIdLessThanOrderByCreatedAtDesc(authorId, cursorId, Pageable)
  → Cursor-based pagination implementation
  → Returns posts created BEFORE cursor timestamp
  → Cursor is the document ID of the last item from previous page
```

#### LikeRepository (extends MongoRepository<LikeEntity, String>)
```
Methods:
- findByUserIdAndPostId(userId, postId)
  → Checks if user has already liked a post
  → Returns Optional<LikeEntity>

- deleteByUserIdAndPostId(userId, postId)
  → Removes a like record

- countByPostId(postId)
  → Counts total likes for a post
```

### 3. Service Layer (PostService)

#### Core Methods

**READ OPERATIONS**:

1. `getPost(postId: String, viewerId: String?): PostResponse`
   - Fetches single post by ID
   - Access control: viewerId must be author OR approved follower
   - Error handling:
     - POST_NOT_FOUND (404) if post doesn't exist
     - FORBIDDEN (403) if no access to view

2. `getPostsByUsername(username: String, limit: int, cursor: String?, viewerId: String?): PaginatedPostsResponse`
   - Fetches paginated posts for a user
   - Cursor-based pagination (limit 1-50, default 20)
   - Access control: Same as getPost()
   - Returns: data (List<PostResponse>) + nextCursor

3. `getMyPost(postId: String, userId: String): PostResponse`
   - Authenticated user viewing their own post
   - Stricter auth: userId must equal author ID

4. `getMyPosts(userId: String, limit: int, cursor: String?): PaginatedPostsResponse`
   - All posts for authenticated user
   - Same pagination as getPostsByUsername()

**WRITE OPERATIONS**:

5. `createPost(authorId: String, createRequest: CreatePostRequest): PostResponse` [@Transactional]
   - Validates mediaUrl (required, not blank)
   - Creates PostEntity with likeCount=0, commentCount=0
   - Saves to database
   - **Emits EVENT**: POST_CREATED with {postId, authorId, mediaUrl, caption, createdAt}
   - Background job handles post_feed fanout to followers
   - Returns: 201 Created with PostResponse

6. `deletePost(postId: String, authorId: String): void` [@Transactional]
   - Hard deletes post from database
   - Ownership verification: postId's authorId must match requesting authorId
   - **Emits EVENT**: POST_DELETED with {postId, authorId, deletedAt}
   - Background job handles post_feed cleanup
   - No cascade delete (likes are orphaned)

7. `likePost(postId: String, userId: String): void` [@Transactional]
   - Creates LikeEntity record
   - Duplicate check: Throws ALREADY_LIKED (409) if exists
   - **Emits EVENT**: POST_LIKED with {postId, userId, authorId, likedAt}
   - Background job updates PostEntity.likeCount
   - Note: likeCount is NOT updated here, only by background job

8. `unlikePost(postId: String, userId: String): void` [@Transactional]
   - Deletes LikeEntity record
   - Existence check: Throws NOT_LIKED (409) if doesn't exist
   - **Emits EVENT**: POST_UNLIKED with {postId, userId, authorId, unlikedAt}
   - Background job updates PostEntity.likeCount

**HELPER METHODS**:

9. `hasAccessToPost(postAuthorId: String, viewerId: String?): Boolean`
   - Returns false if viewerId is null (unauthenticated)
   - Returns true if viewerId == postAuthorId (own post)
   - Returns true if viewerId is approved follower
   - Otherwise returns false

10. `mapToResponse(post: PostEntity, viewerId: String?): PostResponse`
    - Fetches author's username from UserRepository
    - Sets canDelete = true only if viewerId is author
    - Returns PostResponse with all fields

#### Exception Handling Pattern
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

### 4. Controller Layer (PostController)

#### Endpoints

| Method | Endpoint | Auth | Purpose |
|--------|----------|------|---------|
| GET | `/api/v1/posts/{postId}` | Optional | Get single post |
| GET | `/api/v1/posts/user/{username}` | Optional | Get user's posts (paginated) |
| POST | `/api/v1/posts` | Required | Create post |
| GET | `/api/v1/posts/me/{postId}` | Required | Get authenticated user's post |
| GET | `/api/v1/posts/me` | Required | Get authenticated user's posts (paginated) |
| DELETE | `/api/v1/posts/{postId}` | Required | Delete post |
| POST | `/api/v1/posts/{postId}/like` | Required | Like post |
| DELETE | `/api/v1/posts/{postId}/like` | Required | Unlike post |

#### Helper Methods

1. `getCurrentUserId(): String`
   - Extracts UserPrincipal from SecurityContextHolder
   - Throws UNAUTHORIZED (401) if not authenticated

2. `getOptionalUserId(): String?`
   - Returns userId if authenticated
   - Returns null if not authenticated

#### Validation

- Path variables:
  - `postId`: String (String type, validated by service layer)
  - `username`: @Pattern(regex="^[a-zA-Z0-9_.-]{3,30}$")

- Query parameters:
  - `limit`: @Min(1), @Max(50), default=20
  - `cursor`: Optional string

- Request body:
  - @Valid annotation triggers DTO validation
  - CreatePostRequest has @NotBlank mediaUrl, @Size(max=500) caption

### 5. DTOs

#### Request DTOs

**CreatePostRequest**:
```
- mediaUrl: String (@NotBlank)
- caption: String (@Size(max=500), optional)
```

#### Response DTOs

**PostResponse**:
```
- id: String
- authorUsername: String
- mediaUrl: String
- caption: String
- likeCount: Long
- commentCount: Long
- createdAt: Instant
- canDelete: Boolean
```

**PaginatedPostsResponse**:
```
- data: List<PostResponse>
- nextCursor: String? (null if no more pages)
```

**PostActionResponse**:
```
- message: String (e.g., "POST_DELETED", "POST_LIKED", "POST_UNLIKED")
```

### 6. Access Control Model

**Three layers of access**:

1. **Authentication Layer**: JWT via SecurityContextHolder
2. **Authorization Layer**: Follow relationship check
3. **Ownership Layer**: Self-access

**Rules**:
```
For viewing posts:
- If unauthenticated: FORBIDDEN
- If authId == postAuthorId: ALLOWED (self)
- If authId is approved follower of postAuthorId: ALLOWED
- Otherwise: FORBIDDEN

For modifying/interacting:
- Must be authenticated (UNAUTHORIZED if not)
- Must be post author for delete
- Post must exist for like/unlike
- Like/unlike checks for duplicate/missing likes
```

### 7. Event-Driven Architecture

**Events emitted**:
- POST_CREATED: When post is created
- POST_DELETED: When post is hard deleted
- POST_LIKED: When user likes a post
- POST_UNLIKED: When user unlikes a post

**Event payload structure**:
```json
{
  "postId": "...",
  "authorId": "...",
  "userId": "...",  // for like/unlike
  "mediaUrl": "...",
  "caption": "...",
  "createdAt": "...",
  "deletedAt": "...",  // for delete
  "likedAt/unlikedAt": "..."
}
```

**Background job responsibilities**:
- POST_CREATED: Fanout post to all followers' post_feed
- POST_DELETED: Remove post from all followers' post_feed
- POST_LIKED: Increment post.likeCount
- POST_UNLIKED: Decrement post.likeCount

---

## STORY MODULE REPLICATION

### Key Differences from Post Module

| Aspect | Post Module | Story Module |
|--------|-------------|--------------|
| Collection | "posts" | "stories" |
| Caption | ✅ Yes | ❌ No |
| Like/Unlike | ✅ Yes (2 endpoints) | ❌ No |
| Like/Comment Counts | ✅ Yes | ❌ No |
| Expiration | ❌ No | ✅ Yes (expiresAt + TTL) |
| Deletion | Hard delete | Soft delete (isDeleted flag) |
| Deletion timestamp | Only in event | Stored in deletedAt field |
| TTL Index | ❌ No | ✅ Yes on expiresAt |
| Events | POST_CREATED, POST_DELETED, POST_LIKED, POST_UNLIKED | STORY_CREATED, STORY_DELETED |

### StoryEntity Schema

```java
@Document(collection = "stories")
class StoryEntity {
  id: String                  // ObjectId
  authorId: String           // ObjectId (post author)
  mediaUrl: String           // Image/video URL
  createdAt: Instant         // Creation timestamp
  expiresAt: Instant         // Auto-expiration (TTL index)
  isDeleted: Boolean         // Soft delete flag
  deletedAt: Instant         // Manual delete timestamp
}
```

**Indexes**:
1. TTL: `{expiresAt: 1}` with `expireAfterSeconds: 0` - Auto-delete at exact timestamp
2. Compound: `{authorId: 1, createdAt: -1}` - Query user's stories

### Repository Methods

```java
interface StoryRepository {
  // Find active stories by author (filters isDeleted=false)
  findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(authorId, pageable)
  
  // Cursor-based pagination for active stories
  findByAuthorIdAndIsDeletedFalseAndIdLessThanOrderByCreatedAtDesc(authorId, cursorId, pageable)
}
```

### Service Methods (Same as Post, but adapted)

**Read Operations**:
- `getStory(storyId, viewerId)` - Get single story
- `getStoriesByUsername(username, limit, cursor, viewerId)` - Paginated list
- `getMyStory(storyId, userId)` - Auth user's story
- `getMyStories(userId, limit, cursor)` - Auth user's stories paginated

**Write Operations** (No like/unlike):
- `createStory(authorId, createRequest)` - Create story
  - Validates: mediaUrl (required), expiresAt (required, future)
  - Emits: STORY_CREATED event
  
- `deleteStory(storyId, authorId)` - Soft delete story
  - Sets: isDeleted=true, deletedAt=now()
  - Hard deletes NOT performed (soft delete instead)
  - Emits: STORY_DELETED event

**Helper Methods**: Same pattern as PostService
- `hasAccessToStory()` - Access control
- `mapToResponse()` - DTO conversion

### Controller Endpoints

| Method | Endpoint | Auth | Purpose |
|--------|----------|------|---------|
| GET | `/api/v1/stories/{storyId}` | Optional | Get single story |
| GET | `/api/v1/stories/user/{username}` | Optional | Get user's stories (paginated) |
| POST | `/api/v1/stories` | Required | Create story |
| GET | `/api/v1/stories/me/{storyId}` | Required | Get auth user's story |
| GET | `/api/v1/stories/me` | Required | Get auth user's stories (paginated) |
| DELETE | `/api/v1/stories/{storyId}` | Required | Delete story |

**Removed endpoints** (from post module):
- ❌ `/api/v1/stories/{storyId}/like` - No like functionality
- ❌ `/api/v1/stories/{storyId}/like` DELETE - No unlike functionality

### DTOs

**CreateStoryRequest**:
```
- mediaUrl: String (@NotBlank)
- expiresAt: Instant (@NotNull)  // NEW: expiration required
```

**StoryResponse**:
```
- id: String
- authorUsername: String
- mediaUrl: String
- createdAt: Instant
- expiresAt: Instant  // NEW field
- canDelete: Boolean

// REMOVED fields:
- caption: REMOVED
- likeCount: REMOVED
- commentCount: REMOVED
```

**PaginatedStoriesResponse**:
```
- data: List<StoryResponse>
- nextCursor: String?
```

**StoryActionResponse**:
```
- message: String ("STORY_DELETED")
```

### Access Control (Identical to Post Module)

Same authentication and authorization flow:
1. Optional auth for GET endpoints
2. Required auth for POST/DELETE
3. Follower-based access control
4. Self-access always allowed

### Event Emission (Different Event Types)

- **STORY_CREATED**: When story created
  - Payload: {storyId, authorId, mediaUrl, createdAt, expiresAt}
  - Background job: Fanout to followers' story_feed

- **STORY_DELETED**: When story soft-deleted
  - Payload: {storyId, authorId, deletedAt}
  - Background job: Remove from story_feed

### Business Logic Differences

**Post Module**:
- Hard delete immediately removes record
- Like count managed by background job
- Stories stay indefinitely
- Caption is part of post

**Story Module**:
- Soft delete sets isDeleted=true, stores deletedAt
- No like functionality
- TTL index auto-deletes at expiresAt
- No caption field (ephemeral content)
- Manual delete via soft flag (for recent access patterns)

### File Structure Created

```
story/
├── model/
│   └── StoryEntity.java           # Similar to PostEntity, no likes
├── repository/
│   └── StoryRepository.java       # Filters on isDeleted=false
├── service/
│   └── StoryService.java          # Same as PostService, no like/unlike
├── controller/
│   └── StoryController.java       # Same endpoints except like/unlike
├── dto/
│   ├── request/
│   │   └── CreateStoryRequest.java
│   └── response/
│       ├── StoryResponse.java
│       ├── PaginatedStoriesResponse.java
│       └── StoryActionResponse.java
├── API_CONTRACT.md                # API documentation
└── STORY_MODULE_DESIGN.md         # Design & architecture
```

### Summary of Replication

The Story module is an **exact architectural replica** of the Post module with these modifications:

1. **Schema changes**: Added expiresAt (TTL), isDeleted, deletedAt; removed caption, likeCount, commentCount
2. **Deletion strategy**: Soft delete instead of hard delete
3. **Like functionality**: Completely removed (no LikeEntity, no like/unlike endpoints)
4. **Event types**: STORY_CREATED and STORY_DELETED (instead of POST_* variants)
5. **Repository queries**: Filter on isDeleted=false for active stories
6. **Validation**: expiresAt must be future timestamp

**All other aspects remain identical**:
- Authentication & authorization model
- Pagination strategy (cursor-based)
- Transactional operations
- Error handling pattern
- Access control logic
- Response mapping
- Service layer structure
- Controller endpoint patterns

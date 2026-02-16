# Story Module - Deep Dive Analysis & API Design

## Database Collections

### Stories Collection
```
{
  _id: ObjectId,
  authorId: ObjectId,              // User who created the story
  mediaUrl: string,                 // URL to media (image/video)
  createdAt: ISODate,               // Creation timestamp
  expiresAt: ISODate,               // When story expires (TTL index)
  isDeleted: boolean,               // Soft delete flag
  deletedAt: ISODate                // When story was deleted
}

Indexes:
- { expiresAt: 1 } with expireAfterSeconds: 0  // TTL index - auto-delete expired stories
- { authorId: 1, createdAt: -1 }               // Fetch stories by user

NOTE: Soft delete (isDeleted flag). TTL index handles auto-deletion of expired stories.
Background job handles story_feed cleanup via STORY_DELETED event.
```

### Events Collection (shared)
```
{
  _id: ObjectId,
  type: "STORY_CREATED" | "STORY_DELETED",
  aggregateId: ObjectId,           // storyId
  payload: {
    userId: ObjectId,
    storyId: ObjectId,
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

### 1. Get Story by ID
```
GET /api/v1/stories/{storyId}

Path Parameters:
- storyId: String (MongoDB ObjectId)

Query Parameters:
- (none)

Response: 200 OK
{
  "id": "123...",
  "authorUsername": "author_username",
  "mediaUrl": "https://...",
  "createdAt": "2026-02-14T10:30:00Z",
  "expiresAt": "2026-02-15T10:30:00Z",
  "canDelete": false
}

Errors:
- 400 Bad Request - Invalid storyId format
- 403 Forbidden - Not authenticated or not an approved follower
- 404 Not Found - Story not found or deleted
- 500 Internal Server Error
```

### 2. Get Stories by Username
```
GET /api/v1/stories/user/{username}

Path Parameters:
- username: String (3-30 chars, pattern: ^[a-zA-Z0-9_.-]{3,30}$)

Query Parameters:
- limit: int (1-50, default 20)
- cursor: string (optional)

Response: 200 OK
{
  "data": [
    {
      "id": "123...",
      "authorUsername": "author_username",
      "mediaUrl": "https://...",
      "createdAt": "2026-02-14T10:30:00Z",
      "expiresAt": "2026-02-15T10:30:00Z",
      "canDelete": true
    }
  ],
  "nextCursor": "abc123..." or null
}

Errors:
- 400 Bad Request - Invalid username format
- 403 Forbidden - Not authenticated or not an approved follower
- 404 Not Found - User not found
- 500 Internal Server Error
```

### 3. Create Story
```
POST /api/v1/stories

Auth: Required (JWT)

Request Body:
{
  "mediaUrl": "https://cdn.example.com/image.jpg",
  "expiresAt": "2026-02-15T10:30:00Z"
}

Response: 201 Created
{
  "id": "123...",
  "authorUsername": "author_username",
  "mediaUrl": "https://...",
  "createdAt": "2026-02-14T10:30:00Z",
  "expiresAt": "2026-02-15T10:30:00Z",
  "canDelete": true
}

Validation:
- mediaUrl: required, must be valid URL format
- expiresAt: required, must be a future timestamp

Events Emitted:
- STORY_CREATED with payload: { storyId, authorId, mediaUrl, createdAt, expiresAt }
- Background job will write story to story_feed for all followers

Errors:
- 400 Bad Request - Validation failed
- 401 Unauthorized - Not authenticated
- 500 Internal Server Error
```

### 4. Delete Story
```
DELETE /api/v1/stories/{storyId}

Auth: Required (JWT)

Path Parameters:
- storyId: String (MongoDB ObjectId)

Response: 200 OK
{
  "message": "STORY_DELETED"
}

Business Logic:
- Only story author can delete
- Soft delete the story (mark isDeleted=true, set deletedAt)
- Emit STORY_DELETED event with deletedAt timestamp
- Background job will handle story_feed cleanup

Events Emitted:
- STORY_DELETED with payload: { storyId, authorId, deletedAt }

Errors:
- 400 Bad Request - Invalid storyId format
- 401 Unauthorized - Not authenticated
- 403 Forbidden - Not story author
- 404 Not Found - Story not found or already deleted
- 500 Internal Server Error
```

## Module Structure

```
story/
├── controller/
│   └── StoryController.java
├── service/
│   └── StoryService.java
├── model/
│   └── StoryEntity.java
├── repository/
│   └── StoryRepository.java
├── dto/
│   ├── request/
│   │   └── CreateStoryRequest.java
│   └── response/
│       ├── StoryResponse.java
│       ├── PaginatedStoriesResponse.java
│       └── StoryActionResponse.java
└── API_CONTRACT.md
```

## DTOs Required

### Request DTOs
1. **CreateStoryRequest**
   - `mediaUrl`: String (required, URL validation)
   - `expiresAt`: Instant (required, must be future timestamp)

### Response DTOs
1. **StoryResponse** (single story)
   - `id`: String
   - `authorUsername`: String
   - `mediaUrl`: String
   - `createdAt`: ISO 8601
   - `expiresAt`: ISO 8601
   - `canDelete`: Boolean

2. **PaginatedStoriesResponse**
   - `data`: List<StoryResponse>
   - `nextCursor`: String (nullable)

3. **StoryActionResponse**
   - `message`: String (e.g., "STORY_DELETED")

## Validation Rules

| Field | Constraint | Error Code |
|-------|-----------|-----------|
| storyId | Valid MongoDB ObjectId | `INVALID_STORY_ID` (400) |
| username | Pattern + length | `INVALID_USERNAME` (400) |
| mediaUrl | Required, URL format | `VALIDATION_ERROR` (400) |
| expiresAt | Required, future timestamp | `VALIDATION_ERROR` (400) |
| limit | 1-50 | `VALIDATION_ERROR` (400) |

## Error Codes & HTTP Status

| Error Code | Status | Scenario |
|-----------|--------|----------|
| `INVALID_STORY_ID` | 400 | Invalid ObjectId format |
| `INVALID_USERNAME` | 400 | Invalid username format |
| `VALIDATION_ERROR` | 400 | Field validation failed |
| `UNAUTHORIZED` | 401 | Not authenticated |
| `FORBIDDEN` | 403 | Not story author or not approved follower |
| `USER_NOT_FOUND` | 404 | User/author not found |
| `STORY_NOT_FOUND` | 404 | Story not found or deleted |

## Key Differences from Post Module

### Story Features vs Post Features

| Feature | Post | Story |
|---------|------|-------|
| Caption | ✅ Yes | ❌ No |
| Like/Unlike | ✅ Yes | ❌ No |
| Like Count | ✅ Yes | ❌ No |
| Comment Count | ✅ Yes | ❌ No |
| Expiration | ❌ No | ✅ Yes (TTL Index) |
| Soft Delete | ❌ No (Hard Delete) | ✅ Yes (isDeleted flag) |
| Deleted At | ❌ No | ✅ Yes (deletedAt) |

### Business Logic Similarities
- **Authentication & Access Control**: Same follower-based access model
- **Pagination**: Cursor-based pagination with limit (1-50)
- **Author Verification**: Only author can delete own story
- **Event Emission**: STORY_CREATED, STORY_DELETED events
- **Background Job Processing**: Event-driven architecture for story_feed fanout

### Technical Implementation Similarities
- Same exception handling pattern (AuthException with error codes)
- Same access control helper methods
- Same pagination query logic
- Transactional operations for event emission
- Same logging and error handling patterns
- Same response mapping and DTO conversion

## TTL Index Details

The MongoDB TTL (Time To Live) index automatically deletes documents when their specified datetime field exceeds the current time.

```javascript
db.stories.createIndex(
    { expiresAt: 1 },
    { expireAfterSeconds: 0 }
)
```

Configuration:
- `expireAfterSeconds: 0` means documents are deleted exactly when the `expiresAt` timestamp is reached
- MongoDB's background thread checks for expired documents every 60 seconds
- Deletion is not guaranteed to happen exactly at the expiration time, but within ~1 minute
- Soft delete flag is still used to prevent viewing deleted stories before TTL cleanup

## Access Control Summary

Stories follow the same access control model as posts:

1. **For getting a story**: 
   - Unauthenticated users: ❌ FORBIDDEN
   - Authenticated, own story: ✅ ALLOWED
   - Authenticated, approved follower: ✅ ALLOWED
   - Authenticated, not follower: ❌ FORBIDDEN

2. **For creating a story**: 
   - Unauthenticated: ❌ UNAUTHORIZED
   - Authenticated: ✅ ALLOWED

3. **For deleting a story**: 
   - Unauthenticated: ❌ UNAUTHORIZED
   - Authenticated, own story: ✅ ALLOWED
   - Authenticated, not own story: ❌ FORBIDDEN


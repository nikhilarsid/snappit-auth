# Story Module API Contract

## Overview
The story module enables users to create, retrieve, and manage ephemeral stories with automatic expiration. Stories are temporary content that expire after a specified time and support no likes/dislikes.

## Authentication & Access Control
- **JWT Authentication Filter**: All endpoints use the `JwtAuthenticationFilter` which extracts the token from cookies and populates the security context with `UserPrincipal`
- **Flexible Auth**: GET endpoints (retrieve stories) support optional authentication
  - If authenticated: Access control is enforced (must be story author or approved follower)
  - If not authenticated: Access is denied (403 FORBIDDEN)
- **Mandatory Auth**: POST/DELETE endpoints (create, delete) require authentication

## Follower Access Model
- Users can view their own stories (always allowed if authenticated as author)
- Users can view stories only if they are **approved followers** of the story author
- Unauthenticated users cannot view any stories (receive 403 FORBIDDEN)

---

## API Endpoints

### 1. Get Story by ID
**GET** `/api/v1/stories/{storyId}`

**Auth**: Optional (access control enforced)

**Description**: Retrieve a single story by ID

**Access Control**:
- ✅ Viewer is the story author
- ✅ Viewer is an approved follower of the story author
- ❌ Viewer is not authenticated
- ❌ Viewer is not an approved follower

**Path Parameters**:
- `storyId` (string, required): MongoDB ObjectId

**Response** (200 OK):
```json
{
  "id": "507f1f77bcf86cd799439011",
  "authorUsername": "john_doe",
  "mediaUrl": "https://example.com/image.jpg",
  "createdAt": "2026-02-14T10:30:00Z",
  "expiresAt": "2026-02-15T10:30:00Z",
  "canDelete": false
}
```

**Error Responses**:
| Error Code | Status | Message | Reason |
|---|---|---|---|
| STORY_NOT_FOUND | 404 | Story not found | Story ID doesn't exist or story is deleted |
| FORBIDDEN | 403 | You don't have access to view this story | User not authenticated or not an approved follower |
| INTERNAL_SERVER_ERROR | 500 | An error occurred while retrieving story | Unexpected server error |

---

### 2. Get Stories by Username (Paginated)
**GET** `/api/v1/stories/user/{username}?limit=20&cursor=...`

**Auth**: Optional (access control enforced)

**Description**: Retrieve paginated stories by a user (cursor-based pagination)

**Access Control**:
- ✅ Viewer is the story author (viewing own stories)
- ✅ Viewer is an approved follower of the author
- ❌ Viewer is not authenticated
- ❌ Viewer is not an approved follower

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
      "createdAt": "2026-02-14T10:30:00Z",
      "expiresAt": "2026-02-15T10:30:00Z",
      "canDelete": true
    },
    {
      "id": "507f1f77bcf86cd799439013",
      "authorUsername": "john_doe",
      "mediaUrl": "https://example.com/image2.jpg",
      "createdAt": "2026-02-14T09:30:00Z",
      "expiresAt": "2026-02-15T09:30:00Z",
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
| FORBIDDEN | 403 | You don't have access to view these stories | User not authenticated or not an approved follower |
| INTERNAL_SERVER_ERROR | 500 | An error occurred while retrieving stories | Unexpected server error |

---

### 3. Create Story
**POST** `/api/v1/stories`

**Auth**: Required (user must be authenticated)

**Description**: Create a new story that expires after a specified time

**Request Body**:
```json
{
  "mediaUrl": "https://example.com/image.jpg",
  "expiresAt": "2026-02-15T10:30:00Z"
}
```

**Request DTO Validation**:
- `mediaUrl` (string, required): Non-blank, max 2048 chars
- `expiresAt` (DateTime, required): Must be a future timestamp

**Response** (201 Created):
```json
{
  "id": "507f1f77bcf86cd799439011",
  "authorUsername": "john_doe",
  "mediaUrl": "https://example.com/image.jpg",
  "createdAt": "2026-02-14T10:30:00Z",
  "expiresAt": "2026-02-15T10:30:00Z",
  "canDelete": true
}
```

**Business Logic**:
1. Validates mediaUrl (required, not blank)
2. Validates expiresAt (required, must be in the future)
3. Creates StoryEntity (isDeleted=false)
4. Saves story to database
5. Emits STORY_CREATED event with payload: {storyId, authorId, mediaUrl, createdAt, expiresAt}
6. Background job processes event to fanout story to follower story_feed
7. TTL index automatically deletes story when expiresAt is reached

**Error Responses**:
| Error Code | Status | Message | Reason |
|---|---|---|---|
| UNAUTHORIZED | 401 | Authentication required | User not authenticated |
| VALIDATION_ERROR | 400 | Media URL is required | mediaUrl blank or missing |
| VALIDATION_ERROR | 400 | Expiration time cannot be null | expiresAt missing |
| VALIDATION_ERROR | 400 | Expiration time must be in the future | expiresAt is in the past |
| VALIDATION_ERROR | 400 | [Field]: [constraint violated] | DTO field validation failed |
| INTERNAL_SERVER_ERROR | 500 | An error occurred while creating story | Unexpected server error |

---

### 4. Delete Story
**DELETE** `/api/v1/stories/{storyId}`

**Auth**: Required (user must be story author)

**Description**: Delete a story (soft delete)

**Path Parameters**:
- `storyId` (string, required): MongoDB ObjectId

**Response** (200 OK):
```json
{
  "message": "STORY_DELETED"
}
```

**Business Logic**:
1. Verifies story exists (404 if not)
2. Verifies user is story author (403 if not)
3. Soft deletes story (marks isDeleted=true, sets deletedAt)
4. Emits STORY_DELETED event with payload: {storyId, authorId, deletedAt}
5. Background job processes event to remove story from story_feed and cleanup

**Error Responses**:
| Error Code | Status | Message | Reason |
|---|---|---|---|
| UNAUTHORIZED | 401 | Authentication required | User not authenticated |
| STORY_NOT_FOUND | 404 | Story not found | Story ID doesn't exist or already deleted |
| FORBIDDEN | 403 | You are not the author of this story | User is not story author |
| INTERNAL_SERVER_ERROR | 500 | An error occurred while deleting story | Unexpected server error |

---

## Data Models

### StoryEntity (MongoDB Collection: `stories`)
```json
{
  "_id": ObjectId,
  "authorId": ObjectId,
  "mediaUrl": string,
  "createdAt": ISODate,
  "expiresAt": ISODate,
  "isDeleted": boolean,
  "deletedAt": ISODate
}
```

**Indexes**:
- `{ expiresAt: 1 }` with `expireAfterSeconds: 0` - Auto-delete expired stories (TTL index)
- `{ authorId: 1, createdAt: -1 }` - Fetch stories by user with newest first

**Notes**:
- Soft delete (isDeleted flag) so stories can be marked as deleted
- TTL index automatically removes stories when expiresAt timestamp is reached
- No caption, likeCount, or commentCount fields (unlike posts)
- No like/unlike functionality (stories are ephemeral)


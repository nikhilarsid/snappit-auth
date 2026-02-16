# STORY Module - Complete Flow Documentation

## Overview
The Story Module provides ephemeral content management for temporary stories with automatic expiration. Users can create stories that expire after a specified time, with sophisticated access control (only authors and approved followers can view).

**Total Endpoints**: 4
**Authentication Required**: 2/4 endpoints (POST, DELETE)
**Pagination Support**: 1 endpoint (GET paginated)
**Key Feature**: Auto-expiration via TTL index

---

## Endpoint Summary

| # | Method | Endpoint | Auth | Purpose |
|---|--------|----------|------|---------|
| 1 | GET | `/api/v1/stories/{storyId}` | Optional | Retrieve single story (with access control) |
| 2 | GET | `/api/v1/stories/user/{username}` | Optional | Retrieve paginated stories (with access control) |
| 3 | POST | `/api/v1/stories` | Required | Create new story |
| 4 | DELETE | `/api/v1/stories/{storyId}` | Required | Delete story (author only, soft delete) |

---

## ENDPOINT 1: Get Single Story

### GET /api/v1/stories/{storyId}

**Happy Path Flow:**
1. User (authenticated as alice) requests single story by ID
2. System verifies story exists and hasn't been soft-deleted
3. System performs access control check:
   - If user is story author → Allow
   - If user is approved follower of author → Allow
   - Otherwise → Deny
4. Story object returned with metadata (author, expiration, timestamp)

**Response (200 OK):**
```json
{
  "id": "507f1f77bcf86cd799439011",
  "authorUsername": "alice",
  "mediaUrl": "https://example.com/story.jpg",
  "createdAt": "2026-02-14T10:30:00Z",
  "expiresAt": "2026-02-15T10:30:00Z",
  "canDelete": false
}
```

**Key Fields:**
- `id`: Unique story identifier
- `authorUsername`: Story creator's username
- `mediaUrl`: URL to story media
- `createdAt`: Story creation timestamp
- `expiresAt`: Story expiration timestamp (auto-deleted by TTL index)
- `canDelete`: Whether current user can delete (author only)

### Error Flows: Get Single Story

**1. Story Not Found (404)**
- User requests story with invalid/non-existent ID
- System cannot find story in database (or story is deleted)
- Returns 404 STORY_NOT_FOUND

**2. Access Denied - Not Authenticated (403)**
- Anonymous user (no auth token) requests story
- System requires authentication for access control
- Returns 403 FORBIDDEN "You don't have access to view this story"

**3. Access Denied - Not Approved Follower (403)**
- User is authenticated but NOT an approved follower of author
- System enforces access control rule
- Returns 403 FORBIDDEN "You don't have access to view this story"

**4. Internal Server Error (500)**
- Unexpected error during retrieval
- Returns 500 INTERNAL_SERVER_ERROR

---

## ENDPOINT 2: Get User's Stories (Paginated)

### GET /api/v1/stories/user/{username}?limit=20&cursor=...

**Happy Path Flow:**
1. User requests paginated stories for specific user
2. System validates username format (3-30 chars, alphanumeric + _-.)
3. System verifies target user exists
4. System performs access control check:
   - If requester is the target user → Allow all non-deleted stories
   - If requester is approved follower → Allow all non-deleted stories
   - Otherwise → Deny
5. System fetches stories in reverse chronological order (newest first)
6. System returns paginated response with nextCursor for subsequent requests

**Query Parameters:**
- `limit`: 1-50 (default 20) - Stories per page
- `cursor`: Optional - Opaque token for pagination

**Response (200 OK):**
```json
{
  "data": [
    {
      "id": "507f1f77bcf86cd799439011",
      "authorUsername": "alice",
      "mediaUrl": "https://example.com/story1.jpg",
      "createdAt": "2026-02-15T10:30:00Z",
      "expiresAt": "2026-02-16T10:30:00Z",
      "canDelete": true
    },
    {
      "id": "507f1f77bcf86cd799439012",
      "authorUsername": "alice",
      "mediaUrl": "https://example.com/story2.jpg",
      "createdAt": "2026-02-14T09:15:00Z",
      "expiresAt": "2026-02-15T09:15:00Z",
      "canDelete": true
    }
  ],
  "nextCursor": "507f1f77bcf86cd799439012"
}
```

### Error Flows: Get User's Stories

**1. Invalid Username Format (400)**
- Username doesn't match pattern (3-30 chars, alphanumeric + _-.)
- Returns 400 VALIDATION_ERROR

**2. User Not Found (404)**
- Target username doesn't exist in system
- Returns 404 USER_NOT_FOUND

**3. Access Denied - Not Authenticated (403)**
- Anonymous user (no auth token) requests stories
- System requires authentication to check approved follower status
- Returns 403 FORBIDDEN "You don't have access to view these stories"

**4. Access Denied - Not Approved Follower (403)**
- User is authenticated but NOT an approved follower of target user
- System enforces access control rule
- Returns 403 FORBIDDEN "You don't have access to view these stories"

**5. Invalid Limit (400)**
- Limit parameter outside valid range (1-50)
- Returns 400 VALIDATION_ERROR

**6. Internal Server Error (500)**
- Unexpected error during retrieval
- Returns 500 INTERNAL_SERVER_ERROR

---

## ENDPOINT 3: Create Story

### POST /api/v1/stories

**Happy Path Flow:**
1. Authenticated user submits request with mediaUrl and expiresAt
2. System validates DTO:
   - mediaUrl: Required, non-blank, max 2048 chars
   - expiresAt: Required, must be future timestamp
3. System creates StoryEntity in database
   - Sets author to authenticated user
   - Sets isDeleted = false (for soft delete tracking)
   - Sets createdAt = current timestamp
4. System emits STORY_CREATED event
5. Background job fanouts story to all approved followers' story_feed
6. TTL index will auto-delete story when expiresAt time is reached
7. New story returned with 201 Created response

**Request Body:**
```json
{
  "mediaUrl": "https://example.com/story.jpg",
  "expiresAt": "2026-02-15T10:30:00Z"
}
```

**Response (201 Created):**
```json
{
  "id": "507f1f77bcf86cd799439013",
  "authorUsername": "alice",
  "mediaUrl": "https://example.com/story.jpg",
  "createdAt": "2026-02-14T12:45:30Z",
  "expiresAt": "2026-02-15T10:30:00Z",
  "canDelete": true
}
```

### Error Flows: Create Story

**1. Missing mediaUrl (400)**
- mediaUrl field is blank or missing
- Returns 400 VALIDATION_ERROR "Media URL is required"

**2. mediaUrl Exceeds Max Length (400)**
- mediaUrl longer than 2048 characters
- Returns 400 VALIDATION_ERROR

**3. Missing expiresAt (400)**
- expiresAt field is null or missing
- Returns 400 VALIDATION_ERROR "Expiration time cannot be null"

**4. expiresAt in the Past (400)**
- expiresAt is before current timestamp
- Returns 400 VALIDATION_ERROR "Expiration time must be in the future"

**5. Not Authenticated (401)**
- No JWT token in cookie or token expired
- Returns 401 UNAUTHORIZED "Authentication required"

**6. Invalid DTO Format (400)**
- JSON body format invalid or other DTO field validation fails
- Returns 400 VALIDATION_ERROR

**7. Internal Server Error (500)**
- Unexpected error during story creation
- Returns 500 INTERNAL_SERVER_ERROR

---

## ENDPOINT 4: Delete Story

### DELETE /api/v1/stories/{storyId}

**Happy Path Flow:**
1. Authenticated user requests to delete their story
2. System verifies story exists and not already soft-deleted
3. System verifies user is the story author
4. System soft deletes story (marks isDeleted=true, sets deletedAt)
5. System emits STORY_DELETED event
6. Background job removes story from all story_feed entries and cleans up
7. Success message returned

**Response (200 OK):**
```json
{
  "message": "STORY_DELETED"
}
```

### Error Flows: Delete Story

**1. Story Not Found (404)**
- Story ID doesn't exist or story is already deleted
- System cannot find story to delete
- Returns 404 STORY_NOT_FOUND

**2. Not Authenticated (401)**
- No JWT token in cookie or token expired
- Returns 401 UNAUTHORIZED "Authentication required"

**3. Not Story Author (403)**
- User is authenticated but NOT the story author
- System enforces authorization rule
- Returns 403 FORBIDDEN "You are not the author of this story"

**4. Internal Server Error (500)**
- Unexpected error during deletion
- Returns 500 INTERNAL_SERVER_ERROR

---

## Complete Flow Scenarios

### Scenario 1: Create Story, View, Wait for Expiration
```
1. alice creates story with expiresAt=tomorrow → 201 CREATED
2. alice views own story → 200 OK (canDelete: true)
3. bob (approved follower of alice) views alice's story → 200 OK
4. After expiresAt timestamp → Story auto-deleted by TTL index
5. Subsequently, anyone trying to view → 404 STORY_NOT_FOUND
```

### Scenario 2: Access Control Enforcement
```
1. alice creates story
2. charlie (NOT following alice) tries to view story:
   - GET /api/v1/stories/{storyId} → 403 FORBIDDEN
3. charlie follows alice and is approved
4. charlie tries to view story again:
   - GET /api/v1/stories/{storyId} → 200 OK
5. charlie tries to delete alice's story:
   - DELETE /api/v1/stories/{storyId} → 403 FORBIDDEN (not author)
```

### Scenario 3: Pagination Workflow
```
1. alice has 100 stories
2. bob (approved follower) requests first page:
   - GET /api/v1/stories/user/alice?limit=20 → 200 OK with 20 stories + nextCursor
3. bob requests second page:
   - GET /api/v1/stories/user/alice?limit=20&cursor=<nextCursor> → 200 OK with next 20 stories
4. bob continues paginating until nextCursor is null (all stories retrieved)
```

### Scenario 4: Story Lifecycle
```
Create Phase:
- User creates story with 24-hour expiration → 201 CREATED
- Story appears in feed, followers can view

Active Phase:
- Story remains accessible for 24 hours
- Any approved follower can view
- Author can delete anytime (soft delete)

Expiration Phase:
- After 24 hours, TTL index auto-deletes from database
- Story becomes inaccessible (404 NOT_FOUND)
- No manual action required
```

---

## Access Control Rules Summary

| Scenario | GET Single | GET User's | POST Create | DELETE |
|----------|---|---|---|---|
| Story author (authenticated) | ✅ Allow | ✅ Allow | N/A | ✅ Allow |
| Approved follower (authenticated) | ✅ Allow | ✅ Allow | N/A | ❌ Reject |
| Not follower (authenticated) | ❌ Reject | ❌ Reject | N/A | ❌ Reject |
| Not authenticated | ❌ Reject | ❌ Reject | ❌ Reject | ❌ Reject |

---

## Data Validation Rules

### mediaUrl
- Required: YES
- Max Length: 2048 chars
- Must be non-blank
- Validation: VALIDATION_ERROR (400) if invalid

### expiresAt
- Required: YES
- Format: ISO 8601 DateTime
- Must be in future
- Must be after createdAt
- Validation: VALIDATION_ERROR (400) if invalid
- Examples: "2026-02-15T10:30:00Z", "2026-02-16T14:45:30Z"

### username (path parameter)
- Required: YES
- Pattern: `^[a-zA-Z0-9_.-]{3,30}$`
- Length: 3-30 chars
- Validation: VALIDATION_ERROR (400) if format invalid

### storyId (path parameter)
- Required: YES
- Format: MongoDB ObjectId (24 hex characters)
- Validation: STORY_NOT_FOUND (404) if doesn't exist

### limit (query parameter)
- Range: 1-50
- Default: 20
- Validation: VALIDATION_ERROR (400) if out of range

---

## Response Status Codes Summary

| Status | Endpoint(s) Using | Meaning |
|--------|----------|---------|
| 200 OK | All GET, DELETE | Successful operation |
| 201 Created | POST /api/v1/stories | Story successfully created |
| 400 Bad Request | GET user stories, POST create | Validation error (format, range, past date) |
| 401 Unauthorized | POST, DELETE | Not authenticated |
| 403 Forbidden | GET endpoints, DELETE | Access control denied |
| 404 Not Found | GET, DELETE | Story/user not found |
| 500 Internal Server Error | All endpoints | Unexpected server error |

---

## Testing Strategy

**Total Test Cases**: 31 (estimated)
- Happy Path: 10 cases
  - Create story: 2 cases (with/without exact expiration)
  - Get single story: 2 cases (as author, as approved follower)
  - Get user stories: 2 cases (with/without pagination)
  - Delete story: 1 case
  - Access control verification: 2 cases
  - Expiration validation: 1 case

- Error Cases: 21 cases
  - Create story: 5 error flows (missing fields, past expiration, oversized URL)
  - Get single story: 4 error flows (not found, not authenticated, not approved)
  - Get user stories: 6 error flows (invalid username, user not found, access denied)
  - Delete story: 3 error flows (not found, not authenticated, not author)
  - Expiration constraints: 3 error flows

**Test Users**:
- `if.kshitij` (User 1) - Story creator
- `hhoehunterr` (User 2) - Approved follower (from Follow module tests)
- Both have password: `Dettcmpw123?`

**Test Data**:
- Valid story with current + 1 day expiration
- Invalid stories (missing mediaUrl, past expiresAt)
- Pagination with cursor
- Access control verification

---

## Implementation Notes

1. **Access Control**: Enforced at application level (not database level)
2. **Soft Delete**: Stories marked isDeleted=true (not hard deleted)
3. **TTL Index**: Auto-deletes stories when expiresAt timestamp is reached
4. **No Likes**: Stories don't have like functionality (ephemeral content)
5. **No Comments**: Stories don't support comments (design choice)
6. **Atomicity**: Database update + event emission are atomic
7. **Background Jobs**: STORY_CREATED fanouts to story_feed, STORY_DELETED removes from feeds

---

## Differences from Post Module

| Feature | Post | Story |
|---------|------|-------|
| Expiration | No (permanent) | Yes (TTL auto-delete) |
| Likes | Yes (like/unlike) | No |
| Comments | Yes | No |
| Delete Type | Hard delete | Soft delete |
| Deletion | Manual only | Manual or auto (TTL) |
| Visibility | Depends on follower status | Depends on follower status |
| Use Case | Permanent content | Ephemeral content (24h) |


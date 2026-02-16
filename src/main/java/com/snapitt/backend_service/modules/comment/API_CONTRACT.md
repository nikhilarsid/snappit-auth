# Comment Module API Contract

## Overview
The comment module enables users to create, retrieve, and manage comments on posts. Comments support nested replies and user mentions/tagging.

## Authentication & Access Control
- **JWT Authentication Filter**: All endpoints use the `JwtAuthenticationFilter` which extracts the token from cookies and populates the security context with `UserPrincipal`
- **Flexible Auth**: GET endpoints (retrieve comments) support optional authentication
  - If authenticated: Access control is enforced (must have access to the post)
  - If not authenticated: Access is denied (403 FORBIDDEN)
- **Mandatory Auth**: POST/DELETE endpoints (create, delete) require authentication

## Access Control Model
- **Viewing comments**: User must have access to the post (post author or approved follower)
- **Creating comments**: User must have access to post author (post author or approved follower)
- **Deleting comments**: Only comment author or post author can delete

### Commenting Rules
- Users can comment on their own posts
- Users can comment on posts from authors they follow (approved status)
- Users can reply to comments in accessible posts
- Parent comment must be active (not deleted) to reply

---

## API Endpoints

### 1. Get Comment by ID
**GET** `/api/v1/posts/{postId}/comments/{commentId}`

**Auth**: Optional (access control enforced)

**Description**: Retrieve a single comment by ID

**Path Parameters**:
- `postId` (string, required): Post ID
- `commentId` (string, required): Comment ID

**Response** (200 OK):
```json
{
  "id": "507f1f77bcf86cd799439011",
  "postId": "507f1f77bcf86cd799439010",
  "authorUsername": "john_doe",
  "text": "Great post!",
  "tagged": [],
  "likeCount": 5,
  "replyCount": 2,
  "createdAt": "2026-02-14T10:30:00Z",
  "parentCommentId": null,
  "canDelete": false
}
```

**Error Responses**:
| Error Code | Status | Message | Reason |
|---|---|---|---|
| COMMENT_NOT_FOUND | 404 | Comment not found | Comment ID doesn't exist or is deleted |
| POST_NOT_FOUND | 404 | Post not found | Post ID doesn't exist |
| FORBIDDEN | 403 | You don't have access to view this comment | User not authenticated or not an approved follower |
| INTERNAL_SERVER_ERROR | 500 | An error occurred while retrieving comment | Unexpected server error |

---

### 2. Get Comments by Post (Paginated)
**GET** `/api/v1/posts/{postId}/comments?limit=20&cursor=...`

**Auth**: Optional (access control enforced)

**Description**: Retrieve paginated top-level comments for a post (cursor-based pagination)

**Path Parameters**:
- `postId` (string, required): Post ID

**Query Parameters**:
- `limit` (integer, optional, default=20): Items per page (1-50)
- `cursor` (string, optional): Opaque cursor for pagination (use `nextCursor` from previous response)

**Response** (200 OK):
```json
{
  "data": [
    {
      "id": "507f1f77bcf86cd799439011",
      "postId": "507f1f77bcf86cd799439010",
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
  "nextCursor": "507f1f77bcf86cd799439011"
}
```

**Error Responses**:
| Error Code | Status | Message | Reason |
|---|---|---|---|
| POST_NOT_FOUND | 404 | Post not found | Post ID doesn't exist |
| FORBIDDEN | 403 | You don't have access to view these comments | User not authenticated or not an approved follower |
| INTERNAL_SERVER_ERROR | 500 | An error occurred while retrieving comments | Unexpected server error |

---

### 3. Get Comment Replies (Paginated)
**GET** `/api/v1/posts/{postId}/comments/{commentId}/replies?limit=20&cursor=...`

**Auth**: Optional (access control enforced)

**Description**: Retrieve paginated replies to a comment (cursor-based pagination)

**Path Parameters**:
- `postId` (string, required): Post ID
- `commentId` (string, required): Parent comment ID

**Query Parameters**:
- `limit` (integer, optional, default=20): Items per page (1-50)
- `cursor` (string, optional): Opaque cursor for pagination

**Response** (200 OK):
```json
{
  "data": [
    {
      "id": "507f1f77bcf86cd799439012",
      "postId": "507f1f77bcf86cd799439010",
      "authorUsername": "jane_doe",
      "text": "I agree!",
      "tagged": ["507f1f77bcf86cd799439011"],
      "likeCount": 2,
      "replyCount": 0,
      "createdAt": "2026-02-14T11:00:00Z",
      "parentCommentId": "507f1f77bcf86cd799439011",
      "canDelete": true
    }
  ],
  "nextCursor": "507f1f77bcf86cd799439012"
}
```

**Error Responses**:
| Error Code | Status | Message | Reason |
|---|---|---|---|
| COMMENT_NOT_FOUND | 404 | Comment not found | Parent comment doesn't exist or is deleted |
| POST_NOT_FOUND | 404 | Post not found | Post ID doesn't exist |
| FORBIDDEN | 403 | You don't have access to view these comments | User not authenticated or not an approved follower |
| INTERNAL_SERVER_ERROR | 500 | An error occurred while retrieving replies | Unexpected server error |

---

### 4. Create Comment
**POST** `/api/v1/posts/{postId}/comments`

**Auth**: Required (user must be authenticated)

**Description**: Create a new comment or reply on a post

**Path Parameters**:
- `postId` (string, required): Post to comment on

**Request Body**:
```json
{
  "text": "Great post! Love the content.",
  "parentCommentId": null,
  "tagged": ["507f1f77bcf86cd7994390aa", "507f1f77bcf86cd7994390bb"]
}
```

**Request DTO Validation**:
- `text` (string, required): 1-5000 characters
- `parentCommentId` (string, optional): For replies, must be a valid comment ID
- `tagged` (array, optional): List of user IDs to mention

**Response** (201 Created):
```json
{
  "id": "507f1f77bcf86cd799439013",
  "postId": "507f1f77bcf86cd799439010",
  "authorUsername": "john_doe",
  "text": "Great post! Love the content.",
  "tagged": ["507f1f77bcf86cd7994390aa", "507f1f77bcf86cd7994390bb"],
  "likeCount": 0,
  "replyCount": 0,
  "createdAt": "2026-02-14T12:00:00Z",
  "parentCommentId": null,
  "canDelete": true
}
```

**Business Logic**:
1. Validates text (required, 1-5000 chars)
2. Verifies post exists (404 if not)
3. Checks access: User must be post author OR approved follower (403 if not)
4. If replying: Verifies parent comment exists and is active
5. Creates CommentEntity with likeCount=0, replyCount=0
6. Saves comment to database
7. If reply: Increments parent comment's replyCount
8. Emits COMMENT_CREATED event with payload: {commentId, postId, authorId, text, parentCommentId, tagged, createdAt}
9. Background job processes event to send notifications to post author and mentioned users

**Error Responses**:
| Error Code | Status | Message | Reason |
|---|---|---|---|
| UNAUTHORIZED | 401 | Authentication required | User not authenticated |
| POST_NOT_FOUND | 404 | Post not found | Post ID doesn't exist |
| COMMENT_NOT_FOUND | 404 | Parent comment not found | Parent comment doesn't exist or is deleted |
| FORBIDDEN | 403 | You are not authorized to comment on this post | User not post author and not approved follower |
| VALIDATION_ERROR | 400 | Comment text cannot be blank | text is empty |
| VALIDATION_ERROR | 400 | Comment text must be between 1 and 5000 characters | text exceeds limits |
| VALIDATION_ERROR | 400 | Parent comment does not belong to this post | parentCommentId from different post |
| INTERNAL_SERVER_ERROR | 500 | An error occurred while creating comment | Unexpected server error |

---

### 5. Delete Comment
**DELETE** `/api/v1/comments/{commentId}`

**Auth**: Required (user must be comment author or post author)

**Description**: Delete a comment (soft delete)

**Path Parameters**:
- `commentId` (string, required): Comment ID to delete

**Response** (200 OK):
```json
{
  "message": "COMMENT_DELETED"
}
```

**Business Logic**:
1. Verifies comment exists (404 if not)
2. Verifies comment is not already deleted
3. Gets associated post
4. Checks authorization: Must be comment author OR post author (403 if not)
5. Soft deletes comment (marks isDeleted=true)
6. If comment is a reply: Decrements parent comment's replyCount
7. No event emitted (as per requirements)

**Error Responses**:
| Error Code | Status | Message | Reason |
|---|---|---|---|
| UNAUTHORIZED | 401 | Authentication required | User not authenticated |
| COMMENT_NOT_FOUND | 404 | Comment not found | Comment ID doesn't exist or already deleted |
| POST_NOT_FOUND | 404 | Post not found | Associated post not found |
| FORBIDDEN | 403 | You are not authorized to delete this comment | User is neither comment author nor post author |
| INTERNAL_SERVER_ERROR | 500 | An error occurred while deleting comment | Unexpected server error |

---

## Data Models

### CommentEntity (MongoDB Collection: `comments`)
```json
{
  "_id": ObjectId,
  "postId": ObjectId,
  "authorId": ObjectId,
  "parentCommentId": ObjectId | null,
  "text": string,
  "tagged": [ObjectId],
  "likeCount": number,
  "replyCount": number,
  "createdAt": ISODate,
  "isDeleted": boolean
}
```

**Indexes**:
- `{ postId: 1, parentCommentId: 1, createdAt: -1 }` - Fetch top-level comments and replies for a post
- `{ parentCommentId: 1, createdAt: -1 }` - Fetch replies to a specific comment

**Notes**:
- Soft delete (isDeleted flag)
- likeCount managed by background job (not updated by create/delete endpoints)
- replyCount incremented/decremented when replies are created/deleted
- parentCommentId is null for top-level comments
- Comments inherit post access control (must have access to post to view/create comments)

---

## Comment Structure

### Top-Level Comment
A comment directly on a post (parentCommentId is null)
```
POST
├── COMMENT_1 (top-level)
├── COMMENT_2 (top-level)
└── ...
```

### Nested Reply
A comment replying to another comment (parentCommentId is set)
```
POST
└── COMMENT_1 (top-level)
    ├── REPLY_1 (parentCommentId = COMMENT_1.id)
    ├── REPLY_2 (parentCommentId = COMMENT_1.id)
    └── ...
```

### Key Rules
1. Only one level of nesting is supported (replies to replies are NOT supported)
2. parentCommentId must belong to same post
3. Parent comment must not be deleted to create replies
4. Deleting a comment does NOT delete its replies (soft delete)

---

## Validation Rules

| Field | Constraint | Error Code |
|-------|-----------|-----------|
| text | Required, 1-5000 chars | `VALIDATION_ERROR` (400) |
| parentCommentId | Optional, valid comment ID | `COMMENT_NOT_FOUND` (404) |
| tagged | Optional, array of user IDs | (No validation) |

## Error Codes & HTTP Status

| Error Code | Status | Scenario |
|-----------|--------|----------|
| `UNAUTHORIZED` | 401 | Not authenticated (for POST/DELETE) |
| `FORBIDDEN` | 403 | Not authorized (access or deletion) |
| `COMMENT_NOT_FOUND` | 404 | Comment not found or deleted |
| `POST_NOT_FOUND` | 404 | Post not found |
| `VALIDATION_ERROR` | 400 | Field validation failed |
| `INTERNAL_SERVER_ERROR` | 500 | Unexpected server error |


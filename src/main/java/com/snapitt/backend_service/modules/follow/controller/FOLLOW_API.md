# Follow Service API Contract

This document describes the Follow module HTTP API exposed by the FollowController.

Base path: `/api/v1/follow`

Authentication: All endpoints require an authenticated user. Send a valid JWT in `Authorization: Bearer <token>` header. The server identifies the caller from the token and uses that user ID as the acting principal.

Common response envelope: endpoints return plain JSON objects (no wrapping metadata unless documented per endpoint). Error responses follow the global error mapping (domain error -> HTTP status and `{ "error": "CODE", "message": "human message" }`).

---

## Endpoints

### 1) Create follow request
- Method: POST
- URL: `/api/v1/follow/{username}`
- Path parameters:
  - `username` (string, required) — target user's username (1-64 chars)
- Request body: none
- Auth: required (caller becomes follower)
- Success: 200 OK
  - Body: `{ "message": "FOLLOW_REQUEST_SENT" }`
- Errors:
  - 400 Bad Request — invalid `username` format
  - 401 Unauthorized — missing/invalid token
  - 409 Conflict — already following / request already exists (optional server mapping)
  - 500 Internal Server Error — unexpected

### 2) Approve follow request
- Method: POST
- URL: `/api/v1/follow/{username}/approve`
- Path parameters:
  - `username` (string, required) — username of requester who asked to follow the authenticated user
- Request body: none
- Auth: required (caller must be target user who received the request)
- Success: 200 OK
  - Body: `{ "message": "FOLLOW_APPROVED" }`
- Errors:
  - 400 Bad Request — invalid input
  - 401 Unauthorized — caller not authenticated
  - 403 Forbidden — caller not authorized to approve (not the target user)
  - 404 Not Found — request not found
  - 500 Internal Server Error

### 3) Reject follow request
- Method: POST
- URL: `/api/v1/follow/{username}/reject`
- Path parameters:
  - `username` (string, required) — username of requester
- Request body: none
- Auth: required
- Success: 200 OK
  - Body: `{ "message": "FOLLOW_REJECTED" }`
- Errors: same as Approve endpoint

### 4) Unfollow (remove a follower relationship)
- Method: DELETE
- URL: `/api/v1/follow/{username}`
- Path parameters:
  - `username` (string, required) — username to unfollow
- Request body: none
- Auth: required (caller is the follower)
- Success: 200 OK
  - Body: `{ "message": "UNFOLLOWED" }`
- Errors:
  - 400 Bad Request — invalid username
  - 401 Unauthorized — missing/invalid token
  - 404 Not Found — follow relationship not present
  - 500 Internal Server Error

### 5) Get followers (paginated)
- Method: GET
- URL: `/api/v1/follow/{username}/followers`
- Path parameters:
  - `username` (string, required) — username whose followers to list
- Query parameters:
  - `limit` (int, optional, default=20) — items per page (min 1, max 50)
  - `cursor` (string, optional) — continuation cursor (opaque)
- Auth: required (caller may be used to indicate viewer context; results may include `isFollowingBack` flags)
- Success: 200 OK
  - Body example:

```json
{
  "data": [
    {
      "id": "u1",
      "username": "alice",
      "displayName": "Alice",
      "avatarUrl": "https://...",
      "followStatus": "APPROVED", // enum: PENDING, APPROVED, REJECTED
      "isFollowingBack": true
    }
  ],
  "nextCursor": null
}
```

  - Notes:
    - `data` is an array of follower DTOs (empty array when none).
    - `nextCursor` is `null` when there are no further pages.

- Errors:
  - 400 Bad Request — invalid query params
  - 401 Unauthorized — missing/invalid token
  - 404 Not Found — target user not found
  - 500 Internal Server Error

### 6) Get following (paginated)
- Method: GET
- URL: `/api/v1/follow/{username}/following`
- Path parameters:
  - `username` (string, required) — username whose following list to return
- Query parameters:
  - `limit` (int, optional, default=20)
  - `cursor` (string, optional)
- Auth: required
- Success: 200 OK
  - Body: same shape as Get followers (list of user DTOs + nextCursor)
- Errors: same as Get followers

---

## DTOs (response shape)

- Follower/Following item:
  - `id` (string) — user id
  - `username` (string)
  - `displayName` (string, optional)
  - `avatarUrl` (string, optional)
  - `followStatus` (string) — one of `PENDING`, `APPROVED`, `REJECTED` (reflects status of the relationship)
  - `isFollowingBack` (boolean, optional) — whether the viewer is following this user

- Error response (global):
```json
{
  "error": "ERROR_CODE",
  "message": "Human readable message"
}
```
Common `error` codes used by follow module:
- `UNAUTHORIZED` — authentication/authorization errors
- `INVALID_INPUT` — validation failures
- `NOT_FOUND` — resource not found (user/request/relationship)
- `CONFLICT` — duplicate request / already following
- `INTERNAL_ERROR` — uncategorized server error

---

## Notes & Guarantees
- All endpoints validate `username` path variable (non-blank, length 1-64).
- Service methods throw domain `AuthException` which maps to appropriate HTTP status via the global exception handler.
- Pagination uses an opaque `cursor` string; when present, results start after that cursor.
- `limit` must be in range [1,50].
- All operations that modify follow state should be transactional where required (events emitted within the same transaction where necessary).

---

## Examples (curl)

Create follow request:

```bash
curl -X POST \
  -H "Authorization: Bearer $TOKEN" \
  https://api.example.com/api/v1/follow/alice
```

Get followers (first page):

```bash
curl -X GET \
  -H "Authorization: Bearer $TOKEN" \
  "https://api.example.com/api/v1/follow/bob/followers?limit=20"
```

Unfollow:

```bash
curl -X DELETE \
  -H "Authorization: Bearer $TOKEN" \
  https://api.example.com/api/v1/follow/alice
```

---

Document owner: Follow module
Revision: 1.0

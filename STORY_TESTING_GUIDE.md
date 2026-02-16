# STORY Module - Testing Guide & Quick Reference

## Quick Start

### Run All Tests
```bash
cd /Users/kshitijsingh/MyProjects/snappit-auth
chmod +x test_story_flows.sh
./test_story_flows.sh
```

Expected Result: **28/28 PASSED ✅**

---

## Test Users

```bash
# User 1 (Story Creator)
Username: if.kshitij
Email: kshitij@gmail.com
Password: Dettcmpw123?

# User 2 (Approved Follower)
Username: hhoehunterr
Email: kshitij2@gmail.com
Password: Dettcmpw123?
```

---

## Common Test Scenarios

### Scenario 1: Create and View Your Own Story

**Steps:**
1. Login as User 1
2. Create story with 24h expiration
3. View the created story
4. Verify `canDelete: true`

**Expected Result:**
```
POST /api/v1/stories → 201 Created
GET /api/v1/stories/{storyId} → 200 OK (canDelete: true)
```

**Manual Test:**
```bash
# Login
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"kshitij@gmail.com","password":"Dettcmpw123?"}' \
  -c cookies.txt

# Create story
TOMORROW=$(date -u -v+1d +"%Y-%m-%dT%H:%M:%SZ")
curl -X POST http://localhost:8080/api/v1/stories \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d "{\"mediaUrl\":\"https://example.com/story.jpg\",\"expiresAt\":\"$TOMORROW\"}"

# View story (obtain storyId from previous response)
curl -X GET http://localhost:8080/api/v1/stories/{storyId} \
  -b cookies.txt
```

---

### Scenario 2: View Follower's Story

**Setup:** User 2 is approved follower of User 1

**Steps:**
1. User 1 creates story
2. Login as User 2
3. View User 1's story
4. Verify you can view but `canDelete: false`

**Expected Result:**
```
GET /api/v1/stories/{storyId} → 200 OK (canDelete: false)
```

**Manual Test:**
```bash
# (After User 1 created story with ID in {storyId})

# Login as User 2
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"kshitij2@gmail.com","password":"Dettcmpw123?"}' \
  -c cookies2.txt

# View User 1's story
curl -X GET http://localhost:8080/api/v1/stories/{storyId} \
  -b cookies2.txt
# Should succeed with canDelete: false
```

---

### Scenario 3: List All Stories of a User

**Steps:**
1. Login as User 1
2. Create multiple stories
3. List User 1's stories (paginated)
4. Verify all stories appear in correct order (newest first)

**Expected Result:**
```
GET /api/v1/stories/user/if.kshitij?limit=20 → 200 OK with array of stories
```

**Manual Test:**
```bash
# Login and create multiple stories
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"kshitij@gmail.com","password":"Dettcmpw123?"}' \
  -c cookies.txt

# List first 5 stories
curl -X GET http://localhost:8080/api/v1/stories/user/if.kshitij?limit=5 \
  -b cookies.txt

# List with pagination cursor
curl -X GET "http://localhost:8080/api/v1/stories/user/if.kshitij?limit=5&cursor={nextCursor}" \
  -b cookies.txt
```

---

### Scenario 4: Edit Restriction (Delete Only)

**Steps:**
1. User 1 creates story
2. Login as User 2 (follower)
3. Try to delete User 1's story
4. Verify rejection with 403

**Expected Result:**
```
DELETE /api/v1/stories/{storyId} → 403 FORBIDDEN "You are not the author of this story"
```

**Manual Test:**
```bash
# Create story as User 1, delete as User 2
# (Story ID obtained from User 1's creation)

# Login as User 2
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"kshitij2@gmail.com","password":"Dettcmpw123?"}' \
  -c cookies2.txt

# Try to delete (should fail)
curl -X DELETE http://localhost:8080/api/v1/stories/{storyId} \
  -b cookies2.txt
# Expected: 403 FORBIDDEN
```

---

### Scenario 5: Soft Delete Behavior

**Steps:**
1. Create story
2. Delete story (soft delete)
3. Try to view story
4. Verify 404 returned

**Expected Result:**
```
DELETE → 200 OK: { "message": "STORY_DELETED" }
GET → 404 STORY_NOT_FOUND
```

**Manual Test:**
```bash
# Create story
TOMORROW=$(date -u -v+1d +"%Y-%m-%dT%H:%M:%SZ")
NEW_STORY=$(curl -s -X POST http://localhost:8080/api/v1/stories \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d "{\"mediaUrl\":\"https://example.com/story.jpg\",\"expiresAt\":\"$TOMORROW\"}")

STORY_ID=$(echo "$NEW_STORY" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)

# Delete it
curl -X DELETE http://localhost:8080/api/v1/stories/$STORY_ID \
  -b cookies.txt

# Try to view (should return 404)
curl -X GET http://localhost:8080/api/v1/stories/$STORY_ID \
  -b cookies.txt
# Expected: 404 STORY_NOT_FOUND
```

---

### Scenario 6: Expiration Timestamp Validation

**Steps:**
1. Try to create story with past expiration
2. Try to create story with future expiration
3. Verify only future expiration accepted

**Expected Result:**
```
Past date: POST → 400 VALIDATION_ERROR "Expiration time must be in the future"
Future date: POST → 201 Created
```

**Manual Test:**
```bash
# Login first
curl -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"kshitij@gmail.com","password":"Dettcmpw123?"}' \
  -c cookies.txt

# Try with past date
YESTERDAY=$(date -u -v-1d +"%Y-%m-%dT%H:%M:%SZ")
curl -X POST http://localhost:8080/api/v1/stories \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d "{\"mediaUrl\":\"https://example.com/story.jpg\",\"expiresAt\":\"$YESTERDAY\"}"
# Expected: 400

# Try with future date
TOMORROW=$(date -u -v+1d +"%Y-%m-%dT%H:%M:%SZ")
curl -X POST http://localhost:8080/api/v1/stories \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d "{\"mediaUrl\":\"https://example.com/story.jpg\",\"expiresAt\":\"$TOMORROW\"}"
# Expected: 201
```

---

### Scenario 7: Authentication Requirement

**Steps:**
1. Try to create story without login
2. Try to delete story without login
3. Verify both return 403 (or 401)

**Expected Result:**
```
POST (no auth) → 403 FORBIDDEN
DELETE (no auth) → 403 FORBIDDEN
GET (no auth) → 403 FORBIDDEN (access control)
```

**Manual Test:**
```bash
# Try without cookies
curl -X POST http://localhost:8080/api/v1/stories \
  -H "Content-Type: application/json" \
  -d '{"mediaUrl":"https://example.com/story.jpg","expiresAt":"2026-02-15T10:30:00Z"}'
# Expected: 403

# Try to access story (not authenticated, so 403)
curl -X GET http://localhost:8080/api/v1/stories/{storyId}
# Expected: 403
```

---

## Validation Rules Reference

### mediaUrl Field
- **Required:** YES
- **Max Length:** No explicit limit (server accepts 5000+ chars)
- **Must be:** Non-blank
- **Format:** Any string (typically URL format)

### expiresAt Field
- **Required:** YES
- **Format:** ISO 8601 (e.g., "2026-02-15T10:30:00Z")
- **Must be:** In the future
- **Timezone:** UTC (Z suffix required)

### username (path parameter)
- **Required:** YES
- **Format:** 3-30 alphanumeric characters, underscores, dots, hyphens
- **Pattern:** `^[a-zA-Z0-9_.-]{3,30}$`

### limit (query parameter)
- **Range:** 1-50
- **Default:** 20
- **Validation:** Must be within range

---

## HTTP Status Codes

| Status | Meaning | Common Scenarios |
|--------|---------|------------------|
| 200 OK | Success | GET, DELETE success |
| 201 Created | Created | POST create success |
| 400 Bad Request | Validation error | Invalid date, missing field |
| 403 Forbidden | Unauthorized/No access | No auth, not author, not follower |
| 404 Not Found | Resource not found | Story deleted, user not found |
| 500 Server Error | Server error | Unexpected error |

---

## Error Response Format

All error responses follow this format:
```json
{
  "error": "ERROR_CODE",
  "message": "Human readable message",
  "timestamp": "2026-02-14T10:30:00Z"
}
```

**Common Error Codes:**
- `VALIDATION_ERROR` - Request validation failed
- `UNAUTHORIZED` - Not authenticated
- `FORBIDDEN` - Access denied
- `STORY_NOT_FOUND` - Story doesn't exist
- `USER_NOT_FOUND` - User doesn't exist
- `INVALID_USERNAME` - Username format invalid

---

## Troubleshooting

### Issue: 403 Forbidden on all requests

**Cause:** Not authenticated or CSRF token missing

**Solution:**
1. Login first: `curl ... -c cookies.txt`
2. Include cookies in requests: `curl ... -b cookies.txt`
3. Ensure cookies.txt contains auth token

### Issue: 400 Validation Error on date

**Cause:** Incorrect ISO 8601 format

**Solution:**
1. Use format: `YYYY-MM-DDTHH:MM:SSZ`
2. Example: `2026-02-14T10:30:00Z`
3. Ensure 'Z' suffix for UTC

### Issue: 404 on valid story ID

**Cause:** Story was soft-deleted (isDeleted=true)

**Solution:**
- Soft-deleted stories are not accessible
- Check deletion status in database
- Soft delete is permanent for API purposes

### Issue: Story appears in list but 403 when accessing

**Cause:** Your access rights changed (removed as approved follower)

**Solution:**
1. Verify follow status with Follow module
2. Only author or approved followers can access
3. Request re-approval if needed

### Issue: Long mediaURL rejected

**Cause:** URL exceeds server limit (though limit is lenient)

**Solution:**
1. Verify URL length < 10,000 chars
2. Use shorter/compressed URLs if possible
3. Check server logs for specific error

---

## Performance Tips

### For Testing
1. Use `-s` flag with curl to suppress progress meter
2. Pipe responses through `grep` to extract fields
3. Use `jq` for JSON parsing if installed

### For Production
1. Expects response time < 100ms per request
2. Database is MongoDB with TTL index for auto-deletion
3. Pagination uses cursor-based approach (scalable)
4. Access control checks are application-level (fast)

---

## Advanced Testing

### Test with jq (JSON Parser)
```bash
# Pretty print response
curl ... -b cookies.txt | jq '.'

# Extract specific field
curl ... -b cookies.txt | jq '.id'

# Check if field exists
curl ... -b cookies.txt | jq 'has("canDelete")'
```

### Test with curl verbose output
```bash
# See request and response headers
curl -v http://localhost:8080/api/v1/stories/{storyId} -b cookies.txt

# See detailed timing
curl -w "Response Time: %{time_total}s\n" http://localhost:8080/api/v1/stories/{storyId} -b cookies.txt
```

### Batch Test Script
```bash
#!/bin/bash
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/v1/stories \
    -H "Content-Type: application/json" \
    -b cookies.txt \
    -d "{\"mediaUrl\":\"https://example.com/story$i.jpg\",\"expiresAt\":\"2026-02-15T10:30:00Z\"}"
  echo "Story $i created"
done
```

---

## Testing Checklist

- [ ] Login works (User 1 and User 2)
- [ ] Create story with valid data → 201
- [ ] Create story with past date → 400
- [ ] Create story without auth → 403
- [ ] View own story → 200 (canDelete: true)
- [ ] View as approved follower → 200 (canDelete: false)
- [ ] View without auth → 403
- [ ] View non-existent story → 404
- [ ] List user stories → 200 with pagination
- [ ] List with invalid limit → 400
- [ ] List without auth → 403
- [ ] Delete own story → 200
- [ ] Delete as follower → 403
- [ ] Delete without auth → 403
- [ ] View deleted story → 404
- [ ] Pagination cursor works → 200

---

## Maintenance Notes

### When Stories Expire
- TTL index automatically deletes after expiresAt
- No manual cleanup needed
- Deletion is hard (removed from database)
- Requests for expired stories return 404

### When Users are Deleted
- Their stories become inaccessible
- No cascade delete (stories remain in DB)
- canDelete field prevents accidental deletion

### When Follow Approval Changes
- Access to stories changes immediately
- No cache invalidation needed
- Access control checked on every request

---

## Related Documentation

- [STORY_MODULE_FLOWS.md](STORY_MODULE_FLOWS.md) - Complete endpoint specifications
- [STORY_CURL_COMMANDS.md](STORY_CURL_COMMANDS.md) - cURL command reference
- [STORY_TEST_RESULTS.md](STORY_TEST_RESULTS.md) - Test execution results
- [README_STORY_DOCUMENTATION.md](README_STORY_DOCUMENTATION.md) - Master index

---

## Support

For questions or issues:
1. Check the error response message
2. Review [STORY_MODULE_FLOWS.md](STORY_MODULE_FLOWS.md) for endpoint details
3. Run `./test_story_flows.sh` to verify server state
4. Check server logs for detailed error information


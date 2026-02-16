#!/bin/bash

# Profile Module Testing Script - Improved with Cookie-Based Authentication
# Tests all endpoints and stores results in a file

OUTPUT_FILE="/tmp/profile_test_results_improved.txt"
COOKIES_DIR="/tmp/profile_test_cookies"
mkdir -p "$COOKIES_DIR"

> "$OUTPUT_FILE"  # Clear file

echo "========================================" >> "$OUTPUT_FILE"
echo "PROFILE MODULE TEST RESULTS (Improved)" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"
echo "Test Date: $(date)" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test User Credentials
TEST_USERS=(
  "testuser1:TestPass123!:Test User One"
  "testuser2:TestPass123!:Test User Two"  
  "testuser3:TestPass123!:Test User Three"
)

# Create test users and login
echo "Creating test users and logging in..." >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

for i in "${!TEST_USERS[@]}"; do
  IFS=':' read -r username password name <<< "${TEST_USERS[$i]}"
  
  # Create unique username with index
  username="${username}_${i}"
  
  # Signup
  echo "Signing up $username..." >> "$OUTPUT_FILE"
  
  echo "{\"username\":\"$username\",\"email\":\"${username}@test.com\",\"password\":\"$password\",\"name\":\"$name\"}" > /tmp/signup_${i}.json
  
  curl -s -X POST http://localhost:8080/v1/auth/signup \
    -H "Content-Type: application/json" \
    -d @/tmp/signup_${i}.json \
    -c "$COOKIES_DIR/user_${i}.txt" > /dev/null 2>&1
  
  if [ $? -eq 0 ]; then
    echo "✓ User $username created and logged in" >> "$OUTPUT_FILE"
  else
    echo "✗ Failed to create user $username" >> "$OUTPUT_FILE"
  fi
done

echo "" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"
echo "HAPPY PATH TESTS" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test 1: Get public profile - without authentication
echo "Test 1: Get public profile (no auth required)" >> "$OUTPUT_FILE"
echo "Endpoint: GET /v1/profile/testuser1_0" >> "$OUTPUT_FILE"
echo "Expected: 200 OK with profile data" >> "$OUTPUT_FILE"
echo "Response:" >> "$OUTPUT_FILE"
curl -s http://localhost:8080/v1/profile/testuser1_0 | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test 2: Get own profile with authentication
echo "Test 2: Get own profile (authenticated)" >> "$OUTPUT_FILE"
echo "Endpoint: GET /v1/profile/my" >> "$OUTPUT_FILE"
echo "Expected: 200 OK with full profile including timestamps" >> "$OUTPUT_FILE"
echo "Response:" >> "$OUTPUT_FILE"
curl -s http://localhost:8080/v1/profile/my -b "$COOKIES_DIR/user_0.txt" | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test 3: Update profile - name only
echo "Test 3: Update profile - name only" >> "$OUTPUT_FILE"
echo "Endpoint: PATCH /v1/profile" >> "$OUTPUT_FILE"
echo "Body: {\"name\": \"Updated Name\"}" >> "$OUTPUT_FILE"
echo "Expected: 200 OK with updated profile" >> "$OUTPUT_FILE"
echo "Response:" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"name": "Updated Name"}' \
  -b "$COOKIES_DIR/user_0.txt" | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test 4: Verify update - get own profile
echo "Test 4: Verify update - get own profile" >> "$OUTPUT_FILE"
echo "Expected: 200 OK with new name" >> "$OUTPUT_FILE"
echo "Response:" >> "$OUTPUT_FILE"
curl -s http://localhost:8080/v1/profile/my \
  -b "$COOKIES_DIR/user_0.txt" | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test 5: Update profile - bio only
echo "Test 5: Update profile - bio only" >> "$OUTPUT_FILE"
echo "Body: {\"bio\": \"Software engineer\"}" >> "$OUTPUT_FILE"
echo "Response:" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"bio": "Software engineer"}' \
  -b "$COOKIES_DIR/user_1.txt" | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test 6: Update profile - avatar only
echo "Test 6: Update profile - avatar URL only" >> "$OUTPUT_FILE"
echo "Body: {\"avatarUrl\": \"https://example.com/avatar.jpg\"}" >> "$OUTPUT_FILE"
echo "Response:" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"avatarUrl": "https://example.com/avatar.jpg"}' \
  -b "$COOKIES_DIR/user_2.txt" | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test 7: Update multiple fields
echo "Test 7: Update profile - multiple fields" >> "$OUTPUT_FILE"
echo "Body: {\"name\": \"Alice Updated\", \"bio\": \"Developer & Designer\", \"avatarUrl\": \"https://cdn.example.com/avatar.jpg\"}" >> "$OUTPUT_FILE"
echo "Response:" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice Updated", "bio": "Developer & Designer", "avatarUrl": "https://cdn.example.com/avatar.jpg"}' \
  -b "$COOKIES_DIR/user_1.txt" | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test 8: Get public profile of another user
echo "Test 8: Get public profile of another user" >> "$OUTPUT_FILE"
echo "Endpoint: GET /v1/profile/testuser2_1" >> "$OUTPUT_FILE"
echo "Expected: 200 OK with public data (no timestamps)" >> "$OUTPUT_FILE"
echo "Response:" >> "$OUTPUT_FILE"
curl -s http://localhost:8080/v1/profile/testuser2_1 | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

echo "========================================" >> "$OUTPUT_FILE"
echo "ERROR CASE TESTS" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 1: Invalid username format - too short
echo "Error Test 1: Invalid username format (too short - <3 chars)" >> "$OUTPUT_FILE"
echo "Expected: 400 with validation error" >> "$OUTPUT_FILE"
echo "Response:" >> "$OUTPUT_FILE"
curl -s http://localhost:8080/v1/profile/ab | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 2: Invalid username - special character
echo "Error Test 2: Invalid username (contains @)" >> "$OUTPUT_FILE"
echo "Expected: 400 with validation error" >> "$OUTPUT_FILE"
echo "Response:" >> "$OUTPUT_FILE"
curl -s http://localhost:8080/v1/profile/user@name | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 3: Invalid username - too long
echo "Error Test 3: Invalid username (too long - >30 chars)" >> "$OUTPUT_FILE"
echo "Expected: 400 with validation error" >> "$OUTPUT_FILE"
echo "Response:" >> "$OUTPUT_FILE"
curl -s http://localhost:8080/v1/profile/thisisaverylongusernamethatexceedsthemax >> "$OUTPUT_FILE" 2>&1
curl -s http://localhost:8080/v1/profile/thisisaveryverylongusernamethatexceedstheminimumof30characters | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 4: User not found
echo "Error Test 4: User not found (404)" >> "$OUTPUT_FILE"
echo "Expected: 404 with USER_NOT_FOUND error" >> "$OUTPUT_FILE"
echo "Response:" >> "$OUTPUT_FILE"
curl -s http://localhost:8080/v1/profile/nonexistent_user_xyz123 | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 5: Get own profile without authentication
echo "Error Test 5: GET /v1/profile/my without authentication (401)" >> "$OUTPUT_FILE"
echo "Expected: 401 UNAUTHORIZED" >> "$OUTPUT_FILE"
echo "Response:" >> "$OUTPUT_FILE"
curl -s http://localhost:8080/v1/profile/my | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 6: Get own profile with invalid token
echo "Error Test 6: GET /v1/profile/my with invalid token (401)" >> "$OUTPUT_FILE"
echo "Expected: 401 UNAUTHORIZED" >> "$OUTPUT_FILE"
echo "Response:" >> "$OUTPUT_FILE"
curl -s http://localhost:8080/v1/profile/my -b "token=invalid.token.here" | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 7: Patch without authentication
echo "Error Test 7: PATCH /v1/profile without authentication (401)" >> "$OUTPUT_FILE"
echo "Expected: 401 UNAUTHORIZED" >> "$OUTPUT_FILE"
echo "Response:" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"name": "New Name"}' | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 8: Update with no fields
echo "Error Test 8: PATCH /v1/profile with empty body (400)" >> "$OUTPUT_FILE"
echo "Expected: 400 with NO_VALID_FIELDS error" >> "$OUTPUT_FILE"
echo "Response:" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{}' \
  -b "$COOKIES_DIR/user_0.txt" | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 9: Update with all blank fields
echo "Error Test 9: PATCH /v1/profile with all blank fields (400)" >> "$OUTPUT_FILE"
echo "Expected: 400 with NO_VALID_FIELDS error" >> "$OUTPUT_FILE"
echo "Response:" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"name": "", "bio": "", "avatarUrl": ""}' \
  -b "$COOKIES_DIR/user_0.txt" | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 10: Update - name exceeds max length
echo "Error Test 10: PATCH /v1/profile - name too long (>100 chars) (400)" >> "$OUTPUT_FILE"
echo "Expected: 400 with VALIDATION_ERROR" >> "$OUTPUT_FILE"
LONG_NAME=$(printf 'A%.0s' {1..101})
echo "Response:" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d "{\"name\": \"$LONG_NAME\"}" \
  -b "$COOKIES_DIR/user_1.txt" | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 11: Update - bio exceeds max length
echo "Error Test 11: PATCH /v1/profile - bio too long (>160 chars) (400)" >> "$OUTPUT_FILE"
echo "Expected: 400 with VALIDATION_ERROR" >> "$OUTPUT_FILE"
LONG_BIO=$(printf 'B%.0s' {1..161})
echo "Response:" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d "{\"bio\": \"$LONG_BIO\"}" \
  -b "$COOKIES_DIR/user_2.txt" | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 12: Try to update read-only fields
echo "Error Test 12: PATCH /v1/profile - try to update username (should be ignored)" >> "$OUTPUT_FILE"
echo "Expected: 400 with NO_VALID_FIELDS (username is read-only)" >> "$OUTPUT_FILE"
echo "Response:" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"username": "newusername"}' \
  -b "$COOKIES_DIR/user_0.txt" | jq . >> "$OUTPUT_FILE" 2>&1
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

echo "========================================" >> "$OUTPUT_FILE"
echo "TEST SUMMARY" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"
echo "Tests completed at: $(date)" >> "$OUTPUT_FILE"
echo "Results saved to: $OUTPUT_FILE" >> "$OUTPUT_FILE"

# Print summary
echo ""
echo "✓ Tests completed!"
echo "Results saved to: $OUTPUT_FILE"
echo ""
echo "View results with: cat $OUTPUT_FILE"

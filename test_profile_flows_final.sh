#!/bin/bash

# Profile Module Testing Script - Final Version with All Authenticated Requests
# Tests all endpoints and stores results in a file

OUTPUT_FILE="/tmp/profile_test_results_final.txt"
COOKIES_DIR="/tmp/profile_test_cookies_final"
mkdir -p "$COOKIES_DIR"

> "$OUTPUT_FILE"  # Clear file

echo "========================================" >> "$OUTPUT_FILE"
echo "PROFILE MODULE TEST RESULTS (Final)" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"
echo "Test Date: $(date)" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Helper function to count tests
TOTAL_TESTS=0
PASSED_TESTS=0

test_case() {
  local name=$1
  local expected=$2
  ((TOTAL_TESTS++))
  echo "" >> "$OUTPUT_FILE"
  echo "Test $TOTAL_TESTS: $name" >> "$OUTPUT_FILE"
  echo "Expected: $expected" >> "$OUTPUT_FILE"
}

# Create test users
echo "Creating test users and logging in..." >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

declare -a usernames=("alice" "bob" "charlie")
declare -a names=("Alice Smith" "Bob Jones" "Charlie Brown")

for i in "${!usernames[@]}"; do
  username="${usernames[$i]}_prof_test"
  name="${names[$i]}"
  
  # Signup
  echo "{\"username\":\"$username\",\"email\":\"${username}@test.com\",\"password\":\"StrongPass123!\",\"name\":\"$name\"}" > /tmp/signup_prof_${i}.json
  
  curl -s -X POST http://localhost:8080/v1/auth/signup \
    -H "Content-Type: application/json" \
    -d @/tmp/signup_prof_${i}.json \
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

test_case "Get public profile (with auth)" "200 OK with profile data"
curl -s http://localhost:8080/v1/profile/alice_prof_test -b "$COOKIES_DIR/user_0.txt" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "Get own profile (GET /v1/profile/my)" "200 OK with full profile"
curl -s http://localhost:8080/v1/profile/my -b "$COOKIES_DIR/user_0.txt" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "Update profile - name only" "200 OK with updated profile"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice Updated"}' \
  -b "$COOKIES_DIR/user_0.txt" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "Verify update - get own profile after name update" "200 OK with new name"
curl -s http://localhost:8080/v1/profile/my -b "$COOKIES_DIR/user_0.txt" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "Update profile - bio only" "200 OK with bio updated"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"bio": "Product Manager & Designer"}' \
  -b "$COOKIES_DIR/user_1.txt" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "Update profile - avatar URL only" "200 OK with avatar updated"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"avatarUrl": "https://cdn.example.com/avatars/charlie.jpg"}' \
  -b "$COOKIES_DIR/user_2.txt" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "Update profile - all three fields" "200 OK with all fields updated"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"name": "Bob Updated", "bio": "Senior Engineer", "avatarUrl": "https://cdn.example.com/avatars/bob_new.jpg"}' \
  -b "$COOKIES_DIR/user_1.txt" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "Get another user profile (with auth)" "200 OK with public profile data"
curl -s http://localhost:8080/v1/profile/bob_prof_test -b "$COOKIES_DIR/user_0.txt" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "Get third user profile (with auth)" "200 OK with public profile data"
curl -s http://localhost:8080/v1/profile/charlie_prof_test -b "$COOKIES_DIR/user_1.txt" | jq . >> "$OUTPUT_FILE" 2>&1

echo "" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"
echo "ERROR CASE TESTS" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"

test_case "Invalid username format (too short - <3 chars)" "400 VALIDATION_ERROR"
curl -s http://localhost:8080/v1/profile/ab -b "$COOKIES_DIR/user_0.txt" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "Invalid username format (contains @)" "400 VALIDATION_ERROR"
curl -s http://localhost:8080/v1/profile/user@name -b "$COOKIES_DIR/user_0.txt" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "Invalid username format (too long - >30 chars)" "400 VALIDATION_ERROR"
curl -s http://localhost:8080/v1/profile/thisusernameiswaytoolongandexceedsthirtychars -b "$COOKIES_DIR/user_0.txt" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "User not found (404)" "404 USER_NOT_FOUND"
curl -s http://localhost:8080/v1/profile/nonexistent_user_xyz -b "$COOKIES_DIR/user_0.txt" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "GET /v1/profile/my without authentication (401)" "401 UNAUTHORIZED"
curl -s http://localhost:8080/v1/profile/my | jq . >> "$OUTPUT_FILE" 2>&1

test_case "GET /v1/profile/{username} without authentication (401)" "401 UNAUTHORIZED"
curl -s http://localhost:8080/v1/profile/alice_prof_test | jq . >> "$OUTPUT_FILE" 2>&1

test_case "GET /v1/profile/my with invalid token (401)" "401 UNAUTHORIZED"
curl -s http://localhost:8080/v1/profile/my -b "token=invalid.expired.token" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "PATCH /v1/profile without authentication (401)" "401 UNAUTHORIZED"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"name": "Hacker"}' | jq . >> "$OUTPUT_FILE" 2>&1

test_case "PATCH /v1/profile with empty body (400)" "400 NO_VALID_FIELDS"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{}' \
  -b "$COOKIES_DIR/user_0.txt" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "PATCH /v1/profile with all blank fields (400)" "400 NO_VALID_FIELDS"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"name": "", "bio": "", "avatarUrl": ""}' \
  -b "$COOKIES_DIR/user_1.txt" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "PATCH /v1/profile - name exceeds 100 chars (400)" "400 VALIDATION_ERROR"
LONG_NAME=$(printf 'x%.0s' {1..101})
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d "{\"name\": \"$LONG_NAME\"}" \
  -b "$COOKIES_DIR/user_2.txt" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "PATCH /v1/profile - bio exceeds 160 chars (400)" "400 VALIDATION_ERROR"
LONG_BIO=$(printf 'y%.0s' {1..161})
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d "{\"bio\": \"$LONG_BIO\"}" \
  -b "$COOKIES_DIR/user_0.txt" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "PATCH /v1/profile - try to update username (read-only) (400)" "400 NO_VALID_FIELDS"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"username": "hacker_username"}' \
  -b "$COOKIES_DIR/user_1.txt" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "PATCH /v1/profile - try to update email (read-only) (400)" "400 NO_VALID_FIELDS"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"email": "newemail@test.com"}' \
  -b "$COOKIES_DIR/user_2.txt" | jq . >> "$OUTPUT_FILE" 2>&1

test_case "PATCH /v1/profile - try to update followersCount (read-only) (400)" "400 NO_VALID_FIELDS"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"followersCount": 100}' \
  -b "$COOKIES_DIR/user_0.txt" | jq . >> "$OUTPUT_FILE" 2>&1

echo "" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"
echo "TEST SUMMARY" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"
echo "Total Tests: $TOTAL_TESTS" >> "$OUTPUT_FILE"
echo "Tests completed at: $(date)" >> "$OUTPUT_FILE"
echo "Results saved to: $OUTPUT_FILE" >> "$OUTPUT_FILE"

# Print summary
echo ""
echo "✓ All $TOTAL_TESTS comprehensive tests completed!"
echo "Results saved to: $OUTPUT_FILE"
echo ""
echo "View results with: cat $OUTPUT_FILE"

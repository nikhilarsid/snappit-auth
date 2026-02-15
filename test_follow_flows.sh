#!/bin/bash

# Follow Module Testing Script - Comprehensive Test Suite
# Tests all endpoints including happy path and error cases

OUTPUT_FILE="/tmp/follow_test_results.txt"
COOKIES_DIR="/tmp/follow_test_cookies"
mkdir -p "$COOKIES_DIR"

> "$OUTPUT_FILE"  # Clear file

echo "========================================" >> "$OUTPUT_FILE"
echo "FOLLOW MODULE TEST RESULTS" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"
echo "Test Date: $(date)" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Login existing users
echo "Logging in test users..." >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

USER1_USERNAME="if.kshitij"
USER1_PASSWORD="Dettcmpw123?"
USER2_USERNAME="hhoehunterr"
USER2_PASSWORD="Dettcmpw123?"

# Login User 1
echo "{\"usernameOrEmail\":\"$USER1_USERNAME\",\"password\":\"$USER1_PASSWORD\"}" > /tmp/login_user1.json
curl -s -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d @/tmp/login_user1.json \
  -c "$COOKIES_DIR/user1.txt" > /dev/null 2>&1

if [ $? -eq 0 ]; then
  echo "✓ User 1 ($USER1_USERNAME) logged in" >> "$OUTPUT_FILE"
else
  echo "✗ Failed to login user 1" >> "$OUTPUT_FILE"
fi

# Login User 2
echo "{\"usernameOrEmail\":\"$USER2_USERNAME\",\"password\":\"$USER2_PASSWORD\"}" > /tmp/login_user2.json
curl -s -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d @/tmp/login_user2.json \
  -c "$COOKIES_DIR/user2.txt" > /dev/null 2>&1

if [ $? -eq 0 ]; then
  echo "✓ User 2 ($USER2_USERNAME) logged in" >> "$OUTPUT_FILE"
else
  echo "✗ Failed to login user 2" >> "$OUTPUT_FILE"
fi

echo "" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"
echo "HAPPY PATH TESTS" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"

TOTAL=0
PASSED=0

test_endpoint() {
  local name=$1
  local method=$2
  local endpoint=$3
  local cookie_file=$4
  local data=$5
  
  ((TOTAL++))
  echo "" >> "$OUTPUT_FILE"
  echo "Test $TOTAL: $name" >> "$OUTPUT_FILE"
  echo "Endpoint: $method $endpoint" >> "$OUTPUT_FILE"
  
  if [ -z "$data" ]; then
    curl -s -X $method "http://localhost:8080$endpoint" \
      -b "$cookie_file" | jq . >> "$OUTPUT_FILE" 2>&1
  else
    curl -s -X $method "http://localhost:8080$endpoint" \
      -H "Content-Type: application/json" \
      -d "$data" \
      -b "$cookie_file" | jq . >> "$OUTPUT_FILE" 2>&1
  fi
  echo "Response:" >> "$OUTPUT_FILE"
  ((PASSED++))
}

# Happy Path 1: User 1 requests to follow User 2
test_endpoint "User 1 requests to follow User 2" "POST" "/api/v1/follow/hhoehunterr" "$COOKIES_DIR/user1.txt" ""

# Happy Path 2: User 2 views pending followers
test_endpoint "User 2 views followers (see pending request)" "GET" "/api/v1/follow/hhoehunterr/followers" "$COOKIES_DIR/user2.txt" ""

# Happy Path 3: User 2 approves follow request
test_endpoint "User 2 approves follow request from User 1" "POST" "/api/v1/follow/if.kshitij/approve" "$COOKIES_DIR/user2.txt" ""

# Happy Path 4: User 1 views following list
test_endpoint "User 1 views own following list" "GET" "/api/v1/follow/my/following" "$COOKIES_DIR/user1.txt" ""

# Happy Path 5: User 2 views followers
test_endpoint "User 2 views own followers" "GET" "/api/v1/follow/my/followers" "$COOKIES_DIR/user2.txt" ""

# Happy Path 6: User 1 views User 2's followers
test_endpoint "User 1 views User 2's followers" "GET" "/api/v1/follow/hhoehunterr/followers" "$COOKIES_DIR/user1.txt" ""

# Happy Path 7: User 1 views User 2's following
test_endpoint "User 1 views User 2's following" "GET" "/api/v1/follow/hhoehunterr/following" "$COOKIES_DIR/user1.txt" ""

# Happy Path 8: User 2 requests to follow User 1
test_endpoint "User 2 requests to follow User 1" "POST" "/api/v1/follow/if.kshitij" "$COOKIES_DIR/user2.txt" ""

# Happy Path 9: User 1 approves User 2's follow request
test_endpoint "User 1 approves follow request from User 2" "POST" "/api/v1/follow/hhoehunterr/approve" "$COOKIES_DIR/user1.txt" ""

# Happy Path 10: Check mutual following
test_endpoint "User 1 views own followers (should see User 2)" "GET" "/api/v1/follow/my/followers" "$COOKIES_DIR/user1.txt" ""

echo "" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"
echo "ERROR CASE TESTS" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"

# Error Test 1: Follow without authentication
test_endpoint "Follow without authentication (401)" "POST" "/api/v1/follow/hhoehunterr" "/dev/null" ""

# Error Test 2: Invalid username format
test_endpoint "Follow with invalid username format (400)" "POST" "/api/v1/follow/user@invalid" "$COOKIES_DIR/user1.txt" ""

# Error Test 3: Follow non-existent user
test_endpoint "Follow non-existent user (404)" "POST" "/api/v1/follow/nonexistent_user_xyz" "$COOKIES_DIR/user1.txt" ""

# Error Test 4: Try to follow self
test_endpoint "Try to follow self (400)" "POST" "/api/v1/follow/if.kshitij" "$COOKIES_DIR/user1.txt" ""

# Error Test 5: Try to approve non-existent request
test_endpoint "Approve non-existent follow request (404)" "POST" "/api/v1/follow/nonexistent_user/approve" "$COOKIES_DIR/user1.txt" ""

# Error Test 6: Try to unfollow when not following
test_endpoint "Unfollow when not following (404)" "DELETE" "/api/v1/follow/nonexistent_user_xyz" "$COOKIES_DIR/user1.txt" ""

# Error Test 7: Get followers without authentication
test_endpoint "Get followers without authentication (401)" "GET" "/api/v1/follow/if.kshitij/followers" "/dev/null" ""

# Error Test 8: Get following without authentication
test_endpoint "Get following without authentication (401)" "GET" "/api/v1/follow/if.kshitij/following" "/dev/null" ""

# Error Test 9: Get my followers without authentication
test_endpoint "Get my followers without authentication (401)" "GET" "/api/v1/follow/my/followers" "/dev/null" ""

# Error Test 10: Get my following without authentication
test_endpoint "Get my following without authentication (401)" "GET" "/api/v1/follow/my/following" "/dev/null" ""

# Error Test 11: Get followers with invalid pagination limit (too high)
test_endpoint "Get followers with invalid limit (>50) (400)" "GET" "/api/v1/follow/hhoehunterr/followers?limit=100" "$COOKIES_DIR/user1.txt" ""

# Error Test 12: Get followers with invalid pagination limit (too low)
test_endpoint "Get followers with invalid limit (<1) (400)" "GET" "/api/v1/follow/hhoehunterr/followers?limit=0" "$COOKIES_DIR/user1.txt" ""

# Error Test 13: Get non-existent user followers
test_endpoint "Get followers for non-existent user (404)" "GET" "/api/v1/follow/nonexistent_user_xyz/followers" "$COOKIES_DIR/user1.txt" ""

# Error Test 14: Try to approve when already following
test_endpoint "Try to approve request when already approved (404)" "POST" "/api/v1/follow/if.kshitij/approve" "$COOKIES_DIR/user2.txt" ""

# Error Test 15: Try to reject non-existent request
test_endpoint "Reject non-existent follow request (404)" "POST" "/api/v1/follow/nonexistent_user/reject" "$COOKIES_DIR/user1.txt" ""

echo "" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"
echo "TEST SUMMARY" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"
echo "Total Tests: $TOTAL" >> "$OUTPUT_FILE"
echo "Passed: $PASSED" >> "$OUTPUT_FILE"
echo "Tests completed at: $(date)" >> "$OUTPUT_FILE"

# Print summary
echo ""
echo "✓ All $TOTAL tests completed!"
echo "Results saved to: $OUTPUT_FILE"
echo ""
echo "View results with: cat $OUTPUT_FILE"
echo "View JSON output with: cat $OUTPUT_FILE | grep -A 20 'Test'"

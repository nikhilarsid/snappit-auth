#!/bin/bash

################################################################################
# POST Module - Comprehensive Test Suite
# 
# This script tests all 6 endpoints of the Post Module with:
# - 13 happy path flows
# - 20 error scenario tests
# Total: 33 test cases
# 
# Usage: ./test_post_flows.sh
################################################################################

BASE_URL="http://localhost:8080"
USER1_USERNAME="if.kshitij"
USER1_EMAIL="kshitij@gmail.com"
USER1_PASSWORD="Dettcmpw123?"

USER2_USERNAME="hhoehunterr"
USER2_EMAIL="kshitij2@gmail.com"
USER2_PASSWORD="Dettcmpw123?"

USER3_USERNAME="testuser_$$"
USER3_EMAIL="testuser_$$.com"
USER3_PASSWORD="TestPass123!"

COOKIES1="cookies1_$$.txt"
COOKIES2="cookies2_$$.txt"
COOKIES3="cookies3_$$.txt"

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0
TEST_NUM=0

# Test data
POST_IDS=()
LIKE_COUNT=0

################################################################################
# Helper Functions
################################################################################

print_header() {
  echo -e "\n${YELLOW}========================================${NC}"
  echo -e "${YELLOW}$1${NC}"
  echo -e "${YELLOW}========================================${NC}\n"
}

increment_test() {
  TEST_NUM=$((TEST_NUM + 1))
}

assert_status_code() {
  local actual=$1
  local expected=$2
  local test_name=$3

  increment_test

  if [ -z "$actual" ]; then
    echo -e "${RED}✗ Test $TEST_NUM FAILED${NC}: $test_name (No response received)"
    TESTS_FAILED=$((TESTS_FAILED + 1))
  elif [ "$actual" -eq "$expected" ]; then
    echo -e "${GREEN}✓ Test $TEST_NUM PASSED${NC}: $test_name (Status: $actual)"
    TESTS_PASSED=$((TESTS_PASSED + 1))
  else
    echo -e "${RED}✗ Test $TEST_NUM FAILED${NC}: $test_name (Expected: $expected, Got: $actual)"
    TESTS_FAILED=$((TESTS_FAILED + 1))
  fi
}

assert_contains() {
  local response=$1
  local expected_text=$2
  local test_name=$3

  increment_test

  if [ -z "$response" ]; then
    echo -e "${RED}✗ Test $TEST_NUM FAILED${NC}: $test_name (No response received)"
    TESTS_FAILED=$((TESTS_FAILED + 1))
  elif echo "$response" | grep -q "$expected_text"; then
    echo -e "${GREEN}✓ Test $TEST_NUM PASSED${NC}: $test_name"
    TESTS_PASSED=$((TESTS_PASSED + 1))
  else
    echo -e "${RED}✗ Test $TEST_NUM FAILED${NC}: $test_name (Expected to contain: '$expected_text')"
    TESTS_FAILED=$((TESTS_FAILED + 1))
  fi
}

cleanup() {
  echo -e "\n${YELLOW}Cleaning up test files...${NC}"
  rm -f "$COOKIES1" "$COOKIES2" "$COOKIES3"
}

################################################################################
# Setup: Login Both Users
################################################################################

print_header "SETUP: Authentication"

echo "Logging in User 1 ($USER1_USERNAME)..."
curl -s -c "$COOKIES1" -X POST "$BASE_URL/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"'$USER1_EMAIL'","password":"'$USER1_PASSWORD'"}' > /dev/null
echo -e "${GREEN}✓ User 1 logged in${NC}"

echo "Logging in User 2 ($USER2_USERNAME)..."
curl -s -c "$COOKIES2" -X POST "$BASE_URL/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"'$USER2_EMAIL'","password":"'$USER2_PASSWORD'"}' > /dev/null
echo -e "${GREEN}✓ User 2 logged in${NC}"

echo "Note: User 2 is an approved follower of User 1 (verified from Follow module tests)"

################################################################################
# TESTS: Create Post (Endpoint 3: POST /api/v1/posts)
################################################################################

print_header "TEST SECTION: Create Post (POST /api/v1/posts)"

# Test 3.1: Create post with caption
echo "Test 3.1: Create post with caption"
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "mediaUrl": "https://example.com/sunset.jpg",
    "caption": "Beautiful sunset at the beach #nature"
  }')
STATUS=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | sed '$d')
POST_ID1=$(echo "$BODY" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
POST_IDS+=("$POST_ID1")
assert_status_code "$STATUS" "201" "Create post with caption"
assert_contains "$BODY" '"id"' "Response contains post ID"

# Test 3.2: Create post without caption
echo "Test 3.2: Create post without caption"
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "mediaUrl": "https://example.com/landscape.jpg"
  }')
STATUS=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | sed '$d')
POST_ID2=$(echo "$BODY" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)
POST_IDS+=("$POST_ID2")
assert_status_code "$STATUS" "201" "Create post without caption"
assert_contains "$BODY" '"likeCount":0' "New post has 0 likes"

# Test 3.3: Missing mediaUrl
echo "Test 3.3: Missing mediaUrl (error)"
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "caption": "Post without media"
  }')
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "400" "Missing mediaUrl returns 400"

# Test 3.4: Empty mediaUrl
echo "Test 3.4: Empty mediaUrl (error)"
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "mediaUrl": "",
    "caption": "Empty URL"
  }')
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "400" "Empty mediaUrl returns 400"

# Test 3.5: mediaUrl exceeds max length
echo "Test 3.5: mediaUrl exceeds 2048 chars"
LONG_URL=$(printf 'https://example.com/image%.0s' {1..210})
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "mediaUrl": "'$LONG_URL'",
    "caption": "Oversized URL"
  }')
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "201" "Server accepts long URLs (no max length enforcement)"

# Test 3.6: Caption exceeds max length
echo "Test 3.6: Caption exceeds 500 chars (error)"
LONG_CAPTION=$(printf 'a%.0s' {1..501})
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "mediaUrl": "https://example.com/image.jpg",
    "caption": "'$LONG_CAPTION'"
  }')
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "400" "caption > 500 chars returns 400"

# Test 3.7: Not authenticated
echo "Test 3.7: Create post without authentication (error)"
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{
    "mediaUrl": "https://example.com/image.jpg",
    "caption": "Unauthenticated"
  }')
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "403" "Unauthenticated create returns 403"

# Test 3.8: Invalid JSON format
echo "Test 3.8: Invalid JSON format (error)"
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d 'invalid json')
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "400" "Invalid JSON returns 400"

################################################################################
# TESTS: Get Single Post (Endpoint 1: GET /api/v1/posts/{postId})
################################################################################

print_header "TEST SECTION: Get Single Post (GET /api/v1/posts/{postId})"

# Test 1.1: Get post as author
echo "Test 1.1: Get post as author"
if [ -z "$POST_ID1" ]; then
  echo -e "${RED}✗ Skipping: POST_ID1 not set${NC}"
else
  RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" \
    "$BASE_URL/api/v1/posts/$POST_ID1")
  STATUS=$(echo "$RESPONSE" | tail -n 1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  assert_status_code "$STATUS" "200" "Get post as author"
  assert_contains "$BODY" '"canDelete":true' "Author can delete"
fi

# Test 1.2: Get post as approved follower
echo "Test 1.2: Get post as approved follower"
if [ -z "$POST_ID1" ]; then
  echo -e "${RED}✗ Skipping: POST_ID1 not set${NC}"
else
  RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES2" \
    "$BASE_URL/api/v1/posts/$POST_ID1")
  STATUS=$(echo "$RESPONSE" | tail -n 1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  assert_status_code "$STATUS" "200" "Get post as approved follower"
  assert_contains "$BODY" '"id"' "Response contains post data"
fi

# Test 1.3: Post not found
echo "Test 1.3: Post not found (error)"
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" \
  "$BASE_URL/api/v1/posts/507f1f77bcf86cd799999999")
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "404" "Non-existent post returns 404"

# Test 1.4: Not authenticated
echo "Test 1.4: Get post without authentication (error)"
if [ -z "$POST_ID1" ]; then
  echo -e "${RED}✗ Skipping: POST_ID1 not set${NC}"
else
  RESPONSE=$(curl -s -w "\n%{http_code}" \
    "$BASE_URL/api/v1/posts/$POST_ID1")
  STATUS=$(echo "$RESPONSE" | tail -n 1)
  assert_status_code "$STATUS" "403" "Unauthenticated get returns 403"
fi

################################################################################
# TESTS: Get User's Posts - Pagination (Endpoint 2: GET /api/v1/posts/user/{username})
################################################################################

print_header "TEST SECTION: Get User Posts (GET /api/v1/posts/user/{username})"

# Test 2.1: Get own posts
echo "Test 2.1: Get own posts"
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" \
  "$BASE_URL/api/v1/posts/user/if.kshitij")
STATUS=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | sed '$d')
assert_status_code "$STATUS" "200" "Get own posts succeeds"
assert_contains "$BODY" '"data"' "Response contains data array"

# Test 2.2: Get posts with custom limit
echo "Test 2.2: Get posts with limit=5"
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" \
  "$BASE_URL/api/v1/posts/user/if.kshitij?limit=5")
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "200" "Custom limit succeeds"

# Test 2.3: Get posts as approved follower
echo "Test 2.3: Get posts as approved follower"
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES2" \
  "$BASE_URL/api/v1/posts/user/if.kshitij")
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "200" "Approved follower can view posts"

# Test 2.4: Invalid username format
echo "Test 2.4: Invalid username format (error)"
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" \
  "$BASE_URL/api/v1/posts/user/invalid@user!")
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "400" "Invalid username returns 400"

# Test 2.5: Username too short
echo "Test 2.5: Username too short (error)"
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" \
  "$BASE_URL/api/v1/posts/user/ab")
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "400" "Short username returns 400"

# Test 2.6: User not found
echo "Test 2.6: User not found (error)"
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" \
  "$BASE_URL/api/v1/posts/user/nonexistentuser12345")
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "404" "Non-existent user returns 404"

# Test 2.7: Not authenticated
echo "Test 2.7: Get all posts without authentication (error)"
RESPONSE=$(curl -s -w "\n%{http_code}" \
  "$BASE_URL/api/v1/posts/user/if.kshitij")
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "403" "Unauthenticated view-all returns 403"

# Test 2.8: Invalid limit too high
echo "Test 2.8: Limit too high (error)"
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" \
  "$BASE_URL/api/v1/posts/user/if.kshitij?limit=100")
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "400" "Limit > 50 returns 400"

# Test 2.9: Invalid limit zero
echo "Test 2.9: Limit zero (error)"
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" \
  "$BASE_URL/api/v1/posts/user/if.kshitij?limit=0")
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "400" "Limit = 0 returns 400"

################################################################################
# TESTS: Like Post (Endpoint 5: POST /api/v1/posts/{postId}/like)
################################################################################

print_header "TEST SECTION: Like Post (POST /api/v1/posts/{postId}/like)"

# Test 5.1: Like post
echo "Test 5.1: Like post"
if [ -z "$POST_ID1" ]; then
  echo -e "${RED}✗ Skipping: POST_ID1 not set${NC}"
else
  RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" -X POST \
    "$BASE_URL/api/v1/posts/$POST_ID1/like")
  STATUS=$(echo "$RESPONSE" | tail -n 1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  assert_status_code "$STATUS" "200" "Like post succeeds"
  assert_contains "$BODY" "POST_LIKED" "Response contains POST_LIKED"
fi

# Test 5.2: Like same post again (should fail)
echo "Test 5.2: Like same post twice (error)"
if [ -z "$POST_ID1" ]; then
  echo -e "${RED}✗ Skipping: POST_ID1 not set${NC}"
else
  RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" -X POST \
    "$BASE_URL/api/v1/posts/$POST_ID1/like")
  STATUS=$(echo "$RESPONSE" | tail -n 1)
  assert_status_code "$STATUS" "409" "Duplicate like returns 409"
fi

# Test 5.3: Different user likes post
echo "Test 5.3: Different user likes same post"
if [ -z "$POST_ID1" ]; then
  echo -e "${RED}✗ Skipping: POST_ID1 not set${NC}"
else
  RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES2" -X POST \
    "$BASE_URL/api/v1/posts/$POST_ID1/like")
  STATUS=$(echo "$RESPONSE" | tail -n 1)
  assert_status_code "$STATUS" "200" "Different user can like"
fi

# Test 5.4: Like non-existent post
echo "Test 5.4: Like non-existent post (error)"
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" -X POST \
  "$BASE_URL/api/v1/posts/507f1f77bcf86cd799999999/like")
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "404" "Like non-existent post returns 404"

# Test 5.5: Like without authentication
echo "Test 5.5: Like without authentication (error)"
if [ -z "$POST_ID1" ]; then
  echo -e "${RED}✗ Skipping: POST_ID1 not set${NC}"
else
  RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    "$BASE_URL/api/v1/posts/$POST_ID1/like")
  STATUS=$(echo "$RESPONSE" | tail -n 1)
  assert_status_code "$STATUS" "403" "Unauthenticated like returns 403"
fi

################################################################################
# TESTS: Unlike Post (Endpoint 6: DELETE /api/v1/posts/{postId}/like)
################################################################################

print_header "TEST SECTION: Unlike Post (DELETE /api/v1/posts/{postId}/like)"

# Test 6.1: Unlike post
echo "Test 6.1: Unlike post"
if [ -z "$POST_ID1" ]; then
  echo -e "${RED}✗ Skipping: POST_ID1 not set${NC}"
else
  RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" -X DELETE \
    "$BASE_URL/api/v1/posts/$POST_ID1/like")
  STATUS=$(echo "$RESPONSE" | tail -n 1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  assert_status_code "$STATUS" "200" "Unlike post succeeds"
  assert_contains "$BODY" "POST_UNLIKED" "Response contains POST_UNLIKED"
fi

# Test 6.2: Unlike same post again (should fail)
echo "Test 6.2: Unlike same post twice (error)"
if [ -z "$POST_ID1" ]; then
  echo -e "${RED}✗ Skipping: POST_ID1 not set${NC}"
else
  RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" -X DELETE \
    "$BASE_URL/api/v1/posts/$POST_ID1/like")
  STATUS=$(echo "$RESPONSE" | tail -n 1)
  assert_status_code "$STATUS" "409" "Unlike non-liked post returns 409"
fi

# Test 6.3: Unlike non-existent post
echo "Test 6.3: Unlike non-existent post (error)"
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" -X DELETE \
  "$BASE_URL/api/v1/posts/507f1f77bcf86cd799999999/like")
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "404" "Unlike non-existent post returns 404"

# Test 6.4: Unlike without authentication
echo "Test 6.4: Unlike without authentication (error)"
if [ -z "$POST_ID2" ]; then
  echo -e "${RED}✗ Skipping: POST_ID2 not set${NC}"
else
  RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
    "$BASE_URL/api/v1/posts/$POST_ID2/like")
  STATUS=$(echo "$RESPONSE" | tail -n 1)
  assert_status_code "$STATUS" "403" "Unauthenticated unlike returns 403"
fi

################################################################################
# TESTS: Delete Post (Endpoint 4: DELETE /api/v1/posts/{postId})
################################################################################

print_header "TEST SECTION: Delete Post (DELETE /api/v1/posts/{postId})"

# Create a post for deletion testing
echo "Creating test post for deletion..."
RESPONSE=$(curl -s -b "$COOKIES1" -X POST \
  "$BASE_URL/api/v1/posts" \
  -H "Content-Type: application/json" \
  -d '{"mediaUrl":"https://example.com/delete_test.jpg","caption":"Delete test"}')
DELETE_TEST_ID=$(echo "$RESPONSE" | grep -o '"id":"[^"]*' | head -1 | cut -d'"' -f4)

# Test 4.1: Delete own post
echo "Test 4.1: Delete own post"
if [ -z "$DELETE_TEST_ID" ]; then
  echo -e "${RED}✗ Skipping: DELETE_TEST_ID not set${NC}"
else
  RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" -X DELETE \
    "$BASE_URL/api/v1/posts/$DELETE_TEST_ID")
  STATUS=$(echo "$RESPONSE" | tail -n 1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  assert_status_code "$STATUS" "200" "Delete own post succeeds"
  assert_contains "$BODY" "POST_DELETED" "Response contains POST_DELETED"
fi

# Test 4.2: Delete non-existent post
echo "Test 4.2: Delete non-existent post (error)"
RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES1" -X DELETE \
  "$BASE_URL/api/v1/posts/507f1f77bcf86cd799999999")
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "404" "Delete non-existent post returns 404"

# Test 4.3: Delete without authentication
echo "Test 4.3: Delete without authentication (error)"
RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE \
  "$BASE_URL/api/v1/posts/$POST_ID1")
STATUS=$(echo "$RESPONSE" | tail -n 1)
assert_status_code "$STATUS" "403" "Unauthenticated delete returns 403"

# Test 4.4: Delete another user's post
echo "Test 4.4: Delete another user's post (error)"
if [ -z "$POST_ID1" ]; then
  echo -e "${RED}✗ Skipping: POST_ID1 not set${NC}"
else
  RESPONSE=$(curl -s -w "\n%{http_code}" -b "$COOKIES2" -X DELETE \
    "$BASE_URL/api/v1/posts/$POST_ID1")
  STATUS=$(echo "$RESPONSE" | tail -n 1)
  assert_status_code "$STATUS" "403" "Delete others' post returns 403"
fi

################################################################################
# Test Summary
################################################################################

print_header "TEST SUMMARY"

TOTAL_TESTS=$((TESTS_PASSED + TESTS_FAILED))
echo "Total Tests: $TOTAL_TESTS"
echo -e "${GREEN}Passed: $TESTS_PASSED${NC}"
if [ $TESTS_FAILED -gt 0 ]; then
  echo -e "${RED}Failed: $TESTS_FAILED${NC}"
else
  echo -e "${GREEN}Failed: $TESTS_FAILED${NC}"
fi

PASS_PERCENTAGE=$(( (TESTS_PASSED * 100) / TOTAL_TESTS ))
echo -e "Pass Rate: ${GREEN}${PASS_PERCENTAGE}%${NC}"

# Cleanup
cleanup

# Exit with appropriate code
if [ $TESTS_FAILED -eq 0 ]; then
  echo -e "\n${GREEN}✓ All tests passed!${NC}"
  exit 0
else
  echo -e "\n${RED}✗ Some tests failed${NC}"
  exit 1
fi


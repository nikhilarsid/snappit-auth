#!/bin/bash

# Story Module Automated Test Suite
# Tests all 4 endpoints with happy path and error scenarios

set -e

BASE_URL="http://localhost:8080"
COOKIES="cookies.txt"
PASSED=0
FAILED=0
TEST_COUNT=0

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test users
USER1_USERNAME="if.kshitij"
USER1_EMAIL="kshitij@gmail.com"
USER1_PASSWORD="Dettcmpw123?"

USER2_USERNAME="hhoehunterr"
USER2_EMAIL="kshitij2@gmail.com"
USER2_PASSWORD="Dettcmpw123?"

# ===== Helper Functions =====

login_user() {
  local email=$1
  local password=$2
  local user_label=$3
  
  echo -e "${YELLOW}Logging in $user_label...${NC}"
  RESPONSE=$(curl -s -X POST "$BASE_URL/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"usernameOrEmail\": \"$email\", \"password\": \"$password\"}" \
    -c "$COOKIES" \
    -w "\n%{http_code}")
  
  # Extract HTTP code
  HTTP_CODE=$(echo "$RESPONSE" | tail -1)
  BODY=$(echo "$RESPONSE" | sed '$d')
  
  # Verify login successful (200 OK)
  if [ "$HTTP_CODE" = "200" ]; then
    echo -e "${GREEN}✓ Logged in: $user_label${NC}"
  else
    echo -e "${RED}✗ Login failed for $user_label (HTTP $HTTP_CODE)${NC}"
    echo -e "${RED}Response: $BODY${NC}"
    exit 1
  fi
}

test_endpoint() {
  local test_name=$1
  local method=$2
  local endpoint=$3
  local data=$4
  local expected_status=$5
  local require_auth=${6:-true}
  
  TEST_COUNT=$((TEST_COUNT + 1))
  
  # Build curl command
  local curl_cmd="curl -s -X $method \"$BASE_URL$endpoint\" \
    -H \"Content-Type: application/json\""
  
  if [ "$require_auth" != "false" ]; then
    curl_cmd="$curl_cmd -b \"$COOKIES\""
  fi
  
  if [ ! -z "$data" ]; then
    curl_cmd="$curl_cmd -d '$data'"
  fi
  
  curl_cmd="$curl_cmd -w \"\n%{http_code}\""
  
  # Execute request
  local response=$(eval "$curl_cmd")
  local http_code=$(echo "$response" | tail -1)
  local body=$(echo "$response" | sed '$d')
  
  # Check result
  if [ "$http_code" = "$expected_status" ]; then
    PASSED=$((PASSED + 1))
    echo -e "${GREEN}✓ Test $TEST_COUNT: $test_name (HTTP $http_code)${NC}"
  else
    FAILED=$((FAILED + 1))
    echo -e "${RED}✗ Test $TEST_COUNT: $test_name (Expected $expected_status, got $http_code)${NC}"
    echo -e "${RED}  Response: $(echo "$body" | head -c 100)...${NC}"
  fi
}

create_story() {
  local days_ahead=$1
  local media_url=${2:-"https://example.com/story.jpg"}
  
  # Calculate expiration date
  if [[ "$OSTYPE" == "darwin"* ]]; then
    EXPIRES_AT=$(date -u -v+${days_ahead}d +"%Y-%m-%dT%H:%M:%SZ")
  else
    EXPIRES_AT=$(date -u -d "+${days_ahead} days" +"%Y-%m-%dT%H:%M:%SZ")
  fi
  
  local response=$(curl -s -X POST "$BASE_URL/api/v1/stories" \
    -H "Content-Type: application/json" \
    -b "$COOKIES" \
    -d "{\"mediaUrl\": \"$media_url\", \"expiresAt\": \"$EXPIRES_AT\"}" \
    -w "\n%{http_code}")
  
  local http_code=$(echo "$response" | tail -1)
  local body=$(echo "$response" | sed '$d')
  
  if [ "$http_code" = "201" ]; then
    local story_id=$(echo "$body" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "$story_id"
  else
    echo ""
  fi
}

# ===== Test Suite =====

echo "================================================"
echo "STORY MODULE TEST SUITE"
echo "================================================"
echo ""

# Clean up any previous cookies
rm -f "$COOKIES"

# ===== Phase 1: LOGIN AND SETUP =====
echo ""
echo "PHASE 1: Authentication Setup"
echo "---"
login_user "$USER1_EMAIL" "$USER1_PASSWORD" "User 1 (if.kshitij)"

# ===== Phase 2: CREATE STORY TESTS =====
echo ""
echo "PHASE 2: Create Story Tests (POST /api/v1/stories)"
echo "---"

# Test 2.1: Create story - Happy path
TOMORROW=$(if [[ "$OSTYPE" == "darwin"* ]]; then date -u -v+1d +"%Y-%m-%dT%H:%M:%SZ"; else date -u -d "+1 day" +"%Y-%m-%dT%H:%M:%SZ"; fi)
test_endpoint "Create story - Happy path (1 day expiration)" "POST" "/api/v1/stories" \
  "{\"mediaUrl\": \"https://example.com/story_happy.jpg\", \"expiresAt\": \"$TOMORROW\"}" "201"
STORY_ID_1=$(create_story 1 "https://example.com/story_1.jpg")

# Test 2.2: Create story - 2 day expiration
TWO_DAYS=$(if [[ "$OSTYPE" == "darwin"* ]]; then date -u -v+2d +"%Y-%m-%dT%H:%M:%SZ"; else date -u -d "+2 days" +"%Y-%m-%dT%H:%M:%SZ"; fi)
test_endpoint "Create story - 2 day expiration" "POST" "/api/v1/stories" \
  "{\"mediaUrl\": \"https://example.com/story_2day.jpg\", \"expiresAt\": \"$TWO_DAYS\"}" "201"
STORY_ID_2=$(create_story 2 "https://example.com/story_2.jpg")

# Test 2.3: Create story - Missing mediaUrl
test_endpoint "Create story - Missing mediaUrl (400)" "POST" "/api/v1/stories" \
  "{\"expiresAt\": \"$TOMORROW\"}" "400"

# Test 2.4: Create story - Blank mediaUrl
test_endpoint "Create story - Blank mediaUrl (400)" "POST" "/api/v1/stories" \
  "{\"mediaUrl\": \"\", \"expiresAt\": \"$TOMORROW\"}" "400"

# Test 2.5: Create story - Missing expiresAt
test_endpoint "Create story - Missing expiresAt (400)" "POST" "/api/v1/stories" \
  "{\"mediaUrl\": \"https://example.com/story.jpg\"}" "400"

# Test 2.6: Create story - expiresAt in past
YESTERDAY=$(if [[ "$OSTYPE" == "darwin"* ]]; then date -u -v-1d +"%Y-%m-%dT%H:%M:%SZ"; else date -u -d "-1 day" +"%Y-%m-%dT%H:%M:%SZ"; fi)
test_endpoint "Create story - expiresAt in past (400)" "POST" "/api/v1/stories" \
  "{\"mediaUrl\": \"https://example.com/story.jpg\", \"expiresAt\": \"$YESTERDAY\"}" "400"

# Test 2.7: Create story - Not authenticated
test_endpoint "Create story - Not authenticated (403)" "POST" "/api/v1/stories" \
  "{\"mediaUrl\": \"https://example.com/story.jpg\", \"expiresAt\": \"$TOMORROW\"}" "403" "false"

# ===== Phase 3: GET SINGLE STORY TESTS =====
echo ""
echo "PHASE 3: Get Single Story Tests (GET /api/v1/stories/{storyId})"
echo "---"

# Re-login for authenticated tests
login_user "$USER1_EMAIL" "$USER1_PASSWORD" "User 1"
STORY_ID_FOR_FUN=$(create_story 1 "https://example.com/story_for_viewing.jpg")

# Test 3.1: Get story - As author (happy path)
test_endpoint "Get story - As author (200)" "GET" "/api/v1/stories/$STORY_ID_FOR_FUN" "" "200"

# Test 3.2: Get story - Not found
test_endpoint "Get story - Not found (404)" "GET" "/api/v1/stories/507f1f77bcf86cd799438888" "" "404"

# Test 3.3: Get story - Not authenticated
test_endpoint "Get story - Not authenticated (403)" "GET" "/api/v1/stories/$STORY_ID_FOR_FUN" "" "403" "false"

# Test 3.4: Get story - As approved follower
rm -f "$COOKIES"
login_user "$USER2_EMAIL" "$USER2_PASSWORD" "User 2 (approved follower)"
# Create and test a story from User 1
rm -f "$COOKIES"
login_user "$USER1_EMAIL" "$USER1_PASSWORD" "User 1"
STORY_ID_FOR_FOLLOWER=$(create_story 1 "https://example.com/story_for_follower.jpg")
rm -f "$COOKIES"
login_user "$USER2_EMAIL" "$USER2_PASSWORD" "User 2"
test_endpoint "Get story - As approved follower (200)" "GET" "/api/v1/stories/$STORY_ID_FOR_FOLLOWER" "" "200"

# ===== Phase 4: GET USER'S STORIES (PAGINATED) TESTS =====
echo ""
echo "PHASE 4: Get User's Stories Tests (GET /api/v1/stories/user/{username})"
echo "---"

# Login as User 1 for rest of tests
rm -f "$COOKIES"
login_user "$USER1_EMAIL" "$USER1_PASSWORD" "User 1"

# Test 4.1: Get user's stories - Happy path (as author)
test_endpoint "Get user's stories - As author (200)" "GET" "/api/v1/stories/user/$USER1_USERNAME?limit=20" "" "200"

# Test 4.2: Get user's stories - With custom limit
test_endpoint "Get user's stories - Limit 5 (200)" "GET" "/api/v1/stories/user/$USER1_USERNAME?limit=5" "" "200"

# Test 4.3: Get user's stories - Invalid username format (too short)
test_endpoint "Get user's stories - Invalid username (400)" "GET" "/api/v1/stories/user/ab?limit=20" "" "400"

# Test 4.4: Get user's stories - User not found
test_endpoint "Get user's stories - User not found (404)" "GET" "/api/v1/stories/user/nonexistent_user_99999?limit=20" "" "404"

# Test 4.5: Get user's stories - Invalid limit (too high)
test_endpoint "Get user's stories - Invalid limit (400)" "GET" "/api/v1/stories/user/$USER1_USERNAME?limit=100" "" "400"

# Test 4.6: Get user's stories - Invalid limit (zero)
test_endpoint "Get user's stories - Limit zero (400)" "GET" "/api/v1/stories/user/$USER1_USERNAME?limit=0" "" "400"

# Test 4.7: Get user's stories - Not authenticated
test_endpoint "Get user's stories - Not authenticated (403)" "GET" "/api/v1/stories/user/$USER1_USERNAME?limit=20" "" "403" "false"

# Test 4.8: Get user's stories - As approved follower
rm -f "$COOKIES"
login_user "$USER2_EMAIL" "$USER2_PASSWORD" "User 2 (approved follower)"
test_endpoint "Get user's stories - As approved follower (200)" "GET" "/api/v1/stories/user/$USER1_USERNAME?limit=20" "" "200"

# ===== Phase 5: DELETE STORY TESTS =====
echo ""
echo "PHASE 5: Delete Story Tests (DELETE /api/v1/stories/{storyId})"
echo "---"

# Login as User 1 to delete own stories
rm -f "$COOKIES"
login_user "$USER1_EMAIL" "$USER1_PASSWORD" "User 1"

# Create story for deletion
STORY_ID_FOR_DELETE=$(create_story 1 "https://example.com/story_for_delete.jpg")

# Test 5.1: Delete story - As author (happy path)
test_endpoint "Delete story - As author (200)" "DELETE" "/api/v1/stories/$STORY_ID_FOR_DELETE" "" "200"

# Test 5.2: Delete story - Not found
test_endpoint "Delete story - Not found (404)" "DELETE" "/api/v1/stories/507f1f77bcf86cd799438888" "" "404"

# Test 5.3: Delete story - Not authenticated
test_endpoint "Delete story - Not authenticated (403)" "DELETE" "/api/v1/stories/$STORY_ID_FOR_DELETE" "" "403" "false"

# Test 5.4: Delete story - Not author (approved follower tries)
STORY_ID_FOR_NOTAUTH=$(create_story 1 "https://example.com/story_not_auth.jpg")
rm -f "$COOKIES"
login_user "$USER2_EMAIL" "$USER2_PASSWORD" "User 2"
test_endpoint "Delete story - Not author (403)" "DELETE" "/api/v1/stories/$STORY_ID_FOR_NOTAUTH" "" "403"

# ===== Phase 6: INTEGRATION TESTS =====
echo ""
echo "PHASE 6: Integration and Edge Case Tests"
echo "---"

# Login User 1
rm -f "$COOKIES"
login_user "$USER1_EMAIL" "$USER1_PASSWORD" "User 1"

# Test 6.1: Create story and immediately view
STORY_ID_INT=$(create_story 1 "https://example.com/integration_test.jpg")
test_endpoint "Integration - Create then view (200)" "GET" "/api/v1/stories/$STORY_ID_INT" "" "200"

# Test 6.2: Create story and immediately delete
STORY_ID_INT2=$(create_story 1 "https://example.com/integration_delete.jpg")
test_endpoint "Integration - Create then delete (200)" "DELETE" "/api/v1/stories/$STORY_ID_INT2" "" "200"

# Test 6.3: Try to view deleted story
test_endpoint "Integration - View deleted story (404)" "GET" "/api/v1/stories/$STORY_ID_INT2" "" "404"

# Test 6.4: Get paginated stories with pagination cursor
RESPONSE=$(curl -s -X GET "$BASE_URL/api/v1/stories/user/$USER1_USERNAME?limit=1" -b "$COOKIES")
NEXT_CURSOR=$(echo "$RESPONSE" | grep -o '"nextCursor":"[^"]*"' | cut -d'"' -f4)
if [ ! -z "$NEXT_CURSOR" ]; then
  test_endpoint "Integration - Pagination with cursor (200)" "GET" "/api/v1/stories/user/$USER1_USERNAME?limit=1&cursor=$NEXT_CURSOR" "" "200"
fi

# Test 6.5: Long URL (verify server accepts longer URLs)
LONG_URL="https://example.com/$(printf 'a%.0s' {1..500})/story.jpg"
test_endpoint "Integration - Long mediaURL (200)" "POST" "/api/v1/stories" \
  "{\"mediaUrl\": \"$LONG_URL\", \"expiresAt\": \"$TOMORROW\"}" "201"

# ===== Results Summary =====
echo ""
echo "================================================"
echo "TEST RESULTS"
echo "================================================"
echo -e "Total Tests: $TEST_COUNT"
echo -e "${GREEN}Passed: $PASSED${NC}"
echo -e "${RED}Failed: $FAILED${NC}"

if [ $FAILED -eq 0 ]; then
  echo -e "${GREEN}✓ ALL TESTS PASSED${NC}"
  exit 0
else
  echo -e "${RED}✗ SOME TESTS FAILED${NC}"
  exit 1
fi

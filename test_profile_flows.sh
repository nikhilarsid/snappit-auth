#!/bin/bash

# Profile Module Testing Script
# Tests all endpoints and stores results in a file

OUTPUT_FILE="/tmp/profile_test_results.txt"
> "$OUTPUT_FILE"  # Clear file

echo "========================================" >> "$OUTPUT_FILE"
echo "PROFILE MODULE TEST RESULTS" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# JWT Tokens (from earlier login)
TOKEN_TEST_USER="eyJhbGciOiJIUzI1NiJ9.eyJfaWQiOiI2OTkwZTJmMzkwZjk2ODJjZjAyZDkxN2IiLCJpYXQiOjE3NzExMDQwMDQsImV4cCI6MTc3MTcwODgwNH0.aL_FiUN3roUSBE2w8x6SUo6c38Gk7Pk0qOG4CGzyWtk"
TOKEN_ALICE="eyJhbGciOiJIUzI1NiJ9.eyJfaWQiOiI2OTkwZTM0ZDkwZjk2ODJjZjAyZDkxN2UiLCJpYXQiOjE3NzExMDQwMTEsImV4cCI6MTc3MTcwODgxMX0.vbzN4C0L_GvW7XDL_hdfz0upxDkno4OlRGNhuwLb36M"
TOKEN_BOB="eyJhbGciOiJIUzI1NiJ9.eyJfaWQiOiI2OTkwZTNiMTkwZjk2ODJjZjAyZDkxODAiLCJpYXQiOjE3NzExMDQwMTcsImV4cCI6MTc3MTcwODgxN30.tcf8sr9vV6FyMPF8hZGbHVutteoFrJBhH1GF9UHVSFc"

echo "HAPPY PATH TESTS" >> "$OUTPUT_FILE"
echo "==================" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test 1: Get public profile
echo "Test 1: Get public profile - test_user_one" >> "$OUTPUT_FILE"
curl -s http://localhost:8080/v1/profile/test_user_one >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test 2: Get public profile - alice.johnson  
echo "Test 2: Get public profile - alice.johnson" >> "$OUTPUT_FILE"
curl -s http://localhost:8080/v1/profile/alice.johnson >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test 3: Get public profile - bob.smith
echo "Test 3: Get public profile - bob.smith" >> "$OUTPUT_FILE"
curl -s http://localhost:8080/v1/profile/bob.smith >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test 4: Get own profile with token
echo "Test 4: Get own profile - test_user_one (authenticated)" >> "$OUTPUT_FILE"
curl -s -H "Authorization: Bearer $TOKEN_TEST_USER" http://localhost:8080/v1/profile/my >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test 5: Update profile - name only
echo "Test 5: Update profile - name only (test_user_one)" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_TEST_USER" \
  -d '{"name": "Updated Test User"}' >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test 6: Verify update
echo "Test 6: Verify update - get own profile after name update" >> "$OUTPUT_FILE"
curl -s -H "Authorization: Bearer $TOKEN_TEST_USER" http://localhost:8080/v1/profile/my >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test 7: Update profile - bio
echo "Test 7: Update profile - bio only (test_user_one)" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_TEST_USER" \
  -d '{"bio": "Software engineer & API tester"}' >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test 8: Update profile - avatar
echo "Test 8: Update profile - avatar only (test_user_one)" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_TEST_USER" \
  -d '{"avatarUrl": "https://example.com/avatar.jpg"}' >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test 9: Update multiple fields
echo "Test 9: Update profile - multiple fields (alice.johnson)" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_ALICE" \
  -d '{"name": "Alice J.", "bio": "Developer", "avatarUrl": "https://api.example.com/avatars/alice.jpg"}' >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Test 10: Verify alice update
echo "Test 10: Verify alice profile after update" >> "$OUTPUT_FILE"
curl -s -H "Authorization: Bearer $TOKEN_ALICE" http://localhost:8080/v1/profile/my >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

echo "" >> "$OUTPUT_FILE"
echo "ERROR CASE TESTS" >> "$OUTPUT_FILE"
echo "==================" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 1: Invalid username format - too short
echo "Error Test 1: Invalid username format (too short)" >> "$OUTPUT_FILE"
curl -s http://localhost:8080/v1/profile/ab >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 2: Invalid username - special character
echo "Error Test 2: Invalid username (with @)" >> "$OUTPUT_FILE"
curl -s http://localhost:8080/v1/profile/user@name >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 3: User not found
echo "Error Test 3: User not found" >> "$OUTPUT_FILE"
curl -s http://localhost:8080/v1/profile/nonexistent_user_xyz >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 4: Get own profile - no token
echo "Error Test 4: Get own profile without token (401)" >> "$OUTPUT_FILE"
curl -s http://localhost:8080/v1/profile/my >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 5: Get own profile - invalid token
echo "Error Test 5: Get own profile with invalid token (401)" >> "$OUTPUT_FILE"
curl -s -H "Authorization: Bearer invalid.token.here" http://localhost:8080/v1/profile/my >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 6: Update - no valid fields
echo "Error Test 6: Update profile - no valid fields" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_BOB" \
  -d '{}' >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 7: Update - all blank fields
echo "Error Test 7: Update profile - all blank fields" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_BOB" \
  -d '{"name": "", "bio": "", "avatarUrl": ""}' >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 8: Update - name too long
echo "Error Test 8: Update profile - name too long (>100 chars)" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_BOB" \
  -d '{"name": "This is a very long name that exceeds the maximum limit of 100 characters because we want to test validation of the name field in our API"}' >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 9: Update - bio too long
echo "Error Test 9: Update profile - bio too long (>160 chars)" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_BOB" \
  -d '{"bio": "This is a very long bio that exceeds the maximum limit of 160 characters which is the typical social media bio length and we want to test that validation works correctly here"}' >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 10: Update - no token
echo "Error Test 10: Update profile without token (401)" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -d '{"name": "New Name"}' >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 11: Update - invalid token
echo "Error Test 11: Update profile with invalid token (401)" >> "$OUTPUT_FILE"
curl -s -X PATCH http://localhost:8080/v1/profile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer invalid.token.here" \
  -d '{"name": "New Name"}' >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Error Test 12: Public profile for non-existent user
echo "Error Test 12: Get public profile - non-existent user with valid format" >> "$OUTPUT_FILE"
curl -s http://localhost:8080/v1/profile/unknown_user_12345 >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

echo "========================================" >> "$OUTPUT_FILE"
echo "TEST COMPLETE" >> "$OUTPUT_FILE"
echo "========================================" >> "$OUTPUT_FILE"

echo "Results saved to $OUTPUT_FILE"
cat "$OUTPUT_FILE"


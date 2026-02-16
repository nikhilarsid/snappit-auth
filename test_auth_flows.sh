#!/bin/bash

# Auth Module - Comprehensive Curl Testing Script
# Note: Run commands manually one at a time, observing outputs

BASE_URL="http://localhost:8080"
TOKEN=""
OTP_CODE=""

# Color codes for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Auth Module - Complete Flow Testing${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# ========================================
# HAPPY PATH TESTS
# ========================================

echo -e "${YELLOW}[HAPPY PATH] Testing Signup Flow${NC}"
echo ""
echo -e "${GREEN}1. SIGNUP - Create New User${NC}"
echo "Endpoint: POST /v1/auth/signup"
echo "Command:"
echo 'curl -X POST http://localhost:8080/v1/auth/signup \\'
echo '  -H "Content-Type: application/json" \\'
echo '  -d '"'"'{"name": "Test User One", "username": "test_user_one", "email": "testuser1@example.com", "password": "TestPass@123"}'"'"' \\'
echo '  -v'
echo ""
read -p "Press Enter to run command..."
SIGNUP_RESULT=$(curl -s -X POST http://localhost:8080/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name": "Test User One", "username": "test_user_one", "email": "testuser1@example.com", "password": "TestPass@123"}' \
  -v 2>&1)
echo "$SIGNUP_RESULT"
echo ""
echo "---"
echo ""

echo -e "${GREEN}2. LOGIN - Login with Email${NC}"
echo "Endpoint: POST /v1/auth/login"
echo "Command:"
echo 'curl -X POST http://localhost:8080/v1/auth/login \\'
echo '  -H "Content-Type: application/json" \\'
echo '  -d '"'"'{"usernameOrEmail": "testuser1@example.com", "password": "TestPass@123"}'"'"' \\'
echo '  -v'
echo ""
read -p "Press Enter to run command..."
LOGIN_RESULT=$(curl -s -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail": "testuser1@example.com", "password": "TestPass@123"}' \
  -v 2>&1)
echo "$LOGIN_RESULT"
echo ""
echo "---"
echo ""

echo -e "${GREEN}3. LOGIN - Login with Username${NC}"
echo "Endpoint: POST /v1/auth/login"
echo "Command:"
echo 'curl -X POST http://localhost:8080/v1/auth/login \\'
echo '  -H "Content-Type: application/json" \\'
echo '  -d '"'"'{"usernameOrEmail": "test_user_one", "password": "TestPass@123"}'"'"' \\'
echo '  -v'
echo ""
read -p "Press Enter to run command..."
LOGIN_USERNAME=$(curl -s -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail": "test_user_one", "password": "TestPass@123"}' \
  -v 2>&1)
echo "$LOGIN_USERNAME"
echo ""
echo "---"
echo ""

echo -e "${GREEN}4. LOGOUT - Clear Authentication${NC}"
echo "Endpoint: POST /v1/auth/logout"
echo "Command:"
echo 'curl -X POST http://localhost:8080/v1/auth/logout \\'
echo '  -v'
echo ""
read -p "Press Enter to run command..."
LOGOUT_RESULT=$(curl -s -X POST http://localhost:8080/v1/auth/logout -v 2>&1)
echo "$LOGOUT_RESULT"
echo ""
echo "---"
echo ""

# ========================================
# FORGOT PASSWORD FLOW
# ========================================

echo -e "${YELLOW}[HAPPY PATH] Testing Forgot Password Flow${NC}"
echo ""
echo -e "${GREEN}5. FORGOT PASSWORD INIT - Request OTP${NC}"
echo "Endpoint: POST /v1/auth/forgot-password/init"
echo "Command:"
echo 'curl -X POST http://localhost:8080/v1/auth/forgot-password/init \\'
echo '  -H "Content-Type: application/json" \\'
echo '  -d '"'"'{"email": "testuser1@example.com"}'"'"' \\'
echo '  -v'
echo ""
read -p "Press Enter to run command..."
FORGOT_INIT=$(curl -s -X POST http://localhost:8080/v1/auth/forgot-password/init \
  -H "Content-Type: application/json" \
  -d '{"email": "testuser1@example.com"}' \
  -v 2>&1)
echo "$FORGOT_INIT"
echo ""
echo -e "${YELLOW}>>> IMPORTANT: Copy the OTP code from the console output above <<<${NC}"
echo ""
read -p "Enter the OTP code displayed: " OTP_CODE
echo ""
echo "---"
echo ""

echo -e "${GREEN}6. FORGOT PASSWORD VERIFY - Verify OTP${NC}"
echo "Endpoint: POST /v1/auth/forgot-password/verify"
echo "Using OTP: $OTP_CODE"
echo "Command:"
echo 'curl -X POST http://localhost:8080/v1/auth/forgot-password/verify \\'
echo '  -H "Content-Type: application/json" \\'
echo '  -d '"'"'{"email": "testuser1@example.com", "code": "'"$OTP_CODE"'"}'"'"' \\'
echo '  -v'
echo ""
read -p "Press Enter to run command..."
FORGOT_VERIFY=$(curl -s -X POST http://localhost:8080/v1/auth/forgot-password/verify \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"testuser1@example.com\", \"code\": \"$OTP_CODE\"}" \
  -v 2>&1)
echo "$FORGOT_VERIFY"
echo ""
echo "---"
echo ""

echo -e "${GREEN}7. FORGOT PASSWORD RESET - Set New Password${NC}"
echo "Endpoint: POST /v1/auth/forgot-password/reset"
echo "Using OTP: $OTP_CODE"
echo "Command:"
echo 'curl -X POST http://localhost:8080/v1/auth/forgot-password/reset \\'
echo '  -H "Content-Type: application/json" \\'
echo '  -d '"'"'{"email": "testuser1@example.com", "code": "'"$OTP_CODE"'", "newPassword": "NewPass@456"}'"'"' \\'
echo '  -v'
echo ""
read -p "Press Enter to run command..."
FORGOT_RESET=$(curl -s -X POST http://localhost:8080/v1/auth/forgot-password/reset \
  -H "Content-Type: application/json" \
  -d "{\"email\": \"testuser1@example.com\", \"code\": \"$OTP_CODE\", \"newPassword\": \"NewPass@456\"}" \
  -v 2>&1)
echo "$FORGOT_RESET"
echo ""
echo "---"
echo ""

# ========================================
# ERROR/EDGE CASE TESTS
# ========================================

echo -e "${YELLOW}[ERROR CASES] Signup Errors${NC}"
echo ""

echo -e "${RED}8. SIGNUP ERROR - Weak Password${NC}"
echo "Endpoint: POST /v1/auth/signup"
echo "Command:"
echo 'curl -X POST http://localhost:8080/v1/auth/signup \\'
echo '  -H "Content-Type: application/json" \\'
echo '  -d '"'"'{"name": "Weak Pass User", "username": "weak_user1", "email": "weak1@example.com", "password": "weak"}'"'"' \\'
echo '  -v'
echo ""
read -p "Press Enter to run command..."
ERROR_WEAK=$(curl -s -X POST http://localhost:8080/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name": "Weak Pass User", "username": "weak_user1", "email": "weak1@example.com", "password": "weak"}' \
  -v 2>&1)
echo "$ERROR_WEAK"
echo ""
echo "---"
echo ""

echo -e "${RED}9. SIGNUP ERROR - Invalid Username Format${NC}"
echo "Endpoint: POST /v1/auth/signup"
echo "Command:"
echo 'curl -X POST http://localhost:8080/v1/auth/signup \\'
echo '  -H "Content-Type: application/json" \\'
echo '  -d '"'"'{"name": "Invalid User", "username": "user@invalid", "email": "invalid@example.com", "password": "TestPass@123"}'"'"' \\'
echo '  -v'
echo ""
read -p "Press Enter to run command..."
ERROR_USERNAME=$(curl -s -X POST http://localhost:8080/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name": "Invalid User", "username": "user@invalid", "email": "invalid@example.com", "password": "TestPass@123"}' \
  -v 2>&1)
echo "$ERROR_USERNAME"
echo ""
echo "---"
echo ""

echo -e "${RED}10. SIGNUP ERROR - Username Already Exists${NC}"
echo "Endpoint: POST /v1/auth/signup"
echo "Command:"
echo 'curl -X POST http://localhost:8080/v1/auth/signup \\'
echo '  -H "Content-Type: application/json" \\'
echo '  -d '"'"'{"name": "Duplicate User", "username": "test_user_one", "email": "different@example.com", "password": "TestPass@123"}'"'"' \\'
echo '  -v'
echo ""
read -p "Press Enter to run command..."
ERROR_DUP_USER=$(curl -s -X POST http://localhost:8080/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name": "Duplicate User", "username": "test_user_one", "email": "different@example.com", "password": "TestPass@123"}' \
  -v 2>&1)
echo "$ERROR_DUP_USER"
echo ""
echo "---"
echo ""

echo -e "${RED}11. SIGNUP ERROR - Email Already Registered${NC}"
echo "Endpoint: POST /v1/auth/signup"
echo "Command:"
echo 'curl -X POST http://localhost:8080/v1/auth/signup \\'
echo '  -H "Content-Type: application/json" \\'
echo '  -d '"'"'{"name": "Duplicate Email", "username": "different_user", "email": "testuser1@example.com", "password": "TestPass@123"}'"'"' \\'
echo '  -v'
echo ""
read -p "Press Enter to run command..."
ERROR_DUP_EMAIL=$(curl -s -X POST http://localhost:8080/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name": "Duplicate Email", "username": "different_user", "email": "testuser1@example.com", "password": "TestPass@123"}' \
  -v 2>&1)
echo "$ERROR_DUP_EMAIL"
echo ""
echo "---"
echo ""

echo -e "${RED}12. SIGNUP ERROR - Missing Required Fields${NC}"
echo "Endpoint: POST /v1/auth/signup"
echo "Command:"
echo 'curl -X POST http://localhost:8080/v1/auth/signup \\'
echo '  -H "Content-Type: application/json" \\'
echo '  -d '"'"'{"username": "test_user", "email": "test@example.com", "password": "TestPass@123"}'"'"' \\'
echo '  -v'
echo ""
read -p "Press Enter to run command..."
ERROR_MISSING=$(curl -s -X POST http://localhost:8080/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username": "test_user", "email": "test@example.com", "password": "TestPass@123"}' \
  -v 2>&1)
echo "$ERROR_MISSING"
echo ""
echo "---"
echo ""

# ========================================
# LOGIN ERROR CASES
# ========================================

echo -e "${YELLOW}[ERROR CASES] Login Errors${NC}"
echo ""

echo -e "${RED}13. LOGIN ERROR - User Not Found${NC}"
echo "Endpoint: POST /v1/auth/login"
echo "Command:"
echo 'curl -X POST http://localhost:8080/v1/auth/login \\'
echo '  -H "Content-Type: application/json" \\'
echo '  -d '"'"'{"usernameOrEmail": "nonexistent@example.com", "password": "TestPass@123"}'"'"' \\'
echo '  -v'
echo ""
read -p "Press Enter to run command..."
ERROR_LOGIN_NOT_FOUND=$(curl -s -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail": "nonexistent@example.com", "password": "TestPass@123"}' \
  -v 2>&1)
echo "$ERROR_LOGIN_NOT_FOUND"
echo ""
echo "---"
echo ""

echo -e "${RED}14. LOGIN ERROR - Wrong Password${NC}"
echo "Endpoint: POST /v1/auth/login"
echo "Command:"
echo 'curl -X POST http://localhost:8080/v1/auth/login \\'
echo '  -H "Content-Type: application/json" \\'
echo '  -d '"'"'{"usernameOrEmail": "test_user_one", "password": "WrongPassword@123"}'"'"' \\'
echo '  -v'
echo ""
read -p "Press Enter to run command..."
ERROR_LOGIN_WRONG=$(curl -s -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail": "test_user_one", "password": "WrongPassword@123"}' \
  -v 2>&1)
echo "$ERROR_LOGIN_WRONG"
echo ""
echo "---"
echo ""

echo -e "${RED}15. LOGIN ERROR - Missing Password${NC}"
echo "Endpoint: POST /v1/auth/login"
echo "Command:"
echo 'curl -X POST http://localhost:8080/v1/auth/login \\'
echo '  -H "Content-Type: application/json" \\'
echo '  -d '"'"'{"usernameOrEmail": "test_user_one"}'"'"' \\'
echo '  -v'
echo ""
read -p "Press Enter to run command..."
ERROR_LOGIN_MISSING=$(curl -s -X POST http://localhost:8080/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail": "test_user_one"}' \
  -v 2>&1)
echo "$ERROR_LOGIN_MISSING"
echo ""
echo "---"
echo ""

# ========================================
# FORGOT PASSWORD ERROR CASES
# ========================================

echo -e "${YELLOW}[ERROR CASES] Forgot Password Errors${NC}"
echo ""

echo -e "${RED}16. FORGOT PASSWORD INIT ERROR - User Not Found${NC}"
echo "Endpoint: POST /v1/auth/forgot-password/init"
echo "Command:"
echo 'curl -X POST http://localhost:8080/v1/auth/forgot-password/init \\'
echo '  -H "Content-Type: application/json" \\'
echo '  -d '"'"'{"email": "nonexistent@example.com"}'"'"' \\'
echo '  -v'
echo ""
read -p "Press Enter to run command..."
ERROR_FORGOT_NOT_FOUND=$(curl -s -X POST http://localhost:8080/v1/auth/forgot-password/init \
  -H "Content-Type: application/json" \
  -d '{"email": "nonexistent@example.com"}' \
  -v 2>&1)
echo "$ERROR_FORGOT_NOT_FOUND"
echo ""
echo "---"
echo ""

echo -e "${RED}17. FORGOT PASSWORD VERIFY ERROR - Invalid OTP${NC}"
echo "Endpoint: POST /v1/auth/forgot-password/verify"
echo "Command:"
echo 'curl -X POST http://localhost:8080/v1/auth/forgot-password/verify \\'
echo '  -H "Content-Type: application/json" \\'
echo '  -d '"'"'{"email": "testuser1@example.com", "code": "999999"}'"'"' \\'
echo '  -v'
echo ""
read -p "Press Enter to run command..."
ERROR_INVALID_OTP=$(curl -s -X POST http://localhost:8080/v1/auth/forgot-password/verify \
  -H "Content-Type: application/json" \
  -d '{"email": "testuser1@example.com", "code": "999999"}' \
  -v 2>&1)
echo "$ERROR_INVALID_OTP"
echo ""
echo "---"
echo ""

echo -e "${RED}18. FORGOT PASSWORD RESET ERROR - Weak Password${NC}"
echo "Endpoint: POST /v1/auth/forgot-password/reset"
echo "Command (using current OTP or request new one first):"
echo 'curl -X POST http://localhost:8080/v1/auth/forgot-password/reset \\'
echo '  -H "Content-Type: application/json" \\'
echo '  -d '"'"'{"email": "testuser1@example.com", "code": "123456", "newPassword": "weak"}'"'"' \\'
echo '  -v'
echo ""
read -p "Press Enter to run command..."
ERROR_WEAK_RESET=$(curl -s -X POST http://localhost:8080/v1/auth/forgot-password/reset \
  -H "Content-Type: application/json" \
  -d '{"email": "testuser1@example.com", "code": "123456", "newPassword": "weak"}' \
  -v 2>&1)
echo "$ERROR_WEAK_RESET"
echo ""
echo "---"
echo ""

# ========================================
# SUMMARY
# ========================================

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}All Tests Complete!${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "Note: Review the outputs above for each test case."
echo "The responses should match the expected formats documented in AUTH_MODULE_FLOWS.md"


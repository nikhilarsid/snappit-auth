package com.snapitt.backend_service.modules.profile.service;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.modules.follow.repository.FollowRepository;
import com.snapitt.backend_service.modules.profile.dto.request.UpdateProfileRequest;
import com.snapitt.backend_service.modules.profile.dto.response.ProfileResponse;
import com.snapitt.backend_service.modules.user.model.UserEntity;
import com.snapitt.backend_service.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * ProfileService - Handles profile operations and validation
 *
 * Validation Rules:
 * - name: 1-100 characters (optional)
 * - bio: 1-160 characters (optional)
 * - avatarUrl: any length (optional)
 * - Restricted fields: username, email, followersCount, followingCount, createdAt (not updatable)
 *
 * Exception Handling:
 * - AuthException: All business logic exceptions with specific error codes
 * - Generic Exception: Caught and wrapped as INTERNAL_SERVER_ERROR
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final Logger logger = LoggerFactory.getLogger(ProfileService.class);

    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    // Validation constraints
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_BIO_LENGTH = 160;
    private static final int MIN_NAME_LENGTH = 1;
    private static final int MIN_BIO_LENGTH = 1;

    /**
     * Retrieve user profile by username
     *
     * @param viewerId User making the request (optional for public profiles)
     * @param username Target username
     * @return ProfileResponse with profile data
     * @throws AuthException (USER_NOT_FOUND, 404) - User does not exist
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Database or unexpected errors
     */
    public ProfileResponse getProfile(String viewerId, String username) {
        try {
            UserEntity targetUser = userRepository.findByUsername(username)
                    .orElseThrow(() -> new AuthException("Profile does not exist", "USER_NOT_FOUND", HttpStatus.NOT_FOUND));

            boolean isFollowing = false;
            boolean canViewFullProfile = false;

            if (viewerId != null) {
                if (viewerId.equals(targetUser.getId())) {
                    canViewFullProfile = true;
                } else {
                    followRepository.findByFollowerIdAndFollowingId(viewerId, targetUser.getId()).ifPresent(relation -> {
                        if ("approved".equalsIgnoreCase(String.valueOf(relation.getStatus()))) {
                            // set flags via side effects on local variables by capturing via final wrapper is inconvenient; instead mutate local via closure not allowed.
                        }
                    });
                    // Simpler: query optional then inspect
                    var relOpt = followRepository.findByFollowerIdAndFollowingId(viewerId, targetUser.getId());
                    if (relOpt.isPresent() && "approved".equalsIgnoreCase(String.valueOf(relOpt.get().getStatus()))) {
                        isFollowing = true;
                        canViewFullProfile = true;
                    }
                }
            }

            return buildProfileResponse(targetUser, canViewFullProfile, isFollowing);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving profile for username: {}", username, ex);
            throw new AuthException("An error occurred while retrieving profile", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Resolve username -> UserEntity (used by follow service per design)
    public UserEntity getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElse(null);
    }

    // Resolve id -> UserEntity (needed for endpoints that operate directly on userId)
    public UserEntity getUserById(String id) {
        return userRepository.findById(id).orElse(null);
    }

    /**
     * Retrieve own profile by user ID
     *
     * @param userId User ID of the authenticated user
     * @return ProfileResponse with full profile data
     * @throws AuthException (USER_NOT_FOUND, 404) - User does not exist
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Database or unexpected errors
     */
    public ProfileResponse getMyProfile(String userId) {
        try {
            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new AuthException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND));

            return buildProfileResponse(user, true, false);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error retrieving own profile for userId: {}", userId, ex);
            throw new AuthException("An error occurred while retrieving profile", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Given list of ids return minimal user objects (username mapping) used by follow pagination
    public java.util.List<com.snapitt.backend_service.modules.user.model.UserEntity> getUsersByIds(java.util.List<String> ids) {
        return userRepository.findAllById(ids);
    }

    private ProfileResponse buildProfileResponse(UserEntity user, boolean canViewFull, boolean isFollowing) {
        String bio = null;
        if (user.getProfile() != null) {
            bio = user.getProfile().getBio();
        }

        String avatarUrl = null;
        if (user.getProfile() != null) {
            avatarUrl = user.getProfile().getAvatarUrl();
        }

        return new ProfileResponse(
                user.getUsername(),
                avatarUrl,
                bio,
                user.getFollowersCount(),
                user.getFollowingCount(),
                isFollowing,
                user.getCreatedAt()
        );
    }

    /**
     * Update user profile
     *
     * @param userId User ID to update
     * @param update Update request with new values
     * @return Updated ProfileResponse
     * @throws AuthException (NO_VALID_FIELDS, 400) - No updatable fields provided
     * @throws AuthException (VALIDATION_ERROR, 400) - Field validation failed (size constraints)
     * @throws AuthException (USER_NOT_FOUND, 404) - User not found
     * @throws AuthException (INTERNAL_SERVER_ERROR, 500) - Database or unexpected errors
     */
    public ProfileResponse updateProfile(String userId, UpdateProfileRequest update) {
        try {
            // Validate that at least one updatable field is provided
            if ((update.getName() == null || update.getName().isBlank())
                    && (update.getBio() == null || update.getBio().isBlank())
                    && (update.getAvatarUrl() == null || update.getAvatarUrl().isBlank())) {
                throw new AuthException("No updatable fields provided", "NO_VALID_FIELDS", HttpStatus.BAD_REQUEST);
            }

            // Validate field sizes for non-blank values
//            if (update.getName() != null && !update.getName().isBlank()) {
//                validateNameField(update.getName());
//            }
//            if (update.getBio() != null && !update.getBio().isBlank()) {
//                validateBioField(update.getBio());
//            }// validation already done in dto

            UserEntity user = userRepository.findById(userId)
                    .orElseThrow(() -> new AuthException("User not found", "USER_NOT_FOUND", HttpStatus.NOT_FOUND));

            if (user.getProfile() == null) {
                user.setProfile(UserEntity.Profile.builder().build());
            }

            if (update.getName() != null && !update.getName().isBlank()) {
                user.getProfile().setName(update.getName());
            }
            if (update.getBio() != null && !update.getBio().isBlank()) {
                user.getProfile().setBio(update.getBio());
            }
            if (update.getAvatarUrl() != null && !update.getAvatarUrl().isBlank()) {
                user.getProfile().setAvatarUrl(update.getAvatarUrl());
            }

            user.setUpdatedAt(Instant.now());

            UserEntity saved = userRepository.save(user);

            String avatarUrl = saved.getProfile() != null ? saved.getProfile().getAvatarUrl() : null;
            String bio = saved.getProfile() != null ? saved.getProfile().getBio() : null;
            String name = saved.getProfile() != null ? saved.getProfile().getName() : null;

            ProfileResponse response = new ProfileResponse(
                    saved.getUsername(),
                    avatarUrl,
                    bio,
                    saved.getFollowersCount(),
                    saved.getFollowingCount(),
                    false,
                    saved.getUpdatedAt()
            );
            response.setName(name);
            return response;
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            logger.error("Error updating profile for userId: {}", userId, ex);
            throw new AuthException("An error occurred while updating profile", "INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

//    /**
//     * Validates name field length constraints
//     * @throws AuthException (VALIDATION_ERROR, 400) - Invalid field length
//     */
//    private void validateNameField(String name) {
//        if (name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
//            throw new AuthException(
//                    "Name must be between " + MIN_NAME_LENGTH + " and " + MAX_NAME_LENGTH + " characters",
//                    "VALIDATION_ERROR",
//                    HttpStatus.BAD_REQUEST
//            );
//        }
//    }
//
//    /**
//     * Validates bio field length constraints
//     * @throws AuthException (VALIDATION_ERROR, 400) - Invalid field length
//     */
//    private void validateBioField(String bio) {
//        if (bio.length() < MIN_BIO_LENGTH || bio.length() > MAX_BIO_LENGTH) {
//            throw new AuthException(
//                    "Bio must be under " + MAX_BIO_LENGTH + " characters",
//                    "VALIDATION_ERROR",
//                    HttpStatus.BAD_REQUEST
//            );
//        }
//    }
}

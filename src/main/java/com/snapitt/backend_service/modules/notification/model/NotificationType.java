package com.snapitt.backend_service.modules.notification.model;

/**
 * NotificationType - Enum for notification types
 * 
 * Types:
 * - LIKE: Someone liked your post
 * - COMMENT: Someone commented on your post
 * - FOLLOW: Someone followed you
 */
public enum NotificationType {
    LIKE,
    COMMENT,
    FOLLOW,
    FOLLOW_REQUEST,
    FOLLOW_ACCEPTED
}

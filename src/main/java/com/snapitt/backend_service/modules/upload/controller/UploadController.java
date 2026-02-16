package com.snapitt.backend_service.modules.upload.controller;

import com.snapitt.backend_service.modules.auth.common.exception.AuthException;
import com.snapitt.backend_service.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * UploadController - Handles file uploads (images)
 *
 * POST /api/v1/upload
 * - Accepts multipart/form-data with a "file" part
 * - Validates file type (JPEG, PNG, GIF, WebP) and size (max 10MB)
 * - Saves to configured upload directory
 * - Returns { "url": "/uploads/<uuid>.<ext>" }
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/upload")
public class UploadController {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @PostMapping
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file) {

        // Auth check
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new AuthException("Authentication required", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED);
        }

        // Validate file
        if (file.isEmpty()) {
            throw new AuthException("File is empty", "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AuthException("File size exceeds 10MB limit", "VALIDATION_ERROR", HttpStatus.BAD_REQUEST);
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new AuthException(
                    "Only JPEG, PNG, GIF, and WebP images are allowed",
                    "VALIDATION_ERROR",
                    HttpStatus.BAD_REQUEST
            );
        }

        try {
            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename preserving original extension
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = UUID.randomUUID() + extension;

            // Save file
            Path filePath = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = "/uploads/" + filename;

            log.info("File uploaded successfully: {}", fileUrl);

            return ResponseEntity.ok(Map.of("url", fileUrl));
        } catch (IOException e) {
            log.error("Failed to upload file", e);
            throw new AuthException(
                    "Failed to upload file",
                    "UPLOAD_ERROR",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}

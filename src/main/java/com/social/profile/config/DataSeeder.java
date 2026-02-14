package com.social.profile.config;

import com.social.profile.entity.ProfileData;
import com.social.profile.entity.User;
import com.social.profile.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;

    @Override
    public void run(String... args) throws Exception {
        // Create a dummy user "vikrant" if not exists
        if (userRepository.findByUsername("vikrant").isEmpty()) {
            User user = new User();
            user.setUsername("vikrant");
            user.setEmail("vikrant@example.com");

            ProfileData profile = new ProfileData();
            profile.setName("vikrant kumar");
            profile.setBio("I love coding!");
            profile.setAvatarUrl("default-avatar.png");

            user.setProfile(profile);
            user.setFollowersCount(100);
            user.setFollowingCount(50);
            user.setUpdatedAt(Instant.now());

            userRepository.save(user);
            System.out.println(" Dummy User 'vikrant' created with Email!");
        }
    }
}
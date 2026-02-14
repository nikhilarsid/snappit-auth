package com.social.profile.repository;

import com.social.profile.entity.User;
import java.util.List;

public interface UserSearchRepository {
    List<User> searchUsers(String keyword);
}
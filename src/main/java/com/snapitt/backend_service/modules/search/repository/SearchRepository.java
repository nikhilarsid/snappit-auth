package com.snapitt.backend_service.modules.search.repository;

import com.snapitt.backend_service.modules.user.model.UserEntity;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SearchRepository {

    private final MongoTemplate mongoTemplate;

    public List<UserEntity> searchUsers(String query, int limit) {
        String trimmed = query.trim();
        if (trimmed.length() < 2) {
            return List.of();
        }

        Document searchStage = new Document("$search", new Document()
                .append("index", "user_search_index")
                .append("compound", new Document()
                        .append("should", List.of(
                                new Document("autocomplete", new Document()
                                        .append("query", trimmed)
                                        .append("path", "username")
                                        .append("fuzzy", new Document()
                                                .append("maxEdits", 1)
                                                .append("prefixLength", 2)
                                        )
                                ),
                                new Document("autocomplete", new Document()
                                        .append("query", trimmed)
                                        .append("path", "profile.name")
                                        .append("fuzzy", new Document()
                                                .append("maxEdits", 1)
                                                .append("prefixLength", 2)
                                        )
                                )
                        ))
                        .append("minimumShouldMatch", 1)
                )
        );

        Document limitStage = new Document("$limit", limit);

        Document projectStage = new Document("$project", new Document()
                .append("_id", 1)
                .append("username", 1)
                .append("email", 1)
                .append("profile", 1)
                .append("followersCount", 1)
                .append("followingCount", 1)
                .append("createdAt", 1)
                .append("updatedAt", 1)
                .append("score", new Document("$meta", "searchScore"))
        );

        List<Document> pipeline = List.of(searchStage, limitStage, projectStage);

        return mongoTemplate.getCollection("users")
                .aggregate(pipeline)
                .map(doc -> mongoTemplate.getConverter().read(UserEntity.class, doc))
                .into(new ArrayList<>());
    }

    public List<UserEntity> searchUsersFallback(String query, int limit) {
        String trimmed = query.trim();
        if (trimmed.length() < 2) {
            return List.of();
        }

        String escaped = trimmed.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0");
        String pattern = "(?i)" + escaped;

        Document matchStage = new Document("$match", new Document("$or", List.of(
                new Document("username", new Document("$regex", pattern)),
                new Document("profile.name", new Document("$regex", pattern))
        )));

        Document limitStage = new Document("$limit", limit);

        List<Document> pipeline = List.of(matchStage, limitStage);

        return mongoTemplate.getCollection("users")
                .aggregate(pipeline)
                .map(doc -> mongoTemplate.getConverter().read(UserEntity.class, doc))
                .into(new ArrayList<>());
    }
}

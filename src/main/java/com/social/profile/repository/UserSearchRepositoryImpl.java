package com.social.profile.repository;

import com.social.profile.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserSearchRepositoryImpl implements UserSearchRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public List<User> searchUsers(String keyword) {
        // 1. Define the Atlas Search Stage ($search)
        // We use 'compound' -> 'should' to allow finding matches in EITHER username OR name.
        // We use 'autocomplete' to support partial matches (e.g., "Vik" finds "Vikrant").
        String searchStage = """
            {
                "$search": {
                    "index": "user_search_index",
                    "compound": {
                        "should": [
                            {
                                "autocomplete": {
                                    "query": "%s",
                                    "path": "username",
                                    "fuzzy": { "maxEdits": 1 }
                                }
                            },
                            {
                                "autocomplete": {
                                    "query": "%s",
                                    "path": "profile.name",
                                    "fuzzy": { "maxEdits": 1 }
                                }
                            }
                        ],
                        "minimumShouldMatch": 1
                    }
                }
            }
            """.formatted(keyword, keyword); // ⚠️ Important: Pass keyword TWICE (once for each %s)

        // 2. Build the Aggregation
        Aggregation aggregation = Aggregation.newAggregation(
                ctx -> org.bson.Document.parse(searchStage),
                Aggregation.limit(10) // Only fetch top 10 results
        );

        // 3. Execute
        return mongoTemplate.aggregate(aggregation, "users", User.class)
                .getMappedResults();
    }
}
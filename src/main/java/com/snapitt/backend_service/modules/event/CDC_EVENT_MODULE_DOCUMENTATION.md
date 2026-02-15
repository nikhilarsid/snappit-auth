# MongoDB CDC Event Architecture Implementation

## Overview

This module implements a **transactional outbox pattern** with MongoDB Change Streams to provide:

- **Transactional Safety**: Events and business mutations happen atomically
- **Idempotency**: Events are claimed atomically before processing
- **Horizontal Scalability**: Multiple worker instances process events independently
- **Fault Tolerance**: Dead worker detection and event recovery
- **At-Least-Once Processing**: Events are guaranteed to be processed even if workers fail

## Architecture Components

### 1. **Event Model** (`EventEntity`)

```
{
  _id: ObjectId,
  type: EventType (enum),
  aggregateId: String,          // ID of the main entity (post, story, etc.)
  payload: Map<String, Object>, // Event details
  status: EventStatus,          // pending | processing | done | failed
  retryCount: int,              // Number of retry attempts
  createdAt: Instant,           // Event creation time
  processedAt: Instant,         // Event completion time
  lockedBy: String,             // Worker ID that claimed the event
  lockedAt: Instant             // Time event was claimed
}
```

### 2. **Event Types** (`EventType`)

Supported events:
- `POST_CREATED` - Feed distribution
- `STORY_CREATED` - Story feed updates
- `POST_LIKED` - Engagement tracking
- `POST_UNLIKED` - Engagement updates
- `COMMENT_CREATED` - Comment counting
- `FOLLOW_REQUESTED` - Follow notifications
- `FOLLOW_ACCEPTED` - Follow completion
- `FOLLOW_REJECTED` - Follow rejection
- `UNFOLLOWED` - Unfollow handling
- `POST_DELETED` - Post deletion
- `STORY_DELETED` - Story deletion

### 3. **Core Services**

#### EventService
- `emitEvent()` - Create a new event (call within transactions)
- `claimEvent()` - Atomically claim an event for processing
- `completeEvent()` - Mark an event as processed
- `handleEventFailure()` - Handle processing failures with retries
- `recoverStaleLockedEvents()` - Recover events from dead workers

#### EventProcessor
- Routes events to appropriate handlers
- Manages handler registration
- Handles retries and error logging

#### DeadWorkerRecoveryService
- Monitors events locked for longer than threshold (default: 5 minutes)
- Automatically releases stale locks back to pending
- Runs every 1 minute (configurable)

#### EventCDCAdapter
- Watches the `events` collection using MongoDB Change Streams
- Detects new events automatically
- Claims and routes events to processors
- Runs in a separate daemon thread

### 4. **Event Handlers**

Each event type has a handler implementing `EventHandler`:

```java
public interface EventHandler {
    void handle(EventEntity event) throws Exception;
}
```

Example handlers included:
- `PostCreatedEventHandler` - Distributes posts to followers' feeds
- `StoryCreatedEventHandler` - Distributes stories to followers
- `PostLikedEventHandler` - Updates like counts and notifications
- `CommentCreatedEventHandler` - Updates comment counts
- `FollowAcceptedEventHandler` - Updates follower counts and populates feeds

All handlers are registered at startup via `EventHandlerConfiguration`.

## Processing Flow

### Step 1: Event Creation (Producer)
```java
// Within a transaction alongside domain mutations
EventService.emitEvent(EventType.POST_CREATED, postId, 
    Map.of("authorId", userId, "content", "..."));
```

### Step 2: Change Stream Detection
`EventCDCAdapter` detects new event via MongoDB Change Stream:
- Watches for `insert` operations on `events` collection
- Runs in background thread

### Step 3: Event Claiming
`EventCDCAdapter` atomically claims the event:
```
status: pending → processing
lockedBy: null → workerId
lockedAt: null → now
```

Only ONE worker succeeds; others see empty result.

### Step 4: Event Processing (Consumer)
`EventProcessor` routes to appropriate handler:
1. Handler executes business logic (within MongoDB transaction)
2. All mutations committed together
3. Event status updated to `done`

### Step 5: Dead Worker Recovery
`DeadWorkerRecoveryService` (every 1 minute):
1. Finds events where `lockedAt < now - 5 minutes`
2. Releases lock: status → `pending`, lockedBy → `null`
3. Other workers can now claim and retry

## Configuration

Add to `application.properties`:

```properties
# Database name
snapitt.event.db-name=snapitt

# Maximum retries before marking event as failed
snapitt.event.max-retries=5

# Worker ID (should be unique per instance)
snapitt.event.worker-id=worker-1

# Dead worker recovery
# Threshold for considering a lock stale (ms)
snapitt.event.stale-lock-threshold-ms=300000

# How often to run recovery checks (ms)
snapitt.event.recovery-interval-ms=60000
```

## Usage in Other Modules

### Emitting Events
```java
@Service
@RequiredArgsConstructor
public class PostService {
    private final EventService eventService;

    public void createPost(String userId, String content) {
        // Save post
        Post post = postRepository.save(...);
        
        // Emit event (typically within a transaction)
        eventService.emitEvent(
            EventType.POST_CREATED,
            post.getId(),
            Map.of("authorId", userId, "content", content)
        );
    }
}
```

### Creating Custom Handlers
```java
@Component
@RequiredArgsConstructor
public class CustomEventHandler implements EventHandler {
    
    @Override
    public void handle(EventEntity event) throws Exception {
        // Your business logic here
        // This runs within a MongoDB transaction
    }
}
```

Then register in `EventHandlerConfiguration`:
```java
eventProcessor.registerHandler(EventType.YOUR_EVENT_TYPE, customHandler);
```

## Idempotency Guarantees

1. **Atomic Claiming**: Only one worker can claim an event
2. **Unique Constraints**: Database constraints prevent duplicates
3. **Transaction Wrapping**: All mutations wrapped in MongoDB transactions
4. **Status Tracking**: Strict state machine (pending → processing → done)
5. **Retry Control**: Retries stop at configurable threshold

## Monitoring

Key metrics to monitor:
- `eventService.countProcessingEvents()` - Events currently being processed
- `eventService.getFailedEvents()` - Events that exceeded max retries
- Recovery logs show number of stale events recovered

Monitor these endpoints for production visibility:
- Failed events requiring manual intervention
- Processing time per event type
- Worker availability and health

## Best Practices

1. **Transactions**: Always emit events within the same transaction as domain mutations
2. **Handlers**: Keep handlers focused on a single event type
3. **Errors**: Throw exceptions in handlers to trigger retries
4. **Configuration**: Set `worker-id` uniquely per instance for distributed deployment
5. **Monitoring**: Log successful event processing for audit trails

## Scaling

### Horizontal Scaling
Deploy multiple instances with different `worker-id` values:
```properties
# Instance 1
snapitt.event.worker-id=worker-1

# Instance 2
snapitt.event.worker-id=worker-2
```

The atomic claiming mechanism ensures:
- No event is processed twice
- No conflicts between workers
- Failed instances automatically recover via dead worker detection

### Performance Tuning
- Adjust `recovery-interval-ms` for faster recovery in high-load systems
- Increase `stale-lock-threshold-ms` if your handlers take longer
- Monitor `retryCount` distribution to detect problematic event types

## References

See `mongodb_cdc_event_architecture.md` for detailed design specification.

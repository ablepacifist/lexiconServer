package lexicon.api;

import lexicon.logic.EventManagerService;
import lexicon.object.Event;
import lexicon.object.Poll;
import lexicon.object.PollOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for the Events & Polls feature. Thin delegator only — each
 * method extracts params, calls a single EventManagerService method, and
 * wraps the result. No validation, orchestration, diffing, or aggregation
 * live here — all of that is EventManagerService's job.
 */
@RestController
@RequestMapping("/api/events")
@CrossOrigin(origins = "*")
public class EventController {

    @Autowired
    private EventManagerService eventManagerService;

    /** POST /api/events — body: {title, description, eventDate, userId}. */
    @PostMapping
    public ResponseEntity<?> createEvent(@RequestBody Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            String title = (String) body.get("title");
            String description = (String) body.get("description");
            String eventDateStr = (String) body.get("eventDate");
            LocalDate eventDate = eventDateStr != null ? LocalDate.parse(eventDateStr) : null;
            Object userIdObj = body.get("userId");
            Integer userId = userIdObj != null ? ((Number) userIdObj).intValue() : null;

            Event event = eventManagerService.createEvent(title, description, eventDate, userId);
            return ResponseEntity.ok(event);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to create event: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /** GET /api/events — dashboard list, each event carries pollCount. */
    @GetMapping
    public ResponseEntity<?> listEvents() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Event> events = eventManagerService.listEvents();
            return ResponseEntity.ok(events);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to list events: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /** GET /api/events/{eventId} — public; event plus its polls. */
    @GetMapping("/{eventId}")
    public ResponseEntity<?> getEventDetail(@PathVariable long eventId) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> detail = eventManagerService.getEventDetail(eventId);
            return ResponseEntity.ok(detail);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to get event: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /** POST /api/events/{eventId}/polls — body: {question, allowAddOptions, seedOptions}. */
    @PostMapping("/{eventId}/polls")
    public ResponseEntity<?> addPollToEvent(@PathVariable long eventId, @RequestBody Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            String question = (String) body.get("question");
            Object allowAddOptionsObj = body.get("allowAddOptions");
            boolean allowAddOptions = allowAddOptionsObj != null ? (Boolean) allowAddOptionsObj : true;
            @SuppressWarnings("unchecked")
            List<String> seedOptions = (List<String>) body.get("seedOptions");

            Poll poll = eventManagerService.addPollToEvent(eventId, question, allowAddOptions, seedOptions);
            return ResponseEntity.ok(poll);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to add poll: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /** GET /api/events/{eventId}/polls/{pollId}?voterKey= — public; voterKey optional (pure viewing). */
    @GetMapping("/{eventId}/polls/{pollId}")
    public ResponseEntity<?> getPollDetail(
            @PathVariable long eventId,
            @PathVariable long pollId,
            @RequestParam(required = false) String voterKey) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> detail = eventManagerService.getPollDetail(pollId, voterKey);
            return ResponseEntity.ok(detail);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to get poll: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /** POST /api/events/{eventId}/polls/{pollId}/options — body: {text, voterKey, voterName}. */
    @PostMapping("/{eventId}/polls/{pollId}/options")
    public ResponseEntity<?> addOption(
            @PathVariable long eventId,
            @PathVariable long pollId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            String text = (String) body.get("text");
            String voterKey = (String) body.get("voterKey");
            String voterName = (String) body.get("voterName");

            PollOption option = eventManagerService.addOption(pollId, text, voterKey, voterName);
            return ResponseEntity.ok(option);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to add option: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /** PUT /api/events/{eventId}/polls/{pollId}/votes — body: {voterKey, voterName, optionIds}. */
    @PutMapping("/{eventId}/polls/{pollId}/votes")
    public ResponseEntity<?> setVotesForPoll(
            @PathVariable long eventId,
            @PathVariable long pollId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            String voterKey = (String) body.get("voterKey");
            String voterName = (String) body.get("voterName");
            List<?> rawOptionIds = (List<?>) body.get("optionIds");
            List<Long> optionIds = rawOptionIds == null
                    ? new ArrayList<>()
                    : rawOptionIds.stream().map(o -> ((Number) o).longValue()).collect(Collectors.toList());

            boolean success = eventManagerService.setVotesForPoll(pollId, voterKey, voterName, optionIds);
            response.put("success", success);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to set votes: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}

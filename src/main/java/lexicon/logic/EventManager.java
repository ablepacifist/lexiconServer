package lexicon.logic;

import lexicon.data.IEventDatabase;
import lexicon.object.Event;
import lexicon.object.Poll;
import lexicon.object.PollOption;
import lexicon.object.PollVote;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Events & Polls business logic: validation, orchestration, the seed-option
 * insertion loop, the "adding an option is also your vote" rule, the
 * per-option vote-count/voter-list/votedByMe aggregation, and the
 * add/remove vote-diffing algorithm. This is the ONLY layer that contains
 * this logic — the repository is pure CRUD and the controller is thin.
 */
@Service
public class EventManager implements EventManagerService {

    private final IEventDatabase db;

    @Autowired
    public EventManager(IEventDatabase db) {
        this.db = db;
    }

    @Override
    public Event createEvent(String title, String description, LocalDate eventDate, Integer userId) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Event title cannot be empty");
        }
        Event event = new Event();
        event.setTitle(title.trim());
        event.setDescription(description);
        event.setEventDate(eventDate);
        event.setCreatedByUserId(userId);

        long id = db.insertEvent(event);
        event.setId(id);
        return event;
    }

    @Override
    public List<Event> listEvents() {
        List<Event> events = db.getAllEvents();
        for (Event event : events) {
            event.setPollCount(db.getPollsByEventId(event.getId()).size());
        }
        return events;
    }

    @Override
    public Map<String, Object> getEventDetail(long eventId) {
        Event event = db.getEventById(eventId);
        List<Poll> polls = db.getPollsByEventId(eventId);

        Map<String, Object> result = new HashMap<>();
        result.put("event", event);
        result.put("polls", polls);
        return result;
    }

    @Override
    public Poll addPollToEvent(long eventId, String question, boolean allowAddOptions, List<String> seedOptionTexts) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("Poll question cannot be empty");
        }
        Poll poll = new Poll();
        poll.setEventId(eventId);
        poll.setQuestion(question.trim());
        poll.setAllowAddOptions(allowAddOptions);

        long pollId = db.insertPoll(poll);
        poll.setId(pollId);

        // Seeding is a logic-layer concern: one insertOption call per non-blank seed text.
        if (seedOptionTexts != null) {
            for (String text : seedOptionTexts) {
                if (text == null || text.trim().isEmpty()) continue;
                PollOption option = new PollOption();
                option.setPollId(pollId);
                option.setText(text.trim());
                db.insertOption(option);
            }
        }
        return poll;
    }

    @Override
    public Map<String, Object> getPollDetail(long pollId, String voterKey) {
        Poll poll = db.getPollById(pollId);
        List<PollOption> options = db.getOptionsByPollId(pollId);
        boolean hasVoterKey = voterKey != null && !voterKey.isBlank();

        for (PollOption option : options) {
            List<PollVote> votes = db.getVotesByOptionId(option.getId());

            List<String> voters = new ArrayList<>();
            boolean votedByMe = false;
            for (PollVote vote : votes) {
                voters.add(vote.getVoterName());
                if (hasVoterKey && voterKey.equals(vote.getVoterKey())) {
                    votedByMe = true;
                }
            }
            option.setVoteCount(votes.size());
            option.setVoters(voters);
            option.setVotedByMe(votedByMe);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("poll", poll);
        result.put("options", options);
        return result;
    }

    @Override
    public PollOption addOption(long pollId, String text, String voterKey, String voterName) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Option text cannot be empty");
        }
        if (voterName == null || voterName.trim().isEmpty()) {
            throw new IllegalArgumentException("A name is required to add an option");
        }
        if (voterKey == null || voterKey.trim().isEmpty()) {
            throw new IllegalArgumentException("A voter identity is required to add an option");
        }
        String trimmedName = voterName.trim();

        PollOption option = new PollOption();
        option.setPollId(pollId);
        option.setText(text.trim());
        option.setAddedByName(trimmedName);

        long optionId = db.insertOption(option);
        option.setId(optionId);

        // Adding an option is also the submitter's vote for it.
        db.insertVote(optionId, voterKey, trimmedName);

        option.setVoteCount(1);
        option.setVoters(new ArrayList<>(List.of(trimmedName)));
        option.setVotedByMe(true);
        return option;
    }

    @Override
    public boolean setVotesForPoll(long pollId, String voterKey, String voterName, List<Long> optionIds) {
        if (voterName == null || voterName.trim().isEmpty()) {
            throw new IllegalArgumentException("A name is required to vote");
        }
        if (voterKey == null || voterKey.trim().isEmpty()) {
            throw new IllegalArgumentException("A voter identity is required to vote");
        }
        String trimmedName = voterName.trim();

        // Scope to this poll's own option ids so a vote can never apply to another poll's option.
        List<PollOption> pollOptions = db.getOptionsByPollId(pollId);
        Set<Long> validOptionIds = new HashSet<>();
        for (PollOption option : pollOptions) {
            validOptionIds.add(option.getId());
        }

        Set<Long> requested = new HashSet<>();
        if (optionIds != null) {
            for (Long id : optionIds) {
                if (validOptionIds.contains(id)) requested.add(id);
            }
        }

        Set<Long> current = new HashSet<>(db.getVotedOptionIds(voterKey, new ArrayList<>(validOptionIds)));

        // Real set-difference diffing — not delete-everything-then-reinsert.
        Set<Long> toDelete = new HashSet<>(current);
        toDelete.removeAll(requested);

        Set<Long> toAdd = new HashSet<>(requested);
        toAdd.removeAll(current);

        for (Long optionId : toDelete) {
            db.deleteVote(optionId, voterKey);
        }
        for (Long optionId : toAdd) {
            db.insertVote(optionId, voterKey, trimmedName);
        }

        return true;
    }
}

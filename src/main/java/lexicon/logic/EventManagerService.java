package lexicon.logic;

import lexicon.object.Event;
import lexicon.object.Poll;
import lexicon.object.PollOption;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Service interface for the Events & Polls feature. This is the only layer
 * the controller may call — all validation, orchestration, the vote-diffing
 * algorithm, the vote-count/voter-list aggregation, and the "adding an
 * option is also your vote" rule live behind this interface.
 */
public interface EventManagerService {

    /** Create an event; validates title is non-blank. */
    Event createEvent(String title, String description, LocalDate eventDate, Integer userId);

    /** All events for the dashboard, each with pollCount populated. */
    List<Event> listEvents();

    /** A single event plus its polls, keyed "event" / "polls". */
    Map<String, Object> getEventDetail(long eventId);

    /** Create a poll under an event, seeding it with initial options (each seed text becomes an option, unvoted). */
    Poll addPollToEvent(long eventId, String question, boolean allowAddOptions, List<String> seedOptionTexts);

    /**
     * A poll plus its options, keyed "poll" / "options". Each option has
     * voteCount, voters, and votedByMe populated. Public — voterKey may be
     * null/blank (pure viewing), in which case votedByMe is simply false.
     */
    Map<String, Object> getPollDetail(long pollId, String voterKey);

    /**
     * Add a new option to a poll. This is also the submitter's vote for it —
     * requires a non-blank voterName. Returns the option populated with
     * voteCount=1, voters=[voterName], votedByMe=true.
     */
    PollOption addOption(long pollId, String text, String voterKey, String voterName);

    /**
     * Set the full desired set of option ids a voter has selected for a poll
     * (select, change, or clear all). Diffs against their current votes
     * within this poll and applies only the delta. Requires a non-blank
     * voterName.
     */
    boolean setVotesForPoll(long pollId, String voterKey, String voterName, List<Long> optionIds);
}

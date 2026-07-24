package lexicon.data;

import lexicon.object.Event;
import lexicon.object.Poll;
import lexicon.object.PollOption;
import lexicon.object.PollVote;

import java.util.List;

/**
 * Database operations for events, polls, poll options, and poll votes.
 * Pure CRUD only — one table operation per method, no joins that compute
 * business meaning (vote counts, voter lists, etc). Only the service layer
 * (EventManager) should call this.
 */
public interface IEventDatabase {

    /** Insert an event; returns generated id. */
    long insertEvent(Event e);

    /** All events, e.g. for the Events dashboard. */
    List<Event> getAllEvents();

    /** A single event by id, or null if not found. */
    Event getEventById(long id);

    /** Insert a poll; returns generated id. */
    long insertPoll(Poll p);

    /** All polls belonging to an event. */
    List<Poll> getPollsByEventId(long eventId);

    /** A single poll by id, or null if not found. */
    Poll getPollById(long id);

    /** Insert a poll option; returns generated id. */
    long insertOption(PollOption o);

    /** All options belonging to a poll. */
    List<PollOption> getOptionsByPollId(long pollId);

    /** Insert a vote for an option; idempotent (UNIQUE(option_id, voter_key)). */
    void insertVote(long optionId, String voterKey, String voterName);

    /** Remove a voter's vote for an option (no-op if it doesn't exist). */
    void deleteVote(long optionId, String voterKey);

    /** Raw vote rows for a single option — no counting, no aggregation. */
    List<PollVote> getVotesByOptionId(long optionId);

    /** Which of the given candidate option ids this voter has voted for. */
    List<Long> getVotedOptionIds(String voterKey, List<Long> optionIds);
}

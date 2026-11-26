package lexicon.logic;

import lexicon.data.ILexiconDatabase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ActivityTrackerTest {

    @Test
    void writesAtMostOncePerIntervalPerPlayer() {
        ILexiconDatabase db = mock(ILexiconDatabase.class);
        AtomicLong now = new AtomicLong(1_000_000);
        ActivityTracker tracker = new ActivityTracker(db, now::get);

        tracker.recordActivity(5);
        tracker.recordActivity(5);
        now.addAndGet(ActivityTracker.WRITE_INTERVAL_MS - 1);
        tracker.recordActivity(5);
        verify(db, times(1)).updatePlayerLastActive(eq(5), any());

        now.addAndGet(1);
        tracker.recordActivity(5);
        verify(db, times(2)).updatePlayerLastActive(eq(5), any());

        tracker.recordActivity(6);
        verify(db, times(1)).updatePlayerLastActive(eq(6), any());
    }

    @Test
    void ignoresNonPlayersAndNeverThrows() {
        ILexiconDatabase db = mock(ILexiconDatabase.class);
        doThrow(new RuntimeException("db down")).when(db).updatePlayerLastActive(anyInt(), any());
        ActivityTracker tracker = new ActivityTracker(db, () -> 0L);

        tracker.recordActivity(0);
        tracker.recordActivity(-1);
        verify(db, never()).updatePlayerLastActive(anyInt(), any());
        tracker.recordActivity(3); // must not throw
    }
}

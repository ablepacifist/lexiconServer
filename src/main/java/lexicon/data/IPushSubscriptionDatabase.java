package lexicon.data;

import lexicon.object.PushSubscription;
import java.util.List;

public interface IPushSubscriptionDatabase {
    void saveSubscription(PushSubscription subscription);
    void deleteByEndpoint(String endpoint);
    void deleteByUserId(int userId);
    List<PushSubscription> getByUserId(int userId);
    List<PushSubscription> getAll();
}

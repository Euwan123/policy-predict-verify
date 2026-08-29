import java.util.ArrayList;
import java.util.List;

public class AppState {
    public List<LoginRecord> records = new ArrayList<>();
    public int limit = 5;

    public long predictedUsersLocked = -1;
    public long predictedAttackersBlocked = -1;
    public long actualUsersLocked = -1;
    public long actualAttackersBlocked = -1;

    public boolean simulatorDone = false;
    public boolean sandboxDone = false;

    private final List<Runnable> listeners = new ArrayList<>();

    public void addListener(Runnable r) {
        listeners.add(r);
    }

    public void notifyListeners() {
        for (Runnable r : listeners) r.run();
    }

    public long totalUsers() {
        return records.stream().filter(LoginRecord::isUser).count();
    }

    public long totalAttackers() {
        return records.stream().filter(LoginRecord::isAttacker).count();
    }

    public void resetResults() {
        predictedUsersLocked = -1;
        predictedAttackersBlocked = -1;
        actualUsersLocked = -1;
        actualAttackersBlocked = -1;
        simulatorDone = false;
        sandboxDone = false;
    }
}

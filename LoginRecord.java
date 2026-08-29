public class LoginRecord {
    public final String id;
    public final String type;
    public final int attemptsNeeded;

    public LoginRecord(String id, String type, int attemptsNeeded) {
        this.id = id;
        this.type = type;
        this.attemptsNeeded = attemptsNeeded;
    }

    public boolean isUser() {
        return type.equalsIgnoreCase("USER");
    }

    public boolean isAttacker() {
        return type.equalsIgnoreCase("ATTACKER");
    }
}

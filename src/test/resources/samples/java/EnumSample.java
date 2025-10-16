package samples.java;

public class EnumSample {
    public String getStatusMessage(Status status) {
        switch (status) {
            case PENDING:
                return "Waiting: " + status.getCode();
            case ACTIVE:
                return "Running: " + status.getCode();
            case CLOSED:
                return "Done: " + status.getCode();
            default:
                return "Unknown";
        }
    }

    public void processAllStatuses() {
        for (Status s : Status.values()) {
            System.out.println(s.getCode());
        }
    }
}

enum Status {
    PENDING("P", 1),
    ACTIVE("A", 2),
    CLOSED("C", 3);

    private final String code;
    private final int priority;

    Status(String code, int priority) {
        this.code = code;
        this.priority = priority;
    }

    public String getCode() {
        return code;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isActive() {
        return this == ACTIVE;
    }
}

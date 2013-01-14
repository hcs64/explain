package epl;

public class ChangesetException extends Exception {
    // but not by much

    public ChangesetException(String message) {
        super(message);
    }

    public ChangesetException(String message, Throwable cause) {
        super(message, cause);
    }
}

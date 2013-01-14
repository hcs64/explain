package epl;

public class PadException extends Exception {
    // but not by much

    public PadException(String message) {
        super(message);
    }

    public PadException(String message, Throwable cause) {
        super(message, cause);
    }
}

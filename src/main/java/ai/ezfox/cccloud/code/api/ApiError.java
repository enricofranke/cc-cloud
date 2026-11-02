package ai.ezfox.cccloud.code.api;

/**
 * A wrong call from a script ({@code bad argument}, an unknown event, a full quota): the engine raises it as an error
 * at the calling line, the script can catch it with {@code pcall}.
 */
public final class ApiError extends RuntimeException {
    public ApiError(String message) {
        super(message, null, false, false);
    }
}

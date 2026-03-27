package pl.piomin.signalmind.integration.breeze;

/**
 * Thrown by {@link BreezeClient} when an HTTP call fails or the Breeze API
 * returns an error response.  Unchecked so callers can handle it selectively.
 */
public class BreezeClientException extends RuntimeException {

    public BreezeClientException(String message) {
        super(message);
    }

    public BreezeClientException(String message, Throwable cause) {
        super(message, cause);
    }
}

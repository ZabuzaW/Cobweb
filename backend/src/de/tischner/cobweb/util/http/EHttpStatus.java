package de.tischner.cobweb.util.http;

/**
 * Enumeration of valid HTTP/1.0 states.
 *
 * @author Zabuza {@literal <zabuza.dev@gmail.com>}
 *
 */
public enum EHttpStatus {
  /**
   * The request was in a wrong format.
   */
  BAD_REQUEST(400),
  /**
   * If the requested resource is not allowed to get accessed.
   */
  FORBIDDEN(403),
  /**
   * An unexpected server error occurred.
   */
  INTERNAL_SERVER_ERROR(500),
  /**
   * If the requested method is not allowed or supported.
   */
  METHOD_NOT_ALLOWED(405),
  /**
   * The request was processed successfully but the response does not contain any
   * content.
   */
  NO_CONTENT(204),
  /**
   * If the requested resource could not be found.
   */
  NOT_FOUND(404),
  /**
   * The functionality to serve the given request is not supported by the server.
   */
  NOT_IMPLEMENTED(501),
  /**
   * If everything was valid and went okay.
   */
  OK(200);

  private final int mStatusCode;

  private EHttpStatus(final int statusCode) {
    mStatusCode = statusCode;
  }

  public int getStatusCode() {
    return mStatusCode;
  }
}
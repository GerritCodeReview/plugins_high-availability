package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

public class ForwardingException extends Exception {
  private static final long serialVersionUID = 1L;

  private final boolean isRecoverable;

  public ForwardingException(boolean isRecoverable,
      String message) {
    super(message);
    this.isRecoverable = isRecoverable;
  }

  public ForwardingException(boolean isRecoverable,
      String message,
      Throwable cause) {
    super(message, cause);
    this.isRecoverable = isRecoverable;
  }

  public boolean isRecoverable() {
    return isRecoverable;
  }
}
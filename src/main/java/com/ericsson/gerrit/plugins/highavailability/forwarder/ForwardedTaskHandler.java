package com.ericsson.gerrit.plugins.highavailability.forwarder;

/** Base class for all classes handling forwarded tasks. */
abstract class ForwardedTaskHandler<T> {

  abstract void doHandle(T t) throws Exception;

  public void handle(T t) throws Exception {
    try {
      Context.setForwardedEvent(true);
      doHandle(t);
    } finally {
      Context.unsetForwardedEvent();
    }
  }
}

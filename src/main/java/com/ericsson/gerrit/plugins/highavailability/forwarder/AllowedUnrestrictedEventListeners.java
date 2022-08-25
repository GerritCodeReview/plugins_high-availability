package com.ericsson.gerrit.plugins.highavailability.forwarder;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.gerrit.server.events.EventListener;
import com.google.inject.Inject;
import java.util.Set;

public class AllowedUnrestrictedEventListeners {

  private final Set<String> allowed;

  @Inject
  AllowedUnrestrictedEventListeners(Configuration config) {
    allowed = config.event().allowedListeners();
  }

  public boolean isAllowed(EventListener l) {
    String listenerClassName = l.getClass().getName();
    return allowed.contains(listenerClassName);
  }
}

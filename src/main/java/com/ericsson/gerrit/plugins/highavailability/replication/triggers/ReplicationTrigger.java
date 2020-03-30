// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.ericsson.gerrit.plugins.highavailability.replication.triggers;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.ProjectEvent;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Provider;
import java.util.Optional;

/**
 * Common base class to add ProjectEvent to ReplicationQueue via event triggering.
 *
 * @param <T> Replication listener to ProjectEvent C.
 * @param <C> ProjectEvent the listener is associated with.
 */
public abstract class ReplicationTrigger<T, C extends ProjectEvent> {
  protected static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Optional<T> replicationListener;
  private final String REPLICATION_MODULE =
      "com.googlesource.gerrit.plugins.replication.ReplicationQueue";

  ReplicationTrigger(DynamicSet<T> listeners) {
    replicationListener = getReplicationListener(listeners);
  }

  /**
   * Looks for a listener of ProjectEvent T which is bound to replication plugin. With offloading
   * feature enabled, it is expected for the active master to not have a replication listener. Since
   * replication plugin would be disabled on the active master side, HA won`t have a listener
   * associated with a replication plugin.
   *
   * @param listeners - DynamicSet of bounded listeners of type T.
   * @return ProjectEvent listener.
   */
  public Optional<T> getReplicationListener(DynamicSet<T> listeners) {
    for (Provider<T> listener : listeners.byPlugin("replication")) {
      if (listener.get().getClass().getCanonicalName().equals(REPLICATION_MODULE)) {
        return Optional.ofNullable(listener.get());
      }
    }
    logger.atFine().log("There are no listeners associated with replication plugin");
    return Optional.empty();
  }

  /**
   * Triggers an event corresponding to the ProjectEvent listener.
   *
   * @param event to trigger.
   */
  public void triggerEvent(C event) {
    if (getReplicationListener().isPresent()) {
      scheduleEvent(event);
    }
  }

  public Optional<T> getReplicationListener() {
    return replicationListener;
  }

  /**
   * Schedule the event to be triggered.
   *
   * @param event to schedule.
   */
  protected abstract void scheduleEvent(C event);
}

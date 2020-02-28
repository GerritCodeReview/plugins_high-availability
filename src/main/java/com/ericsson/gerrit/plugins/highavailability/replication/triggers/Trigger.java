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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.events.ProjectEvent;
import com.google.gerrit.extensions.registration.DynamicSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Trigger<T, C extends ProjectEvent> {
  private static final Logger log = LoggerFactory.getLogger(Trigger.class);
  private final T replicationListener;
  private final String REPLICATION_MODULE =
      "com.googlesource.gerrit.plugins.replication.ReplicationQueue";

  Trigger(DynamicSet<T> listeners) {
    this.replicationListener = getReplicationListener(listeners);
  }

  public T getReplicationListener(DynamicSet<T> listeners) {
    return Iterables.tryFind(
            listeners,
            new Predicate<T>() {
              public boolean apply(T listener) {
                return REPLICATION_MODULE.equals(listener.getClass().getCanonicalName());
              }
            })
        .orNull();
  }

  public void trigger(C event) {
    if (getListener() != null) {
      callback(event);
    } else
      log.error("REPLICATIONOFFLOADING: There are no listeners associated with replication plugin");
  }

  public T getListener() {
    return replicationListener;
  }

  protected abstract void callback(C event);
}

// Copyright (C) 2016 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventBroker;
import com.google.gerrit.server.events.EventListener;
import com.google.gerrit.server.events.UserScopedEventListener;
import com.google.gerrit.server.notedb.ChangeNotes.Factory;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import java.util.Objects;

class ForwardedAwareEventBroker extends EventBroker {

  private final AllowedForwardedEventListener allowedListeners;

  @Inject
  ForwardedAwareEventBroker(
      PluginSetContext<UserScopedEventListener> listeners,
      PluginSetContext<EventListener> unrestrictedListeners,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      Factory notesFactory,
      @Nullable @GerritInstanceId String gerritInstanceId,
      @Nullable AllowedForwardedEventListener allowedListeners) {
    super(
        listeners,
        unrestrictedListeners,
        permissionBackend,
        projectCache,
        notesFactory,
        gerritInstanceId);

    this.allowedListeners = allowedListeners;
  }

  private boolean isProducedByLocalInstance(Event event) {
    return Objects.equals(event.instanceId, gerritInstanceId);
  }

  @Override
  protected void fireEventForUnrestrictedListeners(Event event) {
    // An event should not be dispatched when it is "forwarded".
    // Meaning, it was either produced somewhere else
    if (!isProducedByLocalInstance(event)) {
      Context.setForwardedEvent(true);
    }
    // or it was consumed by the high-availability rest endpoint and
    // thus the context of its consumption has already been set to "forwarded".
    unrestrictedListeners.runEach(l -> fireEventForListener(l, event));
  }

  private void fireEventForListener(EventListener l, Event event) {
    if (!Context.isForwardedEvent()
        || (allowedListeners != null && allowedListeners.isAllowed(l))) {
      l.onEvent(event);
    }
  }
}

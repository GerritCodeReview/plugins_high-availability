// Copyright (C) 2016 Ericsson
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

package com.ericsson.gerrit.plugins.highavailability.event;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.google.gerrit.common.EventBroker;
import com.google.gerrit.common.EventListener;
import com.google.gerrit.common.UserScopedEventListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.notedb.ChangeNotes.Factory;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Provider;

class ForwardedAwareEventBroker extends EventBroker {

  @Inject
  ForwardedAwareEventBroker(
      DynamicSet<UserScopedEventListener> listeners,
      DynamicSet<EventListener> unrestrictedListeners,
      ProjectCache projectCache,
      Factory notesFactory,
      Provider<ReviewDb> dbProvider) {
    super(listeners, unrestrictedListeners, projectCache, notesFactory, dbProvider);
  }

  @Override
  protected void fireEventForUnrestrictedListeners(Event event) {
    if (!Context.isForwardedEvent()) {
      super.fireEventForUnrestrictedListeners(event);
    }
  }
}

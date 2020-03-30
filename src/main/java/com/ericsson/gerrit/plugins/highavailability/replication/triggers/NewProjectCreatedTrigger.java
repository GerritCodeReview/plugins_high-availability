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

import com.ericsson.gerrit.plugins.highavailability.replication.events.ForwardedNewProjectCreatedEvent;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class NewProjectCreatedTrigger
    extends ReplicationTrigger<NewProjectCreatedListener, ForwardedNewProjectCreatedEvent> {
  @Inject
  NewProjectCreatedTrigger(DynamicSet<NewProjectCreatedListener> listeners) {
    super(listeners);
  }

  @Override
  public void scheduleEvent(ForwardedNewProjectCreatedEvent event) {
    getReplicationListener().get().onNewProjectCreated(event);
  }
}

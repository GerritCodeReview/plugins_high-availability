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

package com.ericsson.gerrit.plugins.highavailability.replication;

import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.events.HeadUpdatedListener;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import java.util.concurrent.Executor;

public class ReplicationModule extends LifecycleModule {

  @Override
  protected void configure() {
    bind(Executor.class)
        .annotatedWith(ReplicationExecutor.class)
        .toProvider(ReplicationExecutorProvider.class);
    listener().to(ReplicationExecutorProvider.class);

    DynamicSet.bind(binder(), GitReferenceUpdatedListener.class).to(ReplicationListener.class);
    DynamicSet.bind(binder(), NewProjectCreatedListener.class).to(ReplicationListener.class);
    DynamicSet.bind(binder(), ProjectDeletedListener.class).to(ReplicationListener.class);
    DynamicSet.bind(binder(), HeadUpdatedListener.class).to(ReplicationListener.class);
  }
}

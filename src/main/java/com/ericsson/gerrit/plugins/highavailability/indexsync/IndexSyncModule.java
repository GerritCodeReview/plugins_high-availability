// Copyright (C) 2024 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.indexsync;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.inject.internal.UniqueAnnotations;

public class IndexSyncModule extends FactoryModule {
  @Override
  protected void configure() {
    // NOTE: indexSync.enabled is handled in the plugins main Module
    // When not enabled, then this module is not installed
    bind(LifecycleListener.class)
        .annotatedWith(UniqueAnnotations.create())
        .to(IndexSyncScheduler.class);
    bind(QueryChangesResponseHandler.class);
    factory(IndexSyncRunner.Factory.class);
  }
}

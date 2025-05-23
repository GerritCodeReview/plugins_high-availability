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

package com.ericsson.gerrit.plugins.highavailability.index;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.ExecutorProvider;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
class ForwardedBatchIndexExecutorProvider extends ExecutorProvider {

  @UsedAt(UsedAt.Project.PLUGIN_MULTI_SITE)
  public static final String FORWARDED_BATCH_INDEX_EVENT_THREAD_PREFIX =
      "Forwarded-BatchIndex-Event";

  @Inject
  ForwardedBatchIndexExecutorProvider(WorkQueue workQueue, Configuration config) {
    super(
        workQueue,
        config.index().batchThreadPoolSize(),
        FORWARDED_BATCH_INDEX_EVENT_THREAD_PREFIX,
        config.index().initialDelayMsec());
  }
}

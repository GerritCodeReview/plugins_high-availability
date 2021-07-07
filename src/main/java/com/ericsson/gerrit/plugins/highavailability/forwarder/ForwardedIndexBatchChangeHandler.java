// Copyright (C) 2021 The Android Open Source Project
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

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.index.ChangeCheckerImpl.Factory;
import com.ericsson.gerrit.plugins.highavailability.index.ChangeDb;
import com.ericsson.gerrit.plugins.highavailability.index.ForwardedBatchIndexExecutor;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.ScheduledExecutorService;

@Singleton
public class ForwardedIndexBatchChangeHandler extends ForwardedIndexChangeHandler {

  @Inject
  ForwardedIndexBatchChangeHandler(
      ChangeIndexer indexer,
      ChangeDb changeDb,
      Configuration config,
      @ForwardedBatchIndexExecutor ScheduledExecutorService indexExecutor,
      OneOffRequestContext oneOffCtx,
      Factory changeCheckerFactory,
      NoteDbMigration noteDbMigration) {
    super(
        indexer, changeDb, config, indexExecutor, oneOffCtx, changeCheckerFactory, noteDbMigration);
  }
}

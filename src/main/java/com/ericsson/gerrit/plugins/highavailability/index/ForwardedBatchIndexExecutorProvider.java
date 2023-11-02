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

package com.ericsson.gerrit.plugins.highavailability.index;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
class ForwardedBatchIndexExecutorProvider extends ForwardedIndexExecutorProvider {

  @Inject
  ForwardedBatchIndexExecutorProvider(Configuration cfg) {
    super(cfg);
  }

  @Override
  protected int threadPoolSize(Configuration cfg) {
    return cfg.index().batchThreadPoolSize();
  }
}

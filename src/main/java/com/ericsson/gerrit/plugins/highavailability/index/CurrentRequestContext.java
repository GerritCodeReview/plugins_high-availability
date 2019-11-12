// Copyright (C) 2019 The Android Open Source Project
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
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.function.Consumer;

@Singleton
public class CurrentRequestContext {
  private ThreadLocalRequestContext threadLocalCtx;
  private Configuration cfg;
  private OneOffRequestContext oneOffCtx;

  @Inject
  public CurrentRequestContext(
      ThreadLocalRequestContext threadLocalCtx, Configuration cfg, OneOffRequestContext oneOffCtx) {
    this.threadLocalCtx = threadLocalCtx;
    this.cfg = cfg;
    this.oneOffCtx = oneOffCtx;
  }

  public void onlyWithContext(Consumer<RequestContext> body) {
    RequestContext ctx = threadLocalCtx.getContext();
    if (ctx == null && !cfg.index().synchronizeForced()) {
      return;
    }

    if (ctx == null) {
      try (ManualRequestContext manualCtx = oneOffCtx.open()) {
        body.accept(manualCtx);
      }
    } else {
      body.accept(ctx);
    }
  }
}

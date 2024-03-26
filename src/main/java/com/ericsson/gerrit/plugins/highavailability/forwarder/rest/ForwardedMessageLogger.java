// Copyright (C) 2026 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import com.google.common.flogger.FluentLogger;
import javax.servlet.http.HttpServletRequest;

public class ForwardedMessageLogger {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  static void log(HttpServletRequest req, String body) {
    StringBuilder sb = new StringBuilder();
    sb.append(req.getMethod()).append(" ").append(req.getRequestURI());
    if (body != null) {
      sb.append(" ");
      sb.append(body);
    }
    log.atFine().log("Received message: %s", sb.toString());
  }
}

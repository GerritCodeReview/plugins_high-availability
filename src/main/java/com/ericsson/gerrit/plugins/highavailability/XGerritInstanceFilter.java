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

package com.ericsson.gerrit.plugins.highavailability;

import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.httpd.AllRequestFilter;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.config.GerritInstanceName;
import com.google.inject.Inject;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

public class XGerritInstanceFilter extends AllRequestFilter {
  private static final String X_GERRIT_INSTANCE = "X-Gerrit-Instance";

  private final String instanceId;
  private final String instanceName;

  @Inject
  XGerritInstanceFilter(
      @Nullable @GerritInstanceId String instanceId, @GerritInstanceName String instanceName) {
    this.instanceId = instanceId;
    this.instanceName = instanceName;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (response instanceof HttpServletResponse) {
      ((HttpServletResponse) response)
          .addHeader(X_GERRIT_INSTANCE, firstNonNull(instanceId, instanceName));
    }
    chain.doFilter(request, response);
  }
}

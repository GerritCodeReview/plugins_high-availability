// Copyright (C) 2017 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.health;

import static com.google.gerrit.server.permissions.GlobalPermission.ADMINISTRATE_SERVER;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class HealthServlet extends HttpServlet {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();
  private static final long serialVersionUID = -1L;

  private final Provider<CurrentUser> currentUserProvider;
  private final PermissionBackend permissionBackend;
  private final File unhealthyFile;

  @Inject
  HealthServlet(
      Provider<CurrentUser> currentUserProvider,
      PermissionBackend permissionBackend,
      @PluginData Path pluginDataDir) {
    this.currentUserProvider = currentUserProvider;
    this.permissionBackend = permissionBackend;
    this.unhealthyFile = pluginDataDir.resolve("unhealthy.txt").toFile();
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp) {
    if (!permissionBackend.user(currentUserProvider.get()).testOrFalse(ADMINISTRATE_SERVER)) {
      sendError(rsp, SC_FORBIDDEN);
      return;
    }
    try {
      setHealthy();
      rsp.setStatus(SC_NO_CONTENT);
    } catch (IOException e) {
      log.atSevere().withCause(e).log("Failed to set healthy");
      sendError(rsp, SC_INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  protected void doDelete(HttpServletRequest req, HttpServletResponse rsp) {
    if (!permissionBackend.user(currentUserProvider.get()).testOrFalse(ADMINISTRATE_SERVER)) {
      sendError(rsp, SC_FORBIDDEN);
      return;
    }
    try {
      setUnhealthy();
      rsp.setStatus(SC_NO_CONTENT);
    } catch (IOException e) {
      log.atSevere().withCause(e).log("Failed to set unhealthy");
      sendError(rsp, SC_INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp) {
    if (unhealthyFile.exists()) {
      sendError(rsp, SC_SERVICE_UNAVAILABLE);
      return;
    }
    rsp.setStatus(SC_NO_CONTENT);
  }

  private static void sendError(HttpServletResponse rsp, int statusCode) {
    try {
      rsp.sendError(statusCode);
    } catch (IOException e) {
      rsp.setStatus(SC_INTERNAL_SERVER_ERROR);
      log.atSevere().withCause(e).log("Failed to send error response");
    }
  }

  private void setHealthy() throws IOException {
    if (unhealthyFile.exists()) {
      Files.delete(unhealthyFile.toPath());
    }
  }

  private void setUnhealthy() throws IOException {
    if (!unhealthyFile.exists()) {
      Files.newOutputStream(unhealthyFile.toPath(), StandardOpenOption.CREATE).close();
    }
  }
}

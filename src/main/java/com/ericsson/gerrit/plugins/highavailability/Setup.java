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

package com.ericsson.gerrit.plugins.highavailability;

import static com.ericsson.gerrit.plugins.highavailability.Configuration.URL_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.USER_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.PASSWORD_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.CONNECTION_TIMEOUT_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.SOCKET_TIMEOUT_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.MAX_TRIES_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.RETRY_INTERVAL_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.INDEX_THREAD_POOL_SIZE_KEY;
import static com.ericsson.gerrit.plugins.highavailability.Configuration.CACHE_THREAD_POOL_SIZE_KEY;

import com.google.gerrit.common.FileUtil;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import java.nio.file.Path;

public class Setup implements InitStep {

  private final ConsoleUI ui;
  private final Section mySection;
  private final String pluginName;
  private final SitePaths site;

  @Inject
  public Setup(
      ConsoleUI ui, Section.Factory sections, @PluginName String pluginName, SitePaths site) {
    this.ui = ui;
    this.mySection = sections.get("plugin", pluginName);
    this.pluginName = pluginName;
    this.site = site;
  }

  @Override
  public void run() throws Exception {
    ui.message("\n");
    ui.header("%s Plugin", pluginName);

    if (ui.yesno(true, "Configure %s", pluginName)) {
      ui.header("Configuring %s", pluginName);
      configureSharedDir();
      configurePeer();
      configureTimeouts();
      configureRetry();
      configureThreadPools();
    }
  }

  private void configureSharedDir() {
    String sharedDir = mySection.string("Shared directory", "sharedDirectory", null);
    Path shared = site.site_path.resolve(sharedDir);
    FileUtil.mkdirsOrDie(shared, "cannot create " + shared);
  }

  private void configurePeer() {
    mySection.string("Peer URL", URL_KEY, null);
    mySection.string("User", USER_KEY, null);
    mySection.string("Password", PASSWORD_KEY, null);
  }

  private void configureTimeouts() {
    mySection.string("Connection timeout [ms]", CONNECTION_TIMEOUT_KEY, "5000");
    mySection.string("Socket timeout [ms]", SOCKET_TIMEOUT_KEY, "5000");
  }

  private void configureRetry() {
    mySection.string("Max number of tries to forward to remote peer", MAX_TRIES_KEY, "5");
    mySection.string("Retry interval [ms]", RETRY_INTERVAL_KEY, "1000");
  }

  private void configureThreadPools() {
    mySection.string("Index thread pool size", INDEX_THREAD_POOL_SIZE_KEY, "1");
    mySection.string("Cache thread pool size", CACHE_THREAD_POOL_SIZE_KEY, "1");
  }

  @Override
  public void postRun() throws Exception {}
}

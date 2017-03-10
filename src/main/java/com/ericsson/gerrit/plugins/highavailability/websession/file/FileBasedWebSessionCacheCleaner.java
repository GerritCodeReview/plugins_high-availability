// // Copyright (C) 2015 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.websession.file;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.concurrent.ScheduledFuture;

class FileBasedWebSessionCacheCleaner implements LifecycleListener {

    private static final int INITIAL_DELAY_MS = 1000;
    private static final int DEFAULT_CLEANUP_INTERVAL_HR = 24;
    private final WorkQueue queue;
    private final Provider<CleanupTask> cleanupTaskProvider;
    private final long cleanupIntervalMillis;
    private ScheduledFuture<?> scheduledCleanupTask;

  @Inject
  FileBasedWebSessionCacheCleaner(WorkQueue queue,
      Provider<CleanupTask> cleanupTaskProvider,
      PluginConfigFactory cfg,
      @PluginName String pluginName) {
    this.queue = queue;
    this.cleanupTaskProvider = cleanupTaskProvider;
    String fromConfig = Strings.nullToEmpty(
        cfg.getFromGerritConfig(pluginName, true).getString("cleanupInterval"));
    cleanupIntervalMillis = HOURS.toMillis(
        ConfigUtil.getTimeUnit(fromConfig, DEFAULT_CLEANUP_INTERVAL_HR, HOURS));
  }

  @Override
  public void start() {
    scheduledCleanupTask =
        queue.getDefaultQueue().scheduleAtFixedRate(cleanupTaskProvider.get(),
            INITIAL_DELAY_MS, cleanupIntervalMillis, MILLISECONDS);
  }

    @Override
    public void stop() {
      if(scheduledCleanupTask != null){
        scheduledCleanupTask.cancel(true);
      }
    }
  }

  class CleanupTask implements Runnable {

  private final FileBasedWebsessionCache fileBasedWebSessionCache;
  private final String pluginName;

  @Inject
  CleanupTask(
      FileBasedWebsessionCache fileBasedWebSessionCache,
      @PluginName String pluginName) {
    this.fileBasedWebSessionCache = fileBasedWebSessionCache;
    this.pluginName = pluginName;
  }

  @Override
  public void run() {
    fileBasedWebSessionCache.cleanUp();
  }

  @Override
  public String toString() {
    return String.format("[%s] Clean up expired file based websessions",
        pluginName);
  }
}

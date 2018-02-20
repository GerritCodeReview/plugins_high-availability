// Copyright (C) 2018 The Android Open Source Project
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

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IndexTs {
  private static final Logger log = LoggerFactory.getLogger(IndexTs.class);
  private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

  private final Path dataDir;
  private final Configuration cfg;
  private final WorkQueue.Executor exec;
  private final FlusherRunner flusher;

  private volatile LocalDateTime changeTs;
  private volatile LocalDateTime accountTs;
  private volatile LocalDateTime groupTs;

  class FlusherRunner implements Runnable {
    private Map<AbstractIndexRestApiServlet.IndexName, LocalDateTime> storedTs = new HashMap<>();

    @Override
    public void run() {
      store(AbstractIndexRestApiServlet.IndexName.CHANGE, changeTs);
      store(AbstractIndexRestApiServlet.IndexName.ACCOUNT, accountTs);
      store(AbstractIndexRestApiServlet.IndexName.GROUP, groupTs);
    }

    private void store(AbstractIndexRestApiServlet.IndexName index, LocalDateTime latestTs) {
      LocalDateTime currTs = storedTs.get(index);
      if (currTs == null || latestTs.isAfter(currTs)) {
        Path indexTsFile = dataDir.resolve(index.name().toLowerCase());
        try {
          Files.write(indexTsFile, latestTs.format(formatter).getBytes(StandardCharsets.UTF_8));
          storedTs.put(index, currTs);
        } catch (IOException e) {
          log.error("Unable to update last timestamp for index " + index, e);
        }
      }
    }
  }

  @Inject
  public IndexTs(@PluginData Path dataDir, Configuration cfg, WorkQueue queue) {
    this.dataDir = dataDir;
    this.cfg = cfg;
    this.exec = queue.getDefaultQueue();
    this.flusher = new FlusherRunner();
  }

  public void update(AbstractIndexRestApiServlet.IndexName index, LocalDateTime dateTime) {
    if (cfg.autoReindex().enabled()) {
      switch (index) {
        case CHANGE:
          changeTs = dateTime;
          break;
        case ACCOUNT:
          accountTs = dateTime;
          break;
        case GROUP:
          groupTs = dateTime;
          break;
      }
      exec.execute(flusher);
    }
  }

  public Optional<LocalDateTime> getUpdateTs(AbstractIndexRestApiServlet.IndexName index) {
    try {
      Path indexTsFile = dataDir.resolve(index.name().toLowerCase());
      if (indexTsFile.toFile().exists()) {
        String tsString = Files.readAllLines(indexTsFile).get(0);
        return Optional.of(LocalDateTime.parse(tsString, formatter));
      }
    } catch (Exception e) {
      log.warn("Unable to read last timestamp for index {}", index);
    }
    return Optional.empty();
  }
}

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

import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.AbstractIndexRestApiServlet.IndexName;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.AbstractIndexRestApiServlet.Operation;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IndexTs {
  private static final Logger log = LoggerFactory.getLogger(IndexTs.class);
  private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

  private final Path dataDir;
  private final WorkQueue.Executor exec;

  class FlusherRunner implements Runnable {
    private final IndexName indexName;
    private final Operation op;
    private final String id;
    private final LocalDateTime ts;

    public FlusherRunner(IndexName indexName, Operation op, String id, LocalDateTime ts) {
      this.indexName = indexName;
      this.op = op;
      this.id = id;
      this.ts = ts;
    }

    @Override
    public void run() {
      Path indexTsFile = dataDir.resolve(indexName.toString() + "-" + op.toString());
      try {
        Files.write(indexTsFile, (id + "," + ts.format(formatter)).getBytes());
      } catch (IOException e) {
        log.error("Unable to update last timestamp for " + op + " on index " + indexName, e);
      }
    }
  }

  @Inject
  public IndexTs(@PluginData Path dataDir, WorkQueue queue) {
    this.dataDir = dataDir;
    this.exec = queue.getDefaultQueue();
  }

  public void update(
      IndexName indexName, Operation op, String id, Optional<LocalDateTime> maybeTs) {
    exec.execute(new FlusherRunner(indexName, op, id, maybeTs.orElse(LocalDateTime.now())));
  }
}

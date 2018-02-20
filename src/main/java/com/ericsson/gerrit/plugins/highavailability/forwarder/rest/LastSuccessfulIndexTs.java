// Copyright (C) 2018 GerritForge Ltd
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.apache.log4j.Logger;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class LastSuccessfulIndexTs {
  private static final Logger log = Logger.getLogger(LastSuccessfulIndexTs.class);
  private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

  private final Path dataDir;
  private final Configuration cfg;

  @Inject
  public LastSuccessfulIndexTs(@PluginData Path dataDir, Configuration cfg) {
    this.dataDir = dataDir;
    this.cfg = cfg;
  }

  public void update(String index) {
    if (cfg.main().autoReindexAtStart()) {
      Path indexTsFile = dataDir.resolve(index);
      String ts = LocalDateTime.now().format(formatter);
      try {
        Files.write(indexTsFile, ts.getBytes());
      } catch (IOException e) {
        log.error("Unable to update last timestamp for index " + index, e);
      }
    }
  }

  public Optional<LocalDateTime> getUpdateTs(String index) {
    try {
      Path indexTsFile = dataDir.resolve(index);
      if (indexTsFile.toFile().exists()) {
        String tsString = Files.readAllLines(indexTsFile).get(0);
        return Optional.of(LocalDateTime.parse(tsString, formatter));
      }
    } catch (Exception e) {
      log.warn("Unable to read last timestamp for index " + index);
    }
    return Optional.empty();
  }
}

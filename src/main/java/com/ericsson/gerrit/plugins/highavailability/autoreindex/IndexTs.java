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

package com.ericsson.gerrit.plugins.highavailability.autoreindex;

import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.AbstractIndexRestApiServlet;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.AbstractIndexRestApiServlet.IndexName;
import com.ericsson.gerrit.plugins.highavailability.index.CurrentRequestContext;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.events.AccountIndexedListener;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import org.eclipse.jgit.internal.storage.file.FileSnapshot;

@Singleton
public class IndexTs implements ChangeIndexedListener, AccountIndexedListener {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();
  private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

  private final Path dataDir;
  private final ScheduledExecutorService exec;
  private final FlusherRunner changeFlusher;
  private final FlusherRunner accountFlusher;
  private final ChangeFinder changeFinder;
  private final CurrentRequestContext currCtx;

  private volatile LocalDateTime changeTs;
  private volatile LocalDateTime accountTs;

  class FlusherRunner implements Runnable {
    private final AbstractIndexRestApiServlet.IndexName index;
    private final Path tsPath;
    private FileSnapshot tsPathSnapshot;

    @Override
    public void run() {
      reloadIndexTimeStampIfChanged();
      LocalDateTime latestTs = getIndexTimeStamp();
      Optional<LocalDateTime> currTs = getUpdateTs(index);
      if (!currTs.isPresent() || latestTs.isAfter(currTs.get())) {
        try {
          Files.write(tsPath, latestTs.format(formatter).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
          log.atSevere().withCause(e).log("Unable to update last timestamp for index %s", index);
        }
      }
    }

    FlusherRunner(AbstractIndexRestApiServlet.IndexName index) {
      this.index = index;
      this.tsPath = getIndexTsPath(index);
      this.tsPathSnapshot = FileSnapshot.save(tsPath.toFile());
    }

    private LocalDateTime getIndexTimeStamp() {
      switch (index) {
        case CHANGE:
          return changeTs;
        case ACCOUNT:
          return accountTs;
        default:
          throw new IllegalArgumentException("Unsupported index " + index);
      }
    }

    private void reloadIndexTimeStampIfChanged() {
      if (tsPathSnapshot.isModified(tsPath.toFile())) {
        getUpdateTs(index).ifPresent(this::setIndexTimeStamp);
      }
    }

    private void setIndexTimeStamp(LocalDateTime newTs) {
      switch (index) {
        case CHANGE:
          changeTs = newTs;
          break;
        case ACCOUNT:
          accountTs = newTs;
          break;
      }

      tsPathSnapshot = FileSnapshot.save(tsPath.toFile());
    }
  }

  @Inject
  public IndexTs(
      @PluginData Path dataDir,
      WorkQueue queue,
      ChangeFinder changeFinder,
      CurrentRequestContext currCtx) {
    this.dataDir = dataDir;
    this.exec = queue.getDefaultQueue();
    this.changeFlusher = new FlusherRunner(AbstractIndexRestApiServlet.IndexName.CHANGE);
    this.accountFlusher = new FlusherRunner(AbstractIndexRestApiServlet.IndexName.ACCOUNT);
    this.changeFinder = changeFinder;
    this.currCtx = currCtx;
  }

  @Override
  public void onAccountIndexed(int id) {
    currCtx.onlyWithContext((ctx) -> update(IndexName.ACCOUNT, LocalDateTime.now()));
  }

  @Override
  public void onChangeIndexed(String projectName, int id) {
    currCtx.onlyWithContext(
        (ctx) -> {
          try {
            Optional<ChangeNotes> changeNotes = changeFinder.findOne(projectName + "~" + id);
            update(
                IndexName.CHANGE,
                !changeNotes.isPresent()
                    ? LocalDateTime.now()
                    : Timestamp.from(changeNotes.get().getChange().getLastUpdatedOn())
                        .toLocalDateTime());
          } catch (Exception e) {
            log.atWarning().withCause(e).log("Unable to update the latest TS for change %d", id);
          }
        });
  }

  @Override
  public void onChangeDeleted(int id) {
    currCtx.onlyWithContext((ctx) -> update(IndexName.CHANGE, LocalDateTime.now()));
  }

  public Optional<LocalDateTime> getUpdateTs(AbstractIndexRestApiServlet.IndexName index) {
    try {
      Path indexTsFile = getIndexTsPath(index);
      if (indexTsFile.toFile().exists()) {
        String tsString = Files.readAllLines(indexTsFile).get(0);
        return Optional.of(LocalDateTime.parse(tsString, formatter));
      }
    } catch (Exception e) {
      log.atWarning().withCause(e).log("Unable to read last timestamp for index %s", index);
    }
    return Optional.empty();
  }

  void update(AbstractIndexRestApiServlet.IndexName index, LocalDateTime dateTime) {
    switch (index) {
      case CHANGE:
        changeTs = dateTime;
        exec.execute(changeFlusher);
        break;
      case ACCOUNT:
        accountTs = dateTime;
        exec.execute(accountFlusher);
        break;
      default:
        throw new IllegalArgumentException("Unsupported index " + index);
    }
  }

  private Path getIndexTsPath(AbstractIndexRestApiServlet.IndexName index) {
    return dataDir.resolve(index.name().toLowerCase());
  }
}

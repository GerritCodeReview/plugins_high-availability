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

package com.ericsson.gerrit.plugins.highavailability;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import org.apache.log4j.Logger;

import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.LastSuccessfulIndexTs;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class AutoReindexAtStart implements LifecycleListener {
  private static final Logger log = Logger.getLogger(AutoReindexAtStart.class);

  private final ChangeIndexer changeIdx;
  private final AccountIndexer accountIdx;
  private final GroupIndexer groupIdx;
  private final LastSuccessfulIndexTs indexTs;
  private final SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  public AutoReindexAtStart(ChangeIndexer changeIdx, AccountIndexer accountIdx, GroupIndexer groupIdx,
      LastSuccessfulIndexTs indexTs, SchemaFactory<ReviewDb> reviewDbProvider) {
    this.changeIdx = changeIdx;
    this.accountIdx = accountIdx;
    this.groupIdx = groupIdx;
    this.indexTs = indexTs;
    this.reviewDbProvider = reviewDbProvider;
  }

  @Override
  public void start() {
    Optional<LocalDateTime> lastChangeTs = indexTs.getUpdateTs("change");
    if (lastChangeTs.isPresent()) {

      log.info("Scanning for all the changes modified after " + lastChangeTs.get());
      try (ReviewDb db = reviewDbProvider.open()) {
        for (Change c : db.changes().all()) {
          try {
            Timestamp changeTs = c.getLastUpdatedOn();
            Timestamp lastIndexTs = Timestamp.valueOf(lastChangeTs.get());

            if (changeTs.after(lastIndexTs)) {
              log.info(
                  "Reindexing " + c.getId() + " for project " + c.getProject() + " (lastUpdatedOn: " + changeTs + ")");
              changeIdx.index(db, c.getProject(), c.getId());
            }
          } catch (IOException e) {
            log.error("Unable to index change " + c.getId() + " for project " + c.getProject(), e);
          }
        }
      } catch (OrmException e) {
        log.error("Unable to scan changes", e);
      }
    }
  }

  @Override
  public void stop() {

  }

}

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

import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexGroupHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.AbstractIndexRestApiServlet;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

public class GroupReindexRunnable extends ReindexRunnable<GroupReference> {

  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private final Groups groups;
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsers;
  private final ForwardedIndexGroupHandler indexer;

  @Inject
  public GroupReindexRunnable(
      ForwardedIndexGroupHandler indexer,
      IndexTs indexTs,
      OneOffRequestContext ctx,
      Groups groups,
      GitRepositoryManager repoManager,
      AllUsersName allUsers) {
    super(AbstractIndexRestApiServlet.IndexName.GROUP, indexTs, ctx);
    this.groups = groups;
    this.repoManager = repoManager;
    this.allUsers = allUsers;
    this.indexer = indexer;
  }

  @Override
  protected Iterable<GroupReference> fetchItems(ReviewDb db) throws Exception {
    return groups.getAllGroupReferences()::iterator;
  }

  @Override
  protected Optional<Timestamp> indexIfNeeded(ReviewDb db, GroupReference g, Timestamp sinceTs) {
    try {
      Optional<InternalGroup> internalGroup = groups.getGroup(g.getUUID());
      if (internalGroup.isPresent()) {
        InternalGroup ig = internalGroup.get();
        Timestamp createGroupTs = ig.getCreatedOn();

        Repository allUsersRepo = repoManager.openRepository(allUsers);

        List<AccountGroupByIdAud> subGroupMembersAud =
            groups.getSubgroupsAudit(allUsersRepo, g.getUUID());
        Stream<Timestamp> groupIdAudAddTs =
            subGroupMembersAud.stream()
                .map(AccountGroupByIdAud::getAddedOn)
                .filter(Objects::nonNull);
        Stream<Timestamp> groupIdAudRemovedTs =
            subGroupMembersAud.stream()
                .map(AccountGroupByIdAud::getRemovedOn)
                .filter(Objects::nonNull);

        List<AccountGroupMemberAudit> groupMembersAud =
            groups.getMembersAudit(allUsersRepo, g.getUUID());
        Stream<Timestamp> groupMemberAudAddedTs =
            groupMembersAud.stream()
                .map(AccountGroupMemberAudit::getAddedOn)
                .filter(Objects::nonNull);
        Stream<Timestamp> groupMemberAudRemovedTs =
            groupMembersAud.stream()
                .map(AccountGroupMemberAudit::getRemovedOn)
                .filter(Objects::nonNull);
        Optional<Timestamp> groupLastTs =
            Streams.concat(
                    groupIdAudAddTs,
                    groupIdAudRemovedTs,
                    groupMemberAudAddedTs,
                    groupMemberAudRemovedTs,
                    Stream.of(createGroupTs))
                .max(Comparator.naturalOrder());

        if (groupLastTs.isPresent() && groupLastTs.get().after(sinceTs)) {
          log.atInfo().log("Index {}/{}/{}", g.getUUID(), g.getName(), groupLastTs.get());
          indexer.index(g.getUUID(), Operation.INDEX, Optional.empty());
          return groupLastTs;
        }
      }
    } catch (OrmException | IOException | ConfigInvalidException e) {
      log.atSevere().withCause(e).log("Reindex failed");
    }
    return Optional.empty();
  }
}

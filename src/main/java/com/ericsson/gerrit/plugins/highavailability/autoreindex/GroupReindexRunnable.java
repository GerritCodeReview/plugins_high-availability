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
import com.google.common.collect.Streams;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.sun.tools.javac.util.List;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.Id;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

public class GroupReindexRunnable extends ReindexRunnable<GroupReference> {

  private final Groups groups;

  @Inject
  public GroupReindexRunnable(IndexTs indexTs, OneOffRequestContext ctx, Groups groups) {
    super(AbstractIndexRestApiServlet.IndexName.GROUP, indexTs, ctx);
    this.groups = groups;
  }

  @Override
  protected Iterable<GroupReference> fetchItems(ReviewDb db) throws Exception {
    return groups.getAllGroupReferences()::iterator;
  }

  @Override
  protected Optional<Timestamp> indexIfNeeded(ReviewDb db, GroupReference g, Timestamp sinceTs) {
    try {
      //Id groupId = g.getId();
      Id groupId = g.getUUID();
      g.getUUID();
      ///
      groups.getMembersAudit()

      //private final GitRepositoryManager repoManager;
      //try (Repository allUsersRepo = repoManager.openRepository(allUsers))
      //
      Optional<InternalGroup> gg=groups.getGroup(g.getUUID());
      if (gg.isPresent())
      {
        Ts = gg.get().getCreatedOn();
      }

      Stream<Timestamp> groupIdAudTs =
          db.accountGroupByIdAud()
              .byGroup(g.getId())
              .toList()
              .stream()
              .map(ga -> ga.getRemovedOn())
              .filter(Objects::nonNull);
      List<AccountGroupMemberAudit> groupMembersAud =
          db.accountGroupMembersAudit().byGroup(groupId).toList();
      Stream<Timestamp> groupMemberAudAddedTs =
          groupMembersAud.stream().map(ga -> ga.getKey().getAddedOn()).filter(Objects::nonNull);
      Stream<Timestamp> groupMemberAudRemovedTs =
          groupMembersAud.stream().map(ga -> ga.getRemovedOn()).filter(Objects::nonNull);
      Optional<Timestamp> groupLastTs =
          Streams.concat(groupIdAudTs, groupMemberAudAddedTs, groupMemberAudRemovedTs)
              .max(Comparator.naturalOrder());

      if (groupLastTs.isPresent() && groupLastTs.get().after(sinceTs)) {
        log.info("Index {}/{}/{}", g.getGroupUUID(), g.getName(), groupLastTs.get());
        indexer.index(g.getGroupUUID(), Operation.INDEX, Optional.empty());
        return groupLastTs;
      }
    } catch (OrmException | IOException | ConfigInvalidException e) {
      log.error("Reindex failed", e);
    }
    return Optional.empty();
  }
}

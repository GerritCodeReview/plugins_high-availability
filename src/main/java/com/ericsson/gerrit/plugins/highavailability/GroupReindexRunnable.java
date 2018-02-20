package com.ericsson.gerrit.plugins.highavailability;

import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.IndexTs;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.IndexTs.IndexName;
import com.google.common.collect.Streams;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.Id;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroupReindexRunnable extends ReindexRunnable<AccountGroup> {
  private static final Logger log = LoggerFactory.getLogger(GroupReindexRunnable.class);

  private final GroupIndexer indexer;

  @Inject
  public GroupReindexRunnable(GroupIndexer indexer, IndexTs indexTs, OneOffRequestContext ctx) {
    super(IndexName.GROUP, indexTs, ctx);
    this.indexer = indexer;
  }

  @Override
  protected ResultSet<AccountGroup> fetchItems(ReviewDb db) throws OrmException {
    return db.accountGroups().all();
  }

  @Override
  protected Optional<Timestamp> indexIfNeeded(ReviewDb db, AccountGroup g, Timestamp sinceTs) {
    try {
      Id groupId = g.getId();
      Stream<Timestamp> groupIdAudTs =
          db.accountGroupByIdAud()
              .byGroup(g.getId())
              .toList()
              .stream()
              .map(ga -> ga.getRemovedOn());
      Stream<Timestamp> groupMemberAudAddedTs =
          db.accountGroupMembersAudit()
              .byGroup(groupId)
              .toList()
              .stream()
              .map(ga -> ga.getKey().getAddedOn());
      Stream<Timestamp> groupMemberAudRemovedTs =
          db.accountGroupMembersAudit()
              .byGroup(groupId)
              .toList()
              .stream()
              .map(ga -> ga.getRemovedOn())
              .filter(Objects::nonNull);
      Optional<Timestamp> groupLastTs =
          Streams.concat(groupIdAudTs, groupMemberAudAddedTs, groupMemberAudRemovedTs)
              .max(TimestampComparator.INSTANCE);

      if (groupLastTs.isPresent() && groupLastTs.get().after(sinceTs)) {
        log.info("Index {}/{}/{}", g.getGroupUUID(), g.getName(), groupLastTs.get());
        indexer.index(g.getGroupUUID());
        return groupLastTs;
      }
    } catch (OrmException | IOException e) {
      log.error("Reindex failed", e);
    }
    return Optional.empty();
  }
}

class TimestampComparator implements Comparator<Timestamp> {
  public static final TimestampComparator INSTANCE = new TimestampComparator();

  @Override
  public int compare(Timestamp o1, Timestamp o2) {
    return o1.compareTo(o2);
  }
}

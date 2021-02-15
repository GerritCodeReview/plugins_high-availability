// Copyright (C) 2021 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexGroupHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.UUID;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMember.Key;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.Groups;
import com.google.gerrit.server.util.OneOffRequestContext;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GroupReindexRunnableTest {

  @Mock private ForwardedIndexGroupHandler indexer;
  @Mock private IndexTs indexTs;
  @Mock private OneOffRequestContext ctx;
  @Mock private Groups groups;
  @Mock private GitRepositoryManager repoManager;
  @Mock private AllUsersName allUsers;
  @Mock private GroupReference groupReference;

  private GroupReindexRunnable groupReindexRunnable;
  private static UUID uuid;

  @Before
  public void setUp() throws Exception {
    groupReindexRunnable =
        new GroupReindexRunnable(indexer, indexTs, ctx, groups, repoManager, allUsers);
    uuid = new UUID("123");
    when(groupReference.getUUID()).thenReturn(uuid);
  }

  @Test
  public void groupIsIndexedWhenItIsCreatedAfterLastGroupReindex() throws Exception {
    Timestamp currentTime = Timestamp.valueOf(LocalDateTime.now());
    Timestamp afterCurrentTime = new Timestamp(currentTime.getTime() + 1000L);
    when(groups.getGroup(uuid)).thenReturn(getInternalGroup(afterCurrentTime));

    Optional<Timestamp> groupLastTs =
        groupReindexRunnable.indexIfNeeded(null, groupReference, currentTime);
    assertThat(groupLastTs.isPresent()).isTrue();
    assertThat(groupLastTs.get()).isEqualTo(afterCurrentTime);
    verify(indexer).index(uuid, Operation.INDEX, Optional.empty());
  }

  @Test
  public void groupIsNotIndexedWhenItIsCreatedBeforeLastGroupReindex() throws Exception {
    Timestamp currentTime = Timestamp.valueOf(LocalDateTime.now());
    Timestamp beforeCurrentTime = new Timestamp(currentTime.getTime() - 1000L);
    when(groups.getGroup(uuid)).thenReturn(getInternalGroup(beforeCurrentTime));

    Optional<Timestamp> groupLastTs =
        groupReindexRunnable.indexIfNeeded(null, groupReference, currentTime);
    assertThat(groupLastTs.isPresent()).isFalse();
    verify(indexer, never()).index(uuid, Operation.INDEX, Optional.empty());
  }

  @Test
  public void groupIsNotIndexedGroupReferenceNotPresent() {
    Timestamp currentTime = Timestamp.valueOf(LocalDateTime.now());
    Optional<Timestamp> groupLastTs =
        groupReindexRunnable.indexIfNeeded(null, groupReference, currentTime);
    assertThat(groupLastTs.isPresent()).isFalse();
  }

  @Test
  public void groupIsIndexedWhenNewUserAddedAfterLastGroupReindex() throws Exception {
    Timestamp currentTime = Timestamp.valueOf(LocalDateTime.now());
    Timestamp afterCurrentTime = new Timestamp(currentTime.getTime() + 1000L);
    when(groups.getGroup(uuid)).thenReturn(getInternalGroup(currentTime));
    when(groups.getMembersAudit(any(), any()))
        .thenReturn(
            Collections.singletonList(
                new AccountGroupMemberAudit(
                    new AccountGroupMember(new Key(new Id(1), new AccountGroup.Id(2))),
                    null,
                    afterCurrentTime)));
    Optional<Timestamp> groupLastTs =
        groupReindexRunnable.indexIfNeeded(null, groupReference, currentTime);
    assertThat(groupLastTs.isPresent()).isTrue();
    assertThat(groupLastTs.get()).isEqualTo(afterCurrentTime);
    verify(indexer).index(uuid, Operation.INDEX, Optional.empty());
  }

  @Test
  public void groupIsIndexedWhenItIsSubGroupAddedAfterLastGroupReindex() throws Exception {
    Timestamp currentTime = Timestamp.valueOf(LocalDateTime.now());
    Timestamp afterCurrentTime = new Timestamp(currentTime.getTime() + 1000L);
    Timestamp beforeCurrentTime = new Timestamp(currentTime.getTime() - 1000L);
    when(groups.getGroup(uuid)).thenReturn(getInternalGroup(beforeCurrentTime));
    when(groups.getSubgroupsAudit(any(), any()))
        .thenReturn(
            Collections.singletonList(
                new AccountGroupByIdAud(
                    new AccountGroupById(
                        new AccountGroupById.Key(new AccountGroup.Id(1), new UUID("123"))),
                    null,
                    afterCurrentTime)));
    Optional<Timestamp> groupLastTs =
        groupReindexRunnable.indexIfNeeded(null, groupReference, currentTime);
    assertThat(groupLastTs.isPresent()).isTrue();
    assertThat(groupLastTs.get()).isEqualTo(afterCurrentTime);
    verify(indexer).index(uuid, Operation.INDEX, Optional.empty());
  }

  private Optional<InternalGroup> getInternalGroup(Timestamp timestamp) {
    AccountGroup accountGroup =
        new AccountGroup(new AccountGroup.NameKey("Test"), new AccountGroup.Id(1), uuid, timestamp);
    return Optional.ofNullable(
        InternalGroup.create(
            accountGroup,
            ImmutableSet.<Id>builder().build(),
            ImmutableSet.<UUID>builder().build(),
            null));
  }
}

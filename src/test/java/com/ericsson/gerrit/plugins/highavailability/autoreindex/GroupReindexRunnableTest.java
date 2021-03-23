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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Account.Id;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.AccountGroup.UUID;
import com.google.gerrit.entities.AccountGroupByIdAudit;
import com.google.gerrit.entities.AccountGroupMemberAudit;
import com.google.gerrit.entities.GroupReference;
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
  private GroupReference groupReference;

  private GroupReindexRunnable groupReindexRunnable;
  private static UUID uuid;

  @Before
  public void setUp() throws Exception {
    groupReindexRunnable =
        new GroupReindexRunnable(indexer, indexTs, ctx, groups, repoManager, allUsers);
    uuid = UUID.parse("123");
    groupReference = GroupReference.create(uuid, "123");
  }

  @Test
  public void groupIsIndexedWhenItIsCreatedAfterLastGroupReindex() throws Exception {
    Timestamp currentTime = Timestamp.valueOf(LocalDateTime.now());
    Timestamp afterCurrentTime = new Timestamp(currentTime.getTime() + 1000L);
    when(groups.getGroup(uuid)).thenReturn(getInternalGroup(afterCurrentTime));

    Optional<Timestamp> groupLastTs =
        groupReindexRunnable.indexIfNeeded(groupReference, currentTime);
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
        groupReindexRunnable.indexIfNeeded(groupReference, currentTime);
    assertThat(groupLastTs.isPresent()).isFalse();
    verify(indexer, never()).index(uuid, Operation.INDEX, Optional.empty());
  }

  @Test
  public void groupIsNotIndexedGroupReferenceNotPresent() {
    Timestamp currentTime = Timestamp.valueOf(LocalDateTime.now());
    Optional<Timestamp> groupLastTs =
        groupReindexRunnable.indexIfNeeded(groupReference, currentTime);
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
                AccountGroupMemberAudit.builder()
                    .addedBy(Account.Id.tryParse("1").get())
                    .addedOn(afterCurrentTime)
                    .memberId(Account.Id.tryParse("1").get())
                    .groupId(AccountGroup.Id.parse("1"))
                    .build()));
    Optional<Timestamp> groupLastTs =
        groupReindexRunnable.indexIfNeeded(groupReference, currentTime);
    assertThat(groupLastTs.isPresent()).isTrue();
    assertThat(groupLastTs.get()).isEqualTo(afterCurrentTime);
    verify(indexer).index(uuid, Operation.INDEX, Optional.empty());
  }

  @Test
  public void groupIsIndexedWhenUserRemovedAfterLastGroupReindex() throws Exception {
    Timestamp currentTime = Timestamp.valueOf(LocalDateTime.now());
    Timestamp afterCurrentTime = new Timestamp(currentTime.getTime() + 1000L);
    Timestamp beforeCurrentTime = new Timestamp(currentTime.getTime() - 1000L);

    AccountGroupMemberAudit accountGroupMemberAudit =
        AccountGroupMemberAudit.builder()
            .addedBy(Account.Id.tryParse("1").get())
            .addedOn(beforeCurrentTime)
            .memberId(Account.Id.tryParse("1").get())
            .groupId(AccountGroup.Id.parse("2"))
            .removed(Account.Id.tryParse("2").get(), afterCurrentTime)
            .build();
    when(groups.getGroup(uuid)).thenReturn(getInternalGroup(currentTime));
    when(groups.getMembersAudit(any(), any()))
        .thenReturn(Collections.singletonList(accountGroupMemberAudit));
    Optional<Timestamp> groupLastTs =
        groupReindexRunnable.indexIfNeeded(groupReference, currentTime);
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
                AccountGroupByIdAudit.builder()
                    .groupId(AccountGroup.Id.parse("1"))
                    .includeUuid(UUID.parse("123"))
                    .addedBy(Account.Id.tryParse("1").get())
                    .addedOn(afterCurrentTime)
                    .build()));
    Optional<Timestamp> groupLastTs =
        groupReindexRunnable.indexIfNeeded(groupReference, currentTime);
    assertThat(groupLastTs.isPresent()).isTrue();
    assertThat(groupLastTs.get()).isEqualTo(afterCurrentTime);
    verify(indexer).index(uuid, Operation.INDEX, Optional.empty());
  }

  @Test
  public void groupIsIndexedWhenItIsSubGroupRemovedAfterLastGroupReindex() throws Exception {
    Timestamp currentTime = Timestamp.valueOf(LocalDateTime.now());
    Timestamp afterCurrentTime = new Timestamp(currentTime.getTime() + 1000L);
    Timestamp beforeCurrentTime = new Timestamp(currentTime.getTime() - 1000L);

    AccountGroupByIdAudit accountGroupByIdAud =
        AccountGroupByIdAudit.builder()
            .groupId(AccountGroup.Id.parse("1"))
            .includeUuid(UUID.parse("123"))
            .addedBy(Account.Id.tryParse("1").get())
            .addedOn(beforeCurrentTime)
            .removed(Account.Id.tryParse("2").get(), afterCurrentTime)
            .build();

    when(groups.getGroup(uuid)).thenReturn(getInternalGroup(beforeCurrentTime));
    when(groups.getSubgroupsAudit(any(), any()))
        .thenReturn(Collections.singletonList(accountGroupByIdAud));

    Optional<Timestamp> groupLastTs =
        groupReindexRunnable.indexIfNeeded(groupReference, currentTime);
    assertThat(groupLastTs.isPresent()).isTrue();
    assertThat(groupLastTs.get()).isEqualTo(afterCurrentTime);
    verify(indexer).index(uuid, Operation.INDEX, Optional.empty());
  }

  private Optional<InternalGroup> getInternalGroup(Timestamp timestamp) {
    return Optional.ofNullable(
        InternalGroup.builder()
            .setId(AccountGroup.Id.parse("1"))
            .setNameKey(AccountGroup.nameKey("Test"))
            .setOwnerGroupUUID(uuid)
            .setVisibleToAll(true)
            .setGroupUUID(UUID.parse("12"))
            .setCreatedOn(timestamp)
            .setMembers(ImmutableSet.<Id>builder().build())
            .setSubgroups(ImmutableSet.<UUID>builder().build())
            .build());
  }
}

// Copyright (C) 2015 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.index;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.DeleteChangeTask;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.IndexAccountTask;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.IndexChangeTask;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.IndexGroupTask;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexEventHandlerTest {
  private static final String PLUGIN_NAME = "high-availability";
  private static final String PROJECT_NAME = "test/project";
  private static final int CHANGE_ID = 1;
  private static final int ACCOUNT_ID = 2;
  private static final String UUID = "3";
  private static final String OTHER_UUID = "4";

  private IndexEventHandler indexEventHandler;
  @Mock private Forwarder forwarder;
  @Mock private ChangeCheckerImpl.Factory changeCheckerFactoryMock;
  @Mock private ChangeChecker changeCheckerMock;
  private Change.Id changeId;
  private Account.Id accountId;
  private AccountGroup.UUID accountGroupUUID;
  @Mock private RequestContext mockCtx;

  private CurrentRequestContext currCtx =
      new CurrentRequestContext(null, null, null) {
        @Override
        public void onlyWithContext(Consumer<RequestContext> body) {
          body.accept(mockCtx);
        }
      };

  @Before
  public void setUpMocks() throws Exception {
    changeId = new Change.Id(CHANGE_ID);
    accountId = new Account.Id(ACCOUNT_ID);
    accountGroupUUID = new AccountGroup.UUID(UUID);
    when(changeCheckerFactoryMock.create(any())).thenReturn(changeCheckerMock);
    when(changeCheckerMock.newIndexEvent()).thenReturn(Optional.of(new IndexEvent()));

    setUpIndexEventHandler(currCtx);
  }

  public void setUpIndexEventHandler(CurrentRequestContext currCtx) throws Exception {
    indexEventHandler =
        new IndexEventHandler(
            MoreExecutors.directExecutor(),
            MoreExecutors.directExecutor(),
            PLUGIN_NAME,
            forwarder,
            changeCheckerFactoryMock,
            currCtx);
  }

  @Test
  public void shouldIndexInRemoteOnChangeIndexedEvent() throws Exception {
    indexEventHandler.onChangeIndexed(PROJECT_NAME, changeId.get());
    verify(forwarder).indexChange(eq(PROJECT_NAME), eq(CHANGE_ID), any());
  }

  @Test
  public void shouldNotIndexInRemoteWhenContextIsMissing() throws Exception {
    ThreadLocalRequestContext threadLocalCtxMock = mock(ThreadLocalRequestContext.class);
    OneOffRequestContext oneOffCtxMock = mock(OneOffRequestContext.class);
    Configuration cfgMock = mock(Configuration.class);
    Configuration.Index cfgIndex = mock(Configuration.Index.class);
    when(cfgMock.index()).thenReturn(cfgIndex);

    setUpIndexEventHandler(new CurrentRequestContext(threadLocalCtxMock, cfgMock, oneOffCtxMock));
    indexEventHandler.onChangeIndexed(PROJECT_NAME, changeId.get());
    verify(forwarder, never()).indexChange(eq(PROJECT_NAME), eq(CHANGE_ID), any());
  }

  @Test
  public void shouldReindexInRemoteWhenContextIsMissingButForcedIndexingEnabled() throws Exception {
    ThreadLocalRequestContext threadLocalCtxMock = mock(ThreadLocalRequestContext.class);
    OneOffRequestContext oneOffCtxMock = mock(OneOffRequestContext.class);
    Configuration cfgMock = mock(Configuration.class);
    Configuration.Index cfgIndex = mock(Configuration.Index.class);
    when(cfgMock.index()).thenReturn(cfgIndex);
    when(cfgIndex.synchronizeForced()).thenReturn(true);

    setUpIndexEventHandler(new CurrentRequestContext(threadLocalCtxMock, cfgMock, oneOffCtxMock));
    indexEventHandler.onChangeIndexed(PROJECT_NAME, changeId.get());
    verify(forwarder).indexChange(eq(PROJECT_NAME), eq(CHANGE_ID), any());
  }

  @Test
  public void shouldIndexInRemoteOnAccountIndexedEvent() throws Exception {
    indexEventHandler.onAccountIndexed(accountId.get());
    verify(forwarder).indexAccount(eq(ACCOUNT_ID), any());
  }

  @Test
  public void shouldDeleteFromIndexInRemoteOnChangeDeletedEvent() throws Exception {
    indexEventHandler.onChangeDeleted(changeId.get());
    verify(forwarder).deleteChangeFromIndex(eq(CHANGE_ID), any());
    verifyZeroInteractions(
        changeCheckerMock); // Deleted changes should not be checked against NoteDb
  }

  @Test
  public void shouldIndexInRemoteOnGroupIndexedEvent() throws Exception {
    indexEventHandler.onGroupIndexed(accountGroupUUID.get());
    verify(forwarder).indexGroup(eq(UUID), any());
  }

  @Test
  public void shouldNotCallRemoteWhenChangeEventIsForwarded() throws Exception {
    Context.setForwardedEvent(true);
    indexEventHandler.onChangeIndexed(PROJECT_NAME, changeId.get());
    indexEventHandler.onChangeDeleted(changeId.get());
    Context.unsetForwardedEvent();
    verifyZeroInteractions(forwarder);
  }

  @Test
  public void shouldNotCallRemoteWhenAccountEventIsForwarded() throws Exception {
    Context.setForwardedEvent(true);
    indexEventHandler.onAccountIndexed(accountId.get());
    indexEventHandler.onAccountIndexed(accountId.get());
    Context.unsetForwardedEvent();
    verifyZeroInteractions(forwarder);
  }

  @Test
  public void shouldNotCallRemoteWhenGroupEventIsForwarded() throws Exception {
    Context.setForwardedEvent(true);
    indexEventHandler.onGroupIndexed(accountGroupUUID.get());
    indexEventHandler.onGroupIndexed(accountGroupUUID.get());
    Context.unsetForwardedEvent();
    verifyZeroInteractions(forwarder);
  }

  @Test
  public void duplicateChangeEventOfAQueuedEventShouldGetDiscarded() {
    ScheduledThreadPoolExecutor poolMock = mock(ScheduledThreadPoolExecutor.class);
    ScheduledThreadPoolExecutor poolBatchMock = mock(ScheduledThreadPoolExecutor.class);
    indexEventHandler =
        new IndexEventHandler(
            poolMock, poolBatchMock, PLUGIN_NAME, forwarder, changeCheckerFactoryMock, currCtx);
    indexEventHandler.onChangeIndexed(PROJECT_NAME, changeId.get());
    indexEventHandler.onChangeIndexed(PROJECT_NAME, changeId.get());
    verify(poolMock, times(1))
        .execute(indexEventHandler.new IndexChangeTask(PROJECT_NAME, CHANGE_ID, null));
  }

  @Test
  public void duplicateAccountEventOfAQueuedEventShouldGetDiscarded() {
    ScheduledThreadPoolExecutor poolMock = mock(ScheduledThreadPoolExecutor.class);
    ScheduledThreadPoolExecutor poolBatchMock = mock(ScheduledThreadPoolExecutor.class);
    indexEventHandler =
        new IndexEventHandler(
            poolMock, poolBatchMock, PLUGIN_NAME, forwarder, changeCheckerFactoryMock, currCtx);
    indexEventHandler.onAccountIndexed(accountId.get());
    indexEventHandler.onAccountIndexed(accountId.get());
    verify(poolMock, times(1)).execute(indexEventHandler.new IndexAccountTask(ACCOUNT_ID));
  }

  @Test
  public void duplicateGroupEventOfAQueuedEventShouldGetDiscarded() {
    ScheduledThreadPoolExecutor poolMock = mock(ScheduledThreadPoolExecutor.class);
    ScheduledThreadPoolExecutor poolBatchMock = mock(ScheduledThreadPoolExecutor.class);
    indexEventHandler =
        new IndexEventHandler(
            poolMock, poolBatchMock, PLUGIN_NAME, forwarder, changeCheckerFactoryMock, currCtx);
    indexEventHandler.onGroupIndexed(accountGroupUUID.get());
    indexEventHandler.onGroupIndexed(accountGroupUUID.get());
    verify(poolMock, times(1)).execute(indexEventHandler.new IndexGroupTask(UUID));
  }

  @Test
  public void testIndexChangeTaskToString() throws Exception {
    IndexChangeTask task = indexEventHandler.new IndexChangeTask(PROJECT_NAME, CHANGE_ID, null);
    assertThat(task.toString())
        .isEqualTo(
            String.format("[%s] Index change %s in target instance", PLUGIN_NAME, CHANGE_ID));
  }

  @Test
  public void testIndexAccountTaskToString() throws Exception {
    IndexAccountTask task = indexEventHandler.new IndexAccountTask(ACCOUNT_ID);
    assertThat(task.toString())
        .isEqualTo(
            String.format("[%s] Index account %s in target instance", PLUGIN_NAME, ACCOUNT_ID));
  }

  @Test
  public void testIndexGroupTaskToString() throws Exception {
    IndexGroupTask task = indexEventHandler.new IndexGroupTask(UUID);
    assertThat(task.toString())
        .isEqualTo(String.format("[%s] Index group %s in target instance", PLUGIN_NAME, UUID));
  }

  @Test
  public void testIndexChangeTaskHashCodeAndEquals() {
    IndexChangeTask task = indexEventHandler.new IndexChangeTask(PROJECT_NAME, CHANGE_ID, null);

    IndexChangeTask sameTask = task;
    assertThat(task.equals(sameTask)).isTrue();
    assertThat(task.hashCode()).isEqualTo(sameTask.hashCode());

    IndexChangeTask identicalTask =
        indexEventHandler.new IndexChangeTask(PROJECT_NAME, CHANGE_ID, null);
    assertThat(task.equals(identicalTask)).isTrue();
    assertThat(task.hashCode()).isEqualTo(identicalTask.hashCode());

    assertThat(task.equals(null)).isFalse();
    assertThat(
            task.equals(indexEventHandler.new IndexChangeTask(PROJECT_NAME, CHANGE_ID + 1, null)))
        .isFalse();
    assertThat(task.hashCode()).isNotEqualTo("test".hashCode());

    IndexChangeTask differentChangeIdTask =
        indexEventHandler.new IndexChangeTask(PROJECT_NAME, 123, null);
    assertThat(task.equals(differentChangeIdTask)).isFalse();
    assertThat(task.hashCode()).isNotEqualTo(differentChangeIdTask.hashCode());
  }

  @Test
  public void testDeleteChangeTaskHashCodeAndEquals() {
    DeleteChangeTask task = indexEventHandler.new DeleteChangeTask(CHANGE_ID, null);

    DeleteChangeTask sameTask = task;
    assertThat(task.equals(sameTask)).isTrue();
    assertThat(task.hashCode()).isEqualTo(sameTask.hashCode());

    DeleteChangeTask identicalTask = indexEventHandler.new DeleteChangeTask(CHANGE_ID, null);
    assertThat(task.equals(identicalTask)).isTrue();
    assertThat(task.hashCode()).isEqualTo(identicalTask.hashCode());

    assertThat(task.equals(null)).isFalse();
    assertThat(task.equals(indexEventHandler.new DeleteChangeTask(CHANGE_ID + 1, null))).isFalse();
    assertThat(task.hashCode()).isNotEqualTo("test".hashCode());

    DeleteChangeTask differentChangeIdTask = indexEventHandler.new DeleteChangeTask(123, null);
    assertThat(task.equals(differentChangeIdTask)).isFalse();
    assertThat(task.hashCode()).isNotEqualTo(differentChangeIdTask.hashCode());
  }

  @Test
  public void testIndexAccountTaskHashCodeAndEquals() {
    IndexAccountTask task = indexEventHandler.new IndexAccountTask(ACCOUNT_ID);

    IndexAccountTask sameTask = task;
    assertThat(task.equals(sameTask)).isTrue();
    assertThat(task.hashCode()).isEqualTo(sameTask.hashCode());

    IndexAccountTask identicalTask = indexEventHandler.new IndexAccountTask(ACCOUNT_ID);
    assertThat(task.equals(identicalTask)).isTrue();
    assertThat(task.hashCode()).isEqualTo(identicalTask.hashCode());

    assertThat(task.equals(null)).isFalse();
    assertThat(task.equals(indexEventHandler.new IndexAccountTask(ACCOUNT_ID + 1))).isFalse();
    assertThat(task.hashCode()).isNotEqualTo("test".hashCode());

    IndexAccountTask differentAccountIdTask = indexEventHandler.new IndexAccountTask(123);
    assertThat(task.equals(differentAccountIdTask)).isFalse();
    assertThat(task.hashCode()).isNotEqualTo(differentAccountIdTask.hashCode());
  }

  @Test
  public void testIndexGroupTaskHashCodeAndEquals() {
    IndexGroupTask task = indexEventHandler.new IndexGroupTask(UUID);

    IndexGroupTask sameTask = task;
    assertThat(task.equals(sameTask)).isTrue();
    assertThat(task.hashCode()).isEqualTo(sameTask.hashCode());

    IndexGroupTask identicalTask = indexEventHandler.new IndexGroupTask(UUID);
    assertThat(task.equals(identicalTask)).isTrue();
    assertThat(task.hashCode()).isEqualTo(identicalTask.hashCode());

    assertThat(task.equals(null)).isFalse();
    assertThat(task.equals(indexEventHandler.new IndexGroupTask(OTHER_UUID))).isFalse();
    assertThat(task.hashCode()).isNotEqualTo("test".hashCode());

    IndexGroupTask differentGroupIdTask = indexEventHandler.new IndexGroupTask("123");
    assertThat(task.equals(differentGroupIdTask)).isFalse();
    assertThat(task.hashCode()).isNotEqualTo(differentGroupIdTask.hashCode());
  }
}

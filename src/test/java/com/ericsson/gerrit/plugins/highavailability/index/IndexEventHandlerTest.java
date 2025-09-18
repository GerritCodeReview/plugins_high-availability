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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.ericsson.gerrit.plugins.highavailability.forwarder.EventType;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder.Result;
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexEventHandlerTest {
  private static final String PROJECT_NAME = "test/project";
  private static final int CHANGE_ID = 1;
  private static final int ACCOUNT_ID = 2;
  private static final String UUID = "3";

  private IndexEventHandler indexEventHandler;
  @Mock private Forwarder forwarder;
  @Mock private ChangeCheckerImpl.Factory changeCheckerFactoryMock;
  @Mock private ChangeChecker changeCheckerMock;
  private Change.Id changeId;
  private Account.Id accountId;
  private AccountGroup.UUID accountGroupUUID;
  @Mock private RequestContext mockCtx;
  @Mock private Configuration configuration;

  private CurrentRequestContext currCtx =
      new CurrentRequestContext(null, null, null) {
        @Override
        public void onlyWithContext(Consumer<RequestContext> body) {
          body.accept(mockCtx);
        }
      };

  @Before
  public void setUpMocks() throws Exception {
    changeId = Change.id(CHANGE_ID);
    accountId = Account.id(ACCOUNT_ID);
    accountGroupUUID = AccountGroup.uuid(UUID);
    when(changeCheckerFactoryMock.create(any())).thenReturn(changeCheckerMock);
    when(changeCheckerMock.newIndexEvent()).thenReturn(Optional.of(new IndexEvent()));

    when(forwarder.indexAccount(eq(ACCOUNT_ID), any()))
        .thenReturn(
            CompletableFuture.completedFuture(new Result(EventType.INDEX_ACCOUNT_UPDATE, true)));
    when(forwarder.deleteChangeFromIndex(eq(CHANGE_ID), any()))
        .thenReturn(
            CompletableFuture.completedFuture(new Result(EventType.INDEX_CHANGE_DELETION, true)));
    when(forwarder.indexGroup(eq(UUID), any()))
        .thenReturn(
            CompletableFuture.completedFuture(new Result(EventType.INDEX_GROUP_UPDATE, true)));
    when(forwarder.indexChange(eq(PROJECT_NAME), eq(CHANGE_ID), any()))
        .thenReturn(
            CompletableFuture.completedFuture(new Result(EventType.INDEX_CHANGE_UPDATE, true)));

    setUpIndexEventHandler(currCtx);
  }

  public void setUpIndexEventHandler(CurrentRequestContext currCtx) throws Exception {
    indexEventHandler = new IndexEventHandler(forwarder, changeCheckerFactoryMock, currCtx);
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
    verifyNoInteractions(changeCheckerMock); // Deleted changes should not be checked against NoteDb
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
    verifyNoInteractions(forwarder);
  }

  @Test
  public void shouldNotCallRemoteWhenAccountEventIsForwarded() throws Exception {
    Context.setForwardedEvent(true);
    indexEventHandler.onAccountIndexed(accountId.get());
    indexEventHandler.onAccountIndexed(accountId.get());
    Context.unsetForwardedEvent();
    verifyNoInteractions(forwarder);
  }

  @Test
  public void shouldNotCallRemoteWhenGroupEventIsForwarded() throws Exception {
    Context.setForwardedEvent(true);
    indexEventHandler.onGroupIndexed(accountGroupUUID.get());
    indexEventHandler.onGroupIndexed(accountGroupUUID.get());
    Context.unsetForwardedEvent();
    verifyNoInteractions(forwarder);
  }
}

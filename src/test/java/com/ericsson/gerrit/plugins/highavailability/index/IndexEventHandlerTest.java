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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.ericsson.gerrit.plugins.highavailability.forwarder.IndexEvent;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.DeleteChangeTask;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.IndexAccountTask;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.IndexChangeTask;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.IndexGroupTask;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventHandler.IndexTask;
import com.ericsson.gerrit.plugins.highavailability.index.IndexEventLocks.VoidFunction;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Change;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import java.lang.Thread.State;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexEventHandlerTest {
  private static final String PLUGIN_NAME = "high-availability";
  private static final String PROJECT_NAME = "test/project";
  private static final int CHANGE_ID = 1;
  private static final int ACCOUNT_ID = 2;
  private static final String UUID = "3";
  private static final String OTHER_UUID = "4";
  private static final Integer INDEX_WAIT_TIMEOUT_MS = 5;
  private static final int MAX_TEST_PARALLELISM = 4;
  private static final String EXECUTOR_THREAD_NAME = "EXECUTOR_THREAD";

  private IndexEventHandler indexEventHandler;
  @Mock private Forwarder forwarder;
  @Mock private ChangeCheckerImpl.Factory changeCheckerFactoryMock;
  @Mock private ChangeChecker changeCheckerMock;
  private Change.Id changeId;
  private Account.Id accountId;
  private AccountGroup.UUID accountGroupUUID;
  private ScheduledExecutorService executor = new CurrentThreadScheduledExecutorService();
  private ScheduledExecutorService batchExecutor = new CurrentThreadScheduledExecutorService();
  private ScheduledExecutorService testExecutor =
      Executors.newScheduledThreadPool(MAX_TEST_PARALLELISM);
  @Mock private RequestContext mockCtx;
  @Mock private Configuration configuration;
  private IndexEventLocks idLocks;

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

    Configuration.Index cfgIndex = mock(Configuration.Index.class);
    when(configuration.index()).thenReturn(cfgIndex);
    when(cfgIndex.numStripedLocks()).thenReturn(Configuration.DEFAULT_NUM_STRIPED_LOCKS);

    Configuration.Http http = mock(Configuration.Http.class);
    when(configuration.http()).thenReturn(http);
    when(http.maxTries()).thenReturn(Configuration.Http.DEFAULT_MAX_TRIES);
    when(http.retryInterval()).thenReturn(Configuration.Http.DEFAULT_RETRY_INTERVAL);
    when(forwarder.indexAccount(eq(ACCOUNT_ID), any()))
        .thenReturn(CompletableFuture.completedFuture(true));
    when(forwarder.deleteChangeFromIndex(eq(CHANGE_ID), any()))
        .thenReturn(CompletableFuture.completedFuture(true));
    when(forwarder.indexGroup(eq(UUID), any())).thenReturn(CompletableFuture.completedFuture(true));
    when(forwarder.indexChange(eq(PROJECT_NAME), eq(CHANGE_ID), any()))
        .thenReturn(CompletableFuture.completedFuture(true));

    idLocks = new IndexEventLocks(configuration);
    setUpIndexEventHandler(currCtx);
  }

  public void setUpIndexEventHandler(CurrentRequestContext currCtx) throws Exception {
    setUpIndexEventHandler(currCtx, idLocks, configuration);
  }

  public void setUpIndexEventHandler(CurrentRequestContext currCtx, IndexEventLocks idLocks)
      throws Exception {
    setUpIndexEventHandler(currCtx, idLocks, configuration);
  }

  public void setUpIndexEventHandler(
      CurrentRequestContext currCtx, IndexEventLocks idLocks, Configuration configuration)
      throws Exception {
    indexEventHandler =
        new IndexEventHandler(
            executor,
            batchExecutor,
            PLUGIN_NAME,
            forwarder,
            changeCheckerFactoryMock,
            currCtx,
            configuration,
            idLocks);
  }

  @Test
  public void shouldIndexInRemoteOnChangeIndexedEvent() throws Exception {
    indexEventHandler.onChangeIndexed(PROJECT_NAME, changeId.get());
    getExecutorThread();
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
  public void shouldNotIndexChangeWhenCannotAcquireLock() throws Exception {
    IndexEventLocks locks = mock(IndexEventLocks.class);
    Semaphore semaphore = mock(Semaphore.class);
    when(locks.getSemaphore(anyString())).thenReturn(semaphore);
    Mockito.doCallRealMethod().when(locks).withLock(any(), any(), any());
    when(semaphore.tryAcquire(Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
        .thenReturn(false);
    setUpIndexEventHandler(currCtx, locks);

    indexEventHandler.onChangeIndexed(PROJECT_NAME, changeId.get());

    verify(forwarder, never()).indexChange(eq(PROJECT_NAME), eq(CHANGE_ID), any());
  }

  @Test
  public void shouldNotIndexAccountWhenCannotAcquireLock() throws Exception {
    IndexEventLocks locks = mock(IndexEventLocks.class);
    Semaphore semaphore = mock(Semaphore.class);
    when(locks.getSemaphore(anyString())).thenReturn(semaphore);
    when(semaphore.tryAcquire(Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
        .thenReturn(false);
    Mockito.doCallRealMethod().when(locks).withLock(any(), any(), any());
    setUpIndexEventHandler(currCtx, locks);

    indexEventHandler.onAccountIndexed(accountId.get());

    verify(forwarder, never()).indexAccount(eq(ACCOUNT_ID), any());
  }

  @Test
  public void shouldNotDeleteChangeWhenCannotAcquireLock() throws Exception {
    IndexEventLocks locks = mock(IndexEventLocks.class);
    Semaphore semaphore = mock(Semaphore.class);
    when(locks.getSemaphore(anyString())).thenReturn(semaphore);
    when(semaphore.tryAcquire(Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
        .thenReturn(false);
    Mockito.doCallRealMethod().when(locks).withLock(any(), any(), any());
    setUpIndexEventHandler(currCtx, locks);

    indexEventHandler.onChangeDeleted(changeId.get());

    verify(forwarder, never()).deleteChangeFromIndex(eq(CHANGE_ID), any());
  }

  @Test
  public void shouldNotIndexGroupWhenCannotAcquireLock() throws Exception {
    IndexEventLocks locks = mock(IndexEventLocks.class);
    Semaphore semaphore = mock(Semaphore.class);
    when(locks.getSemaphore(anyString())).thenReturn(semaphore);
    when(semaphore.tryAcquire(Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
        .thenReturn(false);
    Mockito.doCallRealMethod().when(locks).withLock(any(), any(), any());
    setUpIndexEventHandler(currCtx, locks);

    indexEventHandler.onGroupIndexed(accountGroupUUID.get());

    verify(forwarder, never()).indexGroup(eq(UUID), any());
  }

  @Test
  public void shouldNotIndexProjectWhenCannotAcquireLock() throws Exception {
    IndexEventLocks locks = mock(IndexEventLocks.class);
    Semaphore semaphore = mock(Semaphore.class);
    when(locks.getSemaphore(anyString())).thenReturn(semaphore);
    when(semaphore.tryAcquire(Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
        .thenReturn(false);
    Mockito.doCallRealMethod().when(locks).withLock(any(), any(), any());
    setUpIndexEventHandler(currCtx, locks);

    indexEventHandler.onProjectIndexed(PROJECT_NAME);

    verify(forwarder, never()).indexProject(eq(PROJECT_NAME), any());
  }

  @Test
  public void shouldRetryIndexChangeWhenCannotAcquireLock() throws Exception {
    IndexEventLocks locks = mock(IndexEventLocks.class);
    Semaphore semaphore = mock(Semaphore.class);
    when(locks.getSemaphore(anyString())).thenReturn(semaphore);
    Mockito.doCallRealMethod().when(locks).withLock(any(), any(), any());
    when(semaphore.tryAcquire(Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
        .thenReturn(false, true);
    setUpIndexEventHandler(currCtx, locks);

    indexEventHandler.onChangeIndexed(PROJECT_NAME, changeId.get());
    getExecutorThread();
    verify(locks, times(2)).withLock(any(), any(), any());
    verify(forwarder, times(1)).indexChange(eq(PROJECT_NAME), eq(CHANGE_ID), any());
  }

  @Test
  public void shouldRetryUpToMaxTriesWhenCannotAcquireLock() throws Exception {
    IndexEventLocks locks = mock(IndexEventLocks.class);
    Semaphore semaphore = mock(Semaphore.class);
    when(locks.getSemaphore(anyString())).thenReturn(semaphore);
    Mockito.doCallRealMethod().when(locks).withLock(any(), any(), any());
    when(semaphore.tryAcquire(Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
        .thenReturn(false);

    Configuration cfg = mock(Configuration.class);
    Configuration.Http httpCfg = mock(Configuration.Http.class);
    when(httpCfg.maxTries()).thenReturn(10);
    when(cfg.http()).thenReturn(httpCfg);
    setUpIndexEventHandler(currCtx, locks, cfg);
    indexEventHandler.onChangeIndexed(PROJECT_NAME, changeId.get());
    getExecutorThread();
    verify(locks, times(11)).withLock(any(), any(), any());
    verify(forwarder, never()).indexChange(eq(PROJECT_NAME), eq(CHANGE_ID), any());
  }

  @Test
  public void shouldNotRetryWhenMaxTriesLowerThanOne() throws Exception {
    IndexEventLocks locks = mock(IndexEventLocks.class);
    Semaphore semaphore = mock(Semaphore.class);
    when(locks.getSemaphore(anyString())).thenReturn(semaphore);
    Mockito.doCallRealMethod().when(locks).withLock(any(), any(), any());
    when(semaphore.tryAcquire(Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
        .thenReturn(false);

    Configuration cfg = mock(Configuration.class);
    Configuration.Http httpCfg = mock(Configuration.Http.class);
    when(httpCfg.maxTries()).thenReturn(0);
    when(cfg.http()).thenReturn(httpCfg);
    setUpIndexEventHandler(currCtx, locks, cfg);

    indexEventHandler.onChangeIndexed(PROJECT_NAME, changeId.get());
    getExecutorThread();
    verify(locks, times(1)).withLock(any(), any(), any());
    verify(forwarder, never()).indexChange(eq(PROJECT_NAME), eq(CHANGE_ID), any());
  }

  @Test
  public void shouldLockPerIndexEventType() throws Exception {
    IndexEventLocks locks = mock(IndexEventLocks.class);
    Semaphore indexChangeLock = mock(Semaphore.class);
    when(indexChangeLock.tryAcquire(Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
        .thenReturn(false);
    Semaphore accountChangeLock = mock(Semaphore.class);
    when(accountChangeLock.tryAcquire(Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS)))
        .thenReturn(true);
    when(locks.getSemaphore(eq("change/" + CHANGE_ID))).thenReturn(indexChangeLock);
    when(locks.getSemaphore(eq("account/" + ACCOUNT_ID))).thenReturn(accountChangeLock);
    Mockito.doCallRealMethod().when(locks).withLock(any(), any(), any());
    setUpIndexEventHandler(currCtx, locks);

    indexEventHandler.onChangeIndexed(PROJECT_NAME, changeId.get());
    indexEventHandler.onAccountIndexed(accountId.get());
    getExecutorThread();
    verify(forwarder, never()).indexChange(eq(PROJECT_NAME), eq(CHANGE_ID), any());
    verify(forwarder).indexAccount(eq(ACCOUNT_ID), any());
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
    getExecutorThread();
    verify(forwarder).indexChange(eq(PROJECT_NAME), eq(CHANGE_ID), any());
  }

  @Test
  public void shouldIndexInRemoteOnAccountIndexedEvent() throws Exception {
    indexEventHandler.onAccountIndexed(accountId.get());
    getExecutorThread();
    verify(forwarder).indexAccount(eq(ACCOUNT_ID), any());
  }

  @Test
  public void shouldDeleteFromIndexInRemoteOnChangeDeletedEvent() throws Exception {
    indexEventHandler.onChangeDeleted(changeId.get());
    getExecutorThread();
    verify(forwarder).deleteChangeFromIndex(eq(CHANGE_ID), any());
    verifyNoInteractions(changeCheckerMock); // Deleted changes should not be checked against NoteDb
  }

  @Test
  public void shouldIndexInRemoteOnGroupIndexedEvent() throws Exception {
    indexEventHandler.onGroupIndexed(accountGroupUUID.get());
    getExecutorThread();
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

  @Test
  public void duplicateChangeEventOfAQueuedEventShouldGetDiscarded() {
    ScheduledThreadPoolExecutor poolMock = mock(ScheduledThreadPoolExecutor.class);
    ScheduledThreadPoolExecutor poolBatchMock = mock(ScheduledThreadPoolExecutor.class);
    indexEventHandler =
        new IndexEventHandler(
            poolMock,
            poolBatchMock,
            PLUGIN_NAME,
            forwarder,
            changeCheckerFactoryMock,
            currCtx,
            configuration,
            idLocks);
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
            poolMock,
            poolBatchMock,
            PLUGIN_NAME,
            forwarder,
            changeCheckerFactoryMock,
            currCtx,
            configuration,
            idLocks);
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
            poolMock,
            poolBatchMock,
            PLUGIN_NAME,
            forwarder,
            changeCheckerFactoryMock,
            currCtx,
            configuration,
            idLocks);
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

  class TestTask<T> implements Runnable {
    private IndexTask task;
    private CyclicBarrier testBarrier;
    private Supplier<T> successFunc;
    private VoidFunction failureFunc;
    private CompletableFuture<T> future;

    public TestTask(
        IndexTask task,
        CyclicBarrier testBarrier,
        Supplier<T> successFunc,
        VoidFunction failureFunc) {
      this.task = task;
      this.testBarrier = testBarrier;
      this.successFunc = successFunc;
      this.failureFunc = failureFunc;
      this.future = new CompletableFuture<>();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
      try {
        testBarrier.await();
        idLocks
            .withLock(
                task,
                () ->
                    runLater(
                        INDEX_WAIT_TIMEOUT_MS * 2,
                        () -> CompletableFuture.completedFuture(successFunc.get())),
                failureFunc)
            .whenComplete(
                (v, t) -> {
                  if (t == null) {
                    future.complete((T) v);
                  } else {
                    future.completeExceptionally(t);
                  }
                });
      } catch (Throwable t) {
        future = new CompletableFuture<>();
        future.completeExceptionally(t);
      }
    }

    public void join() {
      try {
        future.join();
      } catch (Exception e) {
      }
    }

    private CompletableFuture<T> runLater(
        long scheduledTimeMsec, Supplier<CompletableFuture<T>> supplier) {
      CompletableFuture<T> resFuture = new CompletableFuture<>();
      testExecutor.schedule(
          () -> {
            try {
              return supplier
                  .get()
                  .whenComplete(
                      (v, t) -> {
                        if (t == null) {
                          resFuture.complete(v);
                        }
                        resFuture.completeExceptionally(t);
                      });
            } catch (Throwable t) {
              return resFuture.completeExceptionally(t);
            }
          },
          scheduledTimeMsec,
          TimeUnit.MILLISECONDS);
      return resFuture;
    }
  }

  @Test
  public void indexLocksShouldBlockConcurrentIndexChange() throws Exception {
    IndexChangeTask indexTask1 =
        indexEventHandler.new IndexChangeTask(PROJECT_NAME, CHANGE_ID, new IndexEvent());
    IndexChangeTask indexTask2 =
        indexEventHandler.new IndexChangeTask(PROJECT_NAME, CHANGE_ID, new IndexEvent());
    testIsolationOfCuncurrentIndexTasks(indexTask1, indexTask2);
  }

  @Test
  public void indexLocksShouldBlockConcurrentIndexAndDeleteChange() throws Exception {
    IndexChangeTask indexTask =
        indexEventHandler.new IndexChangeTask(PROJECT_NAME, CHANGE_ID, new IndexEvent());
    DeleteChangeTask deleteTask =
        indexEventHandler.new DeleteChangeTask(CHANGE_ID, new IndexEvent());
    testIsolationOfCuncurrentIndexTasks(indexTask, deleteTask);
  }

  private void testIsolationOfCuncurrentIndexTasks(IndexTask indexTask1, IndexTask indexTask2)
      throws Exception {
    AtomicInteger changeIndexedCount = new AtomicInteger();
    AtomicInteger lockFailedCounts = new AtomicInteger();
    CyclicBarrier changeThreadsSync = new CyclicBarrier(2);

    TestTask<Integer> task1 =
        new TestTask<>(
            indexTask1,
            changeThreadsSync,
            () -> changeIndexedCount.incrementAndGet(),
            () -> lockFailedCounts.incrementAndGet());
    TestTask<Integer> task2 =
        new TestTask<>(
            indexTask2,
            changeThreadsSync,
            () -> changeIndexedCount.incrementAndGet(),
            () -> lockFailedCounts.incrementAndGet());

    new Thread(task1).start();
    new Thread(task2).start();
    task1.join();
    task2.join();

    /* Both assertions needs to be true, the order doesn't really matter:
     * - Only one of the two tasks should succeed
     * - Only one of the two tasks should fail to acquire the lock
     */
    assertThat(changeIndexedCount.get()).isEqualTo(1);
    assertThat(lockFailedCounts.get()).isEqualTo(1);
  }

  private void getExecutorThread() {
    Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
    for (Thread executorThread : threadSet) {
      if (executorThread.getName().equals(EXECUTOR_THREAD_NAME)) {
        while (!executorThread.getState().equals(State.TERMINATED)) {
          Thread.onSpinWait();
        }
      }
    }
  }

  private class CurrentThreadScheduledExecutorService implements ScheduledExecutorService {
    @Override
    public void shutdown() {}

    @Override
    public List<Runnable> shutdownNow() {
      return null;
    }

    @Override
    public boolean isShutdown() {
      return false;
    }

    @Override
    public boolean isTerminated() {
      return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return false;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
      return null;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
      return null;
    }

    @Override
    public Future<?> submit(Runnable task) {
      return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
        throws InterruptedException {
      return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(
        Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException {
      return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
        throws InterruptedException, ExecutionException {
      return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      return null;
    }

    @Override
    public void execute(Runnable command) {
      Thread thread = new Thread(command);
      thread.setName(EXECUTOR_THREAD_NAME);
      thread.start();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
      command.run();
      return null;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
      return null;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(
        Runnable command, long initialDelay, long period, TimeUnit unit) {
      return null;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(
        Runnable command, long initialDelay, long delay, TimeUnit unit) {
      return null;
    }
  }
}

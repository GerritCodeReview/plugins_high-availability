// Copyright (C) 2023 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder.jgroups;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.forwarder.CacheEntry;
import com.ericsson.gerrit.plugins.highavailability.forwarder.CacheNotFoundException;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedCacheEvictionHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedEventHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexAccountHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexBatchChangeHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexChangeHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedProjectListUpdateHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ProcessorMetrics;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ProcessorMetricsRegistry;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventGsonProvider;
import com.google.gerrit.server.events.EventTypes;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gson.Gson;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jgroups.ObjectMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class MessageProcessorTest {

  private MessageProcessor processor;
  private Gson gson;

  private ForwardedIndexChangeHandler indexChangeHandler;
  private ForwardedIndexBatchChangeHandler indexBatchChangeHandler;
  private ForwardedIndexAccountHandler indexAccountHandler;
  private ForwardedCacheEvictionHandler cacheEvictionHandler;
  private ForwardedEventHandler eventHandler;
  private ForwardedProjectListUpdateHandler projectListUpdateHandler;

  @Mock ProcessorMetrics processorMetrics;
  @Mock ProcessorMetricsRegistry metricsRegistry;

  private List<Object> allHandlers = new ArrayList<>();

  @Before
  public void setUp() {
    when(metricsRegistry.get(any())).thenReturn(processorMetrics);
    Gson eventGson = new EventGsonProvider().get();
    gson = new JGroupsForwarderModule().buildJGroupsGson(eventGson);

    indexChangeHandler = createHandlerMock(ForwardedIndexChangeHandler.class);
    indexBatchChangeHandler = createHandlerMock(ForwardedIndexBatchChangeHandler.class);
    indexAccountHandler = createHandlerMock(ForwardedIndexAccountHandler.class);
    cacheEvictionHandler = createHandlerMock(ForwardedCacheEvictionHandler.class);
    eventHandler = createHandlerMock(ForwardedEventHandler.class);
    projectListUpdateHandler = createHandlerMock(ForwardedProjectListUpdateHandler.class);

    processor =
        new MessageProcessor(
            gson,
            indexChangeHandler,
            indexBatchChangeHandler,
            indexAccountHandler,
            cacheEvictionHandler,
            eventHandler,
            projectListUpdateHandler,
            metricsRegistry);
  }

  private <T> T createHandlerMock(Class<T> handlerClass) {
    T handlerMock = mock(handlerClass);
    allHandlers.add(handlerMock);
    return handlerMock;
  }

  @Test
  public void indexAccount() throws IOException {
    int ACCOUNT_ID = 100;

    IndexAccount cmd = new IndexAccount(ACCOUNT_ID, Instant.now().toEpochMilli());
    assertThat(processor.handle(new ObjectMessage(null, gson.toJson(cmd)))).isEqualTo(true);
    verify(indexAccountHandler, times(1))
        .index(Account.id(ACCOUNT_ID), Operation.INDEX, Optional.empty());
    verifyOtherHandlersNotUsed(indexAccountHandler);
  }

  @Test
  public void indexChange() throws IOException {
    String PROJECT = "foo";
    int CHANGE_ID = 100;

    IndexChange.Update cmd =
        new IndexChange.Update(PROJECT, CHANGE_ID, Instant.now().toEpochMilli());
    assertThat(processor.handle(new ObjectMessage(null, gson.toJson(cmd)))).isEqualTo(true);
    verify(indexChangeHandler, times(1))
        .index(PROJECT + "~" + Change.id(CHANGE_ID), Operation.INDEX, Optional.empty());
    verifyOtherHandlersNotUsed(indexChangeHandler);
  }

  @Test
  public void indexChangeBatchMode() throws IOException {
    String PROJECT = "foo";
    int CHANGE_ID = 100;

    IndexChange.BatchUpdate cmd =
        new IndexChange.BatchUpdate(PROJECT, CHANGE_ID, Instant.now().toEpochMilli());
    assertThat(processor.handle(new ObjectMessage(null, gson.toJson(cmd)))).isEqualTo(true);
    verify(indexBatchChangeHandler, times(1))
        .index(PROJECT + "~" + Change.id(CHANGE_ID), Operation.INDEX, Optional.empty());
    verifyOtherHandlersNotUsed(indexBatchChangeHandler);
  }

  @Test
  public void deleteChangeFromIndex() throws IOException {
    String PROJECT = "foo";
    int CHANGE_ID = 100;

    IndexChange.Delete cmd =
        new IndexChange.Delete(PROJECT, CHANGE_ID, Instant.now().toEpochMilli());
    assertThat(processor.handle(new ObjectMessage(null, gson.toJson(cmd)))).isEqualTo(true);
    verify(indexChangeHandler, times(1))
        .index(PROJECT + "~" + Change.id(CHANGE_ID), Operation.DELETE, Optional.empty());
    verifyOtherHandlersNotUsed(indexChangeHandler);
  }

  @Test
  public void evictCache() throws CacheNotFoundException {
    String CACHE = "foo";
    String KEY_JSON = gson.toJson(100);

    EvictCache cmd = new EvictCache(CACHE, KEY_JSON, Instant.now().toEpochMilli());
    assertThat(processor.handle(new ObjectMessage(null, gson.toJson(cmd)))).isEqualTo(true);
    CacheEntry e = CacheEntry.from(CACHE, KEY_JSON);
    verify(cacheEvictionHandler, times(1)).evict(e);
    verifyOtherHandlersNotUsed(cacheEvictionHandler);
  }

  @Test
  public void postEvent() throws PermissionBackendException {
    String FOO = "foo";
    int BAR = 100;

    EventTypes.register(TestEvent.TYPE, TestEvent.class);
    TestEvent event = new TestEvent(FOO, BAR);
    PostEvent cmd = new PostEvent(event, Instant.now().toEpochMilli());
    assertThat(processor.handle(new ObjectMessage(null, gson.toJson(cmd)))).isEqualTo(true);
    ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
    verify(eventHandler, times(1)).dispatch(captor.capture());
    assertThat(captor.getValue()).isInstanceOf(TestEvent.class);
    TestEvent v = (TestEvent) captor.getValue();
    assertThat(v.foo).isEqualTo(FOO);
    assertThat(v.bar).isEqualTo(BAR);
    verifyOtherHandlersNotUsed(eventHandler);
  }

  @Test
  public void addToProjectList() throws IOException {
    String PROJECT = "foo";

    AddToProjectList cmd = new AddToProjectList(PROJECT, Instant.now().toEpochMilli());
    assertThat(processor.handle(new ObjectMessage(null, gson.toJson(cmd)))).isEqualTo(true);
    verify(projectListUpdateHandler, times(1)).update(PROJECT, false);
    verifyOtherHandlersNotUsed(projectListUpdateHandler);
  }

  @Test
  public void removeFromProjectList() throws IOException {
    String PROJECT = "foo";

    RemoveFromProjectList cmd = new RemoveFromProjectList(PROJECT, Instant.now().toEpochMilli());
    assertThat(processor.handle(new ObjectMessage(null, gson.toJson(cmd)))).isEqualTo(true);
    verify(projectListUpdateHandler, times(1)).update(PROJECT, true);
    verifyOtherHandlersNotUsed(projectListUpdateHandler);
  }

  private void verifyOtherHandlersNotUsed(Object onlyUsedHandler) {
    for (Object handler : allHandlers) {
      if (handler != onlyUsedHandler) {
        verifyNoInteractions(handler);
      }
    }
  }

  private static class TestEvent extends Event {
    static final String TYPE = "test-event";

    String foo;
    int bar;

    TestEvent(String foo, int bar) {
      super(TYPE);
      this.foo = foo;
      this.bar = bar;
    }
  }
}

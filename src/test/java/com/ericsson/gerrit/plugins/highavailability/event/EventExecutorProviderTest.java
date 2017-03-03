// Copyright (C) 2016 Ericsson
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

package com.ericsson.gerrit.plugins.highavailability.event;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.git.WorkQueue;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.event.EventExecutorProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EventExecutorProviderTest {
  @Mock
  private WorkQueue.Executor executorMock;
  private EventExecutorProvider eventsExecutorProvider;

  @Before
  public void setUp() throws Exception {
    WorkQueue workQueueMock = mock(WorkQueue.class);
    when(workQueueMock.createQueue(4, "Forward-stream-event"))
        .thenReturn(executorMock);
    Configuration configMock = mock(Configuration.class);
    when(configMock.getEventThreadPoolSize()).thenReturn(4);

    eventsExecutorProvider =
        new EventExecutorProvider(workQueueMock, configMock);
  }

  @Test
  public void shouldReturnExecutor() throws Exception {
    assertThat(eventsExecutorProvider.get()).isEqualTo(executorMock);
  }

  @Test
  public void testStop() throws Exception {
    eventsExecutorProvider.start();
    assertThat(eventsExecutorProvider.get()).isEqualTo(executorMock);
    eventsExecutorProvider.stop();
    verify(executorMock).shutdown();
    verify(executorMock).unregisterWorkQueue();
    assertThat(eventsExecutorProvider.get()).isNull();
  }
}

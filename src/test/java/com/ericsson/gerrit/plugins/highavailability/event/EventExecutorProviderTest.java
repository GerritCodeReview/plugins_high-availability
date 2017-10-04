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
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.git.WorkQueue;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.Configuration.Mode;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EventExecutorProviderTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Configuration configMock;
  @Mock private WorkQueue.Executor executorMock;
  @Mock private WorkQueue workQueueMock;

  private EventExecutorProvider eventsExecutorProvider;

  @Test
  public void shouldReturnWorkQueueExecutor() throws Exception {
    when(configMock.main().mode()).thenReturn(Mode.WARM_STANDBY);
    when(workQueueMock.createQueue(1, "Forward-stream-event")).thenReturn(executorMock);
    eventsExecutorProvider = new EventExecutorProvider(workQueueMock, configMock);

    assertThat(eventsExecutorProvider.get()).isEqualTo(executorMock);
  }

  @Test
  public void testStopWorkQueueExecutor() throws Exception {
    when(configMock.main().mode()).thenReturn(Mode.WARM_STANDBY);
    when(workQueueMock.createQueue(1, "Forward-stream-event")).thenReturn(executorMock);
    eventsExecutorProvider = new EventExecutorProvider(workQueueMock, configMock);

    eventsExecutorProvider.start();
    assertThat(eventsExecutorProvider.get()).isNotNull();
    eventsExecutorProvider.stop();
    verify(executorMock).shutdown();
    verify(executorMock).unregisterWorkQueue();
    assertThat(eventsExecutorProvider.get()).isNull();
  }

  @Test
  public void shouldReturnDirectExecutor() throws Exception {
    when(configMock.main().mode()).thenReturn(Mode.LOAD_BALANCING);
    eventsExecutorProvider = new EventExecutorProvider(workQueueMock, configMock);

    // Determine if executor is a direct exector by checking if the thread
    // executing the task is the same as the submiter thread.
    final Thread submiterThread = Thread.currentThread();
    eventsExecutorProvider.get().execute(new Runnable() {
      @Override
      public void run() {
       assertThat(Thread.currentThread()).isEqualTo(submiterThread);
      }
    });

    eventsExecutorProvider.stop();
  }


  @Test
  public void testStopWorkDirectExecutor() throws Exception {
    when(configMock.main().mode()).thenReturn(Mode.LOAD_BALANCING);
    eventsExecutorProvider = new EventExecutorProvider(workQueueMock, configMock);

    eventsExecutorProvider.start();
    assertThat(eventsExecutorProvider.get()).isNotNull();
    eventsExecutorProvider.stop();
    assertThat(eventsExecutorProvider.get()).isNull();
  }
}

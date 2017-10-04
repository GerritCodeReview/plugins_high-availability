// Copyright (C) 2015 Ericsson
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.Configuration.Mode;

import com.google.gerrit.server.git.WorkQueue;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IndexExecutorProviderTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Configuration configMock;
  @Mock private WorkQueue.Executor executorMock;
  @Mock private WorkQueue workQueueMock;

  private IndexExecutorProvider indexExecutorProvider;

  @Test
  public void shouldReturnWorkQueueExecutor() throws Exception {
    when(configMock.main().mode()).thenReturn(Mode.WARM_STANDBY);
    when(configMock.index().threadPoolSize()).thenReturn(4);
    when(workQueueMock.createQueue(4, "Forward-index-event")).thenReturn(executorMock);
    indexExecutorProvider = new IndexExecutorProvider(workQueueMock, configMock);

    assertThat(indexExecutorProvider.get()).isEqualTo(executorMock);
  }

  @Test
  public void testStopWorkQueueExecutor() throws Exception {
    when(configMock.main().mode()).thenReturn(Mode.WARM_STANDBY);
    when(configMock.index().threadPoolSize()).thenReturn(4);
    when(workQueueMock.createQueue(4, "Forward-index-event")).thenReturn(executorMock);
    indexExecutorProvider = new IndexExecutorProvider(workQueueMock, configMock);

    indexExecutorProvider.start();
    assertThat(indexExecutorProvider.get()).isNotNull();
    indexExecutorProvider.stop();
    verify(executorMock).shutdown();
    verify(executorMock).unregisterWorkQueue();
    assertThat(indexExecutorProvider.get()).isNull();
  }

  @Test
  public void shouldReturnDirectExecutor() throws Exception {
    when(configMock.main().mode()).thenReturn(Mode.LOAD_BALANCING);
    indexExecutorProvider = new IndexExecutorProvider(workQueueMock, configMock);

    // Determine if executor is a direct exector by checking if the thread
    // executing the task is the same as the submiter thread.
    final Thread submiterThread = Thread.currentThread();
    indexExecutorProvider.get().execute(new Runnable() {
      @Override
      public void run() {
       assertThat(Thread.currentThread()).isEqualTo(submiterThread);
      }
    });

    indexExecutorProvider.stop();
  }


  @Test
  public void testStopWorkDirectExecutor() throws Exception {
    when(configMock.main().mode()).thenReturn(Mode.LOAD_BALANCING);
    indexExecutorProvider = new IndexExecutorProvider(workQueueMock, configMock);

    indexExecutorProvider.start();
    assertThat(indexExecutorProvider.get()).isNotNull();
    indexExecutorProvider.stop();
    assertThat(indexExecutorProvider.get()).isNull();
  }
}

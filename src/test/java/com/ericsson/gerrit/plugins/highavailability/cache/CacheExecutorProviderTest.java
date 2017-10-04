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

package com.ericsson.gerrit.plugins.highavailability.cache;

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
public class CacheExecutorProviderTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private Configuration configMock;
  @Mock private WorkQueue.Executor executorMock;
  @Mock private WorkQueue workQueueMock;

  private CacheExecutorProvider cacheExecutorProvider;

  @Test
  public void shouldReturnWorkQueueExecutor() throws Exception {
    when(configMock.main().mode()).thenReturn(Mode.WARM_STANDBY);
    when(configMock.cache().threadPoolSize()).thenReturn(4);
    when(workQueueMock.createQueue(4, "Forward-cache-eviction-event")).thenReturn(executorMock);
    cacheExecutorProvider = new CacheExecutorProvider(workQueueMock, configMock);

    assertThat(cacheExecutorProvider.get()).isEqualTo(executorMock);
  }

  @Test
  public void testStopWorkQueueExecutor() throws Exception {
    when(configMock.main().mode()).thenReturn(Mode.WARM_STANDBY);
    when(configMock.cache().threadPoolSize()).thenReturn(4);
    when(workQueueMock.createQueue(4, "Forward-cache-eviction-event")).thenReturn(executorMock);
    cacheExecutorProvider = new CacheExecutorProvider(workQueueMock, configMock);

    cacheExecutorProvider.start();
    assertThat(cacheExecutorProvider.get()).isNotNull();
    cacheExecutorProvider.stop();
    verify(executorMock).shutdown();
    verify(executorMock).unregisterWorkQueue();
    assertThat(cacheExecutorProvider.get()).isNull();
  }

  @Test
  public void shouldReturnDirectExecutor() throws Exception {
    when(configMock.main().mode()).thenReturn(Mode.LOAD_BALANCING);
    cacheExecutorProvider = new CacheExecutorProvider(workQueueMock, configMock);

    // Determine if executor is a direct exector by checking if the thread
    // executing the task is the same as the submiter thread.
    final Thread submiterThread = Thread.currentThread();
    cacheExecutorProvider.get().execute(new Runnable() {
      @Override
      public void run() {
       assertThat(Thread.currentThread()).isEqualTo(submiterThread);
      }
    });

    cacheExecutorProvider.stop();
  }


  @Test
  public void testStopWorkDirectExecutor() throws Exception {
    when(configMock.main().mode()).thenReturn(Mode.LOAD_BALANCING);
    cacheExecutorProvider = new CacheExecutorProvider(workQueueMock, configMock);

    cacheExecutorProvider.start();
    assertThat(cacheExecutorProvider.get()).isNotNull();
    cacheExecutorProvider.stop();
    assertThat(cacheExecutorProvider.get()).isNull();
  }
}

// Copyright (C) 2017 Ericsson
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

package com.ericsson.gerrit.plugins.highavailability.websession.file;


import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.WorkQueue.Executor;
import com.google.inject.Provider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public class FileBasedWebSessionCacheCleanerTest {

  private static String SOME_PLUGIN_NAME = "somePluginName";

  @Mock
  private PluginConfig configMock;
  @Mock
  private PluginConfigFactory pluginCfgFactoryMock;
  @Mock
  private Executor executorMock;
  @Mock
  private WorkQueue workQueueMock;
  @Mock
  private Provider<CleanupTask> cleanupTaskProviderMock;

  private FileBasedWebSessionCacheCleaner cleaner;

  @Before
  public void setUp() {
    when(pluginCfgFactoryMock.getFromGerritConfig(SOME_PLUGIN_NAME, true))
        .thenReturn(configMock);
    when(workQueueMock.getDefaultQueue()).thenReturn(executorMock);
    when(cleanupTaskProviderMock.get()).thenReturn(new CleanupTask(null, null));
    cleaner = new FileBasedWebSessionCacheCleaner(workQueueMock,
        cleanupTaskProviderMock, pluginCfgFactoryMock, SOME_PLUGIN_NAME);
  }

  @Test
  public void testCleanupTaskRun() {
    FileBasedWebsessionCache cacheMock = mock(FileBasedWebsessionCache.class);
    CleanupTask task = new CleanupTask(cacheMock, null);
    int numberOfRuns = 5;
    for (int i = 0; i < numberOfRuns; i++) {
      task.run();

    }
    verify(cacheMock, times(numberOfRuns)).cleanUp();
  }

  @Test
  public void testCleanupTaskToString() {
    CleanupTask task = new CleanupTask(null, SOME_PLUGIN_NAME);
    assertThat(task.toString()).isEqualTo(String.format(
        "[%s] Clean up expired file based websessions", SOME_PLUGIN_NAME));
  }

  @Test
  public void testCleanupTaskIsScheduledOnStartDefaultInterval() {
    cleaner.start();
    verify(executorMock, times(1)).scheduleAtFixedRate(isA(CleanupTask.class),
        eq(1000l), eq(TimeUnit.HOURS.toMillis(24)), eq(TimeUnit.MILLISECONDS));
  }

}

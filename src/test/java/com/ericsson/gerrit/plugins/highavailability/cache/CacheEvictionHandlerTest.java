// Copyright (C) 2020 The Android Open Source Project
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

import static org.mockito.Mockito.verifyNoInteractions;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.SitePaths;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executor;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CacheEvictionHandlerTest {
  @Mock private Executor executorMock;
  @Mock private Forwarder forwarder;
  @Mock private PluginConfigFactory pluginConfigFactoryMock;

  private static final String PLUGIN_NAME = "high-availability";
  private static final Path SITE_PATH = Paths.get("/site_path");
  private CachePatternMatcher defaultCacheMatcher;

  @Before
  public void setUp() throws IOException {
    defaultCacheMatcher =
        new CachePatternMatcher(new Configuration(new Config(), new SitePaths(SITE_PATH)));
  }

  @Test
  public void shouldNotPublishAccountsCacheEvictions() {
    CacheEvictionHandler<String, String> handler =
        new CacheEvictionHandler<>(forwarder, executorMock, PLUGIN_NAME, defaultCacheMatcher);
    handler.onRemoval(
        "test", "accounts", RemovalNotification.create("test", "accounts", RemovalCause.EXPLICIT));
    verifyNoInteractions(executorMock);
  }
}

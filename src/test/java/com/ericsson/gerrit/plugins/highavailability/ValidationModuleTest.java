// Copyright (C) 2025 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability;

import static com.google.common.truth.Truth.assertThat;

import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDbConfiguration.SharedRefDatabase;
import com.gerritforge.gerrit.globalrefdb.validation.dfsrefdb.LegacyCustomSharedRefEnforcementByProject;
import com.gerritforge.gerrit.globalrefdb.validation.dfsrefdb.LegacyDefaultSharedRefEnforcement;
import com.gerritforge.gerrit.globalrefdb.validation.dfsrefdb.LegacySharedRefEnforcement;
import com.gerritforge.gerrit.globalrefdb.validation.dfsrefdb.LegacySharedRefEnforcement.EnforcePolicy;
import com.google.gerrit.acceptance.TestMetricMaker;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.jgit.lib.Config;
import org.junit.Before;
import org.junit.Test;

public class ValidationModuleTest {
  private static final Path SITE_PATH = Paths.get("/site_path");

  private Config globalPluginConfig;
  private SitePaths sitePaths;
  private AbstractModule module;

  @Before
  public void setUp() throws IOException {
    globalPluginConfig = new Config();
    sitePaths = new SitePaths(SITE_PATH);
    module =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(SitePaths.class).toInstance(sitePaths);
            bind(Config.class)
                .annotatedWith(GerritServerConfig.class)
                .toInstance(globalPluginConfig);
            bind(MetricMaker.class).toInstance(new TestMetricMaker());
          }
        };
  }

  private Configuration getConfiguration() {
    return new Configuration(globalPluginConfig, sitePaths);
  }

  @Test
  public void shouldNotUseLegacyRefExclusionWhenConfiguredTo() {
    globalPluginConfig.setString(
        SharedRefDatabase.SECTION,
        SharedRefDatabase.SUBSECTION_ENFORCEMENT_RULES,
        EnforcePolicy.REQUIRED.name(),
        "refs/heads/main");
    Configuration configuration = getConfiguration();
    assertThat(configuration.sharedRefDb().getSharedRefDb().getEnforcementRules()).isNotEmpty();
    ValidationModule validationModule = new ValidationModule(configuration);
    Injector injector = Guice.createInjector(validationModule, module);
    assertThat(injector.getInstance(LegacySharedRefEnforcement.class))
        .isInstanceOf(LegacyDefaultSharedRefEnforcement.class);
  }

  @Test
  public void shouldNotUseLegacyRefExclusionWhenNotConfiguredTo() {
    Configuration configuration = getConfiguration();
    assertThat(configuration.sharedRefDb().getSharedRefDb().getEnforcementRules()).isEmpty();
    ValidationModule validationModule = new ValidationModule(configuration);
    Injector injector = Guice.createInjector(validationModule, module);
    assertThat(injector.getInstance(LegacySharedRefEnforcement.class))
        .isInstanceOf(LegacyCustomSharedRefEnforcementByProject.class);
  }
}

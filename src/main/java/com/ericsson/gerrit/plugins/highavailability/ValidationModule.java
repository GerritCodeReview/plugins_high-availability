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

package com.ericsson.gerrit.plugins.highavailability;

import com.gerritforge.gerrit.globalrefdb.validation.BatchRefUpdateValidator;
import com.gerritforge.gerrit.globalrefdb.validation.LockWrapper;
import com.gerritforge.gerrit.globalrefdb.validation.Log4jSharedRefLogger;
import com.gerritforge.gerrit.globalrefdb.validation.RefUpdateValidator;
import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDatabaseWrapper;
import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDbBatchRefUpdate;
import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDbConfiguration;
import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDbGitRepositoryManager;
import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDbRefDatabase;
import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDbRefUpdate;
import com.gerritforge.gerrit.globalrefdb.validation.SharedRefDbRepository;
import com.gerritforge.gerrit.globalrefdb.validation.SharedRefLogger;
import com.gerritforge.gerrit.globalrefdb.validation.dfsrefdb.CustomSharedRefEnforcementByProject;
import com.gerritforge.gerrit.globalrefdb.validation.dfsrefdb.DefaultSharedRefEnforcement;
import com.gerritforge.gerrit.globalrefdb.validation.dfsrefdb.SharedRefEnforcement;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Scopes;

public class ValidationModule extends FactoryModule {
  Configuration configuration;

  @Inject
  public ValidationModule(Configuration configuration) {
    this.configuration = configuration;
  }

  @Override
  protected void configure() {

    factory(SharedRefDbRepository.Factory.class);
    factory(SharedRefDbRefDatabase.Factory.class);
    factory(SharedRefDbRefUpdate.Factory.class);
    factory(SharedRefDbBatchRefUpdate.Factory.class);
    factory(RefUpdateValidator.Factory.class);
    factory(BatchRefUpdateValidator.Factory.class);

    bind(SharedRefDbConfiguration.class).toInstance(configuration.sharedRefDb());

    bind(SharedRefDatabaseWrapper.class).in(Scopes.SINGLETON);
    bind(SharedRefLogger.class).to(Log4jSharedRefLogger.class);
    factory(LockWrapper.Factory.class);

    bind(GitRepositoryManager.class).to(SharedRefDbGitRepositoryManager.class);

    if (configuration.sharedRefDb().getSharedRefDb().getEnforcementRules().isEmpty()) {
      bind(SharedRefEnforcement.class).to(DefaultSharedRefEnforcement.class).in(Scopes.SINGLETON);
    } else {
      bind(SharedRefEnforcement.class)
          .to(CustomSharedRefEnforcementByProject.class)
          .in(Scopes.SINGLETON);
    }
  }
}

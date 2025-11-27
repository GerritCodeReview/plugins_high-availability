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

package com.ericsson.gerrit.plugins.highavailability;

import com.ericsson.gerrit.plugins.highavailability.autoreindex.AutoReindexModule;
import com.ericsson.gerrit.plugins.highavailability.cache.CacheModule;
import com.ericsson.gerrit.plugins.highavailability.event.EventModule;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwarderModule;
import com.ericsson.gerrit.plugins.highavailability.forwarder.commands.ForwarderCommandsModule;
import com.ericsson.gerrit.plugins.highavailability.forwarder.jgroups.JGroupsForwarderModule;
import com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.PubSubForwarderModule;
import com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.aws.AwsPubSubForwarderModule;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.RestForwarderModule;
import com.ericsson.gerrit.plugins.highavailability.index.IndexModule;
import com.ericsson.gerrit.plugins.highavailability.indexsync.IndexSyncModule;
import com.ericsson.gerrit.plugins.highavailability.lock.FileBasedLockManager;
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfoModule;
import com.gerritforge.gerrit.globalrefdb.validation.ProjectDeletedSharedDbCleanup;
import com.google.gerrit.extensions.events.ProjectDeletedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class Module extends LifecycleModule {
  private final Configuration config;

  @Inject
  Module(Configuration config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    install(new EnvModule());
    install(new ForwarderModule());
    install(new FileBasedLockManager.Module());

    switch (config.main().transport()) {
      case HTTP:
        install(new RestForwarderModule());
        install(new PeerInfoModule(config.peerInfo().strategy()));
        break;
      case JGROUPS:
        install(new ForwarderCommandsModule());
        install(new JGroupsForwarderModule());
        break;
      case PUBSUB:
        install(new ForwarderCommandsModule());
        install(new PubSubForwarderModule(config));
        break;
      case PUBSUB_AWS:
        install(new ForwarderCommandsModule());
        install(AwsPubSubForwarderModule.create());
        break;
      default:
        throw new IllegalArgumentException("Unsupported transport: " + config.main().transport());
    }

    if (config.cache().synchronize()) {
      install(new CacheModule());
    }
    if (config.event().synchronize()) {
      install(new EventModule());
    }
    if (config.index().synchronize()) {
      install(new IndexModule());
      if (config.indexSync().enabled()) {
        install(new IndexSyncModule());
      }
    }
    if (config.autoReindex().enabled()) {
      install(new AutoReindexModule());
    }

    if (config.sharedRefDb().getSharedRefDb().isEnabled()) {
      listener().to(PluginStartup.class);
      DynamicSet.bind(binder(), ProjectDeletedListener.class)
          .to(ProjectDeletedSharedDbCleanup.class);
    }
  }

  @Provides
  @Singleton
  @SharedDirectory
  Path getSharedDirectory() throws IOException {
    Path sharedDirectoryPath = config.main().sharedDirectory();
    Files.createDirectories(sharedDirectoryPath);
    return sharedDirectoryPath;
  }
}

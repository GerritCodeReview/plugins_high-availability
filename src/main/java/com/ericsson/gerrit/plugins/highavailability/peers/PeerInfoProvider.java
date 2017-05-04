// Copyright (C) 2017 The Android Open Source Project
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
package com.ericsson.gerrit.plugins.highavailability.peers;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.Configuration.PeerInfoStrategy;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;

public class PeerInfoProvider implements Provider<Optional<PeerInfo>> {

  private final Provider<Optional<PeerInfo>> configuredProvider;

  @Inject
  PeerInfoProvider(Injector injector,
      Configuration cfg) {
    PeerInfoStrategy strategy = cfg.peerInfo().strategy();
    if (strategy == PeerInfoStrategy.CONFIG) {
      configuredProvider = injector.getInstance(PluginConfigPeerInfoProvider.class);
    } else if (strategy == PeerInfoStrategy.JGROUPS) {
      configuredProvider = injector.getInstance(JGroupsPeerInfoProvider.class);
    } else {
      throw new IllegalStateException("Unsupported peer info strategy: " + strategy);
    }
  }

  @Override
  public Optional<PeerInfo> get() {
    return configuredProvider.get();
  }
}

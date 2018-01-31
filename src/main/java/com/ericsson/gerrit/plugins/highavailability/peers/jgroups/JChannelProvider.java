// Copyright (C) 2018 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.peers.jgroups;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.Optional;
import org.jgroups.JChannel;

@Singleton
public class JChannelProvider implements Provider<JChannel> {
  private final Configuration.JGroups jgroupsConfig;
  private final Configuration.JGroupsKubernetes jgroupsKubernetesConfig;

  @Inject
  JChannelProvider(Configuration pluginConfiguration) {
    this.jgroupsConfig = pluginConfiguration.jgroups();
    this.jgroupsKubernetesConfig = pluginConfiguration.jgroupsKubernetes();
  }

  @Override
  public JChannel get() {
    Optional<Path> protocolStack = jgroupsConfig.protocolStack();
    try {
      if (protocolStack.isPresent()) {
        return new JChannel(protocolStack.get().toString());
      }
      if (jgroupsConfig.useKubernetes()) {
        if (jgroupsKubernetesConfig.namespace() != null) {
          System.setProperty("KUBERNETES_NAMESPACE", jgroupsKubernetesConfig.namespace());
        }
        if (!jgroupsKubernetesConfig.labels().isEmpty()) {
          System.setProperty(
              "KUBERNETES_LABELS", String.join(",", jgroupsKubernetesConfig.labels()));
        }
        return new JChannel(getClass().getResource("kubernetes.xml").toString());
      }
      return new JChannel();
    } catch (Exception e) {
      throw new ProvisionException(
          String.format(
              "Unable to create a channel with protocol stack: %s",
              protocolStack.map(Path::toString).orElse("default")),
          e);
    }
  }
}

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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.annotations.PluginData;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Singleton
public class PeerDiscovery implements LifecycleListener, Runnable, Provider<ImmutableList<PeerInfo>> {
  private static final Logger log = LoggerFactory.getLogger(PeerDiscovery.class);

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(PeerDiscovery.class);
    }
  }

  private final WorkQueue queue;
  private final File peersDir;
  private final File peerFile;
  private final String directUrl;

  private ConcurrentHashMap<String, PeerInfo> peers;

  @Inject
  PeerDiscovery(@GerritServerConfig Config cfg, WorkQueue queue, @PluginData Path dataDir) throws UnknownHostException, URISyntaxException {
    this.queue = queue;
    String hostName = InetAddress.getLocalHost().getHostName();
    URIish u = new URIish(cfg.getString("httpd", null, "listenUrl"));
    peersDir = dataDir.toFile();
    peerFile = new File(peersDir, hostName + "-" + u.getPort());
    directUrl = u.setHost(hostName).toString();
    peers = new ConcurrentHashMap<>();
  }

  @Override
  public void start() {
    queue.getDefaultQueue().scheduleAtFixedRate(this, 0, 5000, TimeUnit.MILLISECONDS);
  }

  @Override
  public void stop() {
    // handled by WorkQueue.stop() already
  }

  @Override
  public void run() {
    advertise();
    watch();
  }

  @Override
  public ImmutableList<PeerInfo> get() {
    return ImmutableList.copyOf(peers.values());
  }

  private void advertise() {
    try {
      if (peerFile.exists()) {
        touch();
      } else {
        write();
      }
    } catch (IOException e) {
      log.error("Couldn't write " + peerFile.getAbsolutePath(), e);
    }
  }

  private void touch() {
    peerFile.setLastModified(System.currentTimeMillis());
  }

  private void write() throws IOException {
    FileBasedConfig cfg = new FileBasedConfig(peerFile, FS.DETECTED);
    cfg.setString("httpd", null, "directUrl", directUrl);
    cfg.save();
  }

  private void watch() {
    for (File f : peersDir.listFiles()) {
      if (f.equals(peerFile)) {
        log.error("skipping myself");
        continue;
      }
      log.error("Found peer " + f.getAbsolutePath());
      PeerInfo info = PeerInfo.fromConfigFile(f);
      if (alive(info)) {
        peers.put(f.getName(), info);
      } else {
        peers.remove(f.getName());
        delete(f);
      }
    }
  }

  private boolean alive(PeerInfo info) {
    // TODO: REST call to info.directUrl + /config/server/version
    return true;
  }

  private void delete(File f) {
    log.error("Deleting " + f.getAbsolutePath());
    LockFile lock = new LockFile(f);
    try {
      if (lock.lock()) {
        try {
          f.delete();
        } finally {
          lock.unlock();
        }
      }
    } catch (IOException e) {
      log.error("Couldn't lock " + f.getAbsolutePath());
    }
  }

  @Override
  public String toString() {
    return "peer discovery";
  }
}

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

package com.ericsson.gerrit.plugins.highavailability;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.FileUtil;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.server.config.SitePaths;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

class SetupLocalHAReplica {
  private final String pluginName;
  private final SitePaths master;
  private final Path sharedDir;
  private final Config masterConfig;

  private SitePaths replica;

  SetupLocalHAReplica(
      String pluginName, SitePaths master, InitFlags flags, Path sharedDir, SitePaths replica)
      throws IOException, ConfigInvalidException {
    this.pluginName = pluginName;
    this.master = master;
    this.sharedDir = sharedDir;
    this.masterConfig = flags.cfg;
    this.replica = replica;
  }

  void run() throws Exception {
    FileUtil.mkdirsOrDie(replica.site_path, "cannot create " + replica.site_path);

    for (Path dir : listDirsForCopy()) {
      copyFiles(dir);
    }

    mkdir(replica.logs_dir);
    mkdir(replica.tmp_dir);
    symlink(Paths.get(masterConfig.getString("gerrit", null, "basePath")));
    symlink(sharedDir);

    FileBasedConfig replicaConfig =
        new FileBasedConfig(replica.gerrit_config.toFile(), FS.DETECTED);
    replicaConfig.load();

    if ("h2".equals(masterConfig.getString("database", null, "type"))) {
      masterConfig.setBoolean("database", "h2", "autoServer", true);
      replicaConfig.setBoolean("database", "h2", "autoServer", true);
      symlinkH2ReviewDbDir();
    }

    // setup for haproxy
    masterConfig.setString("httpd", null, "listenUrl", "http://localhost:8081");
    replicaConfig.setString("httpd", null, "listenUrl", "http://localhost:8082");
    masterConfig.setString("sshd", null, "listenAddress", "*:29419");
    replicaConfig.setString("sshd", null, "listenAddress", "*:29420");

    replicaConfig.save();

    setPeerInfoUrl(master, "http://localhost:8082");
    setPeerInfoUrl(replica, "http://localhost:8081");

    writeHAProxyConfigFile();
  }

  private List<Path> listDirsForCopy() throws IOException {
    ImmutableList.Builder<Path> toSkipBuilder = ImmutableList.builder();
    toSkipBuilder.add(
        master.resolve(masterConfig.getString("gerrit", null, "basePath")),
        master.db_dir,
        master.logs_dir,
        replica.site_path,
        master.site_path.resolve(sharedDir),
        master.tmp_dir);
    if ("h2".equals(masterConfig.getString("database", null, "type"))) {
      toSkipBuilder.add(master.resolve(masterConfig.getString("database", null, "database")).getParent());
    }
    final ImmutableList<Path> toSkip = toSkipBuilder.build();

    final ArrayList<Path> dirsForCopy = new ArrayList<>();
    Files.walkFileTree(
        master.site_path,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
              throws IOException {
            if (Files.isSameFile(dir, master.site_path)) {
              return FileVisitResult.CONTINUE;
            }

            Path p = master.site_path.relativize(dir);
            if (shouldSkip(p)) {
              return FileVisitResult.SKIP_SUBTREE;
            }
            dirsForCopy.add(p);
            return FileVisitResult.CONTINUE;
          }

          private boolean shouldSkip(Path p) throws IOException {
            Path resolved = master.site_path.resolve(p);
            for (Path skip : toSkip) {
              if (Files.isSameFile(resolved, skip)) {
                return true;
              }
            }
            return false;
          }
        });

    return dirsForCopy;
  }

  private void copyFiles(Path dir) throws IOException {
    final Path source = master.site_path.resolve(dir);
    final Path target = replica.site_path.resolve(dir);
    Files.createDirectories(target);
    Files.walkFileTree(
        source,
        EnumSet.noneOf(FileVisitOption.class),
        1,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            Path f = source.relativize(file);
            if (Files.isRegularFile(file)) {
              Files.copy(file, target.resolve(f));
            }
            return FileVisitResult.CONTINUE;
          }
        });
  }

  private static final void mkdir(Path dir) throws IOException {
    Files.createDirectories(dir);
  }

  private void symlink(Path path) throws IOException {
    if (!path.isAbsolute()) {
      Files.createSymbolicLink(
          replica.site_path.resolve(path),
          master.site_path.resolve(path).toAbsolutePath().normalize());
    }
  }

  private void symlinkH2ReviewDbDir() throws IOException {
    symlink(Paths.get(masterConfig.getString("database", null, "database")).getParent());
  }

  private void setPeerInfoUrl(SitePaths site, String url)
      throws IOException, ConfigInvalidException {
    FileBasedConfig cfg =
        new FileBasedConfig(site.etc_dir.resolve(pluginName + ".config").toFile(), FS.DETECTED);
    cfg.load();
    cfg.setString("peerInfo", null, "url", url);
    cfg.save();
  }

  private void writeHAProxyConfigFile() throws IOException {
    try (FileWriter out = new FileWriter(master.etc_dir.resolve("haproxy.config").toFile());
        PrintWriter w = new PrintWriter(out)) {
      w.println("global");
      w.println("  daemon");
      w.println();
      w.println("defaults");
      w.println("  timeout connect 5s");
      w.println("  timeout client 50s");
      w.println("  timeout server 50s");
      w.println();
      w.println("frontend http-in");
      w.println("  bind localhost:8080");
      w.println("  default_backend servers");
      w.println();
      w.println("backend servers");
      w.println("  server gerrit1 localhost:8081");
      w.println("  # server gerrit2 localhost:8082");
      w.println();
      w.println("listen ssh-in");
      w.println("  bind localhost:29418");
      w.println("  server gerrit1 localhost:29419");
      w.println("  # server gerrit2 localhost:29420");
    }
  }
}

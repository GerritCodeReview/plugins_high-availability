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

import com.google.common.base.Strings;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;

public class PeerInfo {

  static PeerInfo fromConfigFile(File file) {
    FileBasedConfig cfg = new FileBasedConfig(file, FS.DETECTED);
    try {
      cfg.load();
    } catch (IOException | ConfigInvalidException e) {
      throw new RuntimeException("Error loading " + file.getAbsolutePath(), e);
    }

    String url = cfg.getString("httpd", null, "directUrl");
    if (Strings.isNullOrEmpty(url)) {
      throw new RuntimeException("httpd.directUrl empty or not defined in "
          + cfg.getFile().getAbsolutePath());
    }
    return new PeerInfo(url);
  }

  private final String directUrl;

  private PeerInfo(String directUrl) {
    this.directUrl = directUrl;
  }

  public String getDirectUrl() {
    return directUrl;
  }
}

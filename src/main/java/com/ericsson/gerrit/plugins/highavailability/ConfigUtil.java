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

import com.google.common.base.Strings;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.inject.Inject;
import java.util.Objects;
import org.eclipse.jgit.lib.Config;

class ConfigUtil {

  private final ConsoleUI ui;

  @Inject
  ConfigUtil(ConsoleUI ui) {
    this.ui = ui;
  }

  String promptAndSetString(
      Config cfg, String title, String section, String name, String defaultValue) {
    return promptAndSetString(cfg, title, section, null, name, defaultValue);
  }

  String promptAndSetString(
      Config cfg,
      String title,
      String section,
      String subsection,
      String name,
      String defaultValue) {
    String oldValue = Strings.emptyToNull(cfg.getString(section, null, name));
    String newValue = ui.readString(oldValue != null ? oldValue : defaultValue, title);
    if (!Objects.equals(oldValue, newValue)) {
      if (!Strings.isNullOrEmpty(newValue)) {
        cfg.setString(section, subsection, name, newValue);
      } else {
        cfg.unset(section, subsection, name);
      }
    }
    return newValue;
  }
}

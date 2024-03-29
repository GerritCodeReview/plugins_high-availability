// Copyright (C) 2023 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder.jgroups;

import com.google.common.base.Strings;

public abstract class IndexChange extends Command {
  private final String projectName;
  private final int id;
  private final boolean batchMode;

  protected IndexChange(String type, String projectName, int id, boolean batchMode) {
    super(type);
    this.projectName = projectName;
    this.id = id;
    this.batchMode = batchMode;
  }

  public String getId() {
    return Strings.nullToEmpty(projectName) + "~" + id;
  }

  public boolean isBatch() {
    return batchMode;
  }

  public static class Update extends IndexChange {
    static final String TYPE = "update-change";

    public Update(String projectName, int id) {
      this(projectName, id, false);
    }

    public Update(String projectName, int id, boolean batchMode) {
      super(TYPE, projectName, id, batchMode);
    }
  }

  public static class Delete extends IndexChange {
    static final String TYPE = "delete-change";

    public Delete(int id) {
      this("", id);
    }

    public Delete(String projectName, int id) {
      super(TYPE, projectName, id, false);
    }
  }
}

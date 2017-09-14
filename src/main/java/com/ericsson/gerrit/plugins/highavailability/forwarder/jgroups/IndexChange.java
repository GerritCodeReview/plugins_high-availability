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

package com.ericsson.gerrit.plugins.highavailability.forwarder.jgroups;

import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;

public abstract class IndexChange extends Command {
  private final int id;

  protected IndexChange(String type, int id) {
    super(type);
    this.id = id;
  }

  public int getId() {
    return id;
  }

  abstract Operation getOperation();

  public static class Update extends IndexChange {
    static final String TYPE = "update-change";

    protected Update(int id) {
      super(TYPE, id);
    }

    Operation getOperation() {
      return Operation.INDEX;
    }
  }

  public static class Delete extends IndexChange {
    static final String TYPE = "delete-change";

    protected Delete(int id) {
      super(TYPE, id);
    }

    Operation getOperation() {
      return Operation.DELETE;
    }
  }
}

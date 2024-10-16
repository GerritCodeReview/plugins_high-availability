// Copyright (C) 2024 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.lock;

import com.google.common.base.CharMatcher;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;

public class LockFileFormat {
  private static final CharMatcher HEX_DIGIT_MATCHER = CharMatcher.anyOf("0123456789abcdef");

  private final String lockName;

  public static boolean isLockFileName(String fileName) {
    return fileName.length() == 40 && HEX_DIGIT_MATCHER.matchesAllOf(fileName);
  }

  public LockFileFormat(String lockName) {
    this.lockName = lockName;
  }

  public String fileName() {
    return Hashing.sha1().hashString(content(), StandardCharsets.UTF_8).toString();
  }

  public String content() {
    return lockName + "\n";
  }
}

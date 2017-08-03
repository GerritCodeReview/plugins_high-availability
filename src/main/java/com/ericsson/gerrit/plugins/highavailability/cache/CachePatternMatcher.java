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

package com.ericsson.gerrit.plugins.highavailability.cache;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Singleton
public class CachePatternMatcher {
  private static final List<String> DEFAULT_PATTERNS =
      ImmutableList.of(
          "^accounts.*",
          "^groups.*",
          "ldap_groups",
          "ldap_usernames",
          "^project.*",
          "sshkeys",
          "web_sessions");

  private final Pattern pattern;

  @Inject
  public CachePatternMatcher(Configuration cfg) {
    List<String> patterns = new ArrayList<>(DEFAULT_PATTERNS);
    patterns.addAll(cfg.cache().getPatterns());
    this.pattern = Pattern.compile(Joiner.on("|").join(patterns));
  }

  public boolean matches(String cacheName) {
    return pattern.matcher(cacheName).matches();
  }
}

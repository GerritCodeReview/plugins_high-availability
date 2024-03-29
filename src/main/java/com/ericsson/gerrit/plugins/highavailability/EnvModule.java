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

package com.ericsson.gerrit.plugins.highavailability;

import com.google.common.base.CharMatcher;
import com.google.gerrit.common.Nullable;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class EnvModule extends AbstractModule {
  public static final String MY_URL_ENV_VAR = "GERRIT_URL";

  @Override
  protected void configure() {
    bind(String.class)
        .annotatedWith(Names.named(MY_URL_ENV_VAR))
        .toInstance(urlWithTrimmedTrailingSlash(System.getenv(MY_URL_ENV_VAR)));
  }

  @Nullable
  private static String urlWithTrimmedTrailingSlash(@Nullable String in) {
    return in == null ? "" : CharMatcher.is('/').trimTrailingFrom(in);
  }
}

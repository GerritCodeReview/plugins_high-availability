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

package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class ServiceAccountCredentialsProvider implements Provider<CredentialsProvider> {
  private CredentialsProvider credentials;

  @Inject
  public ServiceAccountCredentialsProvider(Configuration config)
      throws FileNotFoundException, IOException {
    this.credentials =
        FixedCredentialsProvider.create(
            ServiceAccountCredentials.fromStream(
                new FileInputStream(config.pubSub().privateKeyLocation())));
  }

  @Override
  public CredentialsProvider get() {
    return credentials;
  }
}

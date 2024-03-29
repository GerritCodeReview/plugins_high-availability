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

package com.ericsson.gerrit.plugins.highavailability.peers.jgroups;

import static com.ericsson.gerrit.plugins.highavailability.EnvModule.MY_URL_ENV_VAR;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.common.base.CharMatcher;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.URIish;

@Singleton
class MyUrlProvider implements Provider<String> {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  private static final String HTTPD_SECTION = "httpd";
  private static final String LISTEN_URL_KEY = "listenUrl";
  private static final String LISTEN_URL = HTTPD_SECTION + "." + LISTEN_URL_KEY;
  private static final String PROXY_PREFIX = "proxy-";

  private final String myUrl;

  @Inject
  MyUrlProvider(
      @GerritServerConfig Config srvConfig,
      Configuration pluginConfiguration,
      @Named(MY_URL_ENV_VAR) String myUrlEnvVar) {
    String url = pluginConfiguration.peerInfoJGroups().myUrl();
    url = url == null ? myUrlEnvVar : url;
    if (url == null) {
      log.atInfo().log("myUrl not configured; attempting to determine from %s", LISTEN_URL);
      try {
        url = CharMatcher.is('/').trimTrailingFrom(getMyUrlFromListenUrl(srvConfig));
      } catch (MyUrlProviderException e) {
        throw new ProvisionException(e.getMessage());
      }
    }
    this.myUrl = url;
  }

  @Override
  public String get() {
    return myUrl;
  }

  private static String getMyUrlFromListenUrl(Config srvConfig) throws MyUrlProviderException {
    String[] listenUrls = srvConfig.getStringList(HTTPD_SECTION, null, LISTEN_URL_KEY);
    if (listenUrls.length != 1) {
      throw new MyUrlProviderException(
          String.format(
              "Can only determine myUrl from %s when there is exactly 1 value configured; found %d",
              LISTEN_URL, listenUrls.length));
    }
    String url = listenUrls[0];
    if (url.startsWith(PROXY_PREFIX)) {
      throw new MyUrlProviderException(
          String.format(
              "Cannot determine myUrl from %s when configured as reverse-proxy: %s",
              LISTEN_URL, url));
    }
    if (url.contains("*")) {
      throw new MyUrlProviderException(
          String.format(
              "Cannot determine myUrl from %s when configured with wildcard: %s", LISTEN_URL, url));
    }
    try {
      URIish u = new URIish(url);
      return u.setHost(InetAddress.getLocalHost().getHostName()).toString();
    } catch (URISyntaxException | UnknownHostException e) {
      throw new MyUrlProviderException(
          String.format(
              "Unable to determine myUrl from %s value [%s]: %s", LISTEN_URL, url, e.getMessage()));
    }
  }

  private static class MyUrlProviderException extends Exception {
    private static final long serialVersionUID = 1L;

    MyUrlProviderException(String message) {
      super(message);
    }
  }
}

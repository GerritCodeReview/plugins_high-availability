// Copyright (C) 2015 The Android Open Source Project
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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides an HTTP client with SSL capabilities. */
class HttpClientProvider implements Provider<CloseableHttpClient> {
  private static final Logger log = LoggerFactory.getLogger(HttpClientProvider.class);
  private static final int CONNECTIONS_PER_ROUTE = 100;
  // Up to 2 target instances with the max number of connections per host:
  private static final int MAX_CONNECTIONS = 2 * CONNECTIONS_PER_ROUTE;

  private static final int MAX_CONNECTION_INACTIVITY = 10000;

  private final Configuration cfg;
  private final SSLConnectionSocketFactory sslSocketFactory;
  private final int maxTries;

  @Inject
  HttpClientProvider(Configuration cfg) {
    this.cfg = cfg;
    this.sslSocketFactory = buildSslSocketFactory();
    this.maxTries = cfg.http().maxTries();
  }

  @Override
  public CloseableHttpClient get() {
    return HttpClients.custom()
        .setSSLSocketFactory(sslSocketFactory)
        .setConnectionManager(customConnectionManager())
        .setDefaultCredentialsProvider(buildCredentials())
        .setDefaultRequestConfig(customRequestConfig())
        .setRetryHandler(customRetryHandler())
        .setServiceUnavailableRetryStrategy(customServiceUnavailRetryStrategy())
        .build();
  }

  private RequestConfig customRequestConfig() {
    return RequestConfig.custom()
        .setConnectTimeout(cfg.http().connectionTimeout())
        .setSocketTimeout(cfg.http().socketTimeout())
        .setConnectionRequestTimeout(cfg.http().connectionTimeout())
        .build();
  }

  private HttpClientConnectionManager customConnectionManager() {
    Registry<ConnectionSocketFactory> socketFactoryRegistry =
        RegistryBuilder.<ConnectionSocketFactory>create()
            .register("https", sslSocketFactory)
            .register("http", PlainConnectionSocketFactory.INSTANCE)
            .build();
    PoolingHttpClientConnectionManager connManager =
        new PoolingHttpClientConnectionManager(socketFactoryRegistry);
    connManager.setDefaultMaxPerRoute(CONNECTIONS_PER_ROUTE);
    connManager.setMaxTotal(MAX_CONNECTIONS);
    connManager.setValidateAfterInactivity(MAX_CONNECTION_INACTIVITY);
    return connManager;
  }

  private static SSLConnectionSocketFactory buildSslSocketFactory() {
    return new SSLConnectionSocketFactory(buildSslContext(), NoopHostnameVerifier.INSTANCE);
  }

  private static SSLContext buildSslContext() {
    try {
      TrustManager[] trustAllCerts = new TrustManager[] {new DummyX509TrustManager()};
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustAllCerts, null);
      return context;
    } catch (KeyManagementException | NoSuchAlgorithmException e) {
      log.warn("Error building SSLContext object", e);
      return null;
    }
  }

  private BasicCredentialsProvider buildCredentials() {
    BasicCredentialsProvider creds = new BasicCredentialsProvider();
    creds.setCredentials(
        AuthScope.ANY, new UsernamePasswordCredentials(cfg.http().user(), cfg.http().password()));
    return creds;
  }

  private static class DummyX509TrustManager implements X509TrustManager {
    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }

    @Override
    public void checkClientTrusted(X509Certificate[] certs, String authType) {
      // no check
    }

    @Override
    public void checkServerTrusted(X509Certificate[] certs, String authType) {
      // no check
    }
  }

  private HttpRequestRetryHandler customRetryHandler() {
    return (exception, executionCount, context) -> {
      HttpClientContext clientContext = HttpClientContext.adapt(context);
      HttpRequest request = clientContext.getRequest();
      String target =
          request.getRequestLine().getMethod()
              + " "
              + request.getFirstHeader("Host").getValue()
              + request.getRequestLine().getUri();
      log.info("Try request: {} Attempt {}/{}", target, executionCount, maxTries);
      System.out.println(
          String.format("Try request: %s Attempt %d/%d", target, executionCount, maxTries));

      if (executionCount >= maxTries) {
        log.error("Failed to execute {} after {} tries; giving up", target, maxTries);
        System.out.println(
            String.format("Failed to execute %s after %d tries; giving up", target, maxTries));
        return false;
      }
      if (exception instanceof SSLException) {
        log.error("Unable to send request {}:", target, exception);
        return false;
      }
      try {
        TimeUnit.MILLISECONDS.sleep(cfg.http().retryInterval());
      } catch (InterruptedException ie) {
        log.error("{} was interrupted; giving up", target, ie);
        Thread.currentThread().interrupt();
        return false;
      }
      return true;
    };
  }

  private ServiceUnavailableRetryStrategy customServiceUnavailRetryStrategy() {
    return new ServiceUnavailableRetryStrategy() {
      @Override
      public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
        if (executionCount >= maxTries) {
          return false;
        }
        if (response.getStatusLine().getStatusCode() >= 500) {
          log.debug(
              "Retrying request caused by '{}', request: '{}'",
              response.getStatusLine().getReasonPhrase(),
              context.getAttribute("http.request"));
          return true;
        }
        return false;
      }

      @Override
      public long getRetryInterval() {
        return cfg.http().retryInterval();
      }
    };
  }
}

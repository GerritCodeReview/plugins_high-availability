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

import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;

/** Provides an HTTP client with SSL capabilities. */
class HttpClientProvider implements Provider<CloseableHttpClient> {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();
  private static final int CONNECTIONS_PER_ROUTE = 100;
  // Up to 2 target instances with the max number of connections per host:
  private static final int MAX_CONNECTIONS = 2 * CONNECTIONS_PER_ROUTE;

  private static final int MAX_CONNECTION_INACTIVITY = 10000;

  private final Configuration cfg;
  private final SSLConnectionSocketFactory sslSocketFactory;

  @Inject
  HttpClientProvider(Configuration cfg) {
    this.cfg = cfg;
    this.sslSocketFactory = buildSslSocketFactory();
  }

  @Override
  public CloseableHttpClient get() {
    return HttpClients.custom()
        .setSSLSocketFactory(sslSocketFactory)
        .setConnectionManager(customConnectionManager())
        .setDefaultCredentialsProvider(buildCredentials())
        .setDefaultRequestConfig(customRequestConfig())
        .setConnectionReuseStrategy(customConnectionReuseStrategy())
        .build();
  }

  private RequestConfig customRequestConfig() {
    int connectionTimeout = (int) cfg.http().connectionTimeout().toMillis();
    return RequestConfig.custom()
        .setConnectTimeout(connectionTimeout)
        .setSocketTimeout((int) cfg.http().socketTimeout().toMillis())
        .setConnectionRequestTimeout(connectionTimeout)
        .build();
  }

  private HttpClientConnectionManager customConnectionManager() {
    return buildConnectionManager();
  }

  PoolingHttpClientConnectionManager buildConnectionManager() {
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

  private ConnectionReuseStrategy customConnectionReuseStrategy() {
    return (response, context) -> {
      if (response.getStatusLine().getStatusCode() == SC_SERVICE_UNAVAILABLE
          && !cfg.http().reuseConnectionAfter503()) {
        log.atFine().log("Dropping pooled connection %s after 503 response", connInfo(context));
        return false;
      }
      log.atFine().log("Sent request using pooled connection %s", connInfo(context));
      return DefaultConnectionReuseStrategy.INSTANCE.keepAlive(response, context);
    };
  }

  private static String connInfo(HttpContext context) {
    HttpCoreContext ctx = HttpCoreContext.adapt(context);
    HttpHost target = ctx.getTargetHost();
    ManagedHttpClientConnection conn =
        (ManagedHttpClientConnection) context.getAttribute(HttpCoreContext.HTTP_CONNECTION);
    String connId = conn != null ? conn.getId() : "unknown";
    HttpRequest request = ctx.getRequest();
    String requestLine =
        request != null
            ? request.getRequestLine().getMethod() + " " + request.getRequestLine().getUri()
            : "unknown";
    return connId + " to " + target + ": " + requestLine;
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
      log.atWarning().withCause(e).log("Error building SSLContext object");
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
}

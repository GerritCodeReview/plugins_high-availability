// Copyright (C) 2015 Ericsson
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

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;

import com.ericsson.gerrit.plugins.highavailability.cache.Constants;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.google.common.base.Splitter;
import com.google.common.cache.Cache;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class CacheRestApiServlet extends HttpServlet {
  private static final int PLUGIN_NAME_INDEX = 1;
  private static final int CACHENAME_INDEX = 2;
  private static final long serialVersionUID = -1L;
  private static final Logger logger = LoggerFactory.getLogger(CacheRestApiServlet.class);

  private final DynamicMap<Cache<?, ?>> cacheMap;

  @Inject
  CacheRestApiServlet(DynamicMap<Cache<?, ?>> cacheMap) {
    this.cacheMap = cacheMap;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    rsp.setContentType("text/plain");
    rsp.setCharacterEncoding("UTF-8");
    try {
      List<String> params = Splitter.on('/').splitToList(req.getPathInfo());
      String cacheName = params.get(CACHENAME_INDEX);
      String pluginName = params.get(PLUGIN_NAME_INDEX); //TODO This is always "gerrit" so just get rid of it
      String json = req.getReader().readLine();
      Object key = GsonParser.fromJson(cacheName, json);
      Cache<?, ?> cache;
      int dot = cacheName.indexOf(".");
      if (dot < 0) {
        cache = cacheMap.get(pluginName, cacheName);
      } else {
        pluginName = cacheName.substring(0, dot);
        cacheName = cacheName.substring(dot + 1);
        logger.error(String.format("cache for plugin:%s[%s]", pluginName, cacheName));
        cache = cacheMap.byPlugin(pluginName).get(cacheName).get();
      }
      if (cache == null) {
        String msg = String.format("cache %s:%s not found", pluginName, cacheName);
        logger.error("Failed to process eviction request: " + msg);
        sendError(rsp, SC_BAD_REQUEST,msg);
      } else {
        Context.setForwardedEvent(true);
        evictCache(cache, cacheName, key);
        rsp.setStatus(SC_NO_CONTENT);
      }
    } catch (IOException e) {
      logger.error("Failed to process eviction request: " + e.getMessage(), e);
      sendError(rsp, SC_BAD_REQUEST, e.getMessage());
    } finally {
      Context.unsetForwardedEvent();
    }
  }

  private static void sendError(HttpServletResponse rsp, int statusCode, String message) {
    try {
      rsp.sendError(statusCode, message);
    } catch (IOException e) {
      logger.error("Failed to send error messsage: " + e.getMessage(), e);
    }
  }

  private void evictCache(Cache<?, ?> cache, String cacheName, Object key) {
    if (Constants.PROJECT_LIST.equals(cacheName)) {
      // One key is holding the list of projects
      cache.invalidateAll();
    } else {
      cache.invalidate(key);
    }
    logger.info("Invalidated " + cacheName);
  }
}

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

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.forwarder.Forwarder;
import com.google.common.base.Supplier;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.SupplierSerializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.stream.Collectors;
import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.blocks.MessageDispatcher;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.util.Rsp;
import org.jgroups.util.RspList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class JGroupsForwarder implements Forwarder {
  private static final Logger log = LoggerFactory.getLogger(JGroupsForwarder.class);
  private static final Gson gson =
      new GsonBuilder().registerTypeAdapter(Supplier.class, new SupplierSerializer()).create();

  private final MessageDispatcher dispatcher;
  private final Configuration cfg;

  @Inject
  JGroupsForwarder(MessageDispatcher dispatcher, Configuration cfg) {
    this.dispatcher = dispatcher;
    this.cfg = cfg;
  }

  @Override
  public boolean indexAccount(int accountId) {
    return execute(new IndexAccount(accountId));
  }

  @Override
  public boolean indexChange(int changeId) {
    return execute(new IndexChange.Update(changeId));
  }

  @Override
  public boolean deleteChangeFromIndex(int changeId) {
    return execute(new IndexChange.Delete(changeId));
  }

  @Override
  public boolean indexGroup(String uuid) {
    return execute(new IndexGroup(uuid));
  }

  @Override
  public boolean send(Event event) {
    return execute(new PostEvent(event));
  }

  @Override
  public boolean evict(String cacheName, Object key) {
    return execute(new EvictCache(cacheName, key));
  }

  @Override
  public boolean addToProjectList(String projectName) {
    return false;
  }

  @Override
  public boolean removeFromProjectList(String projectName) {
    return false;
  }

  private boolean execute(Command cmd) {
    for (int i = 0; i < cfg.main().maxTries(); i++) {
      if (executeOnce(cmd)) {
        return true;
      }
      try {
        Thread.sleep(cfg.main().retryInterval());
      } catch (InterruptedException ie) {
        log.error("{} was interrupted, giving up", cmd, ie);
        Thread.currentThread().interrupt();
        return false;
      }
    }

    log.error("Forwarding {} failed", cmd);
    return false;
  }

  private boolean executeOnce(Command cmd) {
    String json = gson.toJson(cmd);
    try {
      logJGroupsInfo();

      if (dispatcher.getChannel().getView().size() < 2) {
        log.warn("Less than two members in cluster, not sending {}", json);
        return false;
      }

      log.debug("\nSending {}", json);
      RequestOptions options =
          new RequestOptions(ResponseMode.GET_FIRST, cfg.main().retryInterval());
      RspList<Object> list = dispatcher.castMessage(null, new Message(null, json), options);

      log.debug("Received response list length = {}", list.size());
      if (list.isEmpty()) {
        return false;
      }

      for (Rsp<Object> o : list) {
        log.debug("Response object {}", o);
        if (!Boolean.TRUE.equals(o.getValue())) {
          log.warn(
              "Received a non TRUE response from receiver {}: {}", o.getSender(), o.getValue());
          return false;
        }
      }
      log.debug("Successfully sent message {}", json);
      return true;
    } catch (Exception e) {
      log.error("Forwarding {} failed", json, e);
      return false;
    }
  }

  private void logJGroupsInfo() {
    if (log.isDebugEnabled()) {
      Address myAddress = dispatcher.getChannel().getAddress();
      log.debug(
          "My address: {}. Other members on cluster {}: {}.",
          myAddress,
          dispatcher.getChannel().getClusterName(),
          dispatcher
              .getChannel()
              .getView()
              .getMembers()
              .stream()
              .filter(a -> !a.equals(myAddress))
              .map(Object::toString)
              .collect(Collectors.joining(", ")));
    }
  }
}

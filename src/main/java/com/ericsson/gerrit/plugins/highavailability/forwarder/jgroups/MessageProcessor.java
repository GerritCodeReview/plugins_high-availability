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

import com.ericsson.gerrit.plugins.highavailability.forwarder.CacheEntry;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedCacheEvictionHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedEventHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexAccountHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexChangeHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexGroupHandler;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexingHandler.Operation;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventDeserializer;
import com.google.gerrit.server.events.SupplierDeserializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.jgroups.Message;
import org.jgroups.blocks.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MessageProcessor implements RequestHandler {
  private static final Logger log = LoggerFactory.getLogger(MessageProcessor.class);

  @VisibleForTesting
  public static Gson gson =
      new GsonBuilder()
          .registerTypeAdapter(Command.class, new CommandDeserializer())
          .registerTypeAdapter(Event.class, new EventDeserializer())
          .registerTypeAdapter(Supplier.class, new SupplierDeserializer())
          .create();

  private final ForwardedIndexAccountHandler forwardedIndexAccountHandler;
  private final ForwardedIndexChangeHandler forwardedIndexChangeHandler;
  private final ForwardedIndexGroupHandler forwardedIndexGroupHandler;
  private final ForwardedCacheEvictionHandler forwardedCacheEvictionHandler;
  private final ForwardedEventHandler forwardedEventHandler;

  @Inject
  MessageProcessor(
      ForwardedIndexAccountHandler forwardedIndexAccountHandler,
      ForwardedIndexChangeHandler forwardedIndexChangeHandlers,
      ForwardedIndexGroupHandler forwardedIndexGroupHandler,
      ForwardedCacheEvictionHandler forwardedCacheEvictionHandler,
      ForwardedEventHandler forwardedEventHandler) {
    this.forwardedIndexAccountHandler = forwardedIndexAccountHandler;
    this.forwardedIndexChangeHandler = forwardedIndexChangeHandlers;
    this.forwardedIndexGroupHandler = forwardedIndexGroupHandler;
    this.forwardedCacheEvictionHandler = forwardedCacheEvictionHandler;
    this.forwardedEventHandler = forwardedEventHandler;
  }

  @Override
  public Object handle(Message msg) {
    Command cmd = getCommand(msg);

    try {
      if (cmd instanceof IndexChange) {
        IndexChange indexChange = (IndexChange) cmd;
        Operation op = getOperation(indexChange);
        try {
          forwardedIndexChangeHandler.index(new Change.Id(indexChange.getId()), op);
          log.debug(
              "Change index {} on change {} done", op.name().toLowerCase(), indexChange.getId());
        } catch (Exception e) {
          log.error(
              "Change index {} on change {} failed",
              op.name().toLowerCase(),
              indexChange.getId(),
              e);
          return false;
        }
      }
      if (cmd instanceof IndexAccount) {
        IndexAccount indexAccount = (IndexAccount) cmd;
        try {
          forwardedIndexAccountHandler.index(new Account.Id(indexAccount.getId()), Operation.INDEX);
          log.debug("Account index update on account {} done", indexAccount.getId());
        } catch (IOException e) {
          log.error("Account index update on account {} failed", indexAccount.getId(), e);
          return false;
        }
      }
      if (cmd instanceof IndexGroup) {
        IndexGroup indexGroup = (IndexGroup) cmd;
        try {
          forwardedIndexGroupHandler.index(
              AccountGroup.UUID.parse(indexGroup.getId().get()), Operation.INDEX);
          log.debug("Group index update on group {} done", indexGroup.getId());
        } catch (IOException e) {
          log.error("Group index update on group {} failed", indexGroup.getId(), e);
          return false;
        }
      }
      if (cmd instanceof EvictCache) {
        EvictCache evictCommand = (EvictCache) cmd;
        forwardedCacheEvictionHandler.evict(
            CacheEntry.from(evictCommand.getCacheName(), evictCommand.getKey()));
        log.debug("Cache eviction {} {} done", evictCommand.getCacheName(), evictCommand.getKey());
      }
      if (cmd instanceof PostEvent) {
        Event event = ((PostEvent) cmd).getEvent();
        try {
          forwardedEventHandler.dispatch(event);
          log.debug("Dispatching event {} done", event);
        } catch (OrmException e) {
          log.error("Dispatching event {} failed", event, e);
          e.printStackTrace();
          return false;
        }
      }

      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private Operation getOperation(IndexChange cmd) {
    if (cmd instanceof IndexChange.Update) {
      return Operation.INDEX;
    }
    if (cmd instanceof IndexChange.Delete) {
      return Operation.DELETE;
    }
    throw new IllegalArgumentException("Unknown type of IndexChange command " + cmd.getClass());
  }

  private Command getCommand(Message msg) {
    try {
      String s = (String) msg.getObject();
      log.debug("Received message: {}", s);
      return gson.fromJson(s, Command.class);
    } catch (RuntimeException e) {
      log.error("Error parsing message {}", msg.getObject(), e);
      throw e;
    }
  }
}

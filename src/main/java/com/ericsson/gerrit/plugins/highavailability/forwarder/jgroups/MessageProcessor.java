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

import java.io.IOException;

import org.jgroups.Message;
import org.jgroups.blocks.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.gerrit.plugins.highavailability.forwarder.Context;
import com.ericsson.gerrit.plugins.highavailability.forwarder.util.CacheEviction;
import com.ericsson.gerrit.plugins.highavailability.forwarder.util.IndexAccounts;
import com.ericsson.gerrit.plugins.highavailability.forwarder.util.IndexChanges;
import com.ericsson.gerrit.plugins.highavailability.forwarder.util.IndexChanges.Operation;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.gerrit.common.EventDispatcher;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventDeserializer;
import com.google.gerrit.server.events.SupplierDeserializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

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

  private final IndexChanges indexChanges;
  private final IndexAccounts indexAccounts;
  private final CacheEviction cacheEviction;
  private final EventDispatcher dispatcher;

  @Inject
  MessageProcessor(
      IndexChanges indexChanges,
      IndexAccounts indexAccounts,
      CacheEviction cacheEviction,
      EventDispatcher dispatcher) {
    this.indexChanges = indexChanges;
    this.indexAccounts = indexAccounts;
    this.cacheEviction = cacheEviction;
    this.dispatcher = dispatcher;
  }

  @Override
  public Object handle(Message msg) {
    Command cmd = getCommand(msg);

    Context.setForwardedEvent(true);
    try {

      if (cmd instanceof IndexChange) {
        IndexChange indexChange = (IndexChange) cmd;
        Operation op = getOperation(indexChange);
        try {
          indexChanges.index(new Change.Id(indexChange.getId()), op);
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

      } else if (cmd instanceof IndexAccount) {
        IndexAccount indexAccount = (IndexAccount) cmd;
        try {
          indexAccounts.index(new Account.Id(indexAccount.getId()));
          log.debug("Account index update on account {} done", indexAccount.getId());
        } catch (IOException e) {
          log.error("Account index update on account {} failed", indexAccount.getId(), e);
          return false;
        }

      } else if (cmd instanceof EvictCache) {
        EvictCache evictCommand = (EvictCache) cmd;
        cacheEviction.evict(evictCommand.getCacheName(), evictCommand.getKey());
        log.debug("Cache eviction {} {} done", evictCommand.getCacheName(), evictCommand.getKey());

      } else if (cmd instanceof PostEvent) {
        Event event = ((PostEvent) cmd).getEvent();
        try {
          dispatcher.postEvent(event);
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
    } finally {
      Context.unsetForwardedEvent();
    }
  }

  private Operation getOperation(IndexChange cmd) {
    if (cmd instanceof IndexChange.Update) {
      return Operation.UPDATE;
    } else if (cmd instanceof IndexChange.Delete) {
      return Operation.DELETE;
    } else {
      throw new IllegalArgumentException("Unknown type of IndexChange command " + cmd.getClass());
    }
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

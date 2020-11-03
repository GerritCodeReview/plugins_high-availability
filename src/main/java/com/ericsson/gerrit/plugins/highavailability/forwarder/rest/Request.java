// Copyright (C) 2020 The Android Open Source Project
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

import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.HttpResponseHandler.HttpResult;
import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import javax.net.ssl.SSLException;
import org.apache.http.HttpException;
import org.apache.http.client.ClientProtocolException;

public abstract class Request {
  private static final FluentLogger log = FluentLogger.forEnclosingClass();

  final String action;
  final Object key;
  final String destination;

  Request(String action, Object key, String destination) {
    this.action = action;
    this.key = key;
    this.destination = destination;
  }

  Result execute() {
    log.atFine().log("Executing %s %s towards %s", action, key, destination);
    try {
      tryOnce();
      log.atFine().log("%s %s towards %s OK", action, key, destination);
      return Result.create(this, RequestStatus.SUCCESSFULL);
    } catch (ForwardingException e) {

      log.atFine().withCause(e).log("Failed to %s %s on %s", action, key, destination);
      if (!e.isRecoverable()) {
        log.atSevere().withCause(e).log(
            "%s %s towards %s failed with unrecoverable error; giving up",
            action, key, destination);
        return Result.create(this, RequestStatus.FAILURE);
      }

      log.atFine().log("Retrying to %s %s on %s", action, key, destination);
      return Result.create(this, RequestStatus.RETRY);
    }
  }

  void tryOnce() throws ForwardingException {
    try {
      HttpResult result = send();
      if (!result.isSuccessful()) {
        throw new ForwardingException(
            true, String.format("Unable to %s %s : %s", action, key, result.getMessage()));
      }
    } catch (IOException e) {
      throw new ForwardingException(isRecoverable(e), e.getMessage(), e);
    }
  }

  abstract HttpResult send() throws IOException;

  boolean isRecoverable(IOException e) {
    Throwable cause = e.getCause();
    return !(e instanceof SSLException
        || cause instanceof HttpException
        || cause instanceof ClientProtocolException);
  }
}

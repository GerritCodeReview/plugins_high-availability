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

import com.ericsson.gerrit.plugins.highavailability.event.ChangeIndexedEvent;
import com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedIndexChangeHandler;
import com.google.common.base.Charsets;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.OutputFormat;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.InputStreamReader;
import java.util.Optional;
import javax.servlet.ServletInputStream;

@Singleton
class IndexChangeRestApiServlet extends AbstractIndexRestApiServlet<String> {
  private static final long serialVersionUID = -1L;
  private Gson gson = OutputFormat.JSON.newGson();

  @Inject
  IndexChangeRestApiServlet(ForwardedIndexChangeHandler handler) {
    super(handler, IndexName.CHANGE, true);
  }

  @Override
  String parse(String id) {
    return Url.decode(id);
  }

  @Override
  protected Optional<Object> parseBody(ServletInputStream bodyIn) {
    return Optional.ofNullable(
        gson.fromJson(new InputStreamReader(bodyIn, Charsets.UTF_8), ChangeIndexedEvent.class));
  }
}

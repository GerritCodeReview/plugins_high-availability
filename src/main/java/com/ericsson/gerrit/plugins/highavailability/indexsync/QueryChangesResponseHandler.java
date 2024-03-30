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

package com.ericsson.gerrit.plugins.highavailability.indexsync;

import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.util.EntityUtils;

@Singleton
public class QueryChangesResponseHandler implements ResponseHandler<List<String>> {

  private final Gson gson = new Gson();

  @Override
  public List<String> handleResponse(HttpResponse rsp) throws ClientProtocolException, IOException {
    StatusLine status = rsp.getStatusLine();
    if (rsp.getStatusLine().getStatusCode() != SC_OK) {
      throw new HttpResponseException(status.getStatusCode(), "Query failed");
    }
    HttpEntity entity = rsp.getEntity();
    if (entity == null) {
      return List.of();
    }
    String body = EntityUtils.toString(entity);
    return gson.fromJson(body, new TypeToken<List<String>>() {}.getType());
  }
}

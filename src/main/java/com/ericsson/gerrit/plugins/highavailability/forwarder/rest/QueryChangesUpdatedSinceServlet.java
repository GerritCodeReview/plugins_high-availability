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

package com.ericsson.gerrit.plugins.highavailability.forwarder.rest;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.query.QueryResult;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeQueryProcessor;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class QueryChangesUpdatedSinceServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  Gson gson = new Gson();

  private ChangeQueryBuilder changeQueryBuilder;
  private final Provider<ChangeQueryProcessor> queryProcessorProvider;

  @Inject
  QueryChangesUpdatedSinceServlet(
      ChangeQueryBuilder changeQueryBuilder,
      Provider<ChangeQueryProcessor> queryProcessorProvider) {
    this.changeQueryBuilder = changeQueryBuilder;
    this.queryProcessorProvider = queryProcessorProvider;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp)
      throws ServletException, IOException {
    try {
      String age = req.getPathInfo().substring(1);
      ChangeQueryProcessor queryProcessor = queryProcessorProvider.get();
      queryProcessor.enforceVisibility(false);
      queryProcessor.setNoLimit(true);
      // TODO: prevent too large age, because of the noLimit option
      Predicate<ChangeData> predicate = Predicate.not(changeQueryBuilder.age(age));
      QueryResult<ChangeData> result = queryProcessor.query(predicate);
      ImmutableList<ChangeData> cds = result.entities();
      ArrayList<String> response = new ArrayList<>(cds.size());
      for (ChangeData cd : cds) {
        response.add(String.format("%s~%s", cd.project().get(), cd.getId().get()));
      }

      String json = gson.toJson(response);
      rsp.setStatus(SC_OK);
      rsp.setContentType("application/json");
      rsp.setCharacterEncoding("UTF-8");
      PrintWriter out = rsp.getWriter();
      out.print(json);
      out.print("\n");
      out.flush();
    } catch (IllegalArgumentException e) {
      rsp.setStatus(SC_BAD_REQUEST);
    } catch (QueryParseException e) {
      throw new ServletException(e);
    }
  }
}

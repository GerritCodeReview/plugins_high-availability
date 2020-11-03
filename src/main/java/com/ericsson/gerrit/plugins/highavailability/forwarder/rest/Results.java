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

import com.google.auto.value.AutoValue;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AutoValue
public abstract class Results {

  public static Results create(List<Result> results) {
    Map<RequestStatus, List<Result>> resultsPerStatus =
        results.stream().collect(Collectors.groupingBy(result -> result.status()));
    return new AutoValue_Results(
        getRequestsByStatus(resultsPerStatus, RequestStatus.SUCCESSFULL),
        getRequestsByStatus(resultsPerStatus, RequestStatus.FAILURE),
        getRequestsByStatus(resultsPerStatus, RequestStatus.RETRY));
  }

  public abstract List<Request> successfullRequests();

  public abstract List<Request> failedRequests();

  public abstract List<Request> retryRequests();

  public Boolean isSucessfull() {
    return failedRequests().isEmpty() && retryRequests().isEmpty();
  }

  public Boolean containsRetry() {
    return !retryRequests().isEmpty();
  }

  public Results appendSuccessfull(Results otherResults) {
    return new AutoValue_Results(
        combineLists(successfullRequests(), otherResults.successfullRequests()),
        failedRequests(),
        retryRequests());
  }

  public Results appendFailures(Results otherResults) {
    return new AutoValue_Results(
        successfullRequests(),
        combineLists(failedRequests(), otherResults.failedRequests()),
        retryRequests());
  }

  public Results setRetries(Results otherResults) {
    return new AutoValue_Results(
        successfullRequests(), failedRequests(), otherResults.retryRequests());
  }

  private List<Request> combineLists(List<Request> listOne, List<Request> listTwo) {
    return Stream.of(listOne, listTwo).flatMap(Collection::stream).collect(Collectors.toList());
  }

  private static List<Request> getRequestsByStatus(
      Map<RequestStatus, List<Result>> resultsPerStatus, RequestStatus status) {
    return resultsPerStatus.getOrDefault(status, Lists.newArrayList()).stream()
        .map(result -> result.request())
        .collect(Collectors.toList());
  }
}

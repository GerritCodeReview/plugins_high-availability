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

package com.ericsson.gerrit.plugins.highavailability.index;

import com.ericsson.gerrit.plugins.highavailability.ExecutorProvider;
import com.google.gerrit.extensions.events.AccountIndexedListener;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.events.GroupIndexedListener;
import com.google.gerrit.extensions.events.ProjectIndexedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import dev.failsafe.FailsafeExecutor;

public class IndexModule extends LifecycleModule {

  @Override
  protected void configure() {
    bind(new TypeLiteral<FailsafeExecutor<Boolean>>() {})
        .annotatedWith(ForwardedIndexExecutor.class)
        .toProvider(ForwardedIndexFailsafeExecutorProvider.class)
        .in(Scopes.SINGLETON);
    bind(ExecutorProvider.class)
        .annotatedWith(ForwardedIndexExecutor.class)
        .to(ForwardedIndexExecutorProvider.class)
        .in(Scopes.SINGLETON);
    listener().to(ForwardedIndexExecutorProvider.class);

    bind(new TypeLiteral<FailsafeExecutor<Boolean>>() {})
        .annotatedWith(ForwardedBatchIndexExecutor.class)
        .toProvider(ForwardedBatchIndexFailsafeExecutorProvider.class)
        .in(Scopes.SINGLETON);
    bind(ExecutorProvider.class)
        .annotatedWith(ForwardedBatchIndexExecutor.class)
        .to(ForwardedBatchIndexExecutorProvider.class)
        .in(Scopes.SINGLETON);
    listener().to(ForwardedBatchIndexExecutorProvider.class);

    DynamicSet.bind(binder(), ChangeIndexedListener.class)
        .to(IndexEventHandler.class)
        .in(Scopes.SINGLETON);
    DynamicSet.bind(binder(), AccountIndexedListener.class).to(IndexEventHandler.class);
    DynamicSet.bind(binder(), GroupIndexedListener.class).to(IndexEventHandler.class);
    DynamicSet.bind(binder(), ProjectIndexedListener.class).to(IndexEventHandler.class);

    install(
        new FactoryModuleBuilder()
            .implement(ChangeChecker.class, ChangeCheckerImpl.class)
            .build(ChangeCheckerImpl.Factory.class));
  }
}

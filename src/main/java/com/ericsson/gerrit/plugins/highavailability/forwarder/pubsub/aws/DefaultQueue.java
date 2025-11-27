package com.ericsson.gerrit.plugins.highavailability.forwarder.pubsub.aws;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.BindingAnnotation;
import java.lang.annotation.Retention;

@BindingAnnotation
@Retention(RUNTIME)
public @interface DefaultQueue {}

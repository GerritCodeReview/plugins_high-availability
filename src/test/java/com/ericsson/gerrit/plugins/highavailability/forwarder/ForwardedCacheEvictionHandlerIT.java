package com.ericsson.gerrit.plugins.highavailability.forwarder;

import static com.google.common.truth.Truth.assertThat;

import com.ericsson.gerrit.plugins.highavailability.Configuration;
import com.ericsson.gerrit.plugins.highavailability.ExecutorProvider;
import com.ericsson.gerrit.plugins.highavailability.cache.CacheModule;
import com.ericsson.gerrit.plugins.highavailability.forwarder.rest.RestForwarderModule;
import com.ericsson.gerrit.plugins.highavailability.peers.PeerInfoModule;
import com.google.common.cache.RemovalNotification;
import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.project.ProjectCacheImpl;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@TestPlugin(
    name = "high-availability",
    sysModule =
        "com.ericsson.gerrit.plugins.highavailability.forwarder.ForwardedCacheEvictionHandlerIT$TestModule")
public class ForwardedCacheEvictionHandlerIT extends LightweightPluginDaemonTest {

  private static final Duration CACHE_EVICTIONS_WAIT_TIMEOUT = Duration.ofMinutes(1);

  @SuppressWarnings("rawtypes")
  @Inject
  private DynamicSet<CacheRemovalListener> cacheRemovalListeners;

  private CacheEvictionsTracker<?> evictionsCacheTracker;
  private RegistrationHandle cacheEvictionRegistrationHandle;

  public static class TestModule extends AbstractModule {

    @Override
    protected void configure() {
      install(new ForwarderModule());
      install(new RestForwarderModule());
      install(new CacheModule(TestForwardingExecutorProvider.class));
      install(new PeerInfoModule(Configuration.PeerInfoStrategy.STATIC));
    }
  }

  @Singleton
  public static class TestForwardingExecutorProvider extends ExecutorProvider {
    private final ScheduledThreadPoolExecutor executor;
    private final AtomicInteger executionsCounter;

    @Inject
    protected TestForwardingExecutorProvider(WorkQueue workQueue) {
      super(workQueue, 1, "test");
      executionsCounter = new AtomicInteger();
      executor =
          new ScheduledThreadPoolExecutor(1) {
            @Override
            public void execute(Runnable command) {
              @SuppressWarnings("unused")
              int ignored = executionsCounter.incrementAndGet();
              super.execute(command);
            }
          };
    }

    @Override
    public ScheduledExecutorService get() {
      return executor;
    }

    public int executions() {
      return executionsCounter.get();
    }
  }

  public static class CacheEvictionsTracker<V> implements CacheRemovalListener<Project.NameKey, V> {
    private final Map<String, Set<Project.NameKey>> trackedEvictions;
    private final CountDownLatch allExpectedEvictionsArrived;
    private static final Set<Project.NameKey> EMPTY_SET = Collections.emptySet();

    public CacheEvictionsTracker(int numExpectedEvictions) {
      allExpectedEvictionsArrived = new CountDownLatch(numExpectedEvictions);
      trackedEvictions = new HashMap<>();
    }

    public Set<Project.NameKey> trackedEvictionsFor(String cacheName) {
      return trackedEvictions.getOrDefault(cacheName, EMPTY_SET);
    }

    public void waitForExpectedEvictions() throws InterruptedException {
      allExpectedEvictionsArrived.await(
          CACHE_EVICTIONS_WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void onRemoval(
        String pluginName, String cacheName, RemovalNotification<Project.NameKey, V> notification) {
      if (cacheName.equals(ProjectCacheImpl.CACHE_NAME)) {
        trackedEvictions.compute(
            cacheName,
            (k, v) -> {
              if (v == null) {
                return new HashSet<>(Arrays.asList(notification.getKey()));
              }
              v.add(notification.getKey());
              return v;
            });
        allExpectedEvictionsArrived.countDown();
      }
    }
  }

  @Before
  public void startTrackingCacheEvictions() {
    evictionsCacheTracker = new CacheEvictionsTracker<>(1);
    cacheEvictionRegistrationHandle = cacheRemovalListeners.add("gerrit", evictionsCacheTracker);
  }

  @After
  public void stopTrackingCacheEvictions() {
    cacheEvictionRegistrationHandle.remove();
  }

  @Test
  public void shouldNotForwardProjectCacheEvictionsWhenEventIsForwarded() throws Exception {
    TestForwardingExecutorProvider cacheForwarder =
        plugin.getSysInjector().getInstance(TestForwardingExecutorProvider.class);
    Context.setForwardedEvent(true);
    projectCache.evict(allProjects);
    evictionsCacheTracker.waitForExpectedEvictions();
    assertThat(evictionsCacheTracker.trackedEvictionsFor(ProjectCacheImpl.CACHE_NAME))
        .contains(allProjects);
    assertThat(cacheForwarder.executions()).isEqualTo(0);
  }

  @Test
  public void shouldForwardProjectCacheEvictions() throws Exception {
    TestForwardingExecutorProvider cacheForwarder =
        plugin.getSysInjector().getInstance(TestForwardingExecutorProvider.class);
    projectCache.evict(allProjects);
    evictionsCacheTracker.waitForExpectedEvictions();
    assertThat(evictionsCacheTracker.trackedEvictionsFor(ProjectCacheImpl.CACHE_NAME))
        .contains(allProjects);
    assertThat(cacheForwarder.executions()).isEqualTo(1);
  }
}

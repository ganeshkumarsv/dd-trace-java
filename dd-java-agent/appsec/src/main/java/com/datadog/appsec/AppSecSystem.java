package com.datadog.appsec;

import com.datadog.appsec.blocking.BlockingServiceImpl;
import com.datadog.appsec.config.AppSecConfigService;
import com.datadog.appsec.config.AppSecConfigServiceImpl;
import com.datadog.appsec.event.EventDispatcher;
import com.datadog.appsec.event.ReplaceableEventProducerService;
import com.datadog.appsec.gateway.GatewayBridge;
import com.datadog.appsec.gateway.RateLimiter;
import com.datadog.appsec.util.AbortStartupException;
import com.datadog.appsec.util.StandardizedLogging;
import datadog.appsec.api.blocking.Blocking;
import datadog.communication.ddagent.SharedCommunicationObjects;
import datadog.communication.monitor.Counter;
import datadog.communication.monitor.Monitoring;
import datadog.remoteconfig.ConfigurationPoller;
import datadog.trace.api.Config;
import datadog.trace.api.ProductActivation;
import datadog.trace.api.gateway.SubscriptionService;
import datadog.trace.api.time.SystemTimeSource;
import datadog.trace.bootstrap.ActiveSubsystems;
import datadog.trace.util.Strings;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppSecSystem {

  private static final Logger log = LoggerFactory.getLogger(AppSecSystem.class);
  private static final AtomicBoolean STARTED = new AtomicBoolean();
  private static final Map<AppSecModule, String> STARTED_MODULES_INFO = new HashMap<>();
  private static AppSecConfigServiceImpl APP_SEC_CONFIG_SERVICE;
  private static ReplaceableEventProducerService REPLACEABLE_EVENT_PRODUCER; // testing

  public static void start(SubscriptionService gw, SharedCommunicationObjects sco) {
    try {
      doStart(gw, sco);
    } catch (AbortStartupException ase) {
      throw ase;
    } catch (RuntimeException | Error e) {
      StandardizedLogging.appSecStartupError(log, e);
      setActive(false);
      throw new AbortStartupException(e);
    }
  }

  private static void doStart(SubscriptionService gw, SharedCommunicationObjects sco) {
    final Config config = Config.get();
    ProductActivation appSecEnabledConfig = config.getAppSecActivation();
    if (appSecEnabledConfig == ProductActivation.FULLY_DISABLED) {
      log.debug("AppSec: disabled");
      return;
    }
    log.debug("AppSec is starting ({})", appSecEnabledConfig);

    REPLACEABLE_EVENT_PRODUCER = new ReplaceableEventProducerService();
    EventDispatcher eventDispatcher = new EventDispatcher();
    REPLACEABLE_EVENT_PRODUCER.replaceEventProducerService(eventDispatcher);

    ConfigurationPoller configurationPoller = sco.configurationPoller(config);
    // may throw and abort startup
    APP_SEC_CONFIG_SERVICE =
        new AppSecConfigServiceImpl(
            config, configurationPoller, () -> reloadSubscriptions(REPLACEABLE_EVENT_PRODUCER));
    APP_SEC_CONFIG_SERVICE.init();

    sco.createRemaining(config);

    RateLimiter rateLimiter = getRateLimiter(config, sco.monitoring);
    GatewayBridge gatewayBridge =
        new GatewayBridge(
            gw,
            REPLACEABLE_EVENT_PRODUCER,
            rateLimiter,
            APP_SEC_CONFIG_SERVICE.getTraceSegmentPostProcessors());

    loadModules(eventDispatcher);
    gatewayBridge.init();

    setActive(appSecEnabledConfig == ProductActivation.FULLY_ENABLED);

    APP_SEC_CONFIG_SERVICE.maybeSubscribeConfigPolling();

    Blocking.setBlockingService(new BlockingServiceImpl(REPLACEABLE_EVENT_PRODUCER));

    STARTED.set(true);

    String startedAppSecModules = Strings.join(", ", STARTED_MODULES_INFO.values());
    if (appSecEnabledConfig == ProductActivation.FULLY_ENABLED) {
      log.info("AppSec is {} with {}", appSecEnabledConfig, startedAppSecModules);
    } else {
      log.debug("AppSec is {} with {}", appSecEnabledConfig, startedAppSecModules);
    }
  }

  private static RateLimiter getRateLimiter(Config config, Monitoring monitoring) {
    RateLimiter rateLimiter = null;
    int appSecTraceRateLimit = config.getAppSecTraceRateLimit();
    if (appSecTraceRateLimit > 0) {
      Counter counter = monitoring.newCounter("_dd.java.appsec.rate_limit.dropped_traces");
      rateLimiter =
          new RateLimiter(
              appSecTraceRateLimit, SystemTimeSource.INSTANCE, () -> counter.increment(1));
    }
    return rateLimiter;
  }

  public static boolean isActive() {
    return ActiveSubsystems.APPSEC_ACTIVE;
  }

  public static void setActive(boolean status) {
    ActiveSubsystems.APPSEC_ACTIVE = status;
  }

  public static void stop() {
    if (!STARTED.getAndSet(false)) {
      return;
    }

    APP_SEC_CONFIG_SERVICE.close();
  }

  private static void loadModules(EventDispatcher eventDispatcher) {
    EventDispatcher.EventSubscriptionSet eventSubscriptionSet =
        new EventDispatcher.EventSubscriptionSet();
    EventDispatcher.DataSubscriptionSet dataSubscriptionSet =
        new EventDispatcher.DataSubscriptionSet();

    ServiceLoader<AppSecModule> modules =
        ServiceLoader.load(AppSecModule.class, AppSecSystem.class.getClassLoader());
    for (AppSecModule module : modules) {
      log.debug("Starting appsec module {}", module.getName());
      try {
        AppSecConfigService.TransactionalAppSecModuleConfigurer cfgObject;
        cfgObject = APP_SEC_CONFIG_SERVICE.createAppSecModuleConfigurer();
        module.config(cfgObject);
        cfgObject.commit();
      } catch (RuntimeException | AppSecModule.AppSecModuleActivationException t) {
        log.error("Startup of appsec module {} failed", module.getName(), t);
        continue;
      }

      for (AppSecModule.EventSubscription sub : module.getEventSubscriptions()) {
        eventSubscriptionSet.addSubscription(sub.eventType, sub);
      }

      for (AppSecModule.DataSubscription sub : module.getDataSubscriptions()) {
        dataSubscriptionSet.addSubscription(sub.getSubscribedAddresses(), sub);
      }

      STARTED_MODULES_INFO.put(module, module.getInfo());
    }

    eventDispatcher.subscribeEvents(eventSubscriptionSet);
    eventDispatcher.subscribeDataAvailable(dataSubscriptionSet);
  }

  private static void reloadSubscriptions(
      ReplaceableEventProducerService replaceableEventProducerService) {
    EventDispatcher.EventSubscriptionSet eventSubscriptionSet =
        new EventDispatcher.EventSubscriptionSet();
    EventDispatcher.DataSubscriptionSet dataSubscriptionSet =
        new EventDispatcher.DataSubscriptionSet();

    EventDispatcher newEd = new EventDispatcher();
    for (AppSecModule module : STARTED_MODULES_INFO.keySet()) {
      for (AppSecModule.EventSubscription sub : module.getEventSubscriptions()) {
        eventSubscriptionSet.addSubscription(sub.eventType, sub);
      }

      for (AppSecModule.DataSubscription sub : module.getDataSubscriptions()) {
        dataSubscriptionSet.addSubscription(sub.getSubscribedAddresses(), sub);
      }
    }

    newEd.subscribeEvents(eventSubscriptionSet);
    newEd.subscribeDataAvailable(dataSubscriptionSet);

    replaceableEventProducerService.replaceEventProducerService(newEd);
  }

  public static boolean isStarted() {
    return STARTED.get();
  }

  public static Set<String> getStartedModulesInfo() {
    if (isStarted()) {
      return STARTED_MODULES_INFO.keySet().stream()
          .map(AppSecModule::getName)
          .collect(Collectors.toSet());
    } else {
      return Collections.emptySet();
    }
  }
}

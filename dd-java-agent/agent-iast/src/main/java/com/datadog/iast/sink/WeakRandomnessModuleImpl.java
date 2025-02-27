package com.datadog.iast.sink;

import com.datadog.iast.model.Evidence;
import com.datadog.iast.model.VulnerabilityType;
import datadog.trace.api.iast.sink.WeakRandomnessModule;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import javax.annotation.Nonnull;

public class WeakRandomnessModuleImpl extends SinkModuleBase implements WeakRandomnessModule {

  @Override
  public void onWeakRandom(@Nonnull final Class<?> instance) {
    if (isSecuredInstance(instance)) {
      return;
    }
    final AgentSpan span = AgentTracer.activeSpan();
    report(span, VulnerabilityType.WEAK_RANDOMNESS, new Evidence(instance.getName()));
  }

  /**
   * Skip vulnerabilities on {@link java.security.SecureRandom} or any impl that contains secure in
   * the name
   */
  private boolean isSecuredInstance(@Nonnull final Class<?> instance) {
    return instance.getSimpleName().toLowerCase().contains("secure");
  }
}

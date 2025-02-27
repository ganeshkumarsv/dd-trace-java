package datadog.trace.instrumentation.servlet3.callsite;

import datadog.trace.agent.tooling.csi.CallSite;
import datadog.trace.api.iast.IastAdvice;
import datadog.trace.api.iast.IastAdvice.Source;
import datadog.trace.api.iast.InstrumentationBridge;
import datadog.trace.api.iast.SourceTypes;
import datadog.trace.api.iast.source.WebModule;
import java.util.Map;
import javax.servlet.ServletRequest;

@CallSite(spi = IastAdvice.class)
public class Servlet3RequestCallSite {

  @Source(SourceTypes.REQUEST_PARAMETER_VALUE)
  @CallSite.After("java.util.Map javax.servlet.ServletRequest.getParameterMap()")
  @CallSite.After("java.util.Map javax.servlet.http.HttpServletRequest.getParameterMap()")
  @CallSite.After("java.util.Map javax.servlet.http.HttpServletRequestWrapper.getParameterMap()")
  @CallSite.After("java.util.Map javax.servlet.ServletRequestWrapper.getParameterMap()")
  public static java.util.Map<java.lang.String, java.lang.String[]> afterGetParameterMap(
      @CallSite.This final ServletRequest self, @CallSite.Return final Map<String, String[]> map) {
    final WebModule module = InstrumentationBridge.WEB;
    if (module != null) {
      try {
        module.onParameterValues(map);
      } catch (final Throwable e) {
        module.onUnexpectedException("afterGetParameter threw", e);
      }
    }
    return map;
  }
}

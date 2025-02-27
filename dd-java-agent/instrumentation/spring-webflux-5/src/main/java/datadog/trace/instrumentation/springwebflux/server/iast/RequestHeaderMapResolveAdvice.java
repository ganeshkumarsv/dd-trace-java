package datadog.trace.instrumentation.springwebflux.server.iast;

import datadog.trace.advice.RequiresRequestContext;
import datadog.trace.api.gateway.RequestContextSlot;
import datadog.trace.api.iast.InstrumentationBridge;
import datadog.trace.api.iast.source.WebModule;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.springframework.util.MultiValueMap;

@RequiresRequestContext(RequestContextSlot.IAST)
public class RequestHeaderMapResolveAdvice {
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void after(@Advice.Return(typing = Assigner.Typing.DYNAMIC) Map<String, ?> values) {
    WebModule module = InstrumentationBridge.WEB;
    if (module == null || values == null) {
      return;
    }

    module.onHeaderNames(values.keySet());

    if (values instanceof MultiValueMap) {
      for (Map.Entry<String, List<String>> e :
          ((MultiValueMap<String, String>) values).entrySet()) {
        for (String v : e.getValue()) {
          module.onHeaderValue(e.getKey(), v);
        }
      }
    } else {
      for (Map.Entry<String, String> e : ((Map<String, String>) values).entrySet()) {
        module.onHeaderValue(e.getKey(), e.getValue());
      }
    }
  }
}

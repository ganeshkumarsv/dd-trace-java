package datadog.trace.instrumentation.springwebflux.server.iast;

import datadog.trace.advice.ActiveRequestContext;
import datadog.trace.advice.RequiresRequestContext;
import datadog.trace.api.gateway.RequestContext;
import datadog.trace.api.gateway.RequestContextSlot;
import datadog.trace.api.iast.InstrumentationBridge;
import datadog.trace.api.iast.source.WebModule;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;

@RequiresRequestContext(RequestContextSlot.IAST)
public class HandleMatchAdvice {

  @SuppressWarnings("Duplicates")
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void after(
      @Advice.Argument(2) ServerWebExchange xchg, @ActiveRequestContext RequestContext reqCtx) {

    Object templateVars = xchg.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    Object matrixVars = xchg.getAttribute(HandlerMapping.MATRIX_VARIABLES_ATTRIBUTE);
    if (templateVars == null && matrixVars == null) {
      return;
    }

    Object iastRequestContext = reqCtx.getData(RequestContextSlot.IAST);

    WebModule module = InstrumentationBridge.WEB;
    if (module != null) {
      if (templateVars instanceof Map) {
        for (Map.Entry<String, String> e : ((Map<String, String>) templateVars).entrySet()) {
          String parameterName = e.getKey();
          String value = e.getValue();
          if (parameterName == null || value == null) {
            continue; // should not happen
          }
          module.onRequestPathParameter(parameterName, value, iastRequestContext);
        }
      }

      if (matrixVars instanceof Map) {
        for (Map.Entry<String, Map<String, Iterable<String>>> e :
            ((Map<String, Map<String, Iterable<String>>>) matrixVars).entrySet()) {
          String parameterName = e.getKey();
          Map<String, Iterable<String>> value = e.getValue();
          if (parameterName == null || value == null) {
            continue;
          }

          for (Map.Entry<String, Iterable<String>> ie : value.entrySet()) {
            String innerKey = ie.getKey();
            if (innerKey != null) {
              module.onRequestMatrixParameter(parameterName, innerKey, iastRequestContext);
            }
            Iterable<String> innerValues = ie.getValue();
            if (innerValues != null) {
              for (String iv : innerValues) {
                module.onRequestMatrixParameter(parameterName, iv, iastRequestContext);
              }
            }
          }
        }
      }
    }
  }
}

package datadog.trace.instrumentation.resteasy;

import datadog.trace.api.iast.InstrumentationBridge;
import datadog.trace.api.iast.source.WebModule;
import java.util.Collection;
import net.bytebuddy.asm.Advice;
import org.jboss.resteasy.core.FormParamInjector;

public class FormParamInjectorAdvice {
  @Advice.OnMethodExit
  public static void onExit(
      @Advice.This FormParamInjector self,
      @Advice.Return(readOnly = true) Object result,
      @Advice.FieldValue("paramName") String paramName) {
    if (result instanceof String || result instanceof Collection) {
      final WebModule module = InstrumentationBridge.WEB;
      if (module != null) {
        try {
          if (result instanceof Collection) {
            Collection collection = (Collection) result;
            for (Object o : collection) {
              if (o instanceof String) {
                module.onParameterValue(paramName, (String) o);
              }
            }
          } else {
            module.onParameterValue(paramName, (String) result);
          }
        } catch (final Throwable e) {
          module.onUnexpectedException("FormParamInjectorAdvice.onExit threw", e);
        }
      }
    }
  }
}

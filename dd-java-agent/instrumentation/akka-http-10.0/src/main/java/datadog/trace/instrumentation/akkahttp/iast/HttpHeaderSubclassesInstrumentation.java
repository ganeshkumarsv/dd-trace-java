package datadog.trace.instrumentation.akkahttp.iast;

import static datadog.trace.agent.tooling.bytebuddy.matcher.HierarchyMatchers.extendsClass;
import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.nameStartsWith;
import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import akka.http.scaladsl.model.HttpHeader;
import akka.http.scaladsl.model.HttpRequest;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.iast.InstrumentationBridge;
import datadog.trace.api.iast.Taintable;
import datadog.trace.api.iast.propagation.PropagationModule;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Propagates taint from {@link HttpHeader} to their values, when they're retrieved.
 *
 * @see MakeTaintableInstrumentation makes {@link HttpHeader} taintable
 * @see HttpRequestInstrumentation propagates taint from {@link HttpRequest} to the headers, when
 *     they're retrieved
 */
@AutoService(Instrumenter.class)
public class HttpHeaderSubclassesInstrumentation extends Instrumenter.Iast
    implements Instrumenter.ForTypeHierarchy {
  public HttpHeaderSubclassesInstrumentation() {
    super("akka-http");
  }

  @Override
  public String hierarchyMarkerType() {
    return "akka.http.scaladsl.model.HttpHeader";
  }

  @Override
  public ElementMatcher<TypeDescription> hierarchyMatcher() {
    return nameStartsWith("akka.http.scaladsl.model.")
        .and(not(named(hierarchyMarkerType())))
        .and(extendsClass(named(hierarchyMarkerType())));
  }

  @Override
  public void adviceTransformations(AdviceTransformation transformation) {
    transformation.applyAdvice(
        isMethod().and(named("value")).and(takesArguments(0)).and(returns(String.class)),
        HttpHeaderSubclassesInstrumentation.class.getName() + "$HttpHeaderSubclassesAdvice");
  }

  static class HttpHeaderSubclassesAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    static void onExit(@Advice.This HttpHeader h, @Advice.Return String retVal) {

      PropagationModule propagation = InstrumentationBridge.PROPAGATION;
      if (propagation == null || !(h instanceof Taintable)) {
        return;
      }

      propagation.taintIfInputIsTainted(retVal, h);
    }
  }
}

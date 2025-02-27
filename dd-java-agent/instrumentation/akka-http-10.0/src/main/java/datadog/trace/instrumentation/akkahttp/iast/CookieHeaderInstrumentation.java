package datadog.trace.instrumentation.akkahttp.iast;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import akka.http.javadsl.model.HttpHeader;
import akka.http.scaladsl.model.headers.Cookie;
import akka.http.scaladsl.model.headers.HttpCookiePair;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.iast.InstrumentationBridge;
import datadog.trace.api.iast.propagation.PropagationModule;
import datadog.trace.api.iast.source.WebModule;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.asm.Advice;
import scala.collection.Iterator;
import scala.collection.immutable.Seq;

/**
 * Propagates header taint when calling {@link Cookie#cookies()}.
 *
 * @see Cookie#getCookies() Java API. Is implemented by delegating to the instrumented method.
 */
@AutoService(Instrumenter.class)
public class CookieHeaderInstrumentation extends Instrumenter.Iast
    implements Instrumenter.ForSingleType {
  public CookieHeaderInstrumentation() {
    super("akka-http");
  }

  @Override
  public String instrumentedType() {
    return "akka.http.scaladsl.model.headers.Cookie";
  }

  @Override
  public void adviceTransformations(AdviceTransformation transformation) {
    transformation.applyAdvice(
        isMethod()
            .and(not(isStatic()))
            .and(named("cookies"))
            .and(returns(named("scala.collection.immutable.Seq")))
            .and(takesArguments(0)),
        CookieHeaderInstrumentation.class.getName() + "$TaintAllCookiesAdvice");
  }

  static class TaintAllCookiesAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    static void after(
        @Advice.This HttpHeader cookie, @Advice.Return Seq<HttpCookiePair> cookiePairs) {
      WebModule mod = InstrumentationBridge.WEB;
      PropagationModule prop = InstrumentationBridge.PROPAGATION;
      if (mod == null || prop == null || cookiePairs == null || cookiePairs.isEmpty()) {
        return;
      }
      if (!prop.isTainted(cookie)) {
        return;
      }

      Iterator<HttpCookiePair> iterator = cookiePairs.iterator();
      List<String> cookieNames = new ArrayList<>();
      while (iterator.hasNext()) {
        HttpCookiePair pair = iterator.next();
        cookieNames.add(pair.name());
        mod.onCookieValue(pair.name(), pair.value());
      }
      mod.onCookieNames(cookieNames);
    }
  }
}

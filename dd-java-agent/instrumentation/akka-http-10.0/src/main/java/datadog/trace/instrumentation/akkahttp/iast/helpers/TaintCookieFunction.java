package datadog.trace.instrumentation.akkahttp.iast.helpers;

import akka.http.scaladsl.model.headers.HttpCookiePair;
import datadog.trace.api.iast.InstrumentationBridge;
import datadog.trace.api.iast.source.WebModule;
import scala.Tuple1;
import scala.compat.java8.JFunction1;

public class TaintCookieFunction
    implements JFunction1<Tuple1<HttpCookiePair>, Tuple1<HttpCookiePair>> {
  public static final TaintCookieFunction INSTANCE = new TaintCookieFunction();

  @Override
  public Tuple1<HttpCookiePair> apply(Tuple1<HttpCookiePair> v1) {
    HttpCookiePair httpCookiePair = v1._1();

    WebModule mod = InstrumentationBridge.WEB;
    if (mod == null) {
      return v1;
    }

    mod.onCookieValue(httpCookiePair.name(), httpCookiePair.value());
    return v1;
  }
}

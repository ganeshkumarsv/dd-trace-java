package datadog.trace.instrumentation.jetty11;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator.DD_SPAN_ATTRIBUTE;
import static datadog.trace.instrumentation.jetty11.JettyDecorator.DECORATE;

import datadog.trace.api.CorrelationIdentifier;
import datadog.trace.api.GlobalTracer;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import net.bytebuddy.asm.Advice;
import org.eclipse.jetty.server.HttpChannel;
import org.eclipse.jetty.server.Request;

public class JettyServerAdvice {
  public static class HandleAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope onEnter(
        @Advice.This final HttpChannel channel, @Advice.Local("agentSpan") AgentSpan span) {
      Request req = channel.getRequest();

      Object existingSpan = req.getAttribute(DD_SPAN_ATTRIBUTE);
      if (existingSpan instanceof AgentSpan) {
        // Request already gone through initial processing, so just activate the span.
        ((AgentSpan) existingSpan).finishThreadMigration();
        return activateSpan((AgentSpan) existingSpan);
      }

      final AgentSpan.Context.Extracted extractedContext = DECORATE.extract(req);
      span = DECORATE.startSpan(req, extractedContext);
      span.setMeasured(true);
      DECORATE.afterStart(span);
      DECORATE.onRequest(span, req, req, extractedContext);

      final AgentScope scope = activateSpan(span);
      scope.setAsyncPropagation(true);
      req.setAttribute(DD_SPAN_ATTRIBUTE, span);
      req.setAttribute(CorrelationIdentifier.getTraceIdKey(), GlobalTracer.get().getTraceId());
      req.setAttribute(CorrelationIdentifier.getSpanIdKey(), GlobalTracer.get().getSpanId());
      // request may be processed on any thread; signal thread migration
      span.startThreadMigration();
      return scope;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void closeScope(@Advice.Enter final AgentScope scope) {
      scope.close();
    }
  }

  /**
   * Jetty ensures that connections are reset immediately after the response is sent. This provides
   * a reliable point to finish the server span at the last possible moment.
   */
  public static class ResetAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void stopSpan(@Advice.This final HttpChannel channel) {
      Request req = channel.getRequest();
      Object spanObj = req.getAttribute(DD_SPAN_ATTRIBUTE);
      if (spanObj instanceof AgentSpan) {
        final AgentSpan span = (AgentSpan) spanObj;
        DECORATE.onResponse(span, channel);
        DECORATE.beforeFinish(span);
        // span could have been originated on a different thread and migrated
        span.finishThreadMigration();
        span.finish();
      }
    }

    private void muzzleCheck(HttpChannel connection) {
      connection.run();
    }
  }
}

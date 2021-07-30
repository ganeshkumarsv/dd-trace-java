package datadog.trace.instrumentation.servlet.http;

import datadog.trace.api.function.BiFunction;
import datadog.trace.api.gateway.Flow;
import datadog.trace.api.gateway.RequestContext;
import datadog.trace.api.http.StoredBodyListener;
import datadog.trace.api.http.StoredBodySupplier;

public class IGDelegatingStoredBodyListener implements StoredBodyListener {

  private static final BiFunction<RequestContext, StoredBodySupplier, Void>
      EMPTY_FUNCTION_CALLBACK =
          new BiFunction<RequestContext, StoredBodySupplier, Void>() {
            @Override
            public Void apply(RequestContext input, StoredBodySupplier supplier) {
              return null;
            }
          };

  public static final BiFunction<RequestContext, StoredBodySupplier, Flow<Void>>
      EMPTY_BODY_RAW_CALLBACK =
          new BiFunction<RequestContext, StoredBodySupplier, Flow<Void>>() {
            @Override
            public Flow<Void> apply(RequestContext input, StoredBodySupplier supplier) {
              return Flow.ResultFlow.empty();
            }
          };

  private final BiFunction<RequestContext, StoredBodySupplier, Void> bodyStartCallback;
  private final BiFunction<RequestContext, StoredBodySupplier, Flow<Void>> bodyRawCallback;
  private final RequestContext ctx;

  public IGDelegatingStoredBodyListener(
      BiFunction<RequestContext, StoredBodySupplier, Void> bodyStartCallback,
      BiFunction<RequestContext, StoredBodySupplier, Flow<Void>> bodyRawCallback,
      RequestContext ctx) {
    if (bodyStartCallback == null) {
      bodyStartCallback = EMPTY_FUNCTION_CALLBACK;
    }

    this.bodyStartCallback = bodyStartCallback;

    if (bodyRawCallback == null) {
      bodyRawCallback = EMPTY_BODY_RAW_CALLBACK;
    }
    this.bodyRawCallback = bodyRawCallback;
    this.ctx = ctx;
  }

  @Override
  public void onBodyStart(final StoredBodySupplier storedByteBody) {
    this.bodyStartCallback.apply(ctx, storedByteBody);
  }

  @Override
  public void onBodyEnd(StoredBodySupplier bodySupplier) {
    // TODO: implement blocking action
    this.bodyRawCallback.apply(ctx, bodySupplier);
  }
}

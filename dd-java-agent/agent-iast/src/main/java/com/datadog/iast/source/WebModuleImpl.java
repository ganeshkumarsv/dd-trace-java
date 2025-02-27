package com.datadog.iast.source;

import static com.datadog.iast.taint.Tainteds.canBeTainted;

import com.datadog.iast.IastRequestContext;
import com.datadog.iast.model.Source;
import com.datadog.iast.taint.TaintedObjects;
import datadog.trace.api.iast.SourceTypes;
import datadog.trace.api.iast.source.WebModule;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class WebModuleImpl implements WebModule {

  @Override
  public void onParameterNames(@Nullable final Collection<String> paramNames) {
    onNamed(paramNames, SourceTypes.REQUEST_PARAMETER_NAME);
  }

  @Override
  public void onParameterValue(
      @Nullable final String paramName, @Nullable final String paramValue) {
    onNamed(paramName, paramValue, SourceTypes.REQUEST_PARAMETER_VALUE);
  }

  @Override
  public void onPathParameterValue(
      @Nullable final String paramName, @Nullable final String paramValue) {
    onNamed(paramName, paramValue, SourceTypes.REQUEST_PATH_PARAMETER);
  }

  @Override
  public void onParameterValues(
      @Nullable final String paramName, @Nullable final String[] paramValues) {
    onNamed(paramName, paramValues, SourceTypes.REQUEST_PARAMETER_VALUE);
  }

  @Override
  public void onParameterValues(
      @Nullable final String paramName, @Nullable final Collection<String> paramValues) {
    onNamed(paramName, paramValues, SourceTypes.REQUEST_PARAMETER_VALUE);
  }

  @Override
  public void onParameterValues(@Nullable final Map<String, String[]> values) {
    onNamed(values, SourceTypes.REQUEST_PARAMETER_VALUE);
  }

  @Override
  public void onHeaderNames(@Nullable final Collection<String> headerNames) {
    onNamed(headerNames, SourceTypes.REQUEST_HEADER_NAME);
  }

  @Override
  public void onHeaderValue(@Nullable final String headerName, @Nullable final String headerValue) {
    onNamed(headerName, headerValue, SourceTypes.REQUEST_HEADER_VALUE);
  }

  @Override
  public void onHeaderValues(
      @Nullable final String headerName, @Nullable final Collection<String> headerValues) {
    onNamed(headerName, headerValues, SourceTypes.REQUEST_HEADER_VALUE);
  }

  @Override
  public void onCookieNames(@Nullable Iterable<String> cookieNames) {
    onNamed(cookieNames, SourceTypes.REQUEST_COOKIE_NAME);
  }

  @Override
  public void onCookieValue(@Nullable final String cookieName, @Nullable final String cookieValue) {
    onNamed(cookieName, cookieValue, SourceTypes.REQUEST_COOKIE_VALUE);
  }

  @Override
  public void onQueryString(@Nullable final String queryString) {
    if (!canBeTainted(queryString)) {
      return;
    }
    final IastRequestContext ctx = IastRequestContext.get();
    if (ctx == null) {
      return;
    }
    final TaintedObjects taintedObjects = ctx.getTaintedObjects();
    taintedObjects.taintInputString(
        queryString, new Source(SourceTypes.REQUEST_QUERY, null, queryString));
  }

  private static void onNamed(
      @Nullable final String name, @Nullable final String value, final byte source) {
    if (!canBeTainted(value)) {
      return;
    }
    final IastRequestContext ctx = IastRequestContext.get();
    if (ctx == null) {
      return;
    }
    final TaintedObjects taintedObjects = ctx.getTaintedObjects();
    taintedObjects.taintInputString(value, new Source(source, name, value));
  }

  private static void onNamed(@Nullable final Iterable<String> names, final byte source) {
    if (names == null) {
      return;
    }
    Iterator<String> iterator = names.iterator();
    if (!iterator.hasNext()) {
      return;
    }

    final IastRequestContext ctx = IastRequestContext.get();
    if (ctx == null) {
      return;
    }
    final TaintedObjects taintedObjects = ctx.getTaintedObjects();
    do {
      String name = iterator.next();
      if (canBeTainted(name)) {
        taintedObjects.taintInputString(name, new Source(source, name, name));
      }
    } while (iterator.hasNext());
  }

  private static void onNamed(
      @Nullable final String name, @Nullable final Iterable<String> values, final byte source) {
    if (values == null) {
      return;
    }
    Iterator<String> iterator = values.iterator();
    if (!iterator.hasNext()) {
      return;
    }

    final IastRequestContext ctx = IastRequestContext.get();
    if (ctx == null) {
      return;
    }
    final TaintedObjects taintedObjects = ctx.getTaintedObjects();
    do {
      String value = iterator.next();
      if (canBeTainted(value)) {
        taintedObjects.taintInputString(value, new Source(source, name, value));
      }
    } while (iterator.hasNext());
  }

  private static void onNamed(
      @Nullable final String name, @Nullable final String[] values, final byte source) {
    if (values == null || values.length == 0) {
      return;
    }
    final IastRequestContext ctx = IastRequestContext.get();
    if (ctx == null) {
      return;
    }
    final TaintedObjects taintedObjects = ctx.getTaintedObjects();
    for (final String value : values) {
      if (canBeTainted(value)) {
        taintedObjects.taintInputString(value, new Source(source, name, value));
      }
    }
  }

  private static void onNamed(@Nullable final Map<String, String[]> values, final byte source) {
    if (values == null || values.isEmpty()) {
      return;
    }
    final IastRequestContext ctx = IastRequestContext.get();
    if (ctx == null) {
      return;
    }
    final TaintedObjects taintedObjects = ctx.getTaintedObjects();
    final byte nameSource = SourceTypes.namedSource(source);
    for (final Map.Entry<String, String[]> entry : values.entrySet()) {
      final String name = entry.getKey();
      if (canBeTainted(name)) {
        taintedObjects.taintInputString(name, new Source(nameSource, name, name));
      }
      for (final String value : entry.getValue()) {
        if (canBeTainted(value)) {
          taintedObjects.taintInputString(value, new Source(source, name, value));
        }
      }
    }
  }

  @Override
  public void onRequestPathParameter(
      @Nullable String paramName, @Nullable String value, @Nonnull Object ctx_) {
    if (ctx_ == null || !canBeTainted(value)) {
      return;
    }
    final IastRequestContext ctx = (IastRequestContext) ctx_;
    final TaintedObjects taintedObjects = ctx.getTaintedObjects();
    taintedObjects.taintInputString(
        value, new Source(SourceTypes.REQUEST_PATH_PARAMETER, paramName, value));
  }

  @Override
  public void onRequestMatrixParameter(
      @Nonnull String paramName, @Nullable String value, @Nonnull Object ctx_) {
    if (ctx_ == null || paramName == null || !canBeTainted(value)) {
      return;
    }
    final IastRequestContext ctx = (IastRequestContext) ctx_;
    final TaintedObjects taintedObjects = ctx.getTaintedObjects();
    taintedObjects.taintInputString(
        value, new Source(SourceTypes.REQUEST_MATRIX_PARAMETER, paramName, value));
  }

  @Override
  public void onInjectedParameter(
      @Nullable String name, @Nullable String value, @Nonnull byte sourceType) {
    onNamed(name, value, sourceType);
  }
}

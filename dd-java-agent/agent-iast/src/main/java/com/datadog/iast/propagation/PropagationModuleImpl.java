package com.datadog.iast.propagation;

import static com.datadog.iast.taint.Tainteds.canBeTainted;

import com.datadog.iast.IastRequestContext;
import com.datadog.iast.model.Range;
import com.datadog.iast.model.Source;
import com.datadog.iast.taint.TaintedObject;
import com.datadog.iast.taint.TaintedObjects;
import datadog.trace.api.iast.SourceTypes;
import datadog.trace.api.iast.Taintable;
import datadog.trace.api.iast.propagation.PropagationModule;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PropagationModuleImpl implements PropagationModule {

  @Override
  public void taintIfInputIsTainted(@Nullable final Object toTaint, @Nullable final Object input) {
    if (toTaint == null || input == null) {
      return;
    }
    final TaintedObjects taintedObjects = lazyTaintedObjects();
    final Source source = firstTaintedSource(taintedObjects, input);
    if (source != null) {
      taintObject(taintedObjects, toTaint, source);
    }
  }

  @Override
  public void taintIfInputIsTainted(@Nullable final String toTaint, @Nullable final Object input) {
    if (!canBeTainted(toTaint) || input == null) {
      return;
    }
    final TaintedObjects taintedObjects = lazyTaintedObjects();
    final Source source = firstTaintedSource(taintedObjects, input);
    if (source != null) {
      taintString(taintedObjects, toTaint, source);
    }
  }

  @Override
  public void taintIfInputIsTainted(
      final byte origin,
      @Nullable final String name,
      @Nullable final String toTaint,
      @Nullable final Object input) {
    if (!canBeTainted(toTaint) || input == null) {
      return;
    }
    final TaintedObjects taintedObjects = lazyTaintedObjects();
    if (isTainted(taintedObjects, input)) {
      taintString(taintedObjects, toTaint, new Source(origin, name, toTaint));
    }
  }

  @Override
  public void taintIfInputIsTainted(
      final byte origin,
      @Nullable final String name,
      @Nullable final Collection<String> toTaintCollection,
      @Nullable final Object input) {
    if (toTaintCollection == null || toTaintCollection.isEmpty() || input == null) {
      return;
    }
    final TaintedObjects taintedObjects = lazyTaintedObjects();
    if (isTainted(taintedObjects, input)) {
      for (final String toTaint : toTaintCollection) {
        if (canBeTainted(toTaint)) {
          taintString(taintedObjects, toTaint, new Source(origin, name, toTaint));
        }
      }
    }
  }

  @Override
  public void taintIfInputIsTainted(
      final byte origin,
      @Nullable final Collection<String> toTaintCollection,
      @Nullable final Object input) {
    if (toTaintCollection == null || toTaintCollection.isEmpty() || input == null) {
      return;
    }
    final TaintedObjects taintedObjects = lazyTaintedObjects();
    if (isTainted(taintedObjects, input)) {
      for (final String toTaint : toTaintCollection) {
        if (canBeTainted(toTaint)) {
          taintString(taintedObjects, toTaint, new Source(origin, toTaint, toTaint));
        }
      }
    }
  }

  @Override
  public void taintIfInputIsTainted(
      final byte origin,
      @Nullable final List<Map.Entry<String, String>> toTaintCollection,
      @Nullable final Object input) {
    if (toTaintCollection == null || toTaintCollection.isEmpty() || input == null) {
      return;
    }
    final TaintedObjects taintedObjects = lazyTaintedObjects();
    if (isTainted(taintedObjects, input)) {
      for (final Map.Entry<String, String> entry : toTaintCollection) {
        final String name = entry.getKey();
        if (canBeTainted(name)) {
          taintString(
              taintedObjects, name, new Source(SourceTypes.namedSource(origin), name, name));
        }
        final String toTaint = entry.getValue();
        if (canBeTainted(toTaint)) {
          taintString(taintedObjects, toTaint, new Source(origin, name, toTaint));
        }
      }
    }
  }

  @Override
  public void taintIfAnyInputIsTainted(
      @Nullable final Object toTaint, @Nullable final Object... inputs) {
    if (toTaint == null || inputs == null || inputs.length == 0) {
      return;
    }
    final TaintedObjects taintedObjects = lazyTaintedObjects();
    for (final Object input : inputs) {
      final Source source = firstTaintedSource(taintedObjects, input);
      if (source != null) {
        taintObject(taintedObjects, toTaint, source);
        return;
      }
    }
  }

  @Override
  public void taint(final byte origin, @Nullable final Object... toTaintArray) {
    if (toTaintArray == null || toTaintArray.length == 0) {
      return;
    }
    final TaintedObjects taintedObjects = lazyTaintedObjects();
    final Source source = new Source(origin, null, null);
    for (final Object toTaint : toTaintArray) {
      taintObject(taintedObjects, toTaint, source);
    }
  }

  @Override
  public boolean isTainted(@Nullable Object obj) {
    if (obj instanceof Taintable) {
      return ((Taintable) obj).$DD$isTainted();
    }

    if (obj == null) {
      return false;
    }
    final TaintedObjects taintedObjects = lazyTaintedObjects();
    return taintedObjects.get(obj) != null;
  }

  @Override
  public void taint(final byte origin, @Nullable final Collection<Object> toTaintCollection) {
    if (toTaintCollection == null || toTaintCollection.isEmpty()) {
      return;
    }
    final TaintedObjects taintedObjects = lazyTaintedObjects();
    final Source source = new Source(origin, null, null);
    for (final Object toTaint : toTaintCollection) {
      taintObject(taintedObjects, toTaint, source);
    }
  }

  @Override
  public void taint(
      @Nullable Taintable t, byte origin, @Nullable String name, @Nullable String value) {
    if (t == null) {
      return;
    }
    t.$$DD$setSource(new Source(origin, name, value));
  }

  @Override
  public Taintable.Source firstTaintedSource(@Nullable final Object input) {
    if (input == null) {
      return null;
    }
    final TaintedObjects taintedObjects = lazyTaintedObjects();
    return firstTaintedSource(taintedObjects, input);
  }

  private static void taintString(
      final TaintedObjects taintedObjects, final String toTaint, final Source source) {
    taintedObjects.taintInputString(toTaint, source);
  }

  private static void taintObject(
      final TaintedObjects taintedObjects, final Object toTaint, final Source source) {
    if (toTaint instanceof Taintable) {
      ((Taintable) toTaint).$$DD$setSource(source);
    } else {
      taintedObjects.taintInputObject(toTaint, source);
    }
  }

  private static boolean isTainted(final TaintedObjects taintedObjects, final Object object) {
    return firstTaintedSource(taintedObjects, object) != null;
  }

  private static Source firstTaintedSource(
      final TaintedObjects taintedObjects, final Object object) {
    if (object instanceof Taintable) {
      return (Source) ((Taintable) object).$$DD$getSource();
    } else {
      final TaintedObject tainted = taintedObjects.get(object);
      final Range[] ranges = tainted == null ? null : tainted.getRanges();
      return ranges != null && ranges.length > 0 ? ranges[0].getSource() : null;
    }
  }

  static TaintedObjects lazyTaintedObjects() {
    return new LazyTaintedObjects();
  }

  private static class LazyTaintedObjects implements TaintedObjects {
    private boolean fetched = false;
    private TaintedObjects taintedObjects;

    @Override
    public TaintedObject taintInputString(@Nonnull final String obj, @Nonnull final Source source) {
      final TaintedObjects to = getTaintedObjects();
      return to == null ? null : to.taintInputString(obj, source);
    }

    @Override
    public TaintedObject taintInputObject(@Nonnull final Object obj, @Nonnull final Source source) {
      final TaintedObjects to = getTaintedObjects();
      return to == null ? null : to.taintInputObject(obj, source);
    }

    @Override
    public TaintedObject taint(@Nonnull final Object obj, @Nonnull final Range[] ranges) {
      final TaintedObjects to = getTaintedObjects();
      return to == null ? null : to.taint(obj, ranges);
    }

    @Override
    public TaintedObject get(@Nonnull final Object obj) {
      final TaintedObjects to = getTaintedObjects();
      return to == null ? null : to.get(obj);
    }

    @Override
    public void release() {
      final TaintedObjects to = getTaintedObjects();
      if (to != null) {
        to.release();
      }
    }

    @Override
    public long getEstimatedSize() {
      final TaintedObjects to = getTaintedObjects();
      return to == null ? 0 : to.getEstimatedSize();
    }

    @Override
    public boolean isFlat() {
      final TaintedObjects to = getTaintedObjects();
      return to != null && to.isFlat();
    }

    private TaintedObjects getTaintedObjects() {
      if (!fetched) {
        fetched = true;
        final IastRequestContext ctx = IastRequestContext.get();
        if (ctx != null) {
          taintedObjects = ctx.getTaintedObjects();
        }
      }
      return taintedObjects;
    }
  }
}

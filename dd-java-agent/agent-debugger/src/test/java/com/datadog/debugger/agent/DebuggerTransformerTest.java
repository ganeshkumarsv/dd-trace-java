package com.datadog.debugger.agent;

import static com.datadog.debugger.util.ClassFileHelperTest.getClassFileBytes;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datadog.debugger.instrumentation.DiagnosticMessage;
import com.datadog.debugger.instrumentation.InstrumentationResult;
import com.datadog.debugger.probe.LogProbe;
import com.datadog.debugger.sink.Sink;
import com.datadog.debugger.sink.Snapshot;
import datadog.trace.api.Config;
import datadog.trace.api.GlobalTracer;
import datadog.trace.api.Tracer;
import datadog.trace.api.config.TraceInstrumentationConfig;
import datadog.trace.bootstrap.debugger.CapturedContext;
import datadog.trace.bootstrap.debugger.CorrelationAccess;
import datadog.trace.bootstrap.debugger.DebuggerContext;
import datadog.trace.bootstrap.debugger.ProbeId;
import datadog.trace.bootstrap.debugger.ProbeRateLimiter;
import freemarker.template.Template;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DebuggerTransformerTest {
  private static final String LANGUAGE = "java";
  private static final ProbeId PROBE_ID = new ProbeId("beae1807-f3b0-4ea8-a74f-826790c5e6f8", 0);
  private static final String SERVICE_NAME = "service-name";

  enum InstrumentationKind {
    ENTRY_EXIT,
    LINE
  }

  enum ExceptionKind {
    NONE,
    UNHANDLED,
    HANDLED
  }

  static class TestSnapshotListener implements Sink {
    boolean skipped;
    DebuggerContext.SkipCause cause;
    List<Snapshot> snapshots = new ArrayList<>();
    Map<String, List<DiagnosticMessage>> errors = new HashMap<>();

    @Override
    public void skipSnapshot(String probeId, DebuggerContext.SkipCause cause) {
      skipped = true;
      this.cause = cause;
    }

    @Override
    public void addSnapshot(Snapshot snapshot) {
      snapshots.add(snapshot);
    }

    @Override
    public void addDiagnostics(ProbeId probeId, List<DiagnosticMessage> messages) {
      errors.computeIfAbsent(probeId.getId(), k -> new ArrayList<>()).addAll(messages);
    }
  }

  static final String VAR_NAME = "var";
  static final String SCOPED_VAR_NAME = "scoped";
  static final String SCOPED_VAR_TYPE = "int";
  static final Object SCOPED_VAR_VALUE = 10;

  private static Instrumentation instr;
  private static Template classTemplate;

  private static Tracer noopTracer;

  private static final CapturedContext.CapturedValue[] CORRELATION_FIELDS =
      new CapturedContext.CapturedValue[2];

  @BeforeAll
  static void setupAll() throws Exception {
    // disable tracer integration
    System.setProperty("dd." + TraceInstrumentationConfig.TRACE_ENABLED, "false");

    Field fld = CorrelationAccess.class.getDeclaredField("REUSE_INSTANCE");
    fld.setAccessible(true);
    fld.set(null, false);

    // setup the tracer
    noopTracer = GlobalTracer.get();
    Tracer mockTracer = mock(Tracer.class);
    when(mockTracer.getTraceId()).thenReturn("1");
    when(mockTracer.getSpanId()).thenReturn("2");
    GlobalTracer.forceRegister(mockTracer);

    // prepare the correlation fields golden muster
    CORRELATION_FIELDS[0] =
        CapturedContext.CapturedValue.of(
            "dd.trace_id", "java.lang.String", mockTracer.getTraceId());
    CORRELATION_FIELDS[1] =
        CapturedContext.CapturedValue.of("dd.span_id", "java.lang.String", mockTracer.getSpanId());

    instr = ByteBuddyAgent.install();
    freemarker.template.Configuration cfg =
        new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_29);
    cfg.setBooleanFormat("c");
    classTemplate =
        new Template(
            "classTemplate",
            new InputStreamReader(
                DebuggerTransformerTest.class.getResourceAsStream("/TargetClass.ftlh")),
            cfg);
    // TODO asserts are operating on 'toString()' which requires keeping the underlying object so we
    // just disable serialization for now
    DebuggerContext.initValueSerializer(null);
  }

  @AfterEach
  void tearDown() {
    // disable tracer integration
    System.setProperty("dd." + TraceInstrumentationConfig.TRACE_ENABLED, "false");
    ProbeRateLimiter.resetGlobalRate();
  }

  @BeforeEach
  void setup() {
    DebuggerContext.init(null, null);
  }

  @Test
  public void testDump() {
    Config config = mock(Config.class);
    when(config.isDebuggerClassFileDumpEnabled()).thenReturn(true);
    File instrumentedClassFile = new File("/tmp/debugger/java.util.ArrayList.class");
    File origClassFile = new File("/tmp/debugger/java.util.ArrayList_orig.class");
    if (instrumentedClassFile.exists()) {
      instrumentedClassFile.delete();
    }
    if (origClassFile.exists()) {
      origClassFile.delete();
    }
    LogProbe logProbe =
        LogProbe.builder().where("java.util.ArrayList", "add").probeId("", 0).build();
    DebuggerTransformer debuggerTransformer =
        new DebuggerTransformer(
            config, new Configuration(SERVICE_NAME, Collections.singletonList(logProbe)), null);
    debuggerTransformer.transform(
        ClassLoader.getSystemClassLoader(),
        "java.util.ArrayList",
        ArrayList.class,
        null,
        getClassFileBytes(ArrayList.class));
    Assertions.assertTrue(instrumentedClassFile.exists());
    Assertions.assertTrue(origClassFile.exists());
    Assertions.assertTrue(instrumentedClassFile.delete());
    Assertions.assertTrue(origClassFile.delete());
  }

  @Test
  public void testMultiProbes() {
    doTestMultiProbes(
        Class::getName,
        new ProbeTestInfo(ArrayList.class, "add"),
        new ProbeTestInfo(HashMap.class, "<init>", "void ()"));
  }

  @Test
  public void testMultiProbesSimpleName() {
    doTestMultiProbes(
        Class::getSimpleName,
        new ProbeTestInfo(ArrayList.class, "add"),
        new ProbeTestInfo(HashMap.class, "<init>", "void ()"));
  }

  private void doTestMultiProbes(
      Function<Class<?>, String> getClassName, ProbeTestInfo... probeInfos) {
    Config config = mock(Config.class);
    List<LogProbe> logProbes = new ArrayList<>();
    for (ProbeTestInfo probeInfo : probeInfos) {
      String className = getClassName.apply(probeInfo.clazz);
      LogProbe logProbe =
          LogProbe.builder()
              .where(className, probeInfo.methodName, probeInfo.signature)
              .probeId("", 0)
              .build();
      logProbes.add(logProbe);
    }
    Configuration configuration = new Configuration(SERVICE_NAME, logProbes);
    DebuggerTransformer debuggerTransformer = new DebuggerTransformer(config, configuration);
    for (ProbeTestInfo probeInfo : probeInfos) {
      byte[] newClassBuffer =
          debuggerTransformer.transform(
              ClassLoader.getSystemClassLoader(),
              probeInfo.clazz.getName(), // always FQN
              probeInfo.clazz,
              null,
              getClassFileBytes(probeInfo.clazz));
      Assertions.assertNotNull(newClassBuffer);
    }
    byte[] newClassBuffer =
        debuggerTransformer.transform(
            ClassLoader.getSystemClassLoader(),
            "java.util.HashSet",
            HashSet.class,
            null,
            getClassFileBytes(HashSet.class));
    Assertions.assertNull(newClassBuffer);
  }

  static class ProbeTestInfo {
    final Class<?> clazz;
    final String methodName;
    final String signature;

    public ProbeTestInfo(Class<?> clazz, String methodName) {
      this(clazz, methodName, null);
    }

    public ProbeTestInfo(Class<?> clazz, String methodName, String signature) {
      this.clazz = clazz;
      this.methodName = methodName;
      this.signature = signature;
    }
  }

  @Test
  public void testBlockedProbes() {
    Config config = mock(Config.class);
    List<LogProbe> logProbes =
        Arrays.asList(
            LogProbe.builder()
                .language(LANGUAGE)
                .probeId(PROBE_ID)
                .where("java.lang.String", "toString")
                .build());
    Configuration configuration = new Configuration(SERVICE_NAME, logProbes);
    AtomicReference<InstrumentationResult> lastResult = new AtomicReference<>(null);
    DebuggerTransformer debuggerTransformer =
        new DebuggerTransformer(
            config, configuration, ((definition, result) -> lastResult.set(result)));
    byte[] newClassBuffer =
        debuggerTransformer.transform(
            ClassLoader.getSystemClassLoader(),
            "java.lang.String",
            String.class,
            null,
            getClassFileBytes(String.class));
    Assertions.assertNull(newClassBuffer);
    Assertions.assertNotNull(lastResult.get());
    Assertions.assertTrue(lastResult.get().isBlocked());
    Assertions.assertFalse(lastResult.get().isInstalled());
    Assertions.assertEquals("java.lang.String", lastResult.get().getTypeName());
  }

  @Test
  public void classBeingRedefinedNull() {
    Config config = mock(Config.class);
    LogProbe logProbe = LogProbe.builder().where("ArrayList", "add").probeId("", 0).build();
    Configuration configuration =
        new Configuration(SERVICE_NAME, Collections.singletonList(logProbe));
    AtomicReference<InstrumentationResult> lastResult = new AtomicReference<>(null);
    DebuggerTransformer debuggerTransformer =
        new DebuggerTransformer(
            config, configuration, ((definition, result) -> lastResult.set(result)));
    byte[] newClassBuffer =
        debuggerTransformer.transform(
            ClassLoader.getSystemClassLoader(),
            "java.util.ArrayList",
            null, // classBeingRedefined
            null,
            getClassFileBytes(ArrayList.class));
    Assertions.assertNotNull(newClassBuffer);
    Assertions.assertNotNull(lastResult.get());
    Assertions.assertFalse(lastResult.get().isBlocked());
    Assertions.assertTrue(lastResult.get().isInstalled());
    Assertions.assertEquals("java.util.ArrayList", lastResult.get().getTypeName());
  }
}

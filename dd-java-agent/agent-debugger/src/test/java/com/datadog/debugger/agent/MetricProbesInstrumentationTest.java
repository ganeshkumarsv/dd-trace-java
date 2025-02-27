package com.datadog.debugger.agent;

import static com.datadog.debugger.probe.MetricProbe.MetricKind.COUNT;
import static com.datadog.debugger.probe.MetricProbe.MetricKind.GAUGE;
import static com.datadog.debugger.probe.MetricProbe.MetricKind.HISTOGRAM;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static utils.InstrumentationTestHelper.compileAndLoadClass;

import com.datadog.debugger.el.DSL;
import com.datadog.debugger.el.ValueScript;
import com.datadog.debugger.instrumentation.DiagnosticMessage;
import com.datadog.debugger.probe.MetricProbe;
import com.datadog.debugger.sink.Sink;
import com.datadog.debugger.sink.Snapshot;
import datadog.trace.api.Config;
import datadog.trace.bootstrap.debugger.DebuggerContext;
import datadog.trace.bootstrap.debugger.MethodLocation;
import datadog.trace.bootstrap.debugger.ProbeId;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.joor.Reflect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MetricProbesInstrumentationTest {
  private static final String LANGUAGE = "java";
  private static final ProbeId METRIC_ID = new ProbeId("beae1807-f3b0-4ea8-a74f-826790c5e6f8", 0);
  private static final ProbeId METRIC_ID1 = new ProbeId("beae1807-f3b0-4ea8-a74f-826790c5e6f6", 0);
  private static final ProbeId METRIC_ID2 = new ProbeId("beae1807-f3b0-4ea8-a74f-826790c5e6f7", 0);
  private static final ProbeId METRIC_ID3 = new ProbeId("beae1807-f3b0-4ea8-a74f-826790c5e6f9", 0);
  private static final String SERVICE_NAME = "service-name";
  private static final String METRIC_PROBEID_TAG =
      "debugger.probeid:beae1807-f3b0-4ea8-a74f-826790c5e6f8";

  private Instrumentation instr = ByteBuddyAgent.install();
  private ClassFileTransformer currentTransformer;
  private MockSink mockSink;

  @AfterEach
  public void after() {
    if (currentTransformer != null) {
      instr.removeTransformer(currentTransformer);
    }
  }

  @Test
  public void methodIncCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot01";
    String METRIC_NAME = "call_count";
    MetricForwarderListener listener =
        installSingleMetric(METRIC_NAME, COUNT, CLASS_NAME, "main", "int (java.lang.String)", null);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertEquals(3, result);
    Assertions.assertTrue(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(1, listener.counters.get(METRIC_NAME).longValue());
    Assertions.assertArrayEquals(new String[] {METRIC_PROBEID_TAG}, listener.lastTags);
  }

  @Test
  public void methodIncCountWithTagsMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot01";
    String METRIC_NAME = "call_count";
    MetricForwarderListener listener =
        installSingleMetric(
            METRIC_NAME,
            COUNT,
            CLASS_NAME,
            "main",
            "int (java.lang.String)",
            null,
            new String[] {"tag1:foo1", "tag2:foo2"});
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertEquals(3, result);
    Assertions.assertTrue(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(1, listener.counters.get(METRIC_NAME).longValue());
    Assertions.assertArrayEquals(
        new String[] {"tag1:foo1", "tag2:foo2", METRIC_PROBEID_TAG}, listener.lastTags);
  }

  @Test
  public void methodConstantValueMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot01";
    final String METRIC_NAME = "call_count";
    MetricForwarderListener listener =
        installSingleMetric(
            METRIC_NAME,
            COUNT,
            CLASS_NAME,
            "main",
            "int (java.lang.String)",
            new ValueScript(DSL.value(42L), "42"));
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertEquals(3, result);
    Assertions.assertTrue(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(42, listener.counters.get(METRIC_NAME).longValue());
  }

  @Test
  public void invalidConstantValueMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot01";
    String METRIC_NAME = "call_count";
    MetricForwarderListener listener =
        installSingleMetric(
            METRIC_NAME,
            COUNT,
            CLASS_NAME,
            "main",
            "int (java.lang.String)",
            new ValueScript(DSL.value(42.0), "42.0"));
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertEquals(3, result);
    Assertions.assertFalse(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(
        "Unsupported constant value: 42.0 type: java.lang.Double",
        mockSink.getCurrentDiagnostics().get(0).getMessage());
  }

  @Test
  public void invalidValueMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot01";
    String METRIC_NAME = "call_count";
    MetricForwarderListener listener =
        installSingleMetric(
            METRIC_NAME,
            COUNT,
            CLASS_NAME,
            "main",
            "int (java.lang.String)",
            new ValueScript(DSL.ref("value"), "value"));
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertEquals(3, result);
    Assertions.assertFalse(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(
        "Cannot resolve symbol value", mockSink.getCurrentDiagnostics().get(0).getMessage());
  }

  @Test
  public void multiInvalidValueMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot01";
    String METRIC_NAME = "call_count";
    MetricProbe metric1 =
        MetricProbe.builder()
            .probeId(METRIC_ID1)
            .metricName(METRIC_NAME)
            .kind(COUNT)
            .where(CLASS_NAME, "main", "int (java.lang.String)")
            .valueScript(new ValueScript(DSL.ref("value"), "value"))
            .build();
    MetricProbe metric2 =
        MetricProbe.builder()
            .probeId(METRIC_ID2)
            .metricName(METRIC_NAME)
            .kind(COUNT)
            .where(CLASS_NAME, "main", "int (java.lang.String)")
            .valueScript(new ValueScript(DSL.ref("invalid"), "invalid"))
            .build();
    MetricForwarderListener listener = installMetricProbes(metric1, metric2);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertEquals(3, result);
    Assertions.assertEquals(0, listener.counters.size());
    Assertions.assertEquals(2, mockSink.getCurrentDiagnostics().size());
    Assertions.assertEquals(
        "Cannot resolve symbol value", mockSink.getCurrentDiagnostics().get(0).getMessage());
    Assertions.assertEquals(
        "Cannot resolve symbol invalid", mockSink.getCurrentDiagnostics().get(1).getMessage());
  }

  @Test
  public void methodArgumentRefValueCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot03";
    String METRIC_NAME = "argument_count";
    MetricForwarderListener listener =
        installSingleMetric(
            METRIC_NAME,
            COUNT,
            CLASS_NAME,
            "f1",
            "int (int)",
            new ValueScript(DSL.ref("value"), "value"));
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertEquals(48, result);
    Assertions.assertTrue(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(31, listener.counters.get(METRIC_NAME).longValue());
  }

  @Test
  public void methodArgumentRefValueGaugeMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot03";
    String METRIC_NAME = "argument_gauge";
    MetricForwarderListener listener =
        installSingleMetric(
            METRIC_NAME,
            GAUGE,
            CLASS_NAME,
            "f1",
            "int (int)",
            new ValueScript(DSL.ref("value"), "value"));
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertEquals(48, result);
    Assertions.assertTrue(listener.gauges.containsKey(METRIC_NAME));
    Assertions.assertEquals(31, listener.gauges.get(METRIC_NAME).longValue());
    Assertions.assertArrayEquals(new String[] {METRIC_PROBEID_TAG}, listener.lastTags);
  }

  @Test
  public void methodArgumentRefValueGaugeMetricWithTags() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot03";
    String METRIC_NAME = "argument_gauge";
    MetricForwarderListener listener =
        installSingleMetric(
            METRIC_NAME,
            GAUGE,
            CLASS_NAME,
            "f1",
            "int (int)",
            new ValueScript(DSL.ref("value"), "value"),
            new String[] {"tag1:foo1"});
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertArrayEquals(new String[] {"tag1:foo1", METRIC_PROBEID_TAG}, listener.lastTags);
  }

  @Test
  public void methodArgumentRefValueHistogramMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot03";
    String METRIC_NAME = "argument_gauge";
    MetricForwarderListener listener =
        installSingleMetric(
            METRIC_NAME,
            HISTOGRAM,
            CLASS_NAME,
            "f1",
            "int (int)",
            new ValueScript(DSL.ref("value"), "value"));
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertEquals(48, result);
    Assertions.assertTrue(listener.histrograms.containsKey(METRIC_NAME));
    Assertions.assertEquals(31, listener.histrograms.get(METRIC_NAME).longValue());
    Assertions.assertArrayEquals(new String[] {METRIC_PROBEID_TAG}, listener.lastTags);
  }

  @Test
  public void methodArgumentRefValueHistogramMetricWithTags()
      throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot03";
    String METRIC_NAME = "argument_gauge";
    MetricForwarderListener listener =
        installSingleMetric(
            METRIC_NAME,
            HISTOGRAM,
            CLASS_NAME,
            "f1",
            "int (int)",
            new ValueScript(DSL.ref("value"), "value"),
            new String[] {"tag1:foo1"});
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertEquals(48, result);
    Assertions.assertTrue(listener.histrograms.containsKey(METRIC_NAME));
    Assertions.assertEquals(31, listener.histrograms.get(METRIC_NAME).longValue());
    Assertions.assertArrayEquals(new String[] {"tag1:foo1", METRIC_PROBEID_TAG}, listener.lastTags);
  }

  @Test
  public void lineArgumentRefValueCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot03";
    String METRIC_NAME = "argument_count";
    MetricProbe metricProbe =
        createMetric(
            METRIC_ID,
            METRIC_NAME,
            COUNT,
            CLASS_NAME,
            new ValueScript(DSL.ref("value"), "value"),
            4);
    MetricForwarderListener listener = installMetricProbes(metricProbe);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertEquals(48, result);
    Assertions.assertTrue(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(31, listener.counters.get(METRIC_NAME).longValue());
  }

  @Test
  public void invalidNameArgumentRefValueCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot03";
    String METRIC_NAME = "argument_count";
    MetricForwarderListener listener =
        installSingleMetric(
            METRIC_NAME,
            COUNT,
            CLASS_NAME,
            "f1",
            "int (int)",
            new ValueScript(DSL.ref("foo"), "foo"));
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertEquals(48, result);
    Assertions.assertFalse(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(
        "Cannot resolve symbol foo", mockSink.getCurrentDiagnostics().get(0).getMessage());
  }

  @Test
  public void invalidTypeArgumentRefValueCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot03";
    String METRIC_NAME = "argument_count";
    MetricForwarderListener listener =
        installSingleMetric(
            METRIC_NAME,
            COUNT,
            CLASS_NAME,
            "main",
            "int (java.lang.String)",
            new ValueScript(DSL.ref("arg"), "arg"));
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertEquals(48, result);
    Assertions.assertFalse(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(
        "Incompatible type for expression: java.lang.String with expected type: long",
        mockSink.getCurrentDiagnostics().get(0).getMessage());
  }

  @Test
  public void lineLocalRefValueCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot01";
    String METRIC_NAME = "localvar_count";
    MetricProbe metricProbe =
        createMetric(
            METRIC_ID, METRIC_NAME, COUNT, CLASS_NAME, new ValueScript(DSL.ref("var1"), "var1"), 9);
    MetricForwarderListener listener = installMetricProbes(metricProbe);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertEquals(3, result);
    Assertions.assertTrue(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(3, listener.counters.get(METRIC_NAME).longValue());
  }

  @Test
  public void invalidNameLocalRefValueCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot01";
    String METRIC_NAME = "localvar_count";
    MetricProbe metricProbe =
        createMetric(
            METRIC_ID, METRIC_NAME, COUNT, CLASS_NAME, new ValueScript(DSL.ref("foo"), "foo"), 9);
    MetricForwarderListener listener = installMetricProbes(metricProbe);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertEquals(3, result);
    Assertions.assertFalse(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(
        "Cannot resolve symbol foo", mockSink.getCurrentDiagnostics().get(0).getMessage());
  }

  @Test
  public void invalidTypeLocalRefValueCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot01";
    String METRIC_NAME = "localvar_count";
    MetricProbe metricProbe =
        createMetric(
            METRIC_ID, METRIC_NAME, COUNT, CLASS_NAME, new ValueScript(DSL.ref("arg"), "arg"), 9);
    MetricForwarderListener listener = installMetricProbes(metricProbe);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertEquals(3, result);
    Assertions.assertFalse(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(
        "Incompatible type for expression: java.lang.String with expected type: long",
        mockSink.getCurrentDiagnostics().get(0).getMessage());
  }

  @Test
  public void methodFieldRefValueCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot06";
    String METRIC_NAME = "field_count";
    MetricForwarderListener listener =
        installSingleMetric(
            METRIC_NAME,
            COUNT,
            CLASS_NAME,
            "f",
            "()",
            new ValueScript(DSL.ref("intValue"), "intValue"));

    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "f").get();
    Assertions.assertEquals(42, result);
    Assertions.assertTrue(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(24, listener.counters.get(METRIC_NAME).longValue());
  }

  @Test
  public void methodThisFieldRefValueCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot06";
    String METRIC_NAME = "field_count";
    MetricForwarderListener listener =
        installSingleMetric(
            METRIC_NAME,
            COUNT,
            CLASS_NAME,
            "f",
            "()",
            new ValueScript(DSL.getMember(DSL.ref("this"), "intValue"), "this.intValue"));

    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "f").get();
    Assertions.assertEquals(42, result);
    Assertions.assertTrue(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(24, listener.counters.get(METRIC_NAME).longValue());
  }

  @Test
  public void lineFieldRefValueCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot06";
    String METRIC_NAME = "field_count";
    MetricProbe metricProbe =
        createMetric(
            METRIC_ID,
            METRIC_NAME,
            COUNT,
            CLASS_NAME,
            new ValueScript(DSL.getMember(DSL.ref("this"), "intValue"), "intValue"),
            24);
    MetricForwarderListener listener = installMetricProbes(metricProbe);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "f").get();
    Assertions.assertEquals(42, result);
    Assertions.assertTrue(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(48, listener.counters.get(METRIC_NAME).longValue());
  }

  @Test
  public void lineMultiFieldRefValueCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot04";
    String METRIC_NAME1 = "field1_count";
    String METRIC_NAME2 = "field2_count";
    MetricProbe metricProbe1 =
        createMetric(
            METRIC_ID1,
            METRIC_NAME1,
            COUNT,
            CLASS_NAME,
            new ValueScript(DSL.getMember(DSL.ref("sdata"), "intValue"), "sdata.intValue"),
            24);
    MetricProbe metricProbe2 =
        createMetric(
            METRIC_ID2,
            METRIC_NAME2,
            COUNT,
            CLASS_NAME,
            new ValueScript(
                DSL.getMember(DSL.getMember(DSL.ref("cdata"), "s1"), "intValue"),
                "cdata.s1.intValue"),
            24);
    MetricForwarderListener listener = installMetricProbes(metricProbe1, metricProbe2);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "").get();
    Assertions.assertEquals(143, result);
    Assertions.assertTrue(listener.counters.containsKey(METRIC_NAME1));
    Assertions.assertTrue(listener.counters.containsKey(METRIC_NAME2));
    Assertions.assertEquals(42, listener.counters.get(METRIC_NAME1).longValue());
    Assertions.assertEquals(101, listener.counters.get(METRIC_NAME2).longValue());
  }

  @Test
  public void nullMultiFieldRefValueCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot04";
    String METRIC_NAME1 = "field1_count";
    String METRIC_NAME2 = "field2_count";
    MetricProbe metricProbe1 =
        createMetric(
            METRIC_ID1,
            METRIC_NAME1,
            COUNT,
            CLASS_NAME,
            new ValueScript(
                DSL.getMember(DSL.ref("nullObject"), "intValue"), "nullObject.intValue"),
            25);
    MetricProbe metricProbe2 =
        createMetric(
            METRIC_ID2,
            METRIC_NAME2,
            COUNT,
            CLASS_NAME,
            new ValueScript(
                DSL.getMember(DSL.getMember(DSL.ref("cdata"), "nullsd"), "intValue"),
                "cdata.nullsd.intValue"),
            25);
    MetricForwarderListener listener = installMetricProbes(metricProbe1, metricProbe2);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "").get();
    Assertions.assertEquals(143, result);
    Assertions.assertFalse(listener.counters.containsKey(METRIC_NAME1));
    Assertions.assertFalse(listener.counters.containsKey(METRIC_NAME2));
    Assertions.assertTrue(mockSink.getCurrentDiagnostics().isEmpty());
  }

  @Test
  public void invalidNameMultiFieldRefValueCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot04";
    String METRIC_NAME1 = "field1_count";
    String METRIC_NAME2 = "field2_count";
    MetricProbe metricProbe1 =
        createMetric(
            METRIC_ID1,
            METRIC_NAME1,
            COUNT,
            CLASS_NAME,
            new ValueScript(DSL.getMember(DSL.ref("sdata"), "foovalue"), "sdata.foovalue"),
            24);
    MetricProbe metricProbe2 =
        createMetric(
            METRIC_ID2,
            METRIC_NAME2,
            COUNT,
            CLASS_NAME,
            new ValueScript(
                DSL.getMember(DSL.getMember(DSL.ref("cdata"), "s1"), "foovalue"),
                "cdata.s1.foovalue"),
            24);
    MetricForwarderListener listener = installMetricProbes(metricProbe1, metricProbe2);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "").get();
    Assertions.assertEquals(143, result);
    Assertions.assertFalse(listener.counters.containsKey(METRIC_NAME1));
    Assertions.assertFalse(listener.counters.containsKey(METRIC_NAME2));
    Assertions.assertEquals(
        "Cannot resolve field foovalue", mockSink.getCurrentDiagnostics().get(0).getMessage());
    Assertions.assertEquals(
        "Cannot resolve field foovalue", mockSink.getCurrentDiagnostics().get(1).getMessage());
  }

  @Test
  public void invalidTypeMultiFieldRefValueCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot04";
    String METRIC_NAME1 = "field1_count";
    String METRIC_NAME2 = "field2_count";
    MetricProbe metricProbe1 =
        createMetric(
            METRIC_ID1,
            METRIC_NAME1,
            COUNT,
            CLASS_NAME,
            new ValueScript(DSL.getMember(DSL.ref("sdata"), "strValue"), "sdata.strValue"),
            24);
    MetricProbe metricProbe2 =
        createMetric(
            METRIC_ID2,
            METRIC_NAME2,
            COUNT,
            CLASS_NAME,
            new ValueScript(
                DSL.getMember(DSL.getMember(DSL.ref("cdata"), "s1"), "strValue"),
                "cdata.s1.strValue"),
            24);
    MetricForwarderListener listener = installMetricProbes(metricProbe1, metricProbe2);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "").get();
    Assertions.assertEquals(143, result);
    Assertions.assertFalse(listener.counters.containsKey(METRIC_NAME1));
    Assertions.assertFalse(listener.counters.containsKey(METRIC_NAME2));
    Assertions.assertEquals(
        "Incompatible type for expression: java.lang.String with expected type: long",
        mockSink.getCurrentDiagnostics().get(0).getMessage());
    Assertions.assertEquals(
        "Incompatible type for expression: java.lang.String with expected type: long",
        mockSink.getCurrentDiagnostics().get(1).getMessage());
  }

  @Test
  public void invalidNameFieldRefValueCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot06";
    String METRIC_NAME = "field_count";
    MetricForwarderListener listener =
        installSingleMetric(
            METRIC_NAME,
            COUNT,
            CLASS_NAME,
            "f",
            "()",
            new ValueScript(DSL.ref("fooValue"), "fooValue"));

    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "f").get();
    Assertions.assertEquals(42, result);
    Assertions.assertFalse(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(
        "Cannot resolve symbol fooValue", mockSink.getCurrentDiagnostics().get(0).getMessage());
  }

  @Test
  public void invalidTypeFieldRefValueCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot06";
    String METRIC_NAME = "field_count";
    MetricForwarderListener listener =
        installSingleMetric(
            METRIC_NAME,
            COUNT,
            CLASS_NAME,
            "f",
            "()",
            new ValueScript(DSL.ref("strValue"), "strValue"));
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "f").get();
    Assertions.assertEquals(42, result);
    Assertions.assertFalse(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(
        "Incompatible type for expression: java.lang.String with expected type: long",
        mockSink.getCurrentDiagnostics().get(0).getMessage());
  }

  @Test
  public void metricThrows() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot06";
    ValueScript valueScript =
        new ValueScript(DSL.getMember(DSL.ref("this"), "intValue"), "intValue");
    MetricProbe countProbe =
        createMetric(METRIC_ID1, "field_count", COUNT, CLASS_NAME, "f", "()", valueScript, null);
    MetricProbe gaugeProbe =
        createMetric(METRIC_ID2, "field_gauge", GAUGE, CLASS_NAME, "f", "()", valueScript, null);
    MetricProbe histogramProbe =
        createMetric(
            METRIC_ID3, "field_histo", HISTOGRAM, CLASS_NAME, "f", "()", valueScript, null);
    MetricForwarderListener listener = installMetricProbes(countProbe, gaugeProbe, histogramProbe);
    listener.setThrowing(true);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "f").get();
    Assertions.assertEquals(42, result);
  }

  @Test
  public void singleLineIncCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot01";
    String METRIC_NAME = "call_count";
    MetricForwarderListener listener =
        installSingleMetric(
            METRIC_NAME, COUNT, CLASS_NAME, "main", "int (java.lang.String)", null, null, "8");
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertEquals(3, result);
    Assertions.assertTrue(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(1, listener.counters.get(METRIC_NAME).longValue());
  }

  @Test
  public void multiLineIncCountMetric() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot01";
    String METRIC_NAME = "call_count";
    MetricForwarderListener listener =
        installSingleMetric(
            METRIC_NAME, COUNT, CLASS_NAME, "main", "int (java.lang.String)", null, null, "4-8");
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "1").get();
    Assertions.assertEquals(3, result);
    Assertions.assertTrue(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(2, listener.counters.get(METRIC_NAME).longValue());
    result = Reflect.on(testClass).call("main", "2").get();
    Assertions.assertEquals(3, listener.counters.get(METRIC_NAME).longValue());
  }

  @Test
  public void evaluateAtEntry() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot06";
    String METRIC_NAME = "field_count";
    MetricProbe metricProbe =
        createMetricBuilder(METRIC_ID, METRIC_NAME, COUNT)
            .where(CLASS_NAME, "f", "()")
            .valueScript(new ValueScript(DSL.ref("intValue"), "intValue"))
            .evaluateAt(MethodLocation.ENTRY)
            .build();
    MetricForwarderListener listener = installMetricProbes(metricProbe);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "f").get();
    Assertions.assertEquals(42, result);
    Assertions.assertTrue(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(24, listener.counters.get(METRIC_NAME).longValue());
  }

  @Test
  public void evaluateAtExit() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot06";
    String METRIC_NAME = "field_count";
    MetricProbe metricProbe =
        createMetricBuilder(METRIC_ID, METRIC_NAME, COUNT)
            .where(CLASS_NAME, "f", "()")
            .valueScript(new ValueScript(DSL.ref("intValue"), "intValue"))
            .evaluateAt(MethodLocation.EXIT)
            .build();
    MetricForwarderListener listener = installMetricProbes(metricProbe);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "f").get();
    Assertions.assertEquals(42, result);
    Assertions.assertTrue(listener.counters.containsKey(METRIC_NAME));
    Assertions.assertEquals(48, listener.counters.get(METRIC_NAME).longValue());
  }

  @Test
  public void lenExpression() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot06";
    String STRLEN_METRIC = "strlen";
    String LISTSIZE_METRIC = "listsize";
    String MAPSIZE_METRIC = "mapsize";
    MetricProbe metricProbe1 =
        createMetricBuilder(METRIC_ID, STRLEN_METRIC, GAUGE)
            .where(CLASS_NAME, "f", "()")
            .valueScript(new ValueScript(DSL.len(DSL.ref("strValue")), "len(strValue)"))
            .evaluateAt(MethodLocation.EXIT)
            .build();
    MetricProbe metricProbe2 =
        createMetricBuilder(METRIC_ID, LISTSIZE_METRIC, GAUGE)
            .where(CLASS_NAME, "f", "()")
            .valueScript(new ValueScript(DSL.len(DSL.ref("strList")), "len(strList)"))
            .evaluateAt(MethodLocation.EXIT)
            .build();
    MetricProbe metricProbe3 =
        createMetricBuilder(METRIC_ID, MAPSIZE_METRIC, GAUGE)
            .where(CLASS_NAME, "f", "()")
            .valueScript(new ValueScript(DSL.len(DSL.ref("strMap")), "len(strMap)"))
            .evaluateAt(MethodLocation.EXIT)
            .build();
    MetricForwarderListener listener =
        installMetricProbes(metricProbe1, metricProbe2, metricProbe3);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "f").get();
    Assertions.assertEquals(42, result);
    Assertions.assertTrue(listener.gauges.containsKey(STRLEN_METRIC));
    Assertions.assertEquals(
        4, listener.gauges.get(STRLEN_METRIC).longValue()); // <=> "done".length()
    Assertions.assertTrue(listener.gauges.containsKey(LISTSIZE_METRIC));
    Assertions.assertEquals(
        3, listener.gauges.get(LISTSIZE_METRIC).longValue()); // <=> strList.size()
    Assertions.assertTrue(listener.gauges.containsKey(MAPSIZE_METRIC));
    Assertions.assertEquals(
        1, listener.gauges.get(MAPSIZE_METRIC).longValue()); // <=> strMap.size()
  }

  @Test
  public void nullExpression() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot06";
    String NULLLEN_METRIC = "nulllen";
    String MAPNULL_METRIC = "mapnull";
    String NULL_METRIC = "null";
    MetricProbe metricProbe1 =
        createMetricBuilder(METRIC_ID1, NULLLEN_METRIC, GAUGE)
            .where(CLASS_NAME, "f", "()")
            .valueScript(new ValueScript(DSL.len(DSL.nullValue()), "len(null)"))
            .evaluateAt(MethodLocation.EXIT)
            .build();
    MetricProbe metricProbe2 =
        createMetricBuilder(METRIC_ID2, MAPNULL_METRIC, GAUGE)
            .where(CLASS_NAME, "f", "()")
            .valueScript(
                new ValueScript(DSL.index(DSL.ref("strMap"), DSL.nullValue()), "strMap[null]"))
            .evaluateAt(MethodLocation.EXIT)
            .build();
    MetricProbe metricProbe3 =
        createMetricBuilder(METRIC_ID3, NULL_METRIC, GAUGE)
            .where(CLASS_NAME, "f", "()")
            .valueScript(new ValueScript(DSL.nullValue(), "null"))
            .evaluateAt(MethodLocation.EXIT)
            .build();
    MetricForwarderListener listener =
        installMetricProbes(metricProbe1, metricProbe2, metricProbe3);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "f").get();
    Assertions.assertEquals(42, result);
    Assertions.assertEquals(0, listener.gauges.size());
    Assertions.assertEquals(3, mockSink.getCurrentDiagnostics().size());
    Assertions.assertEquals(
        "Unsupported type for len operation: java.lang.Object",
        mockSink.getCurrentDiagnostics().get(0).getMessage());
    Assertions.assertEquals(
        "Incompatible type for expression: java.lang.String with expected type: long",
        mockSink.getCurrentDiagnostics().get(1).getMessage());
    Assertions.assertEquals(
        "Incompatible type for expression: java.lang.Object with expected type: long",
        mockSink.getCurrentDiagnostics().get(2).getMessage());
  }

  @Test
  public void indexExpression() throws IOException, URISyntaxException {
    final String CLASS_NAME = "com.datadog.debugger.CapturedSnapshot22";
    String ARRAYIDX_METRIC = "arrayindex";
    String LISTIDX_METRIC = "listindex";
    String MAPIDX_METRIC = "mapindex";
    MetricProbe metricProbe1 =
        createMetricBuilder(METRIC_ID, ARRAYIDX_METRIC, GAUGE)
            .where(CLASS_NAME, "doit", "(String)")
            .valueScript(
                new ValueScript(DSL.index(DSL.ref("intArray"), DSL.value(4)), "intArray[4]"))
            .evaluateAt(MethodLocation.EXIT)
            .build();
    MetricProbe metricProbe2 =
        createMetricBuilder(METRIC_ID, LISTIDX_METRIC, GAUGE)
            .where(CLASS_NAME, "doit", "(String)")
            .valueScript(
                new ValueScript(
                    DSL.len(DSL.index(DSL.ref("strList"), DSL.value(1))), "len(strList[1])"))
            .evaluateAt(MethodLocation.EXIT)
            .build();
    MetricProbe metricProbe3 =
        createMetricBuilder(METRIC_ID, MAPIDX_METRIC, GAUGE)
            .where(CLASS_NAME, "doit", "(String)")
            .valueScript(
                new ValueScript(
                    DSL.len(DSL.index(DSL.ref("strMap"), DSL.value("foo1"))),
                    "len(strMap['foo1'])"))
            .evaluateAt(MethodLocation.EXIT)
            .build();
    MetricForwarderListener listener =
        installMetricProbes(metricProbe1, metricProbe2, metricProbe3);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "f").get();
    Assertions.assertEquals(42, result);
    Assertions.assertTrue(listener.gauges.containsKey(ARRAYIDX_METRIC));
    Assertions.assertEquals(4, listener.gauges.get(ARRAYIDX_METRIC).longValue());
    Assertions.assertTrue(listener.gauges.containsKey(LISTIDX_METRIC));
    Assertions.assertEquals(3, listener.gauges.get(LISTIDX_METRIC).longValue());
    Assertions.assertTrue(listener.gauges.containsKey(MAPIDX_METRIC));
    Assertions.assertEquals(4, listener.gauges.get(MAPIDX_METRIC).longValue());
  }

  @Test
  public void indexInvalidKeyTypeExpression() throws IOException, URISyntaxException {
    final String CLASS_NAME = "com.datadog.debugger.CapturedSnapshot22";
    String ARRAYSTRIDX_METRIC = "arrayStrIndex";
    String ARRAYOOBIDX_METRIC = "arrayOutOfBoundsIndex";
    MetricProbe metricProbe1 =
        createMetricBuilder(METRIC_ID, ARRAYSTRIDX_METRIC, GAUGE)
            .where(CLASS_NAME, "doit", "(String)")
            .valueScript(
                new ValueScript(
                    DSL.index(DSL.ref("intArray"), DSL.value("foo")), "intArray['foo']"))
            .evaluateAt(MethodLocation.EXIT)
            .build();
    MetricProbe metricProbe2 =
        createMetricBuilder(METRIC_ID, ARRAYOOBIDX_METRIC, GAUGE)
            .where(CLASS_NAME, "doit", "(String)")
            // generates ArrayOutOfBoundsException
            .valueScript(
                new ValueScript(DSL.index(DSL.ref("intArray"), DSL.value(42)), "intArray[42]"))
            .evaluateAt(MethodLocation.EXIT)
            .build();
    MetricForwarderListener listener = installMetricProbes(metricProbe1, metricProbe2);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "f").get();
    Assertions.assertEquals(42, result);
    Assertions.assertFalse(listener.gauges.containsKey(ARRAYSTRIDX_METRIC));
    Assertions.assertFalse(listener.gauges.containsKey(ARRAYOOBIDX_METRIC));
    Assertions.assertEquals(
        "Incompatible type for key: Type{mainType=Ljava/lang/String;, genericTypes=[]}, expected int or long",
        mockSink.getCurrentDiagnostics().get(0).getMessage());
  }

  @Test
  public void privateFieldValue() throws IOException, URISyntaxException {
    final String CLASS_NAME = "CapturedSnapshot04";
    final String STRVALUE_METRIC = "strvalue_count";
    final String LONGVALUE_METRIC = "longvalue_count";
    final String INTVALUE_METRIC = "intvalue_count";
    MetricProbe metricProbe1 =
        createMetricBuilder(METRIC_ID1, STRVALUE_METRIC, GAUGE)
            .where(CLASS_NAME, 24)
            .valueScript(
                new ValueScript(
                    DSL.len(DSL.getMember(DSL.ref("sdata"), "strValue")), "len(sdata.strValue)"))
            .build();
    MetricProbe metricProbe2 =
        createMetricBuilder(METRIC_ID2, LONGVALUE_METRIC, GAUGE)
            .where(CLASS_NAME, 24)
            .valueScript(
                new ValueScript(
                    DSL.getMember(DSL.getMember(DSL.ref("cdata"), "s2"), "longValue"),
                    "cdata.s2.longValue"))
            .build();
    MetricProbe metricProbe3 =
        createMetricBuilder(METRIC_ID3, INTVALUE_METRIC, GAUGE)
            .where(CLASS_NAME, 24)
            .valueScript(
                new ValueScript(
                    DSL.getMember(DSL.getMember(DSL.ref("cdata"), "s2"), "intValue"),
                    "cdata.s2.intValue"))
            .build();
    MetricForwarderListener listener =
        installMetricProbes(metricProbe1, metricProbe2, metricProbe3);
    Class<?> testClass = compileAndLoadClass(CLASS_NAME);
    int result = Reflect.on(testClass).call("main", "f").get();
    Assertions.assertEquals(143, result);
    Assertions.assertEquals(3, listener.gauges.get(STRVALUE_METRIC));
    Assertions.assertEquals(1042, listener.gauges.get(LONGVALUE_METRIC));
    Assertions.assertEquals(202, listener.gauges.get(INTVALUE_METRIC));
  }

  private MetricForwarderListener installSingleMetric(
      String metricName,
      MetricProbe.MetricKind metricKind,
      String typeName,
      String methodName,
      String signature,
      ValueScript valueScript) {
    MetricProbe metricProbe =
        createMetric(
            METRIC_ID, metricName, metricKind, typeName, methodName, signature, valueScript, null);
    return installMetricProbes(metricProbe);
  }

  private MetricForwarderListener installSingleMetric(
      String metricName,
      MetricProbe.MetricKind metricKind,
      String typeName,
      String methodName,
      String signature,
      ValueScript valueScript,
      String[] tags,
      String... lines) {
    MetricProbe metricProbe =
        createMetric(
            METRIC_ID,
            metricName,
            metricKind,
            typeName,
            methodName,
            signature,
            valueScript,
            tags,
            lines);
    return installMetricProbes(metricProbe);
  }

  private static MetricProbe createMetric(
      ProbeId id,
      String metricName,
      MetricProbe.MetricKind metricKind,
      String typeName,
      String methodName,
      String signature,
      ValueScript valueScript,
      String[] tags,
      String... lines) {
    return createMetricBuilder(id, metricName, metricKind)
        .where(typeName, methodName, signature, lines)
        .valueScript(valueScript)
        .tags(tags)
        .build();
  }

  private static MetricProbe createMetric(
      ProbeId id,
      String metricName,
      MetricProbe.MetricKind metricKind,
      String sourceFile,
      ValueScript valueScript,
      int line) {
    return createMetricBuilder(id, metricName, metricKind)
        .where(sourceFile, line)
        .valueScript(valueScript)
        .build();
  }

  private static MetricProbe.Builder createMetricBuilder(
      ProbeId id, String metricName, MetricProbe.MetricKind metricKind) {
    return MetricProbe.builder()
        .language(LANGUAGE)
        .probeId(id)
        .metricName(metricName)
        .kind(metricKind);
  }

  private MetricForwarderListener installMetricProbes(Configuration configuration) {
    Config config = mock(Config.class);
    when(config.isDebuggerEnabled()).thenReturn(true);
    when(config.isDebuggerClassFileDumpEnabled()).thenReturn(true);
    currentTransformer = new DebuggerTransformer(config, configuration);
    instr.addTransformer(currentTransformer);
    MetricForwarderListener listener = new MetricForwarderListener();
    mockSink = new MockSink();
    DebuggerAgentHelper.injectSink(mockSink);
    DebuggerContext.init(null, listener);
    DebuggerContext.initClassFilter(new DenyListHelper(null));
    return listener;
  }

  private MetricForwarderListener installMetricProbes(MetricProbe... metricProbes) {
    return installMetricProbes(
        Configuration.builder()
            .setService(SERVICE_NAME)
            .addMetricProbes(Arrays.asList(metricProbes))
            .build());
  }

  private static class MetricForwarderListener implements DebuggerContext.MetricForwarder {
    Map<String, Long> counters = new HashMap<>();
    Map<String, Long> gauges = new HashMap<>();
    Map<String, Long> histrograms = new HashMap<>();
    String[] lastTags = null;
    boolean throwing;

    @Override
    public void count(String name, long delta, String[] tags) {
      if (throwing) {
        throw new IllegalArgumentException("oops");
      }
      counters.compute(name, (key, value) -> value != null ? value + delta : delta);
      lastTags = tags;
    }

    @Override
    public void gauge(String name, long value, String[] tags) {
      if (throwing) {
        throw new IllegalArgumentException("oops");
      }
      gauges.put(name, value);
      lastTags = tags;
    }

    @Override
    public void histogram(String name, long value, String[] tags) {
      if (throwing) {
        throw new IllegalArgumentException("oops");
      }
      histrograms.put(name, value);
      lastTags = tags;
    }

    public void setThrowing(boolean value) {
      this.throwing = value;
    }
  }

  private static class MockSink implements Sink {

    private final List<DiagnosticMessage> currentDiagnostics = new ArrayList<>();

    @Override
    public void addSnapshot(Snapshot snapshot) {}

    @Override
    public void skipSnapshot(String probeId, DebuggerContext.SkipCause cause) {}

    @Override
    public void addDiagnostics(ProbeId probeId, List<DiagnosticMessage> messages) {
      for (DiagnosticMessage msg : messages) {
        System.out.println(msg);
      }
      currentDiagnostics.addAll(messages);
    }

    public List<DiagnosticMessage> getCurrentDiagnostics() {
      return currentDiagnostics;
    }
  }
}

package datadog.trace.agent.tooling.csi

import datadog.trace.agent.tooling.bytebuddy.csi.Advices
import spock.lang.Requires

@Requires({
  jvm.java9Compatible
})
class AdvicesInvokeDynamicTest extends BaseCallSiteTest {

  void 'test constant pool introspector with invoke dynamic'() {
    setup:
    final clazz = loadClass(StringPlusExample)
    final advice = mockInvokeDynamicAdvice(pointcutMock)
    final advices = Advices.fromCallSites(advice)
    final introspector = Advices.AdviceIntrospector.ConstantPoolInstrospector.INSTANCE

    when:
    final result = introspector.findAdvices(advices, clazz)
    final found = result.findAdvice(pointcutMock) != null

    then:
    result.empty == emptyAdvices
    found == adviceFound

    where:
    pointcutMock                  | emptyAdvices | adviceFound
    stringConcatPointcut()        | true         | false
    stringConcatFactoryPointcut() | false        | true
  }

  void 'test constant pool introspector with invoke dynamic and constants'() {
    setup:
    final clazz = loadClass(StringPlusConstantsExample)
    final advice = mockInvokeDynamicAdvice(pointcutMock)
    final advices = Advices.fromCallSites(advice)
    final introspector = Advices.AdviceIntrospector.ConstantPoolInstrospector.INSTANCE

    when:
    final result = introspector.findAdvices(advices, clazz)
    final found = result.findAdvice(pointcutMock) != null

    then:
    result.empty == emptyAdvices
    found == adviceFound

    where:
    pointcutMock                  | emptyAdvices | adviceFound
    stringConcatPointcut()        | true         | false
    stringConcatFactoryPointcut() | false        | true
  }

  private static byte[] loadClass(final Class<?> clazz) {
    return clazz.getResourceAsStream("${clazz.simpleName}.class").bytes
  }
}

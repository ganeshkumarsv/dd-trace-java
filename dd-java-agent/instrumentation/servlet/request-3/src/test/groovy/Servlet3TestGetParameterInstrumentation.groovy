import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.iast.InstrumentationBridge
import datadog.trace.api.iast.source.WebModule
import foo.bar.smoketest.Servlet3TestSuite
import groovy.transform.CompileDynamic
import spock.lang.Ignore

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletRequestWrapper

@Ignore("https://github.com/DataDog/dd-trace-java/pull/5213")
@CompileDynamic
class Servlet3TestGetParameterInstrumentation extends AgentTestRunner {

  @Override
  protected void configurePreAgent() {
    injectSysConfig("dd.iast.enabled", "true")
  }

  void cleanup() {
    InstrumentationBridge.clearIastModules()
  }

  void 'test getParameter'() {
    setup:
    final iastModule = Mock(WebModule)
    InstrumentationBridge.registerIastModule(iastModule)
    final map = [param1: ['value1', 'value2'] as String[]]
    final servletRequest = Mock(HttpServletRequest) {
      getParameterMap() >> map
    }
    final wrapper = new HttpServletRequestWrapper(servletRequest)
    final testSuite = new Servlet3TestSuite(wrapper)

    when:
    final returnedMap = testSuite.getParameterMap()

    then:
    returnedMap == map
    1 * iastModule.onParameterValues(map)
  }
}

import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.iast.InstrumentationBridge
import datadog.trace.api.iast.sink.UnvalidatedRedirectModule
import datadog.trace.api.iast.source.WebModule
import foo.bar.smoketest.JakartaHttpServletRequestTestSuite
import foo.bar.smoketest.JakartaHttpServletRequestWrapperTestSuite
import foo.bar.smoketest.JakartaServletRequestTestSuite
import foo.bar.smoketest.JakartaServletRequestWrapperTestSuite
import groovy.transform.CompileDynamic

@CompileDynamic
class JakartaServletRequestCallSiteTest extends AgentTestRunner {

  @Override
  protected void configurePreAgent() {
    injectSysConfig("dd.iast.enabled", "true")
  }

  void 'test getParameter map'() {
    setup:
    final iastModule = Mock(WebModule)
    InstrumentationBridge.registerIastModule(iastModule)
    final servletRequest = Mock(clazz)
    testSuite.init(servletRequest)
    final map = [param1: ['value1', 'value2'] as String[]]
    servletRequest.getParameterMap() >> map

    when:
    final returnedMap = testSuite.getParameterMap()

    then:
    returnedMap == map
    1 * iastModule.onParameterValues(map)

    where:
    testSuite                                       | clazz
    new JakartaServletRequestTestSuite()            | jakarta.servlet.ServletRequest
    new JakartaHttpServletRequestTestSuite()        | jakarta.servlet.http.HttpServletRequest
    new JakartaServletRequestWrapperTestSuite()     | jakarta.servlet.ServletRequestWrapper
    new JakartaHttpServletRequestWrapperTestSuite() | jakarta.servlet.http.HttpServletRequestWrapper
  }

  void 'test getParameterValues and getParameterNames'() {
    setup:
    final iastModule = Mock(WebModule)
    InstrumentationBridge.registerIastModule(iastModule)
    final map = [param1: ['value1', 'value2'] as String[]]
    final servletRequest = Mock(clazz) {
      getParameter(_ as String) >> { map.get(it[0]).first() }
      getParameterValues(_ as String) >> { map.get(it[0]) }
      getParameterNames() >> { Collections.enumeration(map.keySet()) }
    }

    testSuite.init(servletRequest)

    when:
    testSuite.getParameter('param1')

    then:
    1 * iastModule.onParameterValue('param1', 'value1')

    when:
    testSuite.getParameterValues('param1')

    then:
    1 * iastModule.onParameterValues('param1', ['value1', 'value2'])

    when:
    testSuite.getParameterNames()

    then:
    1 * iastModule.onParameterNames(['param1'])

    where:
    testSuite                                       | clazz
    new JakartaServletRequestTestSuite()            | jakarta.servlet.ServletRequest
    new JakartaHttpServletRequestTestSuite()        | jakarta.servlet.http.HttpServletRequest
    new JakartaServletRequestWrapperTestSuite()     | jakarta.servlet.ServletRequestWrapper
    new JakartaHttpServletRequestWrapperTestSuite() | jakarta.servlet.http.HttpServletRequestWrapper
  }

  void 'test getRequestDispatcher'() {
    setup:
    final iastModule = Mock(UnvalidatedRedirectModule)
    InstrumentationBridge.registerIastModule(iastModule)
    final servletRequest = Mock(clazz)
    final path = 'http://dummy.location.com'
    testSuite.init(servletRequest)

    when:
    testSuite.getRequestDispatcher(path)

    then:
    1 * servletRequest.getRequestDispatcher(path)
    1 * iastModule.onRedirect(path)

    where:
    testSuite                                       | clazz
    new JakartaServletRequestTestSuite()            | jakarta.servlet.ServletRequest
    new JakartaHttpServletRequestTestSuite()        | jakarta.servlet.http.HttpServletRequest
    new JakartaServletRequestWrapperTestSuite()     | jakarta.servlet.ServletRequestWrapper
    new JakartaHttpServletRequestWrapperTestSuite() | jakarta.servlet.http.HttpServletRequestWrapper
  }
}

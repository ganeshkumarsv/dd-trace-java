package server


import datadog.trace.api.iast.InstrumentationBridge
import datadog.trace.api.iast.sink.InsecureCookieModule
import okhttp3.Request

class Vertx39InsecureCookieTest extends IastVertx39Server {


  void 'test insecure Cookie'(){
    given:
    final module = Mock(InsecureCookieModule)
    InstrumentationBridge.registerIastModule(module)
    final url = "${address}/iast/vulnerabilities/insecureCookie?name=user-id&value=7"
    final request = new Request.Builder().url(url).build()

    when:
    final response = client.newCall(request).execute()

    then:
    response.code() == 200
    response.body().string() == 'Cookie Set'
    1 * module.onCookie('user-id', '7', false, false, null)
  }

  void 'test secure Cookie'(){
    given:
    final module = Mock(InsecureCookieModule)
    InstrumentationBridge.registerIastModule(module)
    final url = "${address}/iast/vulnerabilities/insecureCookie?name=user-id&value=7&secure=true"
    final request = new Request.Builder().url(url).build()

    when:
    final response = client.newCall(request).execute()

    then:
    response.code() == 200
    response.body().string() == 'Cookie Set'
    1 * module.onCookie('user-id', '7', true, false, null)
  }
}

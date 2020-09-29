package datadog.smoketest

import okhttp3.Request
import spock.lang.Shared
import spock.lang.Timeout

abstract class PlaySmokeTest extends AbstractServerSmokeTest {

  @Shared
  File playDirectory = new File("${buildDirectory}/stage/playBinary")

  @Override
  ProcessBuilder createProcessBuilder() {
    ProcessBuilder processBuilder =
      new ProcessBuilder("${playDirectory}/bin/playBinary")
    processBuilder.directory(playDirectory)
    processBuilder.environment().put("JAVA_OPTS",
      defaultJavaProperties.join(" ")
        + " -Dconfig.file=${workingDirectory}/conf/application.conf -Dhttp.port=${httpPort}"
        + " -Dhttp.address=127.0.0.1"
        + " -Dplay.server.provider=${serverProvider()}"
        + " -Ddd.writer.type=TraceStructureWriter:${output.getAbsolutePath()}")
    return processBuilder
  }

  @Override
  File createTemporaryFile() {
    File.createTempFile("trace-structure-play-${serverProviderName()}", "out")
  }

  abstract String serverProviderName()

  abstract String serverProvider()

  def "welcome endpoint #n th time"() {
    setup:
    String url = "http://localhost:$httpPort/welcome?id=$n"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()

    then:
    def responseBodyStr = response.body().string()
    responseBodyStr == "Welcome $n."
    response.code() == 200

    where:
    n << (1..200)
  }
}

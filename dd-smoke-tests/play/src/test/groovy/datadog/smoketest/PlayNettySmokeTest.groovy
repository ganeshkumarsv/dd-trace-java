package datadog.smoketest

class PlayNettySmokeTest extends PlaySmokeTest {
  @Override
  String serverProviderName() {
    return "netty"
  }

  @Override
  String serverProvider() {
    return "play.core.server.NettyServerProvider"
  }

  @Override
  Set<String> expectedTraces() {
    return ["[netty.request[play.request]]"].toSet()
  }
}

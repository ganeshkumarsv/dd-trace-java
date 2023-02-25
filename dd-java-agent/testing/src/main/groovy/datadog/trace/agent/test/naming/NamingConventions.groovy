package datadog.trace.agent.test.naming

class TestingGenericHttpNamingConventions {
  trait ClientV0 implements VersionedNamingTest {
    @Override
    int version() {
      return 0
    }

    @Override
    String service() {
      return null
    }

    @Override
    String operation() {
      return "http.request"
    }
  }
  trait ClientV1 implements VersionedNamingTest {
    @Override
    int version() {
      return 1
    }

    @Override
    String service() {
      return null
    }

    @Override
    String operation() {
      return "http.client.request"
    }
  }
}

class TestingNettyHttpNamingConventions {
  trait ClientV0 implements VersionedNamingTest {
    @Override
    int version() {
      return 0
    }

    @Override
    String service() {
      return null
    }

    @Override
    String operation() {
      return "netty.client.request"
    }
  }

  trait ClientV1 extends TestingGenericHttpNamingConventions.ClientV1 {
  }
}

package datadog.trace.bootstrap.instrumentation.jms

import datadog.trace.bootstrap.instrumentation.api.AgentSpan
import datadog.trace.test.util.DDSpecification

class SessionStateTest extends DDSpecification {

  def "commit transaction"() {
    setup:
    SessionState sessionState = new SessionState(0)
    def span1 = Mock(AgentSpan)
    def span2 = Mock(AgentSpan)
    when:
    sessionState.finishOnCommit(span1)
    sessionState.finishOnCommit(span2)
    then:
    0 * span1.finish()
    0 * span2.finish()
    sessionState.capturedSpanCount == 2
    when: "transaction committed"
    sessionState.onCommit()
    then: "spans finished and queues empty"
    1 * span1.finish()
    1 * span2.finish()
    sessionState.capturedSpanCount == 0
  }

  def "when buffer overflows, spans are finished eagerly"() {
    setup:
    SessionState sessionState = new SessionState(0)
    AgentSpan span1 = Mock(AgentSpan)
    AgentSpan span2 = Mock(AgentSpan)
    when: "fill the buffer"
    for (int i = 0; i < SessionState.MAX_CAPTURED_SPANS; ++i) {
      sessionState.finishOnCommit(span1)
    }
    then: "spans are not finished on entry"
    0 * span1.finish()
    sessionState.capturedSpanCount == SessionState.MAX_CAPTURED_SPANS
    when: "buffer overflows"
    sessionState.finishOnCommit(span2)
    then: "span is finished on entry"
    1 * span2.finish()
    sessionState.capturedSpanCount == SessionState.MAX_CAPTURED_SPANS
    when: "commit and add span"
    sessionState.onCommit()
    sessionState.finishOnCommit(span2)
    then: "span is enqueued and not finished"
    0 * span2.finish()
    sessionState.capturedSpanCount == 1
  }
}

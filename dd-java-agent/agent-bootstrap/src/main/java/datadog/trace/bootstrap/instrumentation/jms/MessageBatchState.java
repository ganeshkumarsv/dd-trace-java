package datadog.trace.bootstrap.instrumentation.jms;

import datadog.trace.api.Config;
import datadog.trace.api.IdGenerationStrategy;

public final class MessageBatchState {
  public static final String JMS_BATCH_ID_KEY = "x_datadog_jms_batch_id";

  private static final IdGenerationStrategy ID_STRATEGY = Config.get().getIdGenerationStrategy();

  final long batchId;
  final long startMillis;
  final int commitSequence;

  MessageBatchState(int commitSequence) {
    this.batchId = ID_STRATEGY.generate().toLong();
    this.startMillis = System.currentTimeMillis();
    this.commitSequence = commitSequence;
  }

  public long getBatchId() {
    return batchId;
  }

  public long getStartMillis() {
    return startMillis;
  }
}

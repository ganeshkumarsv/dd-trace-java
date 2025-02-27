package datadog.trace;

import datadog.trace.core.Metadata;
import datadog.trace.core.MetadataConsumer;

public class SamplingPriorityMetadataChecker extends MetadataConsumer {
  public volatile boolean hasSamplingPriority;

  @Override
  public void accept(Metadata metadata) {
    this.hasSamplingPriority = metadata.hasSamplingPriority();
  }
}

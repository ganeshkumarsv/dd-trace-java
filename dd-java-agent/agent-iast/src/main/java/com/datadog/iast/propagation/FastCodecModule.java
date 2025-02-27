package com.datadog.iast.propagation;

import static com.datadog.iast.taint.Ranges.highestPriorityRange;

import com.datadog.iast.model.Range;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class FastCodecModule extends BaseCodecModule {

  @Override
  protected Range[] urlDecodeRanges(
      @Nonnull final String value,
      @Nullable final String encoding,
      @Nonnull final String result,
      @Nonnull final Range[] ranges) {
    final Range range = highestPriorityRange(ranges);
    return new Range[] {new Range(0, result.length(), range.getSource())};
  }

  @Override
  protected Range[] fromBytesRanges(
      @Nonnull final byte[] value,
      @Nullable final String charset,
      @Nonnull final String result,
      @Nonnull final Range[] ranges) {
    final Range range = highestPriorityRange(ranges);
    return new Range[] {new Range(0, result.length(), range.getSource())};
  }

  @Override
  protected Range[] getBytesRanges(
      @Nonnull final String value,
      @Nullable final String charset,
      @Nonnull final byte[] result,
      @Nonnull final Range[] ranges) {
    final Range range = highestPriorityRange(ranges);
    return new Range[] {new Range(0, result.length, range.getSource())};
  }

  @Override
  protected Range[] decodeBase64Ranges(
      @Nonnull final byte[] value, @Nonnull final byte[] result, @Nonnull final Range[] ranges) {
    final Range range = highestPriorityRange(ranges);
    return new Range[] {new Range(0, result.length, range.getSource())};
  }

  @Override
  protected Range[] encodeBase64Ranges(
      @Nonnull final byte[] value, @Nonnull final byte[] result, @Nonnull final Range[] ranges) {
    final Range range = highestPriorityRange(ranges);
    return new Range[] {new Range(0, result.length, range.getSource())};
  }
}

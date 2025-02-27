package datadog.trace.api.civisibility.source;

import java.lang.reflect.Method;
import javax.annotation.Nonnull;

public interface MethodLinesResolver {

  @Nonnull
  MethodLines getLines(@Nonnull Method method);

  final class MethodLines {
    public static final MethodLines EMPTY = new MethodLines(Integer.MAX_VALUE, Integer.MIN_VALUE);

    private final int startLineNumber;
    private final int finishLineNumber;

    public MethodLines(int startLineNumber, int finishLineNumber) {
      this.startLineNumber = startLineNumber;
      this.finishLineNumber = finishLineNumber;
    }

    public int getStartLineNumber() {
      return startLineNumber;
    }

    public int getFinishLineNumber() {
      return finishLineNumber;
    }

    public boolean isValid() {
      return startLineNumber <= finishLineNumber;
    }
  }
}

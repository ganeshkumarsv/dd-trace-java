package datadog.smoketest.profiling;

import datadog.trace.api.Trace;
import datadog.trace.api.experimental.Profiling;
import datadog.trace.api.experimental.ProfilingContextSetter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ProfilingTestApplication {
  private static final ThreadMXBean THREAD_MX_BEAN = ManagementFactory.getThreadMXBean();

  private static final ExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadExecutor();

  public static void main(final String[] args) throws InterruptedException, ExecutionException {
    ProfilingContextSetter foo = Profiling.get().createContextSetter("foo");
    long duration = -1;
    if (args.length > 0) {
      duration = TimeUnit.SECONDS.toMillis(Long.parseLong(args[0]));
    }
    setupDeadlock();
    submitWorkToTPE();
    final long startTime = System.currentTimeMillis();
    int counter = 0;
    while (true) {
      foo.set("context" + counter % 10);
      tracedMethod();
      if (duration > 0 && duration + startTime < System.currentTimeMillis()) {
        break;
      }
      counter++;
    }
    System.out.println("Exiting (" + duration + ")");
  }

  @Trace
  @SuppressFBWarnings("DM_GC")
  private static void tracedMethod() throws InterruptedException {
    System.out.println("Tracing");
    tracedBusyMethod();
    // request GC which will in turn trigger OldObjectSample events
    System.gc();
    try {
      throw new IllegalStateException("test");
    } catch (final IllegalStateException ignored) {
    }
    Thread.sleep(50);
  }

  @Trace
  @SuppressFBWarnings("DMI_RANDOM_USED_ONLY_ONCE")
  private static void tracedBusyMethod() {
    long startTime = THREAD_MX_BEAN.getCurrentThreadCpuTime();
    Random random = new Random();
    long accumulator = 0L;
    while (true) {
      accumulator += random.nextInt(113);
      if (THREAD_MX_BEAN.getCurrentThreadCpuTime() - startTime > 10_000_000L) {
        // looking for at least 10ms CPU time
        break;
      }
    }
    System.out.println("accumulated: " + accumulator);
  }

  @Trace
  private static void submitWorkToTPE() throws ExecutionException, InterruptedException {
    AtomicInteger it = new AtomicInteger();
    for (int i = 0; i < 100; i++) {
      EXECUTOR_SERVICE
          .submit(
              () -> {
                try {
                  Thread.sleep(10);
                  it.incrementAndGet();
                } catch (InterruptedException e) {
                }
              })
          .get();
    }
    List<Callable<Integer>> runnables =
        IntStream.range(0, 100)
            .mapToObj(
                i ->
                    (Callable<Integer>)
                        () -> {
                          try {
                            Thread.sleep(10);
                          } catch (InterruptedException e) {
                          }
                          return it.getAndIncrement();
                        })
            .collect(Collectors.toList());
    for (Future f : EXECUTOR_SERVICE.invokeAll(runnables)) {
      f.get();
    }
    System.out.println("incremented: " + it.get());
  }

  private static void setupDeadlock() {
    final Phaser phaser = new Phaser(3);
    final Object lockA = new Object();
    final Object lockB = new Object();

    final Thread threadA =
        new Thread(
            () -> {
              synchronized (lockA) {
                phaser.arriveAndAwaitAdvance(); // sync such as cross-order locking is provoked
                synchronized (lockB) {
                  phaser.arriveAndDeregister(); // virtually unreachable
                }
              }
            },
            "monitor-thread-A");
    final Thread threadB =
        new Thread(
            () -> {
              synchronized (lockB) {
                phaser.arriveAndAwaitAdvance(); // sync such as cross-order locking is provoked
                synchronized (lockA) {
                  phaser.arriveAndDeregister(); // virtually unreachable
                }
              }
            },
            "monitor-thread-B");
    threadA.setDaemon(true);
    threadB.setDaemon(true);

    final CountDownLatch latch = new CountDownLatch(1);
    Thread main =
        new Thread(
            () -> {
              threadA.start();
              threadB.start();
              phaser.arriveAndAwaitAdvance(); // enter deadlock
              phaser.arriveAndAwaitAdvance(); // unreachable if deadlock is present
              latch.countDown();
            },
            "main-monitor-thread");
    main.setDaemon(true);

    main.start();
  }
}

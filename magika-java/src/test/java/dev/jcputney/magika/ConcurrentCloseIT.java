/*
 * Copyright 2026 Jonathan Putney
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.jcputney.magika;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Concurrent-close safety. Replaces the previous "calling close() mid-flight is undefined
 * behavior, may JVM-crash" disclaimer with a deterministic in-flight gate:
 *
 * <ul>
 * <li>{@code close()} flips a volatile flag, then drains an {@link AtomicInteger} gate before
 * disposing the native ORT session.
 * <li>Late {@code identify*} callers observe {@code closed=true} after their increment and throw
 * {@link IllegalStateException} ("Magika has been closed") cleanly.
 * <li>In-flight {@code identify*} callers complete normally; {@code close()} waits for them to
 * decrement the gate before touching the session.
 * </ul>
 *
 * <p>This test replaces the comment in {@link MagikaApiIT} that previously declared "no
 * concurrent-close test." Tagged {@code parity} so Failsafe runs it.
 */
@Tag("parity")
class ConcurrentCloseIT {

  @Test
  void close_during_concurrent_identify_does_not_crash_and_late_callers_throw_ISE()
    throws Exception {
    int workers = Math.max(2, Runtime.getRuntime().availableProcessors());
    Magika m = Magika.create();
    // Prime the engine so first-use lazy init is out of the way and the race targets close()
    // vs in-flight identify, not first-use.
    m.identifyBytes(new byte[] {'a'});

    AtomicInteger okCount = new AtomicInteger();
    AtomicInteger closedCount = new AtomicInteger();
    AtomicInteger unexpected = new AtomicInteger();
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);

    ExecutorService pool = Executors.newFixedThreadPool(workers);
    List<Future<?>> futures = new ArrayList<>();
    try {
      byte[] payload = new byte[32 * 1024];
      for (int i = 0; i < workers; i++) {
        futures.add(pool.submit(() -> {
          ready.countDown();
          start.await();
          // Loop until close() forces an IllegalStateException. Each iteration is a complete
          // identify* call; if close() races mid-call, the in-flight gate guarantees the
          // current call completes normally before close() disposes the session.
          while (true) {
            try {
              m.identifyBytes(payload);
              okCount.incrementAndGet();
            } catch (IllegalStateException e) {
              if ("Magika has been closed".equals(e.getMessage())) {
                closedCount.incrementAndGet();
                return null;
              }
              unexpected.incrementAndGet();
              throw e;
            } catch (Throwable t) {
              unexpected.incrementAndGet();
              throw t;
            }
          }
        }));
      }

      ready.await(10, TimeUnit.SECONDS);
      start.countDown();
      // Let workers run for a bit so close() actually races mid-flight, not just post-fanout.
      Thread.sleep(50);
      m.close(); // drain gate, dispose session

      for (Future<?> f : futures) {
        f.get(30, TimeUnit.SECONDS);
      }
    } finally {
      pool.shutdownNow();
      pool.awaitTermination(10, TimeUnit.SECONDS);
    }

    assertThat(unexpected.get())
      .as("No JVM-level errors or unexpected exceptions allowed across %d workers", workers)
      .isZero();
    assertThat(closedCount.get())
      .as("Every worker must terminate by observing IllegalStateException after close()")
      .isEqualTo(workers);
    assertThat(okCount.get())
      .as("At least some identify* calls should have completed before close() raced in")
      .isGreaterThan(0);
  }

  @Test
  void post_close_identifyStream_throws_ISE_with_exact_message() {
    Magika m = Magika.create();
    m.close();
    try {
      m.identifyStream(new java.io.ByteArrayInputStream(new byte[] {'a'}));
      throw new AssertionError("expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).isEqualTo("Magika has been closed");
    }
  }
}

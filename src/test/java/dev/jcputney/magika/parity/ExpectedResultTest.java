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

package dev.jcputney.magika.parity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jcputney.magika.postprocess.OverwriteReason;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * IN-03 unit tests: the fixture-aware {@code overwriteReasonEnum(Path)} overload surfaces the
 * fixture path in its {@link AssertionError} when a sidecar carries a value that doesn't map to
 * {@link OverwriteReason}.
 */
@Tag("unit")
class ExpectedResultTest {

  @Test
  void overwriteReasonEnum_unknown_value_surfaces_fixture_in_error() {
    // IN-03: a sidecar carrying overwriteReason="XYZ_BOGUS" must fail with an AssertionError
    // naming the fixture, not a bare IllegalArgumentException.
    ExpectedResult.ExpectedPrediction pred = new ExpectedResult.ExpectedPrediction(
      "txt", 0.99, "XYZ_BOGUS");
    Path fakeFixture = Path.of("fixtures/edge/test.bin");

    assertThatThrownBy(() -> pred.overwriteReasonEnum(fakeFixture))
      .isInstanceOf(AssertionError.class)
      .hasMessageContaining("XYZ_BOGUS")
      .hasMessageContaining("test.bin");
  }

  @Test
  void overwriteReasonEnum_null_returns_NONE() {
    ExpectedResult.ExpectedPrediction pred = new ExpectedResult.ExpectedPrediction(
      "txt", 0.99, null);
    assertThat(pred.overwriteReasonEnum()).isEqualTo(OverwriteReason.NONE);
  }
}

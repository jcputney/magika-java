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

package dev.jcputney.magika._meta;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Plan 05-03 (D-09c + P-05) post-publish verification for v0.3.0.
 *
 * <p><b>Layer (d) of the Phase 5 4-layer validation architecture</b> (see 05-VALIDATION.md +
 * 05-RESEARCH.md §Validation Architecture). Confirms the artifact is actually queryable from
 * {@code repo1.maven.org} after Central propagation (~30 min per CONTEXT.md D-09c — note: this
 * latency is documented but not empirically confirmed for fresh-namespace deployments; see
 * 05-03 SUMMARY for the open question on propagation latency).
 *
 * <p><b>GUARDED by the {@code magika.verify.central} system property (P-05):</b> default
 * {@code mvn verify} runs (CI, dev machines, fresh clones) SKIP this test cleanly with NO
 * network access. Activate explicitly via:
 * <pre>
 * mvn -B -ntp -Dmagika.verify.central=true -Dtest='_meta.CentralPublishVerificationTest' verify
 * </pre>
 *
 * <p>Operator runs this AFTER waiting for Central → repo1.maven.org propagation (typically
 * ~30 min; check {@code https://status.maven.org} for current publish-latency metrics).
 *
 * <p><b>NO retries, NO timeout configuration:</b> JDK default HttpClient is used as-is.
 * Adding retry logic would mask the propagation-latency signal we want to capture in the
 * 05-03 SUMMARY (D-09d — record actual published-artifact SHA-256s + Central deployment ID
 * + propagation latency for the audit trail).
 */
@Tag("unit")
class CentralPublishVerificationTest {

  private static final String POM_URL =
      "https://repo1.maven.org/maven2/dev/jcputney/magika-java/0.3.0/magika-java-0.3.0.pom";

  @Test
  void published_pom_is_reachable() throws IOException, InterruptedException {
    // P-05: system-property guard MUST be the first executable line — skip-guard fires
    // BEFORE any work, including network access. Default builds skip cleanly.
    Assumptions.assumeTrue(
        Boolean.getBoolean("magika.verify.central"),
        "Central post-publish verification disabled by default; "
            + "set -Dmagika.verify.central=true to activate (run manually after Central propagation).");

    HttpClient client = HttpClient.newHttpClient();
    HttpRequest req = HttpRequest.newBuilder(URI.create(POM_URL)).GET().build();
    HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());

    assertThat(resp.statusCode())
        .as("pom must be reachable at " + POM_URL)
        .isEqualTo(200);
    assertThat(resp.body())
        .as("returned pom must declare correct coordinates")
        .contains("<groupId>dev.jcputney</groupId>")
        .contains("<artifactId>magika-java</artifactId>")
        .contains("<version>0.3.0</version>");
  }
}

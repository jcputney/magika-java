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

package consumer;

import dev.jcputney.magika.DetectedContentType;
import dev.jcputney.magika.Magika;
import dev.jcputney.magika.MagikaResult;
import dev.jcputney.magika.Status;

/**
 * REF-03 positive consumer IT — verifies (a) compile-against-module-path with
 * {@code requires dev.jcputney.magika;}, (b) Magika / MagikaResult / Status / DetectedContentType
 * are reachable from the public API package, (c) runtime exercise of identifyBytes works (also
 * verifies Pitfall 5 — {@code requires com.microsoft.onnxruntime} in the magika module is correct
 * because identifyBytes will trigger ORT load).
 */
public class Main {

  public static void main(String[] args) {
    try (Magika m = Magika.create()) {
      MagikaResult r = m.identifyBytes(new byte[] {(byte) 'A'});
      Status s = r.status();
      System.out.println("status=" + s);
      // Sanity: success path always produces Status.OK per A-02.
      if (s != Status.OK) {
        throw new IllegalStateException("Expected Status.OK on success, got: " + s);
      }
      DetectedContentType detected = m.detectBytes(new byte[] {(byte) 'A'});
      if (detected.status() != Status.OK) {
        throw new IllegalStateException("Expected detectBytes Status.OK, got: " + detected.status());
      }
    }
  }
}

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

package dev.jcputney.magika.perf;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

final class FixtureLoader {

  private FixtureLoader() {
  }

  static byte[] loadBytes(String relativePath) throws IOException {
    try (InputStream in =
      FixtureLoader.class.getClassLoader().getResourceAsStream("fixtures/" + relativePath)) {
      if (in == null) {
        throw new IllegalStateException("fixture missing on test classpath: " + relativePath);
      }
      return in.readAllBytes();
    }
  }

  static Path resolvePath(String relativePath) {
    URL url = FixtureLoader.class.getClassLoader().getResource("fixtures/" + relativePath);
    if (url == null) {
      throw new IllegalStateException("fixture missing on test classpath: " + relativePath);
    }
    try {
      return Path.of(url.toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("fixture URI invalid: " + relativePath, e);
    }
  }
}

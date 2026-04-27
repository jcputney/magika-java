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

// REF-03 negative IT: this import MUST fail to compile.
// dev.jcputney.magika.inference is NOT exported by module-info.java — it's an internal
// package. javac on the module path will fail with "package dev.jcputney.magika.inference
// is not visible" or "module consumer does not read a module that exports
// dev.jcputney.magika.inference".
import dev.jcputney.magika.inference.InferenceEngine;

public class Main {

  public static void main(String[] args) {
    System.out.println(InferenceEngine.class);
  }
}

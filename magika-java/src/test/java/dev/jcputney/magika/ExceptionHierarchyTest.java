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

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ExceptionHierarchyTest {

  @Test
  void base_is_abstract_and_extends_RuntimeException() {
    assertThat(Modifier.isAbstract(MagikaException.class.getModifiers())).isTrue();
    assertThat(RuntimeException.class.isAssignableFrom(MagikaException.class)).isTrue();
  }

  @Test
  void all_three_subtypes_are_final() {
    assertThat(Modifier.isFinal(ModelLoadException.class.getModifiers())).isTrue();
    assertThat(Modifier.isFinal(InferenceException.class.getModifiers())).isTrue();
    assertThat(Modifier.isFinal(InvalidInputException.class.getModifiers())).isTrue();
  }

  @Test
  void all_three_subtypes_extend_base() {
    assertThat(MagikaException.class.isAssignableFrom(ModelLoadException.class)).isTrue();
    assertThat(MagikaException.class.isAssignableFrom(InferenceException.class)).isTrue();
    assertThat(MagikaException.class.isAssignableFrom(InvalidInputException.class)).isTrue();
  }

  @Test
  void ctors_accept_message_and_cause() {
    Throwable cause = new RuntimeException("root");
    assertThat(new ModelLoadException("msg").getMessage()).isEqualTo("msg");
    assertThat(new ModelLoadException("msg", cause).getCause()).isSameAs(cause);
    assertThat(new InferenceException("msg").getMessage()).isEqualTo("msg");
    assertThat(new InferenceException("msg", cause).getCause()).isSameAs(cause);
    assertThat(new InvalidInputException("msg").getMessage()).isEqualTo("msg");
    assertThat(new InvalidInputException("msg", cause).getCause()).isSameAs(cause);
  }
}

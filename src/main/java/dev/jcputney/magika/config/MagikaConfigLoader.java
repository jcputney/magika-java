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

package dev.jcputney.magika.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import dev.jcputney.magika.ModelLoadException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Jackson-only seam for loading the bundled {@code config.min.json} and
 * {@code content_types_kb.min.json} resources (CFG-04, CFG-05). This is the ONLY class in the
 * library that imports {@code com.fasterxml.jackson.*} — the ArchUnit
 * {@code PackageBoundaryTest.jacksonConfinedToConfig} rule fails the build on any violation.
 *
 * <p><strong>CFG-05 {@code ObjectMapper} configuration:</strong> both
 * {@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES} (false — forward-compat on additive
 * upstream changes) and {@link DeserializationFeature#FAIL_ON_NULL_FOR_PRIMITIVES} (true —
 * catches silent corruption) are load-bearing. PITFALLS.md §Pitfall 9 explains the rationale;
 * the paired {@link dev.jcputney.magika.config.MagikaConfigLoader} test locks the contract.
 */
public final class MagikaConfigLoader {

  /** Classpath path to the bundled {@code standard_v3_3} {@code config.min.json} (D-01). */
  public static final String BUNDLED_CONFIG_RESOURCE =
    "/dev/jcputney/magika/models/standard_v3_3/config.min.json";

  /** Classpath path to the bundled {@code standard_v3_3} content-types registry (D-01). */
  public static final String BUNDLED_REGISTRY_RESOURCE =
    "/dev/jcputney/magika/models/standard_v3_3/content_types_kb.json";

  private static final ObjectMapper MAPPER = JsonMapper.builder()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
    .build();

  private MagikaConfigLoader() {
    // utility class
  }

  /** Load the bundled {@code config.min.json} from the classpath. */
  public static ThresholdConfig loadBundled() {
    return loadThresholdConfig(BUNDLED_CONFIG_RESOURCE);
  }

  /** Load the bundled content-types registry from the classpath. */
  public static ContentTypeRegistry loadBundledRegistry() {
    return loadRegistry(BUNDLED_REGISTRY_RESOURCE);
  }

  /** Load a {@link ThresholdConfig} from the given absolute classpath resource. */
  public static ThresholdConfig loadThresholdConfig(String resourcePath) {
    try (InputStream in = MagikaConfigLoader.class.getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new ModelLoadException("bundled config missing from classpath: " + resourcePath);
      }
      return MAPPER.readValue(in, ThresholdConfig.class);
    } catch (MismatchedInputException e) {
      throw new ModelLoadException(
        "failed to parse config JSON at "
          + resourcePath
          + " (field="
          + fieldPath(e)
          + "): "
          + e.getOriginalMessage(),
        e);
    } catch (IOException e) {
      throw new ModelLoadException("failed to read config JSON at " + resourcePath, e);
    }
  }

  /** Load a {@link ContentTypeRegistry} from the given absolute classpath resource. */
  public static ContentTypeRegistry loadRegistry(String resourcePath) {
    try (InputStream in = MagikaConfigLoader.class.getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new ModelLoadException(
          "bundled content-types registry missing from classpath: " + resourcePath);
      }
      List<ContentTypeInfo> entries =
        MAPPER.readValue(in, new TypeReference<List<ContentTypeInfo>>() {
        });
      return ContentTypeRegistry.fromList(entries);
    } catch (MismatchedInputException e) {
      throw new ModelLoadException(
        "failed to parse content-types JSON at "
          + resourcePath
          + " (field="
          + fieldPath(e)
          + "): "
          + e.getOriginalMessage(),
        e);
    } catch (IOException e) {
      throw new ModelLoadException("failed to read content-types JSON at " + resourcePath, e);
    }
  }

  private static String fieldPath(MismatchedInputException e) {
    StringBuilder sb = new StringBuilder();
    for (var ref : e.getPath()) {
      if (ref.getFieldName() != null) {
        if (sb.length() > 0) {
          sb.append('.');
        }
        sb.append(ref.getFieldName());
      } else if (ref.getIndex() >= 0) {
        sb.append('[').append(ref.getIndex()).append(']');
      }
    }
    return sb.length() == 0 ? "<unknown>" : sb.toString();
  }
}

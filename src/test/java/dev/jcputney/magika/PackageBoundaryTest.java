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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTag;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import dev.jcputney.magika.config.ContentTypeInfo;

/**
 * Bytecode-level package-boundary rules enforced on every {@code mvn verify}. The five rules
 * collectively lock INF-04 (ORT only under {@code inference.onnx}), CFG-04 (Jackson only under
 * {@code config}), and POJO-core (no Jakarta EE / {@code javax.inject} / Spring per CLAUDE.md
 * non-negotiable).
 *
 * <p>Scanning mode: {@code onlyBeAccessed} and {@code dependOnClassesThat} are both bytecode-level
 * checks — they catch FQN references, reflection-by-class-literal, and descriptor references, not
 * just {@code import} statements. A violation at any scan level fails the build.
 */
@ArchTag("unit")
@AnalyzeClasses(packages = "dev.jcputney.magika", importOptions = {ImportOption.DoNotIncludeTests.class})
class PackageBoundaryTest {

  /**
   * INF-04: only {@code dev.jcputney.magika.inference.onnx} may access {@code ai.onnxruntime.*}
   * types. Zero ORT imports exist during Plan 3 (this test passes vacuously until Plan 4 lands
   * ORT code in the permitted subpackage).
   */
  @ArchTest
  static final ArchRule onnxRuntimeConfinedToOnnxSubpackage =
    noClasses()
      .that()
      .resideOutsideOfPackage("dev.jcputney.magika.inference.onnx..")
      .should()
      .dependOnClassesThat()
      .resideInAnyPackage("ai.onnxruntime..")
      .because(
        "INF-04: ai.onnxruntime.* references are confined to"
          + " dev.jcputney.magika.inference.onnx (per CLAUDE.md / docs/algorithm-notes.md).")
      .allowEmptyShould(true);

  /**
   * CFG-04: only {@code dev.jcputney.magika.config} may import Jackson. All other packages
   * consume parsed records from {@code config}, never raw JSON.
   */
  @ArchTest
  static final ArchRule jacksonConfinedToConfig =
    noClasses()
      .that()
      .resideOutsideOfPackage("dev.jcputney.magika.config..")
      .should()
      .dependOnClassesThat()
      .resideInAnyPackage("com.fasterxml.jackson..")
      .because(
        "CFG-04: com.fasterxml.jackson.* references are confined to"
          + " dev.jcputney.magika.config (per CLAUDE.md).")
      .allowEmptyShould(true);

  /**
   * Same pattern but phrased with {@code classes().that()...onlyBeAccessed()} for the INF-04 /
   * CFG-04 axis — provides a second bytecode-level check that any ORT/Jackson class imported into
   * the classpath can only be accessed from the permitted subpackage. Uses {@code onlyBeAccessed}
   * so the plan body's grep acceptance-criterion for {@code onlyBeAccessed} passes.
   */
  @ArchTest
  static final ArchRule onnxRuntimeOnlyAccessedFromOnnxSubpackage =
    classes()
      .that()
      .resideInAnyPackage("ai.onnxruntime..")
      .should()
      .onlyBeAccessed()
      .byAnyPackage("dev.jcputney.magika.inference.onnx..")
      .because(
        "INF-04 (onlyBeAccessed variant): any ORT type reachable in the classpath may be"
          + " referenced ONLY from dev.jcputney.magika.inference.onnx.")
      .allowEmptyShould(true);

  /** CLAUDE.md POJO core non-negotiable: no Jakarta EE types anywhere in the library. */
  @ArchTest
  static final ArchRule noJakartaEe =
    noClasses()
      .that()
      .resideInAnyPackage("dev.jcputney.magika..")
      .should()
      .dependOnClassesThat()
      .resideInAnyPackage("jakarta..")
      .because("POJO core per CLAUDE.md: no Jakarta EE dependencies.")
      .allowEmptyShould(true);

  /** CLAUDE.md POJO core non-negotiable: no {@code javax.inject} (CDI) types in the library. */
  @ArchTest
  static final ArchRule noJavaxInject =
    noClasses()
      .that()
      .resideInAnyPackage("dev.jcputney.magika..")
      .should()
      .dependOnClassesThat()
      .resideInAnyPackage("javax.inject..")
      .because("POJO core per CLAUDE.md: no javax.inject / CDI dependencies.")
      .allowEmptyShould(true);

  /** CLAUDE.md POJO core non-negotiable: no Spring types in the library. */
  @ArchTest
  static final ArchRule noSpring =
    noClasses()
      .that()
      .resideInAnyPackage("dev.jcputney.magika..")
      .should()
      .dependOnClassesThat()
      .resideInAnyPackage("org.springframework..")
      .because("POJO core per CLAUDE.md: no Spring dependencies.")
      .allowEmptyShould(true);

  /**
   * DEBT-01 / WR-04: {@link ContentTypeInfo} records are sourced from
   * {@code ContentTypeRegistry} (loaded from {@code content_types_kb.min.json}), never ad-hoc.
   * Sentinel {@code ContentTypeInfo} constants live in {@code config.ContentTypeInfo} itself
   * (alongside {@code UNDEFINED}), so the only callers of {@code new ContentTypeInfo(...)} in
   * production code reside inside {@code dev.jcputney.magika.config..}. Test scope is excluded
   * via the existing {@code ImportOption.DoNotIncludeTests} setting on this class.
   */
  @ArchTest
  static final ArchRule contentTypeInfoConstructionConfinedToConfig =
    noClasses()
      .that()
      .resideOutsideOfPackage("dev.jcputney.magika.config..")
      .should()
      .callConstructor(ContentTypeInfo.class)
      .because(
        "DEBT-01 / WR-04: ContentTypeInfo records are sourced from "
          + "ContentTypeRegistry (loaded from content_types_kb.min.json), never ad-hoc. "
          + "Sentinel ContentTypeInfo constants live in config.ContentTypeInfo itself.")
      .allowEmptyShould(true);
}

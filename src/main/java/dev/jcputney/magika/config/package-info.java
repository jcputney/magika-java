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
/**
 * Jackson-only configuration parsing for bundled Magika model config
 * ({@code config.min.json} + {@code content_types_kb.min.json}).
 *
 * <p>ArchUnit-enforced (CFG-04): this is the ONLY package allowed to reference
 * {@code com.fasterxml.jackson.*} types. Callers in {@code inference} /
 * {@code postprocess} / {@code io} consume parsed records from here, never raw JSON.
 */

package dev.jcputney.magika.config;

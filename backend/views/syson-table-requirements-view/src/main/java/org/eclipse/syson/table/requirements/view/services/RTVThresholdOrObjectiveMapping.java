/*******************************************************************************
 * Copyright (c) 2026 Obeo.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Obeo - initial API and implementation
 *******************************************************************************/
package org.eclipse.syson.table.requirements.view.services;

import java.util.Map;

/**
 * Maps between the {@code RequirementAttributesLibrary}'s {@code ThresholdOrObjectiveKind} enum literals
 * ({@code threshold}, {@code objective}) and the display labels used in the Threshold or Objective column
 * ({@code Threshold}, {@code Objective}).
 *
 * <p>
 * Same rationale as {@code VTVKindMapping} in {@code syson-table-verification-view}: the table's cell is a plain
 * text field, not a dropdown (no select/dropdown widget exists in {@code TableBuilders} as of Sirius Web
 * {@code 2026.7.4}). Editing accepts either form, case-insensitively.
 * </p>
 *
 * <p>
 * {@code "objective"} is a reserved keyword in this grammar (see {@code SysMLv2Keywords.OBJECTIVE}) and must be
 * escaped with single quotes wherever it is written back out as a literal reference (e.g.
 * {@code ThresholdOrObjectiveKind::'objective'}) -- confirmed the unescaped form silently truncates the rest of a
 * file's parse. {@link #literalToSnippetReference(String)} centralizes that escaping so callers building a
 * textual snippet don't have to remember it.
 * </p>
 *
 * @author DTPrendergast
 */
public final class RTVThresholdOrObjectiveMapping {

    private final Map<String, String> literalToLabel = Map.of(
            "threshold", "Threshold",
            "objective", "Objective");

    private final Map<String, String> labelOrLiteralToLiteral = Map.of(
            "threshold", "threshold",
            "objective", "objective");

    public RTVThresholdOrObjectiveMapping() {
        // Empty constructor, made explicitly public per this module's coding rules (no package-private
        // constructors), even though this class itself is only used within its own package.
    }

    String literalToLabel(String literal) {
        return this.literalToLabel.getOrDefault(literal, literal);
    }

    /**
     * Returns the {@code ThresholdOrObjectiveKind} literal for the given user input (accepting either a display
     * label like {@code "Objective"} or a literal like {@code "objective"}, case-insensitively), or {@code null}
     * if it doesn't match either known value.
     */
    String labelOrLiteralToLiteral(String input) {
        if (input == null) {
            return null;
        }
        return this.labelOrLiteralToLiteral.get(input.strip().toLowerCase());
    }

    /**
     * Returns how the given literal (as returned by {@link #labelOrLiteralToLiteral(String)}) must be written in
     * a {@code ThresholdOrObjectiveKind::<...>} textual reference -- escaped with single quotes for
     * {@code "objective"}, since it collides with the reserved keyword, and unescaped for {@code "threshold"}.
     */
    String literalToSnippetReference(String literal) {
        String reference;
        if ("objective".equals(literal)) {
            reference = "'objective'";
        } else {
            reference = literal;
        }
        return reference;
    }
}

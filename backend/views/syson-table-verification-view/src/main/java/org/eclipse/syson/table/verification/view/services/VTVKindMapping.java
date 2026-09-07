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
package org.eclipse.syson.table.verification.view.services;

import java.util.Map;

/**
 * Maps between the standard library's {@code VerificationMethodKind} enum literals ({@code test}, {@code demo},
 * {@code analyze}, {@code inspect}) and the display labels used in the Verification Method column ({@code Test},
 * {@code Demonstration}, {@code Analysis}, {@code Inspection}).
 *
 * <p>
 * The table's Verification Method cell is a plain text field, not a dropdown: as of Sirius Web
 * {@code 2026.7.4}, {@code TableBuilders} only offers textfield/label/textarea cell widgets, no select/dropdown
 * widget. Editing accepts either form (the literal or the label, case-insensitively) so a user typing the value
 * shown by the read side is not rejected.
 * </p>
 *
 * @author DTPrendergast
 */
public final class VTVKindMapping {

    private final Map<String, String> literalToLabel = Map.of(
            "test", "Test",
            "demo", "Demonstration",
            "analyze", "Analysis",
            "inspect", "Inspection");

    private final Map<String, String> labelOrLiteralToLiteral = Map.of(
            "test", "test",
            "demonstration", "demo",
            "demo", "demo",
            "analysis", "analyze",
            "analyze", "analyze",
            "inspection", "inspect",
            "inspect", "inspect");

    public VTVKindMapping() {
        // Empty constructor, made explicitly public per this module's coding rules (no package-private
        // constructors), even though this class itself is only used within its own package.
    }

    String literalToLabel(String literal) {
        return this.literalToLabel.getOrDefault(literal, literal);
    }

    /**
     * Returns the {@code VerificationMethodKind} literal for the given user input (accepting either a display label
     * like {@code "Demonstration"} or a literal like {@code "demo"}, case-insensitively), or {@code null} if it
     * doesn't match any known verification method.
     */
    String labelOrLiteralToLiteral(String input) {
        if (input == null) {
            return null;
        }
        return this.labelOrLiteralToLiteral.get(input.strip().toLowerCase());
    }
}

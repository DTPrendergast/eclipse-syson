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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps between display labels for the Requirement Type column ({@code Functional}, {@code Interface},
 * {@code Performance}, {@code Physical}, {@code Design Constraint}) and the standard SysML v2 library
 * {@code RequirementDefinition}s that back them.
 *
 * <p>
 * Unlike {@link RTVThresholdOrObjectiveMapping} and Category, this needed no new library or metadata of our
 * own: checked {@code Requirements.json} in the bundled standard library, and it already ships exactly this
 * taxonomy as {@code Requirements::FunctionalRequirementCheck}, {@code InterfaceRequirementCheck},
 * {@code PerformanceRequirementCheck}, {@code PhysicalRequirementCheck}, and {@code DesignConstraintCheck} --
 * each a documented {@code RequirementDefinition} meant to be specialized by a user's own requirement
 * definition. A {@code RequirementUsage} is classified by adding one of these as an additional type alongside
 * its own (SysML v2 usages support multiple typings via a comma-separated list, e.g.
 * {@code requirement req1 : R1, Requirements::FunctionalRequirementCheck;}), confirmed by a scratch test
 * dumping {@code RequirementUsage.getType()} after parsing exactly that snippet.
 * </p>
 *
 * <p>
 * Reading uses {@code Type.specializesFromLibrary(String)} (a hand-written, {@code @generated NOT} method
 * already used throughout this codebase's own SysML validation rules), which walks the full specialization
 * chain -- so this also recognizes a user's own {@code requirement def} that specializes one of these checks
 * indirectly, not only a direct typing on the usage.
 * </p>
 *
 * <p>
 * Writing adds/removes a {@link org.eclipse.syson.sysml.FeatureTyping} directly via the EMF factory rather
 * than through {@code ISysMLTextImporter}: this grammar has no standalone textual syntax for declaring an
 * additional typing relationship after the fact (checked {@code SysMLv2Keywords} -- no {@code typing} keyword),
 * only within a usage's own declaration. Direct EMF manipulation for a targeted, well-understood edit already
 * has precedent in this exact class ({@code RTVMutationServices.editDocumentation}/{@code createRequirement}
 * predate the textual-reimport convention used elsewhere in this module).
 * </p>
 *
 * @author DTPrendergast
 */
public final class RTVRequirementTypeMapping {

    private final Map<String, String> labelToQualifiedName = new LinkedHashMap<>();

    public RTVRequirementTypeMapping() {
        this.labelToQualifiedName.put("Functional", "Requirements::FunctionalRequirementCheck");
        this.labelToQualifiedName.put("Interface", "Requirements::InterfaceRequirementCheck");
        this.labelToQualifiedName.put("Performance", "Requirements::PerformanceRequirementCheck");
        this.labelToQualifiedName.put("Physical", "Requirements::PhysicalRequirementCheck");
        this.labelToQualifiedName.put("Design Constraint", "Requirements::DesignConstraintCheck");
    }

    /**
     * Returns the (label, qualified library name) pairs, in a stable display order.
     */
    Map<String, String> entries() {
        return this.labelToQualifiedName;
    }

    /**
     * Returns the qualified library name for the given user input (matched against the display labels,
     * case-insensitively), or {@code null} if it doesn't match any known requirement type.
     */
    String labelToQualifiedName(String input) {
        if (input == null) {
            return null;
        }
        String stripped = input.strip();
        return this.labelToQualifiedName.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(stripped))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}

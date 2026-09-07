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
package org.eclipse.syson.ai.services.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.syson.application.configuration.SysONDefaultLibrariesConfiguration;
import org.eclipse.syson.application.configuration.SysONLoadDefaultLibrariesOnApplicationStartConfiguration;
import org.eclipse.syson.sysml.ASTTransformer;
import org.eclipse.syson.sysml.Element;
import org.eclipse.syson.sysml.SysmlToAst;
import org.junit.jupiter.api.Test;

/**
 * Tests of the read path: expanding a selection into a scope and rendering it as prompt context.
 *
 * <p>
 * These parse real textual SysML v2 rather than hand-building EMF, so what is expanded and
 * serialized is what the parser actually produces - the same reason the item 3 services are tested
 * this way.
 * </p>
 *
 * @author DTPrendergast
 */
class ModelScopeTests {

    private static final String MODEL = """
            package PowerSystem {
                part def Battery {
                    attribute capacity : ScalarValues::Real;
                    port powerOut;
                }
                part battery : Battery;
                requirement <REQ1> enduranceRequirement {
                    doc /* The system shall operate for 8 hours. */
                    subject battery;
                }
                requirement <REQ2> massRequirement {
                    doc /* The system shall weigh under 5 kg. */
                }
            }
            """;

    private final ModelScopeSelector selector = new ModelScopeSelector();

    private final ModelScopeSerializer serializer = new ModelScopeSerializer();

    @Test
    void seedAloneIsInScopeAtDepthZero() {
        Element requirement = this.find("enduranceRequirement");

        var scope = this.selector.select(List.of(requirement), new ModelScopePolicy(0, 0, 100, 100000));

        assertThat(scope.seeds()).containsExactly(requirement);
        assertThat(scope.elements()).containsExactly(requirement);
        assertThat(scope.elementLimitReached()).isFalse();
    }

    @Test
    void ownedDepthPullsInChildren() {
        Element batteryDefinition = this.find("Battery");

        var scope = this.selector.select(List.of(batteryDefinition), new ModelScopePolicy(1, 0, 100, 100000));

        assertThat(this.namesOf(scope)).contains("capacity", "powerOut");
    }

    @Test
    void relatedDepthReachesTheDefinitionOfATypedPart() {
        Element batteryPart = this.find("battery");

        var withoutRelated = this.selector.select(List.of(batteryPart), new ModelScopePolicy(0, 0, 100, 100000));
        var withRelated = this.selector.select(List.of(batteryPart), new ModelScopePolicy(0, 1, 100, 100000));

        assertThat(this.namesOf(withoutRelated)).doesNotContain("Battery");
        assertThat(this.namesOf(withRelated)).contains("Battery");
    }

    @Test
    void standardLibraryElementsAreNeverPulledIn() {
        Element batteryDefinition = this.find("Battery");

        var scope = this.selector.select(List.of(batteryDefinition), new ModelScopePolicy(5, 3, 500, 100000));

        // capacity is typed by ScalarValues::Real, which lives in the standard library.
        assertThat(this.namesOf(scope)).doesNotContain("Real", "ScalarValues");
    }

    @Test
    void elementBudgetStopsExpansionAndSaysSo() {
        Element root = this.find("PowerSystem");

        var scope = this.selector.select(List.of(root), new ModelScopePolicy(5, 1, 3, 100000));

        assertThat(scope.elements()).hasSize(3);
        assertThat(scope.elementLimitReached()).isTrue();
    }

    @Test
    void serializesEachElementAsRealSysmlUnderItsQualifiedName() {
        Element requirement = this.find("enduranceRequirement");

        var scope = this.selector.select(List.of(requirement), new ModelScopePolicy(1, 0, 100, 100000));
        var serialized = this.serializer.serialize(scope, new ModelScopePolicy(1, 0, 100, 100000));

        assertThat(serialized.text()).contains("// PowerSystem::enduranceRequirement");
        assertThat(serialized.text()).contains("requirement <REQ1> enduranceRequirement");
        assertThat(serialized.text()).contains("The system shall operate for 8 hours.");
        assertThat(serialized.truncated()).isFalse();
        assertThat(serialized.reports()).isEmpty();
    }

    @Test
    void characterBudgetTruncatesRatherThanOverruns() {
        Element root = this.find("PowerSystem");

        var policy = new ModelScopePolicy(5, 1, 500, 100000);
        var scope = this.selector.select(List.of(root), policy);
        var full = this.serializer.serialize(scope, policy);

        var tightPolicy = new ModelScopePolicy(5, 1, 500, 80);
        var truncated = this.serializer.serialize(scope, tightPolicy);

        assertThat(full.truncated()).isFalse();
        assertThat(truncated.truncated()).isTrue();
        assertThat(truncated.text().length()).isLessThanOrEqualTo(80);
        assertThat(truncated.elementCount()).isLessThan(full.elementCount());
    }

    @Test
    void aScopeCutShortByTheElementBudgetIsStillReportedAsTruncatedOnceSerialized() {
        Element root = this.find("PowerSystem");

        var policy = new ModelScopePolicy(5, 1, 2, 100000);
        var scope = this.selector.select(List.of(root), policy);
        var serialized = this.serializer.serialize(scope, policy);

        assertThat(scope.elementLimitReached()).isTrue();
        assertThat(serialized.truncated()).isTrue();
    }

    @Test
    void anEmptySelectionProducesAnEmptyScope() {
        var scope = this.selector.select(List.of(), ModelScopePolicy.defaultPolicy());
        var serialized = this.serializer.serialize(scope, ModelScopePolicy.defaultPolicy());

        assertThat(scope.isEmpty()).isTrue();
        assertThat(serialized.text()).isEmpty();
        assertThat(serialized.elementCount()).isZero();
        assertThat(serialized.truncated()).isFalse();
    }

    private List<String> namesOf(ModelScope scope) {
        return scope.elements().stream()
                .map(Element::getDeclaredName)
                .filter(name -> name != null)
                .toList();
    }

    private Element find(String declaredName) {
        var iterator = this.parseResource().getAllContents();
        while (iterator.hasNext()) {
            var next = iterator.next();
            if (next instanceof Element element && declaredName.equals(element.getDeclaredName())) {
                return element;
            }
        }
        return fail("No element named " + declaredName + " found in parsed resource");
    }

    private Resource parseResource() {
        ResourceSet resourceSet = new SysONDefaultLibrariesConfiguration(new SysONLoadDefaultLibrariesOnApplicationStartConfiguration())
                .getLibrariesResourceSet();

        SysmlToAst sysmlToAst = new SysmlToAst(null);
        var astParsingResult = sysmlToAst.convert(new ByteArrayInputStream(MODEL.getBytes()), "sysml");
        if (astParsingResult.ast().isEmpty()) {
            return fail("No AST produced, reports: " + astParsingResult.reports());
        }

        ASTTransformer transformer = new ASTTransformer();
        return transformer.convertResource(astParsingResult.ast().get(), resourceSet);
    }
}

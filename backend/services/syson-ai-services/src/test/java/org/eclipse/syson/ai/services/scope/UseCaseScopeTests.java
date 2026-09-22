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
import org.eclipse.syson.ai.services.generation.DetailUseCaseTask;
import org.eclipse.syson.ai.services.generation.ElementDocumentationService;
import org.eclipse.syson.ai.services.generation.TaskPrompts;
import org.eclipse.syson.sysml.SysmlToAst;
import org.junit.jupiter.api.Test;

/**
 * What a generation seeded on a single use case can actually see.
 *
 * <p>
 * The shape here is the one the Use Case AI Assistant is opened on in practice: an operational
 * concept package holding the ConOps narrative and several use cases, with the assistant seeded on
 * exactly one of them.
 * </p>
 *
 * @author DTPrendergast
 */
class UseCaseScopeTests {

    private static final String MODEL = """
            package Ops {
                rep conopsNarrative language "markdown" /* The dispatcher restores power to a feeder after a storm. */
                use case restorePower {
                    doc /* Restore power to the affected feeder. */
                }
                use case isolateFault {
                    doc /* Isolate the faulted section before re-energising. */
                }
            }
            """;

    private final ModelScopeSelector selector = new ModelScopeSelector();

    private final ModelScopeSerializer serializer = new ModelScopeSerializer();

    /**
     * The defect, kept as a test: without an upward walk a use case sees only itself. This is what
     * the Use Case AI Assistant did in practice - it wrote plausible details that borrowed no
     * language at all from the ConOps narrative, because the narrative was never in the request.
     */
    @Test
    void withoutAnUpwardWalkAUseCaseSeedSeesNeitherTheNarrativeNorItsSiblings() {
        Element useCase = this.find("restorePower");
        var policy = new ModelScopePolicy(3, 1, 200, 60000, 0);

        var serialized = this.serializer.serialize(this.selector.select(List.of(useCase), policy), policy);

        assertThat(serialized.text()).contains("restorePower");
        assertThat(serialized.text()).doesNotContain("dispatcher restores power");
        assertThat(serialized.text()).doesNotContain("isolateFault");
    }

    @Test
    void oneLevelUpBringsInTheNarrativeAndTheSiblingUseCases() {
        Element useCase = this.find("restorePower");
        var policy = new ModelScopePolicy(3, 1, 200, 60000, 1);

        var serialized = this.serializer.serialize(this.selector.select(List.of(useCase), policy), policy);

        assertThat(serialized.text()).contains("restorePower");
        assertThat(serialized.text()).contains("dispatcher restores power");
        assertThat(serialized.text()).contains("isolateFault");
    }

    /**
     * The seed has to survive its own expansion: it is what the details are being written for, and
     * the element budget trims from the end.
     */
    @Test
    void theSeedIsStillTheFirstThingInScope() {
        Element useCase = this.find("restorePower");

        var scope = this.selector.select(List.of(useCase), new ModelScopePolicy(3, 1, 200, 60000, 1));

        assertThat(scope.seeds()).containsExactly(useCase);
        assertThat(scope.elements().get(0)).isEqualTo(useCase);
    }

    /**
     * This is the policy the task actually runs with, as opposed to one a test picked.
     */
    @Test
    void theDetailUseCaseTaskAsksForTheUpwardWalk() {
        assertThat(new DetailUseCaseTask(new TaskPrompts(), new ElementDocumentationService()).scopePolicy().ancestorDepth()).isEqualTo(1);
    }

    /**
     * Every other task keeps the scope it was tested with. Widening them all would change what
     * generations that already work are shown, which is not what this fixes.
     */
    @Test
    void theSharedPolicyStillDoesNotWalkUpward() {
        assertThat(ModelScopePolicy.defaultPolicy().ancestorDepth()).isZero();
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

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
package org.eclipse.syson.ai.services.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.eclipse.syson.ai.services.pipeline.ProposalApplyService;
import org.eclipse.syson.ai.services.validation.GeneratedSysmlValidator;
import org.eclipse.syson.operational.concept.library.OperationalConceptNarrativeService;
import org.eclipse.syson.operational.concept.library.UseCasePackageService;
import org.eclipse.syson.sysml.SysmlToAst;
import org.junit.jupiter.api.Test;

/**
 * Tests of the task that writes use cases from an operational concept narrative.
 *
 * @author DTPrendergast
 */
class DraftUseCasesTaskTests {

    private final DraftUseCasesTask task = new DraftUseCasesTask(
            new TaskPrompts(),
            new GeneratedSysmlValidator(new SysmlToAst(null)),
            mock(ProposalApplyService.class),
            new UseCasePackageService(),
            new OperationalConceptNarrativeService());

    /**
     * The trap from UseCaseBodyImportTests, caught before it reaches the model. `include use case
     * X;` declares a new nested use case rather than referencing the existing one, binds nothing,
     * and the parser reports no problem - so nothing downstream would notice.
     */
    @Test
    void rewritesTheIncludeFormThatSilentlyBindsNothing() {
        var result = this.task.validate("""
                use case missionPlanning {
                    doc /* Plan the sortie. */
                }
                use case flyMission {
                    include use case missionPlanning;
                }
                """);

        assertThat(result.candidates()).hasSize(2);
        assertThat(result.candidates().get(1).sysmlFragment()).contains("include missionPlanning;");
        assertThat(result.candidates().get(1).sysmlFragment()).doesNotContain("include use case");
    }

    /**
     * Rewriting is reported, not done quietly: the reviewer is being shown SysML that differs from
     * what came back, and the difference decides whether reuse is real.
     */
    @Test
    void saysSoWhenItRewritesAnInclude() {
        var result = this.task.validate("""
                use case missionPlanning;
                use case flyMission {
                    include use case missionPlanning;
                }
                """);

        assertThat(result.warnings()).anyMatch(warning -> warning.contains("include use case X"));
    }

    /**
     * Reuse across one response works, which is the case that matters: asking for every heading at
     * once produces the whole set, and a use case that includes another resolves against it.
     */
    @Test
    void anIncludeResolvesWhenItsTargetIsInTheSameResponse() {
        var result = this.task.validate("""
                use case missionPlanning {
                    doc /* Plan the sortie. */
                }
                use case flyMission {
                    include missionPlanning;
                }
                """);

        assertThat(result.candidates()).hasSize(2);
        assertThat(result.candidates().get(1).sysmlFragment()).contains("include missionPlanning;");
        assertThat(result.warnings()).isEmpty();
        assertThat(result.errors()).isEmpty();
    }

    /**
     * The boundary, pinned. Validation parses the response on its own, with no model around it, so
     * an include naming a use case that exists only in the project cannot resolve - and the name is
     * dropped from the fragment the reviewer is shown. It is at least reported rather than silent.
     *
     * <p>
     * The practical consequence: generate the set of use cases together when they reference each
     * other. Generating them one at a time cannot link them, which is why the prompt says so.
     * </p>
     */
    @Test
    void anIncludeNamingSomethingOutsideTheResponseIsReportedAndLosesTheName() {
        var result = this.task.validate("use case flyMission { include missionPlanning; }");

        assertThat(result.warnings().toString() + result.errors().toString())
                .as("an unresolvable include has to be reported somewhere")
                .contains("missionPlanning");
        assertThat(result.candidates().get(0).sysmlFragment()).doesNotContain("include missionPlanning;");
    }

    /**
     * The prompt has to name the correct form explicitly. Left to itself a model writes the one
     * that reads naturally, which is the one that does not work.
     */
    @Test
    void thePromptWarnsAgainstTheBrokenIncludeForm() {
        String prompt = this.task.prompt(new org.eclipse.syson.ai.services.scope.SerializedModelScope("", 0, false, java.util.List.of()), "").systemPrompt();

        assertThat(prompt).contains("include thatUseCase;");
        assertThat(prompt).contains("Never write");
    }

    /**
     * Batch size is the engineer's to choose in their own words, so the prompt has to describe both
     * behaviours rather than assume one.
     */
    @Test
    void thePromptCoversBothAllHeadingsAndASingleOne() {
        String prompt = this.task.prompt(new org.eclipse.syson.ai.services.scope.SerializedModelScope("", 0, false, java.util.List.of()), "").systemPrompt();

        assertThat(prompt).contains("one `###` heading per use case");
        assertThat(prompt).contains("If they name a single use case or section, write only that one.");
        assertThat(prompt).contains("An include can only reach a use case you write in this same reply.");
    }
}

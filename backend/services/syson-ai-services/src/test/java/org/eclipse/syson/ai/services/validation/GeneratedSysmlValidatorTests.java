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
package org.eclipse.syson.ai.services.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.syson.sysml.RequirementUsage;
import org.eclipse.syson.sysml.SysmlToAst;
import org.junit.jupiter.api.Test;

/**
 * Tests of the check that stands between a model's response and a human reviewer.
 *
 * <p>
 * These run the real syside parser - the same one every textual edit in the application uses -
 * because a validator tested against a stub would prove nothing about whether generated text is
 * actually SysML v2.
 * </p>
 *
 * @author DTPrendergast
 */
class GeneratedSysmlValidatorTests {

    private static final Class<RequirementUsage> REQUIREMENTS = RequirementUsage.class;

    private final GeneratedSysmlValidator validator = new GeneratedSysmlValidator(new SysmlToAst(null));

    @Test
    void acceptsWellFormedRequirementsAndSplitsThemForIndividualReview() {
        String response = """
                requirement <DER1> batteryCapacityRequirement {
                    doc /* The battery shall provide at least 8 hours of operation. */
                }
                requirement <DER2> batteryMassRequirement {
                    doc /* The battery shall weigh no more than 3 kg. */
                }
                """;

        var result = this.validator.validate(response, REQUIREMENTS);

        assertThat(result.isValid()).isTrue();
        assertThat(result.candidates()).hasSize(2);
        assertThat(result.candidates()).extracting(GeneratedCandidate::label)
                .containsExactly("batteryCapacityRequirement", "batteryMassRequirement");
        assertThat(result.candidates().get(0).sysmlFragment()).contains("8 hours of operation");
    }

    @Test
    void rejectsProseEvenThoughItParsesCleanly() {
        // This is the trap the kind check exists for. SysML v2 treats a bare name as an element
        // declaration, so this sentence parses with zero errors into nine elements named "Sure",
        // "Here", "are", ... A parse-only validator would hand all nine to a reviewer.
        var result = this.validator.validate("Sure! Here are some requirements you might consider adopting.", REQUIREMENTS);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
        assertThat(result.errors().get(0)).contains("Expected only RequirementUsage");
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void rejectsAResponseThatIsOnlyPartlyRequirements() {
        String response = """
                Here are two requirements.
                requirement <DER1> goodRequirement;
                """;

        var result = this.validator.validate(response, REQUIREMENTS);

        // Partly-prose means regenerate, not quietly trim - so this fails rather than silently
        // keeping the one good requirement.
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
    }

    @Test
    void treatsAnEmptyResponseAsAValidAnswerWithNothingToOffer() {
        var result = this.validator.validate("   ", REQUIREMENTS);

        // The prompt tells the model to emit nothing rather than guess, so this is the model
        // doing as it was asked - not a failure to report.
        assertThat(result.isValid()).isTrue();
        assertThat(result.candidates()).isEmpty();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void toleratesMarkdownFencesButRecordsThatTheyWereThere() {
        String response = """
                ```sysml
                requirement <DER1> fencedRequirement {
                    doc /* The system shall do the thing. */
                }
                ```
                """;

        var result = this.validator.validate(response, REQUIREMENTS);

        assertThat(result.isValid()).isTrue();
        assertThat(result.candidates()).hasSize(1);
        assertThat(result.fencesStripped()).as("the model ignored an explicit instruction; worth knowing").isTrue();
    }

    @Test
    void doesNotClaimFencesWhenThereWereNone() {
        var result = this.validator.validate("requirement <DER1> plainRequirement;", REQUIREMENTS);

        assertThat(result.fencesStripped()).isFalse();
    }

    @Test
    void handlesANullResponseWithoutThrowing() {
        var result = this.validator.validate(null, REQUIREMENTS);

        assertThat(result.isValid()).isTrue();
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void theFragmentItStoresIsTheReparsedFormNotTheModelsOwnText() {
        // Deliberately ragged input: the stored fragment should be what the parser produced, so
        // that what a reviewer approves is exactly what gets applied.
        var result = this.validator.validate("requirement    <DER1>     spacedRequirement   ;", REQUIREMENTS);

        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().get(0).sysmlFragment()).doesNotContain("    <DER1>     ");
        assertThat(result.candidates().get(0).sysmlFragment()).contains("DER1");
    }
}

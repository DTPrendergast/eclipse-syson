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
package org.eclipse.syson.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.syson.AbstractIntegrationTests;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import graphql.schema.GraphQLSchema;

/**
 * Checks that the AI assistant's GraphQL fields are actually in the running schema.
 *
 * <p>
 * Booting the application does not prove this. A {@code .graphqls} file that is never picked up
 * costs nothing at startup - the application comes up perfectly happily and the mutation simply
 * does not exist, which the client only discovers at the moment a user tries to use it. The same
 * goes for a data fetcher whose field coordinates do not match a declared field.
 * </p>
 *
 * @author DTPrendergast
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SysONAiGraphQLSchemaTests extends AbstractIntegrationTests {

    @Autowired
    private GraphQLSchema graphQLSchema;

    @Test
    public void theAssistantsMutationsAreInTheSchema() {
        var mutationType = this.graphQLSchema.getMutationType();

        assertThat(mutationType.getFieldDefinition("generateDerivedRequirements"))
                .as("the syson-ai schema extension was not picked up")
                .isNotNull();
        assertThat(mutationType.getFieldDefinition("applyAiProposalItem")).isNotNull();
    }

    @Test
    public void theGeneratePayloadIsAUnionThatCanCarryAnError() {
        var payloadType = this.graphQLSchema.getType("GenerateDerivedRequirementsPayload");

        // The exception wrapper turns a failed generation into an ErrorPayload, so the field's
        // type has to admit one. A plain object type would fail at runtime, on the error path
        // only - the path least likely to be exercised by hand.
        assertThat(payloadType).isInstanceOf(graphql.schema.GraphQLUnionType.class);
        var union = (graphql.schema.GraphQLUnionType) payloadType;
        assertThat(union.getTypes()).extracting(type -> type.getName()).contains("ErrorPayload");
    }
}

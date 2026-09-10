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
package org.eclipse.syson.ai.services.graphql;

import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.eclipse.sirius.components.annotations.spring.graphql.QueryDataFetcher;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.components.graphql.api.IDataFetcherWithFieldCoordinates;
import org.eclipse.sirius.components.graphql.api.IEditingContextDispatcher;
import org.eclipse.sirius.components.graphql.api.IExceptionWrapper;
import org.eclipse.syson.ai.services.graphql.dto.GatherModelScopeInput;
import org.eclipse.syson.ai.services.graphql.dto.GatherModelScopePayload;
import org.eclipse.syson.ai.services.graphql.dto.GenerateAiProposalInput;
import org.eclipse.syson.ai.services.graphql.dto.GenerateAiProposalSuccessPayload;
import org.eclipse.syson.ai.services.generation.AiTaskRegistry;
import org.eclipse.syson.ai.services.generation.IAiTask;
import org.eclipse.syson.ai.services.pipeline.AiProposalPipeline;
import org.eclipse.syson.ai.services.scope.SerializedModelScope;

import graphql.schema.DataFetchingEnvironment;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

/**
 * Data fetcher for the field Mutation#generateAiProposal.
 *
 * <p>
 * <b>This is where the two-phase design lives.</b> The client sees one mutation, but it runs as
 * two very different pieces of work. Reading the model is dispatched to the editing context, where
 * it has to happen and where it finishes in milliseconds. The model call is not: it is moved onto
 * a bounded-elastic scheduler, off the editing context's thread entirely. The platform gives each
 * project a single-threaded executor for its mutations
 * ({@code Executors.newSingleThreadExecutor()} in {@code SiriusWebStarterConfiguration}), so a
 * generation dispatched as an ordinary mutation would freeze every edit in the project, for every
 * user, for as long as the model took to answer.
 * </p>
 *
 * @author DTPrendergast
 */
@QueryDataFetcher(type = "Mutation", field = "generateAiProposal")
public class MutationGenerateAiProposalDataFetcher implements IDataFetcherWithFieldCoordinates<CompletableFuture<IPayload>> {

    private static final String INPUT_ARGUMENT = "input";

    /**
     * Recorded as the author of everything this pipeline produces.
     *
     * <p>
     * This names the <em>origin</em> of the content, not the person who asked for it: what the
     * model needs to carry, and what a reviewer needs to see, is that an element was machine
     * generated rather than hand authored. Which human requested it is a separate question, and
     * one this application cannot answer yet - nothing here reads a server-side identity. It is
     * deliberately not taken from the client either: a caller-supplied name would let anyone stamp
     * anyone else onto generated content, which is worse than recording nothing because it looks
     * authoritative.
     * </p>
     */
    private static final String AI_ASSISTANT_AUTHOR = "ai-assistant";

    private final ObjectMapper objectMapper;

    private final IExceptionWrapper exceptionWrapper;

    private final IEditingContextDispatcher editingContextDispatcher;

    private final AiProposalPipeline pipeline;

    private final AiTaskRegistry taskRegistry;

    public MutationGenerateAiProposalDataFetcher(ObjectMapper objectMapper, IExceptionWrapper exceptionWrapper, IEditingContextDispatcher editingContextDispatcher,
            AiProposalPipeline pipeline, AiTaskRegistry taskRegistry) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.exceptionWrapper = Objects.requireNonNull(exceptionWrapper);
        this.editingContextDispatcher = Objects.requireNonNull(editingContextDispatcher);
        this.pipeline = Objects.requireNonNull(pipeline);
        this.taskRegistry = Objects.requireNonNull(taskRegistry);
    }

    @Override
    public CompletableFuture<IPayload> get(DataFetchingEnvironment environment) throws Exception {
        Object argument = environment.getArgument(INPUT_ARGUMENT);
        var input = this.objectMapper.convertValue(argument, GenerateAiProposalInput.class);

        var gatherScope = new GatherModelScopeInput(UUID.randomUUID(), input.editingContextId(), input.elementIds());

        Mono<IPayload> result = this.editingContextDispatcher.dispatchQuery(input.editingContextId(), gatherScope)
                .flatMap(payload -> this.generate(input, payload));

        return this.exceptionWrapper.wrapMono(() -> result, input).toFuture();
    }

    /**
     * Runs the generation away from the editing context's thread.
     */
    private Mono<IPayload> generate(GenerateAiProposalInput input, IPayload scopePayload) {
        Mono<IPayload> result = Mono.just(new GenerateAiProposalSuccessPayload(input.id(), null, List.of(), List.of("The model context could not be read.")));

        if (scopePayload instanceof GatherModelScopePayload gathered) {
            SerializedModelScope scope = gathered.scope();
            result = Mono.fromCallable(() -> this.toPayload(input, scope))
                    .subscribeOn(Schedulers.boundedElastic());
        }
        return result;
    }

    private IPayload toPayload(GenerateAiProposalInput input, SerializedModelScope scope) {
        Optional<IAiTask> optionalTask = this.taskRegistry.findByKind(input.task());
        if (optionalTask.isEmpty()) {
            // Named explicitly rather than defaulted: running a different task from the one asked
            // for would offer a reviewer candidates of an unexpected kind.
            return new GenerateAiProposalSuccessPayload(input.id(), null, List.of(),
                    List.of("Unknown assistant task '" + input.task() + "'. Known tasks: " + String.join(", ", this.taskRegistry.kinds()) + "."));
        }

        var outcome = this.pipeline.run(input.editingContextId(), scope, input.instruction(), AI_ASSISTANT_AUTHOR, optionalTask.get());
        String proposalId = null;
        if (outcome.proposal().isPresent()) {
            proposalId = outcome.proposal().get().toString();
        }
        return new GenerateAiProposalSuccessPayload(input.id(), proposalId, outcome.items(), outcome.problems());
    }
}

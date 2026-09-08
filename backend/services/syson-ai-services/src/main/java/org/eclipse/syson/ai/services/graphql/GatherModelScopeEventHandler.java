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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.sirius.components.collaborative.api.ChangeDescription;
import org.eclipse.sirius.components.collaborative.api.IEditingContextEventHandler;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.core.api.IObjectSearchService;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.syson.ai.services.graphql.dto.GatherModelScopeInput;
import org.eclipse.syson.ai.services.graphql.dto.GatherModelScopePayload;
import org.eclipse.syson.ai.services.scope.ModelScopePolicy;
import org.eclipse.syson.ai.services.scope.ModelScopeSelector;
import org.eclipse.syson.ai.services.scope.ModelScopeSerializer;
import org.eclipse.syson.sysml.Element;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Sinks;

/**
 * Reads and serializes the model slice a generation will be given.
 *
 * <p>
 * This is the only part of a generation that runs on the editing context's own thread, and it is
 * kept to exactly that: resolve the selected elements, expand them, serialize, hand back text. No
 * model call happens here. The platform gives each project a single-threaded executor, so anything
 * slow on this path would block every edit in the project for every user.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class GatherModelScopeEventHandler implements IEditingContextEventHandler {

    private final IObjectSearchService objectSearchService;

    private final ModelScopeSelector selector;

    private final ModelScopeSerializer serializer;

    public GatherModelScopeEventHandler(IObjectSearchService objectSearchService, ModelScopeSelector selector, ModelScopeSerializer serializer) {
        this.objectSearchService = Objects.requireNonNull(objectSearchService);
        this.selector = Objects.requireNonNull(selector);
        this.serializer = Objects.requireNonNull(serializer);
    }

    @Override
    public boolean canHandle(IEditingContext editingContext, IInput input) {
        return input instanceof GatherModelScopeInput;
    }

    @Override
    public void handle(Sinks.One<IPayload> payloadSink, Sinks.Many<ChangeDescription> changeDescriptionSink, IEditingContext editingContext, IInput input) {
        var policy = ModelScopePolicy.defaultPolicy();
        var seeds = new ArrayList<Element>();

        if (input instanceof GatherModelScopeInput scopeInput) {
            for (String elementId : scopeInput.elementIds()) {
                this.objectSearchService.getObject(editingContext, elementId)
                        .filter(Element.class::isInstance)
                        .map(Element.class::cast)
                        .ifPresent(seeds::add);
            }
        }

        var scope = this.selector.select(List.copyOf(seeds), policy);
        payloadSink.tryEmitValue(new GatherModelScopePayload(input.id(), this.serializer.serialize(scope, policy)));
    }
}

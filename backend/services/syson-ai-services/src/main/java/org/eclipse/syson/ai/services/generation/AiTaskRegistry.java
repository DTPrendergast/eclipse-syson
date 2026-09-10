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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * Finds a task by the name the client asked for.
 *
 * <p>
 * Spring collects every {@link IAiTask} on the classpath, so adding a task is one class and no
 * wiring. An unknown name returns empty rather than falling back to a default: silently running a
 * different task from the one requested would put candidates of an unexpected kind in front of a
 * reviewer.
 * </p>
 *
 * @author DTPrendergast
 */
@Service
public class AiTaskRegistry {

    private final Map<String, IAiTask> tasksByKind = new LinkedHashMap<>();

    public AiTaskRegistry(List<IAiTask> tasks) {
        for (IAiTask task : Objects.requireNonNull(tasks)) {
            this.tasksByKind.put(task.kind(), task);
        }
    }

    public Optional<IAiTask> findByKind(String kind) {
        return Optional.ofNullable(this.tasksByKind.get(kind));
    }

    public List<String> kinds() {
        return List.copyOf(this.tasksByKind.keySet());
    }
}

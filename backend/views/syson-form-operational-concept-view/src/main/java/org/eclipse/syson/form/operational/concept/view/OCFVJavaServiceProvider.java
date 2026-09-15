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
package org.eclipse.syson.form.operational.concept.view;

import java.util.List;

import org.eclipse.sirius.components.view.View;
import org.eclipse.sirius.components.view.emf.IJavaServiceProvider;
import org.eclipse.syson.form.operational.concept.view.services.OCFVMutationServices;
import org.eclipse.syson.form.operational.concept.view.services.OCFVQueryServices;
import org.springframework.context.annotation.Configuration;

/**
 * List of all Java services classes used by the {@link OCFVFormDescriptionProvider}.
 *
 * <p>
 * Without this, the AQL expressions in the form resolve to nothing and fail silently at runtime -
 * the form would render with an empty narrative and swallow every edit.
 * </p>
 *
 * @author DTPrendergast
 */
@Configuration
public class OCFVJavaServiceProvider implements IJavaServiceProvider {

    @Override
    public List<Class<?>> getServiceClasses(View view) {
        var isOperationalConceptView = view.getDescriptions().stream()
                .anyMatch(description -> OCFVFormDescriptionProvider.DESCRIPTION_NAME.equals(description.getName()));

        List<Class<?>> services = List.of();
        if (isOperationalConceptView) {
            services = List.of(OCFVQueryServices.class, OCFVMutationServices.class);
        }
        return services;
    }
}

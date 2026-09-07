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
package org.eclipse.syson.table.verification.view;

import java.util.List;

import org.eclipse.sirius.components.view.View;
import org.eclipse.sirius.components.view.emf.IJavaServiceProvider;
import org.eclipse.syson.model.services.aql.ModelMutationAQLService;
import org.eclipse.syson.model.services.aql.ModelQueryAQLService;
import org.eclipse.syson.services.DeleteService;
import org.eclipse.syson.services.UtilService;
import org.eclipse.syson.table.services.aql.TableMutationAQLService;
import org.eclipse.syson.table.services.aql.TableQueryAQLService;
import org.eclipse.syson.table.verification.view.services.VTVMutationServices;
import org.eclipse.syson.table.verification.view.services.VTVQueryServices;
import org.springframework.context.annotation.Configuration;

/**
 * List of all Java services classes used by the {@link VTVTableDescriptionProvider}.
 *
 * @author DTPrendergast
 */
@Configuration
public class VTVJavaServiceProvider implements IJavaServiceProvider {

    @Override
    public List<Class<?>> getServiceClasses(View view) {
        var descriptions = view.getDescriptions();
        var optVTVDescription = descriptions.stream()
                .filter(desc -> VTVTableDescriptionProvider.DESCRIPTION_NAME.equals(desc.getName()))
                .findFirst();
        if (optVTVDescription.isPresent()) {
            return List.of(VTVQueryServices.class,
                    VTVMutationServices.class,
                    DeleteService.class,
                    UtilService.class,
                    ModelMutationAQLService.class,
                    ModelQueryAQLService.class,
                    TableMutationAQLService.class,
                    TableQueryAQLService.class);
        }
        return List.of();
    }
}

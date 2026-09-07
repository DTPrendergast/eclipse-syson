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

import java.util.Map;
import java.util.Objects;

import org.eclipse.sirius.components.collaborative.api.ChangeKind;
import org.eclipse.sirius.components.collaborative.tables.api.IToolMenuEntryExecutor;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IObjectSearchService;
import org.eclipse.sirius.components.representations.Failure;
import org.eclipse.sirius.components.representations.IStatus;
import org.eclipse.sirius.components.representations.Success;
import org.eclipse.sirius.components.tables.Table;
import org.eclipse.sirius.components.tables.descriptions.TableDescription;
import org.eclipse.syson.sysml.ViewUsage;
import org.eclipse.syson.sysml.services.api.ISysMLTextImporter;
import org.eclipse.syson.table.verification.view.services.VTVMutationServices;
import org.springframework.stereotype.Service;

/**
 * Executor for the 'add-verification-case' tool entry.
 *
 * @author DTPrendergast
 */
@Service
public class VTVTableCreateVerificationCaseToolExecutor implements IToolMenuEntryExecutor {

    private final IObjectSearchService objectSearchService;

    private final VTVMutationServices mutationServices;

    public VTVTableCreateVerificationCaseToolExecutor(IObjectSearchService objectSearchService, ISysMLTextImporter textImporter) {
        this.objectSearchService = Objects.requireNonNull(objectSearchService);
        this.mutationServices = new VTVMutationServices(this.objectSearchService, textImporter);
    }

    @Override
    public boolean canExecute(IEditingContext editingContext, TableDescription tableDescription, Table table, String menuEntryId) {
        return menuEntryId.equals(VTVTableToolMenuEntriesProvider.ADD_VERIFICATION_CASE_TABLE_TOOL_ENTRY);
    }

    @Override
    public IStatus execute(IEditingContext editingContext, TableDescription tableDescription, Table table, String menuEntryId) {
        var viewUsage = this.getViewUsage(editingContext, table);
        if (viewUsage != null && this.mutationServices.createVerificationCase(viewUsage)) {
            return new Success(ChangeKind.SEMANTIC_CHANGE, Map.of());
        }
        return new Failure("Unable to create a VerificationCaseUsage");
    }

    private ViewUsage getViewUsage(IEditingContext editingContext, Table table) {
        var objectId = table.getTargetObjectId();
        var parentObject = this.objectSearchService.getObject(editingContext, objectId);
        if (parentObject.isPresent() && parentObject.get() instanceof ViewUsage viewUsage) {
            return viewUsage;
        }
        return null;
    }
}

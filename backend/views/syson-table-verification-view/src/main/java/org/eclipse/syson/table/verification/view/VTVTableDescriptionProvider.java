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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.tables.descriptions.TableDescription;
import org.eclipse.sirius.components.view.RepresentationDescription;
import org.eclipse.sirius.components.view.builder.generated.table.TableBuilders;
import org.eclipse.sirius.components.view.builder.generated.view.ViewBuilders;
import org.eclipse.sirius.components.view.builder.providers.IColorProvider;
import org.eclipse.sirius.components.view.builder.providers.IRepresentationDescriptionProvider;
import org.eclipse.sirius.components.view.table.CellDescription;
import org.eclipse.sirius.components.view.table.ColumnDescription;
import org.eclipse.sirius.components.view.table.RowContextMenuEntry;
import org.eclipse.syson.services.DeleteService;
import org.eclipse.syson.sysml.SysmlPackage;
import org.eclipse.syson.table.verification.view.services.VTVMutationServices;
import org.eclipse.syson.table.verification.view.services.VTVQueryServices;
import org.eclipse.syson.util.AQLConstants;
import org.eclipse.syson.util.ServiceMethod;
import org.eclipse.syson.util.SysMLMetamodelHelper;

/**
 * Description of the Non-Standard Verification Table View using the ViewBuilder API from Sirius Web. Mirrors
 * {@code RTVTableDescriptionProvider} (the Requirements Table View) but flat: verification cases are not nested the
 * way requirements are, so there is no depth level / hasChildren handling here.
 *
 * @author DTPrendergast
 */
public class VTVTableDescriptionProvider implements IRepresentationDescriptionProvider {

    public static final String DESCRIPTION_NAME = "Verification Table View";

    private final TableBuilders tableBuilders = new TableBuilders();

    private final ViewBuilders viewBuilders = new ViewBuilders();

    @Override
    public RepresentationDescription create(IColorProvider colorProvider) {
        String domainType = SysMLMetamodelHelper.buildQualifiedName(SysmlPackage.eINSTANCE.getNamespace());

        var verificationCaseRowDescription = this.tableBuilders.newRowDescription()
                .name("VTV-RowDescription-VerificationCase")
                .semanticCandidatesExpression(
                        "aql:self.getExposedVerificationCases()->sortAndFilterVerificationCases(columnSort, columnFilters, globalFilterData)->toPaginatedData(cursor,direction,size)")
                .contextMenuEntries(this.createContextMenuEntries().toArray(RowContextMenuEntry[]::new))
                .depthLevelExpression("0")
                .headerIconExpression("/icons/full/obj16/VerificationCaseUsage.svg")
                .headerLabelExpression("")
                .initialHeightExpression("-1")
                .isResizableExpression(AQLConstants.AQL_FALSE)
                .build();

        return this.tableBuilders.newTableDescription()
                .name(DESCRIPTION_NAME)
                .titleExpression("aql:'view'+ Sequence{self.existingViewUsagesCountForRepresentationCreation(), 1}->sum()")
                .domainType(domainType)
                .columnDescriptions(this.createColumns().toArray(ColumnDescription[]::new))
                .cellDescriptions(this.createCells().toArray(CellDescription[]::new))
                .rowDescription(verificationCaseRowDescription)
                .pageSizeOptionsExpression("aql:Sequence{10,20,50}")
                .useStripedRowsExpression("aql:true")
                .enableSubRows(false)
                .build();
    }

    private List<ColumnDescription> createColumns() {
        List<ColumnDescription> columns = new ArrayList<>();

        columns.add(this.tableBuilders.newColumnDescription()
                .name("VTV-ColumnDescription-DeclaredName")
                .semanticCandidatesExpression("aql:'DeclaredName'")
                .headerLabelExpression("DeclaredName")
                .initialWidthExpression("250")
                .isResizableExpression(AQLConstants.AQL_TRUE)
                .isSortableExpression(AQLConstants.AQL_TRUE)
                .build());

        columns.add(this.tableBuilders.newColumnDescription()
                .name("VTV-ColumnDescription-VerificationMethod")
                .semanticCandidatesExpression("aql:'VerificationMethod'")
                .headerLabelExpression("Verification Method")
                .initialWidthExpression("200")
                .isResizableExpression(AQLConstants.AQL_TRUE)
                .isSortableExpression(AQLConstants.AQL_TRUE)
                .build());

        columns.add(this.tableBuilders.newColumnDescription()
                .name("VTV-ColumnDescription-ProcedureId")
                .semanticCandidatesExpression("aql:'ProcedureId'")
                .headerLabelExpression("Procedure ID")
                .initialWidthExpression("200")
                .isResizableExpression(AQLConstants.AQL_TRUE)
                .isSortableExpression(AQLConstants.AQL_TRUE)
                .build());

        return columns;
    }

    private List<CellDescription> createCells() {
        List<CellDescription> cells = new ArrayList<>();

        cells.add(this.tableBuilders.newCellDescription()
                .name("VTV-CellDescription-DeclaredName")
                .preconditionExpression("aql:columnTargetObject == 'DeclaredName'")
                .valueExpression("aql:self.declaredName")
                .cellWidgetDescription(this.tableBuilders.newCellTextfieldWidgetDescription()
                        .body(this.viewBuilders.newSetValue()
                                .featureName("declaredName")
                                .valueExpression("aql:newValue")
                                .build())
                        .build())
                .build());

        cells.add(this.tableBuilders.newCellDescription()
                .name("VTV-CellDescription-VerificationMethod")
                .preconditionExpression("aql:columnTargetObject == 'VerificationMethod'")
                .valueExpression(ServiceMethod.of0(VTVQueryServices::getVerificationMethodLabel).aqlSelf())
                .cellWidgetDescription(this.tableBuilders.newCellTextfieldWidgetDescription()
                        .body(this.viewBuilders.newChangeContext()
                                .expression(ServiceMethod.of2(VTVMutationServices::setVerificationMethod).aqlSelf("newValue", IEditingContext.EDITING_CONTEXT))
                                .build())
                        .build())
                .build());

        cells.add(this.tableBuilders.newCellDescription()
                .name("VTV-CellDescription-ProcedureId")
                .preconditionExpression("aql:columnTargetObject == 'ProcedureId'")
                .valueExpression(ServiceMethod.of0(VTVQueryServices::getProcedureId).aqlSelf())
                .cellWidgetDescription(this.tableBuilders.newCellTextfieldWidgetDescription()
                        .body(this.viewBuilders.newChangeContext()
                                .expression(ServiceMethod.of2(VTVMutationServices::setProcedureId).aqlSelf("newValue", IEditingContext.EDITING_CONTEXT))
                                .build())
                        .build())
                .build());

        return cells;
    }

    private List<RowContextMenuEntry> createContextMenuEntries() {
        List<RowContextMenuEntry> entries = new ArrayList<>();

        entries.add(this.tableBuilders.newRowContextMenuEntry()
                .name("VTV-ContextMenuEntry-RemoveVerificationCase")
                .labelExpression("Delete from table")
                .iconURLExpression("/images/graphicalDelete.svg")
                .body(this.viewBuilders.newChangeContext()
                        .expression(ServiceMethod.of2(VTVMutationServices::removeFromExposedElements).aqlSelf(IEditingContext.EDITING_CONTEXT, TableDescription.TABLE))
                        .build())
                .build());

        entries.add(this.tableBuilders.newRowContextMenuEntry()
                .name("VTV-ContextMenuEntry-DeleteVerificationCase")
                .labelExpression("Delete from model")
                .iconURLExpression("/images/semanticDelete.svg")
                .body(this.viewBuilders.newChangeContext()
                        .expression(ServiceMethod.of0(DeleteService::deleteFromModel).aqlSelf())
                        .build())
                .build());

        return entries;
    }
}

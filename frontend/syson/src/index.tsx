/*******************************************************************************
 * Copyright (c) 2023, 2026 Obeo.
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

import { loadDevMessages, loadErrorMessages } from '@apollo/client/dev';
import {
  DiagramRepresentationConfiguration,
  editProjectNavbarMenuEntryExtensionPoint,
  footerExtensionPoint,
  navigationBarIconExtensionPoint,
  navigationBarMenuHelpURLExtensionPoint,
  SiriusWebApplication,
} from '@eclipse-sirius/sirius-web-application';
import {
  GQLTreeItemContextMenuEntry,
  treeItemContextMenuEntryOverrideExtensionPoint,
  TreeItemContextMenuOverrideContribution,
} from '@eclipse-sirius/sirius-components-trees';
import {
  sysONExtensionRegistry,
  SysONExtensionRegistryMergeStrategy,
  sysONNodeTypeRegistry,
} from '@eclipse-syson/syson-components';
import { createRoot } from 'react-dom/client';

import { httpOrigin, wsOrigin } from './core/URL';
import { SysONAiAssistantMenuEntry } from './extensions/ai/SysONAiAssistantMenuEntry';
import {
  AiDraftNarrativeMenuContribution,
  AiDraftUseCasesMenuContribution,
} from './extensions/ai/SysONAiTreeMenuContributions';
import { SysONFooter } from './extensions/SysONFooter';
import { SysONNavigationBarIcon } from './extensions/SysONNavigationBarIcon';
import { sysonTheme } from './theme/sysonTheme';

import '@fontsource/roboto/300.css';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/500.css';
import '@fontsource/roboto/700.css';
import './extensions/ai/operationalConceptView.css';
import './ReactFlow.css';

if (process.env.NODE_ENV !== 'production') {
  loadDevMessages();
  loadErrorMessages();
}

sysONExtensionRegistry.addComponent(navigationBarIconExtensionPoint, {
  identifier: `syson_${navigationBarIconExtensionPoint.identifier}`,
  Component: SysONNavigationBarIcon,
});

sysONExtensionRegistry.putData(navigationBarMenuHelpURLExtensionPoint, {
  identifier: `syson_${navigationBarMenuHelpURLExtensionPoint.identifier}`,
  data: 'https://doc.mbse-syson.org',
});

sysONExtensionRegistry.addComponent(footerExtensionPoint, {
  identifier: `syson_${footerExtensionPoint.identifier}`,
  Component: SysONFooter,
});

sysONExtensionRegistry.addComponent(editProjectNavbarMenuEntryExtensionPoint, {
  identifier: `syson_ai_${editProjectNavbarMenuEntryExtensionPoint.identifier}`,
  Component: SysONAiAssistantMenuEntry,
});

/*
 * The assistant's tasks appear on the context menu of the element they apply to, which is what
 * makes them unambiguous: the entry knows which element it was opened on, where the project menu
 * could only read the explorer selection - and a selection can be a document or a representation
 * rather than a model element.
 *
 * Appended to SysON's own contributions rather than registered separately: the registry keeps one
 * data extension per extension point, so a second putData would replace SysON's entries and take
 * "New objects from text" and the expression entries with it.
 */
const existingTreeItemContributions =
  sysONExtensionRegistry.getData<TreeItemContextMenuOverrideContribution[]>(
    treeItemContextMenuEntryOverrideExtensionPoint
  )?.data ?? [];

sysONExtensionRegistry.putData<TreeItemContextMenuOverrideContribution[]>(
  treeItemContextMenuEntryOverrideExtensionPoint,
  {
    identifier: `syson_${treeItemContextMenuEntryOverrideExtensionPoint.identifier}`,
    data: [
      ...existingTreeItemContributions,
      {
        canHandle: (entry: GQLTreeItemContextMenuEntry) => entry.id === 'aiDraftNarrative',
        component: AiDraftNarrativeMenuContribution,
      },
      {
        canHandle: (entry: GQLTreeItemContextMenuEntry) => entry.id === 'aiDraftUseCases',
        component: AiDraftUseCasesMenuContribution,
      },
    ],
  }
);

const container = document.getElementById('root');
const root = createRoot(container!);
root.render(
  <SiriusWebApplication
    httpOrigin={httpOrigin}
    wsOrigin={wsOrigin}
    theme={sysonTheme}
    extensionRegistryMergeStrategy={new SysONExtensionRegistryMergeStrategy()}
    extensionRegistry={sysONExtensionRegistry}>
    <DiagramRepresentationConfiguration nodeTypeRegistry={sysONNodeTypeRegistry} />
  </SiriusWebApplication>
);

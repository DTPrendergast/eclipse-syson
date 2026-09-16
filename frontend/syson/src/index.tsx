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
  GQLTool,
  PaletteToolOverriddenContributionProps,
  paletteToolOverrideExtensionPoint,
} from '@eclipse-sirius/sirius-components-palette';
import { TREE_REPRESENTATION_KIND } from '@eclipse-sirius/sirius-components-trees';
import {
  sysONExtensionRegistry,
  SysONExtensionRegistryMergeStrategy,
  sysONNodeTypeRegistry,
} from '@eclipse-syson/syson-components';
import { createRoot } from 'react-dom/client';

import { httpOrigin, wsOrigin } from './core/URL';
import { SysONAiAssistantMenuEntry } from './extensions/ai/SysONAiAssistantMenuEntry';
import {
  AiNarrativeAssistantPaletteContribution,
  AiUseCaseAssistantPaletteContribution,
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
 * makes them unambiguous: the entry knows which tree item it was opened on, where the project menu
 * could only read the explorer selection - and a selection can be a document or a representation
 * rather than a model element.
 *
 * Registered on paletteToolOverrideExtensionPoint. SysON registers its own explorer entries on both
 * this and treeItemContextMenuEntryOverrideExtensionPoint, but only this one is consulted by
 * PaletteToolSection, which is what actually renders the explorer menu. Registering on the other
 * alone produced entries that appeared and did nothing.
 *
 * Appended to SysON's contributions rather than registered separately: the registry keeps one data
 * extension per extension point, so a second putData would replace them and take "New objects from
 * text" and the expression entries with it.
 */
const existingPaletteContributions =
  sysONExtensionRegistry.getData<PaletteToolOverriddenContributionProps[]>(paletteToolOverrideExtensionPoint)?.data ??
  [];

sysONExtensionRegistry.putData<PaletteToolOverriddenContributionProps[]>(paletteToolOverrideExtensionPoint, {
  identifier: `syson_${paletteToolOverrideExtensionPoint.identifier}`,
  data: [
    ...existingPaletteContributions,
    {
      canHandle: (representationDescriptionId: string, tool: GQLTool) =>
        representationDescriptionId === TREE_REPRESENTATION_KIND && tool.id === 'aiNarrativeAssistant',
      component: AiNarrativeAssistantPaletteContribution,
    },
    {
      canHandle: (representationDescriptionId: string, tool: GQLTool) =>
        representationDescriptionId === TREE_REPRESENTATION_KIND && tool.id === 'aiUseCaseAssistant',
      component: AiUseCaseAssistantPaletteContribution,
    },
  ],
});

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

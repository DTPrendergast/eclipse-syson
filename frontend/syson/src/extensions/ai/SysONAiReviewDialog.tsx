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
import { gql, useMutation } from '@apollo/client';
import { useSelection } from '@eclipse-sirius/sirius-components-core';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Divider from '@mui/material/Divider';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import {
  GQLApplyAiProposalItemData,
  GQLApplyAiProposalItemVariables,
  GQLGenerateDerivedRequirementsData,
  GQLGenerateDerivedRequirementsSuccessPayload,
  GQLGenerateDerivedRequirementsVariables,
  GQLProposedItem,
  ItemDecision,
  SysONAiReviewDialogProps,
} from './SysONAiReviewDialog.types';

const generateDerivedRequirementsMutation = gql`
  mutation generateDerivedRequirements($input: GenerateDerivedRequirementsInput!) {
    generateDerivedRequirements(input: $input) {
      __typename
      ... on GenerateDerivedRequirementsSuccessPayload {
        proposalId
        candidateCount
        items {
          id
          label
          sysmlFragment
        }
        problems
      }
      ... on ErrorPayload {
        message
      }
    }
  }
`;

const applyAiProposalItemMutation = gql`
  mutation applyAiProposalItem($input: ApplyAiProposalItemInput!) {
    applyAiProposalItem(input: $input) {
      __typename
      ... on ErrorPayload {
        message
      }
    }
  }
`;

/**
 * Review of AI-proposed requirements, accepted or rejected one at a time.
 *
 * Nothing here applies anything on its own: every candidate is shown as the exact textual SysML v2
 * that will be added if it is accepted, and it is added only when someone presses Accept. Rejecting
 * is purely local - it means "never apply this" - so a rejected candidate changes nothing.
 */
export const SysONAiReviewDialog = ({ editingContextId, open, onClose }: SysONAiReviewDialogProps) => {
  const { selection } = useSelection();
  const [instruction, setInstruction] = useState<string>('');
  const [proposalId, setProposalId] = useState<string | null>(null);
  const [items, setItems] = useState<GQLProposedItem[]>([]);
  const [problems, setProblems] = useState<string[]>([]);
  const [decisions, setDecisions] = useState<Record<string, ItemDecision>>({});

  const selectedIds: string[] = selection.entries.map((entry) => entry.id);

  const [generate, { loading: generating }] = useMutation<
    GQLGenerateDerivedRequirementsData,
    GQLGenerateDerivedRequirementsVariables
  >(generateDerivedRequirementsMutation);

  const [applyItem, { loading: applying }] = useMutation<GQLApplyAiProposalItemData, GQLApplyAiProposalItemVariables>(
    applyAiProposalItemMutation
  );

  const onGenerate = () => {
    setProblems([]);
    setItems([]);
    setDecisions({});
    generate({
      variables: {
        input: {
          id: crypto.randomUUID(),
          editingContextId,
          elementIds: selectedIds,
          instruction,
        },
      },
    }).then(({ data }) => {
      const payload = data?.generateDerivedRequirements;
      if (payload && payload.__typename === 'GenerateDerivedRequirementsSuccessPayload') {
        const success = payload as GQLGenerateDerivedRequirementsSuccessPayload;
        setProposalId(success.proposalId);
        setItems(success.items);
        setProblems(success.problems);
      } else if (payload && payload.__typename === 'ErrorPayload') {
        setProblems(['The assistant could not be reached.']);
      }
    });
  };

  const onAccept = (item: GQLProposedItem) => {
    if (proposalId === null || selectedIds.length === 0) {
      return;
    }
    applyItem({
      variables: {
        input: {
          id: crypto.randomUUID(),
          editingContextId,
          proposalId,
          itemId: item.id,
          // The new element is created under the first selected element, which is also what the
          // generation was scoped from.
          parentElementId: selectedIds[0]!,
        },
      },
    }).then(({ data }) => {
      const applied = data?.applyAiProposalItem.__typename === 'SuccessPayload';
      setDecisions((current) => ({ ...current, [item.id]: applied ? 'applied' : 'failed' }));
    });
  };

  const onReject = (item: GQLProposedItem) => {
    setDecisions((current) => ({ ...current, [item.id]: 'rejected' }));
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md" data-testid="ai-review-dialog">
      <DialogTitle>Propose derived requirements</DialogTitle>
      <DialogContent>
        <Stack spacing={2}>
          <Typography variant="body2" color="textSecondary">
            {selectedIds.length === 0
              ? 'Select one or more elements in the explorer to derive requirements from.'
              : `Deriving from ${selectedIds.length} selected element(s).`}
          </Typography>
          <TextField
            label="What should be derived?"
            value={instruction}
            onChange={(event) => setInstruction(event.target.value)}
            multiline
            minRows={2}
            fullWidth
            data-testid="ai-instruction"
          />
          {problems.map((problem, index) => (
            <Alert severity="info" key={index}>
              {problem}
            </Alert>
          ))}
          {items.length > 0 ? <Divider /> : null}
          {items.map((item) => {
            const decision: ItemDecision = decisions[item.id] ?? 'pending';
            return (
              <Stack key={item.id} spacing={1} data-testid={`ai-candidate-${item.label}`}>
                <Typography variant="subtitle2">{item.label}</Typography>
                <Typography variant="body2" component="pre" sx={{ whiteSpace: 'pre-wrap', fontFamily: 'monospace' }}>
                  {item.sysmlFragment}
                </Typography>
                {decision === 'pending' ? (
                  <Stack direction="row" spacing={1}>
                    <Button size="small" variant="contained" disabled={applying} onClick={() => onAccept(item)}>
                      Accept
                    </Button>
                    <Button size="small" onClick={() => onReject(item)}>
                      Reject
                    </Button>
                  </Stack>
                ) : (
                  <Typography variant="caption" color={decision === 'failed' ? 'error' : 'textSecondary'}>
                    {decision}
                  </Typography>
                )}
              </Stack>
            );
          })}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button
          variant="contained"
          onClick={onGenerate}
          disabled={generating || instruction.trim().length === 0 || selectedIds.length === 0}
          data-testid="ai-generate">
          {generating ? <CircularProgress size={20} /> : 'Propose'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

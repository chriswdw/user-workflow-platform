import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import { useSaveDraft, useSubmitForApproval, useApproveSubmission, useRejectSubmission } from '../../api/useSubmissionActions';
import { client } from '../../api/client';

jest.mock('../../api/client', () => ({
  client: { patch: jest.fn(), post: jest.fn(), get: jest.fn() },
}));

const mockedPatch = client.patch as jest.Mock;
const mockedPost = client.post as jest.Mock;

const VALID_SUBMISSION = {
  id: 'sub-1',
  tenantId: 'tenant-1',
  workflowType: 'TRADE',
  displayName: 'Trade',
  description: null,
  statusCode: 'DRAFT',
  statusDisplayName: 'Draft',
  submittedBy: 'user-1',
  submittedAt: null,
  reviewedBy: null,
  reviewedAt: null,
  rejectionReason: null,
  draftConfigs: {},
  currentStep: 1,
  version: 1,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

let queryClient: QueryClient;

beforeEach(() => {
  jest.clearAllMocks();
  queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  mockedPatch.mockResolvedValue({ data: VALID_SUBMISSION });
  mockedPost.mockResolvedValue({ data: VALID_SUBMISSION });
});

function Wrapper({ children }: { children: React.ReactNode }) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe('useSaveDraft', () => {
  it('calls PATCH /workflow-type-submissions/{id} — no /draft suffix', async () => {
    const { result } = renderHook(() => useSaveDraft('sub-123'), { wrapper: Wrapper });

    result.current.mutate({ draftConfigs: {}, currentStep: 2 });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedPatch).toHaveBeenCalledWith(
      '/workflow-type-submissions/sub-123',
      { draftConfigs: {}, currentStep: 2 },
    );
    // Regression: was '/workflow-type-submissions/sub-123/draft' → 401 from Spring Security
    expect(mockedPatch).not.toHaveBeenCalledWith(
      expect.stringContaining('/draft'),
      expect.anything(),
    );
  });
});

describe('useSubmitForApproval', () => {
  it('calls POST /workflow-type-submissions/{id}/submit', async () => {
    const { result } = renderHook(() => useSubmitForApproval('sub-123'), { wrapper: Wrapper });

    result.current.mutate();
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedPost).toHaveBeenCalledWith('/workflow-type-submissions/sub-123/submit');
  });
});

describe('useApproveSubmission', () => {
  it('calls POST /workflow-type-submissions/{id}/approve', async () => {
    const { result } = renderHook(() => useApproveSubmission('sub-123'), { wrapper: Wrapper });

    result.current.mutate();
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedPost).toHaveBeenCalledWith('/workflow-type-submissions/sub-123/approve');
  });
});

describe('useRejectSubmission', () => {
  it('calls POST /workflow-type-submissions/{id}/reject with reason', async () => {
    const { result } = renderHook(() => useRejectSubmission('sub-123'), { wrapper: Wrapper });

    result.current.mutate({ rejectionReason: 'missing data' });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockedPost).toHaveBeenCalledWith(
      '/workflow-type-submissions/sub-123/reject',
      { rejectionReason: 'missing data' },
    );
  });
});

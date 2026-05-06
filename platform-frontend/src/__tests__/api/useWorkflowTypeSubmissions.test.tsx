import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import React from 'react';
import {
  usePendingSubmissions,
  useMyDraftSubmissions,
  useMyRejectedSubmissions,
  useAllDraftSubmissions,
} from '../../api/useWorkflowTypeSubmissions';
import { client } from '../../api/client';
import { useAuthStore } from '../../store/authStore';

jest.mock('../../api/client', () => ({
  client: { get: jest.fn(), patch: jest.fn(), post: jest.fn() },
}));

const mockedGet = client.get as jest.Mock;

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
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  mockedGet.mockResolvedValue({ data: [VALID_SUBMISSION] });
  useAuthStore.setState({ token: 'tok', tenantId: 'tenant-1', userId: 'user-1', role: 'ANALYST' });
});

afterEach(() => {
  useAuthStore.setState({ token: null, tenantId: null, userId: null, role: null });
});

function Wrapper({ children }: { children: React.ReactNode }) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe('submission query hooks — URL correctness', () => {
  it('usePendingSubmissions calls GET /workflow-type-submissions/pending', async () => {
    const { result } = renderHook(() => usePendingSubmissions(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedGet).toHaveBeenCalledWith('/workflow-type-submissions/pending');
  });

  it('useMyDraftSubmissions calls GET /workflow-type-submissions/my-drafts', async () => {
    const { result } = renderHook(() => useMyDraftSubmissions(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedGet).toHaveBeenCalledWith('/workflow-type-submissions/my-drafts');
  });

  it('useMyRejectedSubmissions calls GET /workflow-type-submissions/my-rejected', async () => {
    const { result } = renderHook(() => useMyRejectedSubmissions(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedGet).toHaveBeenCalledWith('/workflow-type-submissions/my-rejected');
  });

  it('useAllDraftSubmissions calls GET /workflow-type-submissions/all-drafts', async () => {
    const { result } = renderHook(() => useAllDraftSubmissions(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockedGet).toHaveBeenCalledWith('/workflow-type-submissions/all-drafts');
  });
});

describe('submission query hooks — no query-param style', () => {
  it('usePendingSubmissions does not use ?status= query params', async () => {
    const { result } = renderHook(() => usePendingSubmissions(), { wrapper: Wrapper });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    const url = mockedGet.mock.calls[0][0] as string;
    expect(url).not.toContain('?status');
    expect(url).not.toContain('?submittedBy');
  });
});

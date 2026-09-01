import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { StepSourceConfig } from '../../../components/wizard/StepSourceConfig';
import { useWizardStore } from '../../../store/wizardStore';
import * as sourceConnectionsModule from '../../../api/useSourceConnections';

jest.mock('../../../api/useSourceConnections');
const mockedUseSourceConnections = sourceConnectionsModule.useSourceConnections as jest.Mock;

beforeEach(() => {
  useWizardStore.setState({ sourceType: null, sourceConnectionId: null } as never);
  mockedUseSourceConnections.mockReturnValue({ data: [], isLoading: false });
});

describe('StepSourceConfig — radio button structure', () => {
  it('renders all four source type options', () => {
    render(<StepSourceConfig />);

    expect(screen.getByRole('radio', { name: 'Kafka Topic' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Database Poll' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'File Share' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Manual Upload' })).toBeInTheDocument();
  });

  it('each radio is labelled by its option text (accessible inline pairing)', () => {
    render(<StepSourceConfig />);

    // getByRole('radio', { name }) only resolves when the input is wrapped by,
    // or associated with, a label containing that text — confirming the HTML
    // structure that drives inline display.
    const radio = screen.getByRole('radio', { name: 'Kafka Topic' }) as HTMLInputElement;
    expect(radio.type).toBe('radio');
    expect(radio.name).toBe('sourceType');
    expect(radio.value).toBe('KAFKA');
  });

  it('starts with no option selected', () => {
    render(<StepSourceConfig />);

    const radios = screen.getAllByRole('radio') as HTMLInputElement[];
    expect(radios.every(r => !r.checked)).toBe(true);
  });
});

describe('StepSourceConfig — MANUAL_UPLOAD behaviour', () => {
  it('does not show a connection dropdown when Manual Upload is selected', async () => {
    const user = userEvent.setup();
    render(<StepSourceConfig />);

    await user.click(screen.getByRole('radio', { name: 'Manual Upload' }));

    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
  });
});

describe('StepSourceConfig — connection types show dropdown', () => {
  it('shows a connection dropdown when Kafka Topic is selected', async () => {
    mockedUseSourceConnections.mockReturnValue({
      data: [
        { id: 'c1', displayName: 'Prod Kafka', type: 'KAFKA', tenantId: 't1', config: {}, createdAt: '' },
      ],
      isLoading: false,
    });

    const user = userEvent.setup();
    render(<StepSourceConfig />);

    await user.click(screen.getByRole('radio', { name: 'Kafka Topic' }));

    expect(screen.getByRole('combobox')).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Prod Kafka' })).toBeInTheDocument();
  });

  it('shows "no connections" message when source type requires a connection but none are configured', async () => {
    mockedUseSourceConnections.mockReturnValue({ data: [], isLoading: false });

    const user = userEvent.setup();
    render(<StepSourceConfig />);

    await user.click(screen.getByRole('radio', { name: 'Database Poll' }));

    expect(screen.getByText(/no connections configured/i)).toBeInTheDocument();
  });
});

package de.rub.nds.tlsscanner.clientscanner.probe.downgrade;

import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsscanner.clientscanner.client.IOrchestrator;
import de.rub.nds.tlsscanner.clientscanner.dispatcher.DispatchInformation;
import de.rub.nds.tlsscanner.clientscanner.dispatcher.exception.DispatchException;
import de.rub.nds.tlsscanner.clientscanner.probe.BaseStatefulProbe;
import de.rub.nds.tlsscanner.clientscanner.report.requirements.ProbeRequirements;

public class DropConnection extends BaseStatefulProbe<DowngradeInternalState> {

    public DropConnection(IOrchestrator orchestrator) {
        super(orchestrator);
    }

    @Override
    protected ProbeRequirements getRequirements() {
        return null;
    }

    @Override
    protected DowngradeInternalState getDefaultState() {
        return new DowngradeInternalState(getClass());
    }

    @Override
    protected DowngradeInternalState execute(State state, DispatchInformation dispatchInformation, DowngradeInternalState internalState) throws DispatchException {
        // only analyze chlo
        internalState.putCHLO(dispatchInformation.chlo);
        executeState(state, dispatchInformation);
        return internalState;
    }
}

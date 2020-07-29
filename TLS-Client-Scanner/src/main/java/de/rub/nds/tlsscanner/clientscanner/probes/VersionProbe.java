package de.rub.nds.tlsscanner.clientscanner.probes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.constants.ProtocolVersion;
import de.rub.nds.tlsattacker.core.constants.RunningModeType;
import de.rub.nds.tlsattacker.core.protocol.message.ClientHelloMessage;
import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsattacker.core.workflow.WorkflowTrace;
import de.rub.nds.tlsattacker.core.workflow.action.ReceiveAction;
import de.rub.nds.tlsattacker.core.workflow.action.TlsAction;
import de.rub.nds.tlsattacker.core.workflow.factory.WorkflowConfigurationFactory;
import de.rub.nds.tlsattacker.core.workflow.factory.WorkflowTraceType;
import de.rub.nds.tlsscanner.clientscanner.dispatcher.StateDispatcher;

public class VersionProbe extends StateDispatcher<Map<ProtocolVersion, State>> {

    private static final Logger LOGGER = LogManager.getLogger();

    public VersionProbe() {
        this.defaultState = new HashMap<>();
    }

    @Override
    protected Map<ProtocolVersion, State> fillTrace(WorkflowTrace trace, State chloState,
            Map<ProtocolVersion, State> previousState) {
        ProtocolVersion toTest = null;
        for (ProtocolVersion v : ProtocolVersion.values()) {
            if (!previousState.containsKey(v)) {
                toTest = v;
                break;
            }
        }
        if (toTest != null) {
            LOGGER.debug("Testing version {}", toTest);
            Config fconfig = chloState.getConfig().createCopy();
            fconfig.setHighestProtocolVersion(toTest);
            fconfig.setDefaultSelectedProtocolVersion(toTest);
            fconfig.setEnforceSettings(true);
            fconfig.setDefaultApplicationMessageData("TLS Version:" + toTest);
            fconfig.setHttpsParsingEnabled(true);
            WorkflowConfigurationFactory factory = new WorkflowConfigurationFactory(fconfig);
            WorkflowTrace t = factory.createWorkflowTrace(WorkflowTraceType.HTTPS, RunningModeType.SERVER);
            List<TlsAction> actions = t.getTlsActions();
            TlsAction firstAction = actions.get(0);
            if (!(firstAction instanceof ReceiveAction
                    && ((ReceiveAction) firstAction).getExpectedMessages().size() == 1
                    && ((ReceiveAction) firstAction).getExpectedMessages().get(0) instanceof ClientHelloMessage)) {
                throw new RuntimeException("Unkown first action " + actions.get(0));
            }
            trace.addTlsActions(actions.subList(1, actions.size()));
        }
        return previousState;
    }

    @Override
    protected Map<ProtocolVersion, State> getNewStatePostExec(Map<ProtocolVersion, State> previousState, State state) {
        boolean res = state.getWorkflowTrace().executedAsPlanned();
        return previousState;
    }

}
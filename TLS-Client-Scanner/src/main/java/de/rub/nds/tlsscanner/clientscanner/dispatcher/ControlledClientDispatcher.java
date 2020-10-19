/**
 * TLS-Scanner - A TLS configuration and analysis tool based on TLS-Attacker.
 *
 * Copyright 2017-2019 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsscanner.clientscanner.dispatcher;

import java.util.concurrent.Future;

import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.rub.nds.tlsattacker.core.state.State;
import de.rub.nds.tlsscanner.clientscanner.dispatcher.exception.DispatchException;
import de.rub.nds.tlsscanner.clientscanner.dispatcher.sni.SNIDispatcher;
import de.rub.nds.tlsscanner.clientscanner.dispatcher.sni.SNIDispatcher.SNIDispatchInformation;
import de.rub.nds.tlsscanner.clientscanner.dispatcher.sni.SNIUidDispatcher;
import de.rub.nds.tlsscanner.clientscanner.dispatcher.sni.SNIUidDispatcher.UidInformation;
import de.rub.nds.tlsscanner.clientscanner.probe.IProbe;
import de.rub.nds.tlsscanner.clientscanner.report.ClientReport;
import de.rub.nds.tlsscanner.clientscanner.report.result.ClientAdapterResult;
import de.rub.nds.tlsscanner.clientscanner.report.result.ClientProbeResult;
import de.rub.nds.tlsscanner.clientscanner.util.helper.BaseFuture;
import de.rub.nds.tlsscanner.clientscanner.util.helper.DuoMapQ;

public class ControlledClientDispatcher implements IDispatcher {
    private static final Logger LOGGER = LogManager.getLogger();
    protected DuoMapQ<String, String, ClientProbeResultFuture> toRun;
    private boolean printedNoSNIWarning = false;

    public ControlledClientDispatcher() {
        toRun = new DuoMapQ<>();
    }

    public boolean isPrintedNoSNIWarning() {
        return printedNoSNIWarning;
    }

    @Override
    public ClientProbeResult execute(State state, DispatchInformation dispatchInformation) throws DispatchException {
        SNIDispatchInformation sniD = dispatchInformation.getAdditionalInformation(SNIDispatcher.class,
                SNIDispatchInformation.class);
        UidInformation uidD = dispatchInformation
                .getAdditionalInformation(SNIUidDispatcher.class, UidInformation.class);
        String sni = null, uid = null;
        if (sniD != null) {
            sni = sniD.remainingHostname;
        }
        if (uidD != null) {
            uid = uidD.uid;
        }

        ClientProbeResultFuture task = getNextTask(sni, uid);
        if (task == null) {
            throw new DispatchException("Did not find task");
        }
        try (final CloseableThreadContext.Instance ctc = CloseableThreadContext.push(task.probe.getClass()
                .getSimpleName())) {
            task.setGotConnection();
            dispatchInformation.additionalInformation.put(
                    getClass(),
                    new ControlledClientDispatchInformation(task.clientResultFuture, task.report));
            ClientProbeResult res = task.probe.execute(state, dispatchInformation);
            task.setResult(res);
            return res;
        } catch (Exception e) {
            task.setException(e);
            throw e;
        }
    }

    protected ClientProbeResultFuture getNextTask(String sni, String uid) throws DispatchException {
        LOGGER.debug("Trying to find task for sni {} with uid {}", sni, uid);
        ClientProbeResultFuture task;
        // we rely on the fact that either sni AND uid are present or none of
        // them as
        // both rely on SNI extension
        if (sni == null && uid != null) {
            throw new DispatchException("Internal Error - SNI is null, but uid is not null");
        }
        if (sni != null && uid == null) {
            throw new DispatchException("Internal Error - SNI is not null, but uid is null");
        }

        if (sni != null) {
            task = toRun.dequeue(sni, uid);
            if (task == null) {
                LOGGER.warn("Got hostname which we do not have a task for {}", sni);
            }
        } else {
            if (!printedNoSNIWarning) {
                printedNoSNIWarning = true;
                LOGGER.warn(
                        "Got no SNI - If the runner is using multiple threads this may cause issues as hostnames may not be matched to probes correctly");
            }
            // pick any probe
            Triple<String, String, ClientProbeResultFuture> taskTriple = toRun.dequeueAny();
            task = taskTriple.getRight();
            if (task == null) {
                LOGGER.warn("Got no tasks left (NO SNI)");
            } else {
                LOGGER.debug("Chose task with sni {} and uid {} (NO SNI)", taskTriple.getLeft(), taskTriple.getMiddle());
            }
        }
        return task;
    }

    public ClientProbeResultFuture enqueueProbe(IProbe probe, String expectedHostname, String expectedUid,
            Future<ClientAdapterResult> clientResultHolder, ClientReport report) {
        ClientProbeResultFuture ret = new ClientProbeResultFuture(probe, clientResultHolder, report);
        toRun.enqueue(expectedHostname, expectedUid, ret);
        return ret;
    }

    public static class ControlledClientDispatchInformation {
        public final Future<ClientAdapterResult> clientFuture;
        public final ClientReport report;

        public ControlledClientDispatchInformation(Future<ClientAdapterResult> clientFuture, ClientReport report) {
            this.clientFuture = clientFuture;
            this.report = report;
        }
    }

    public class ClientProbeResultFuture extends BaseFuture<ClientProbeResult> {
        protected final IProbe probe;
        protected final Future<ClientAdapterResult> clientResultFuture;
        protected final ClientReport report;
        protected boolean gotConnection = false;

        public ClientProbeResultFuture(IProbe probe, Future<ClientAdapterResult> clientResultFuture, ClientReport report) {
            this.probe = probe;
            this.report = report;
            this.clientResultFuture = clientResultFuture;
        }

        protected synchronized void setGotConnection() {
            gotConnection = true;
            notifyAll();
        }

        public synchronized boolean isGotConnection() {
            return gotConnection;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            throw new UnsupportedOperationException("Not implemented");
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

    }

}
package de.rub.nds.tlsscanner.clientscanner.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import de.rub.nds.tlsscanner.clientscanner.config.ClientScannerConfig;
import de.rub.nds.tlsscanner.clientscanner.probe.IProbe;
import de.rub.nds.tlsscanner.clientscanner.report.ClientReport;
import de.rub.nds.tlsscanner.clientscanner.report.result.ClientProbeResult;

public class ThreadLocalOrchestrator implements IOrchestrator {
    protected final ClientScannerConfig csConfig;
    private boolean isStarted = false;
    private boolean isCleanedUp = false;

    private Orchestrator unassignedOrchestrator = null;
    @SuppressWarnings("squid:S5164") // sonarlint: Call "remove()" on "localOrchestrator".
    // We cannot get each thread from the pool executor to call remove
    // Our solution is to cleanup each Orchestrator (using allOrchestrators) one by
    // one and setting localOrchestrator to null. This is by no means perfect and is
    // a memory leak for each thread, as we do not remove our threadLocal from each
    // Thread.threadLocals. But as the threads *should* not live much longer anyway,
    // this *should* not be a problem.
    // If this turns out to be a problem, I guess we should use reflection to access
    // each threads threadLocals and remove our localOrchestrator from there
    private ThreadLocal<Orchestrator> localOrchestrator;
    private final List<Orchestrator> allOrchestrators = new ArrayList<>();

    public ThreadLocalOrchestrator(ClientScannerConfig csConfig) {
        this.csConfig = csConfig;
    }

    protected Orchestrator createOrchestrator() {
        Orchestrator ret = new Orchestrator(csConfig);
        allOrchestrators.add(ret);
        if (isStarted) {
            ret.start();
        }
        if (isCleanedUp) {
            ret.cleanup();
        }
        return ret;
    }

    protected Orchestrator getAnyOrchestrator() {
        Orchestrator ret = null;
        synchronized (this) {
            if (allOrchestrators.isEmpty()) {
                unassignedOrchestrator = createOrchestrator();
                ret = unassignedOrchestrator;
            } else {
                ret = allOrchestrators.get(0);
            }
        }
        return ret;
    }

    protected Orchestrator getLocalOrchestrator() {
        Orchestrator ret = localOrchestrator.get();
        if (ret == null) {
            synchronized (this) {
                // check if we have one unassigned orch which we can reuse
                if (unassignedOrchestrator != null) {
                    ret = unassignedOrchestrator;
                    unassignedOrchestrator = null;
                }
            }
            if (ret == null) {
                // no unassigned orch - create a new one
                ret = createOrchestrator();
            }
            localOrchestrator.set(ret);
        }
        return ret;
    }

    @Override
    public ClientInfo getReportInformation() {
        return getAnyOrchestrator().getReportInformation();
    }

    @Override
    public void start() {
        if (isStarted) {
            throw new IllegalStateException("Orchestrator is already started");
        }
        isStarted = true;
        localOrchestrator = new ThreadLocal<>();
        for (Orchestrator o : allOrchestrators) {
            o.start();
        }
    }

    @Override
    public void cleanup() {
        if (!isStarted) {
            throw new IllegalStateException("Orchestrator is not yet started");
        }
        if (isCleanedUp) {
            throw new IllegalStateException("Orchestrator is already cleaned up");
        }
        isCleanedUp = true;
        for (Orchestrator o : new ArrayList<Orchestrator>(allOrchestrators)) {
            o.cleanup();
            allOrchestrators.remove(o);
        }
        localOrchestrator = null;
    }

    @Override
    public void postProcessing(ClientReport report) {
        for (Orchestrator o : allOrchestrators) {
            o.postProcessing(report);
        }
    }

    @Override
    public ClientProbeResult runProbe(IProbe probe, String hostnamePrefix, String uid, ClientReport report)
            throws InterruptedException, ExecutionException {
        return getLocalOrchestrator().runProbe(probe, hostnamePrefix, uid, report);
    }
}
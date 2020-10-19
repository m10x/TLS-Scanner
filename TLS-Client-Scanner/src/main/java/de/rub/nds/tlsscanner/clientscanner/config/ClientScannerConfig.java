/**
 * TLS-Scanner - A TLS configuration and analysis tool based on TLS-Attacker.
 *
 * Copyright 2017-2019 Ruhr University Bochum / Hackmanit GmbH
 *
 * Licensed under Apache License 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package de.rub.nds.tlsscanner.clientscanner.config;

import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.ParametersDelegate;

import de.rub.nds.tlsattacker.core.config.Config;
import de.rub.nds.tlsattacker.core.config.TLSDelegateConfig;
import de.rub.nds.tlsattacker.core.config.delegate.GeneralDelegate;
import de.rub.nds.tlsattacker.core.connection.InboundConnection;
import de.rub.nds.tlsattacker.core.constants.RunningModeType;
import de.rub.nds.tlsscanner.clientscanner.config.modes.ScanClientCommandConfig;
import de.rub.nds.tlsscanner.clientscanner.config.modes.StandaloneCommandConfig;

public class ClientScannerConfig extends TLSDelegateConfig {

    @ParametersDelegate
    public GeneralDelegate generalDelegate;
    @ParametersDelegate
    private CACertDelegate certificateDelegate;

    // #region Variables to be applied in Config
    @Parameter(names = "-timeout", required = false, description = "The timeout used for the scans in ms (default 1000)")
    private int timeout = 1000;

    @Parameter(names = "-bindaddr", required = false, description = "Hostname/IP to listen on. Defaults to any")
    protected String bindaddr = null;
    // #endregion

    // #region Variables that are handled elsewhere
    @Parameter(names = "-serverBaseURL", required = false, description = "Base URL to use for the server. Defaults to 127.0.0.1.xip.io")
    private String serverBaseURL = "127.0.0.1.xip.io";
    // #endregion

    public final JCommander jCommander;
    private boolean isParsed = false;
    protected ISubcommand selectedSubcommand;

    public ClientScannerConfig(GeneralDelegate delegate) {
        super(delegate);
        jCommander = new JCommander();
        jCommander.addObject(this);
        registerSubcommands();

        this.generalDelegate = delegate;
        addDelegate(delegate);

        this.certificateDelegate = new CACertDelegate();
        addDelegate(certificateDelegate);
    }

    protected void registerSubcommands() {
        new StandaloneCommandConfig().addToJCommander(jCommander);
        new ScanClientCommandConfig().addToJCommander(jCommander);
    }

    public synchronized void setParsed() throws ParameterException {
        if (isParsed) {
            return;
        }
        isParsed = true;
        // find selected subCommand
        String commandName = jCommander.getParsedCommand();
        JCommander commandJc = jCommander.getCommands().get(commandName);
        List<Object> cmdObjs = commandJc.getObjects();
        ISubcommand cmd = (ISubcommand) cmdObjs.get(0);
        selectedSubcommand = cmd;
        selectedSubcommand.setParsed(commandJc);

        // final error handling
        if (selectedSubcommand == null) {
            throw new ParameterException("Could not parse command (is still null)");
        }
    }

    @Override
    public Config createConfig() {
        setParsed(); // ensure we know the subcommand
        Config config = super.createConfig(Config.createConfig());
        config.getDefaultClientConnection().setTimeout(timeout);
        selectedSubcommand.applyDelegate(config);

        config.setDefaultRunningMode(RunningModeType.SERVER);
        InboundConnection inboundConnection = config.getDefaultServerConnection();
        if (inboundConnection == null) {
            config.setDefaultServerConnection(new InboundConnection(0, bindaddr));
        } else {
            inboundConnection.setHostname(bindaddr);
        }
        return config;
    }

    public ISubcommand getSelectedSubcommand() {
        return selectedSubcommand;
    }

    @SuppressWarnings({ "unchecked", "squid:S1172" })
    // unused parameter
    public <T> T getSelectedSubcommand(Class<T> expectedType) {
        return (T) selectedSubcommand;
    }

    public String getServerBaseURL() {
        return serverBaseURL;
    }
}

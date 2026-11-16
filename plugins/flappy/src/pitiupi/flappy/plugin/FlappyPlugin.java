package pitiupi.flappy.plugin;

import pitiupi.api.plugin.PeerActionsRegistrar;
import pitiupi.api.plugin.PluginWorkspace;
import pitiupi.application.peeractions.PeerAction;
import pitiupi.application.peeractions.PeerActionTarget;
import pitiupi.application.peeractions.PeerTarget;
import pitiupi.control.ConnectionManager;
import pitiupi.flappy.GUI.FlappyPanel;
import pitiupi.flappy.control.FlappyController;
import pitiupi.flappy.net.FlappyMatchMessage;
import pitiupi.flappy.net.FlappyStateMessage;
import pitiupi.models.Messages.Message;
import pitiupi.models.Plugin;

import javax.swing.SwingUtilities;
import java.util.List;

public class FlappyPlugin extends Plugin {

    private PluginWorkspace workspace;
    private FlappyController controller;
    private FlappyPanel panel;

    @Override
    public String getName() {
        return "FlappyBird";
    }

    @Override
    public String getDisplayName() {
        return "Flappy Bird P2P";
    }

    @Override
    public String getAuthor() {
        return "Redes P2P";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getIdentifier() {
        return "pitiupi.flappy";
    }

    @Override
    public void createPlugin(ConnectionManager connectionManager, PluginWorkspace workspace) {
        this.workspace = workspace;
        this.controller = new FlappyController(connectionManager);
        this.panel = new FlappyPanel(connectionManager, controller);
        bindShellWorkspaceContent(panel);
        installPeerActions(workspace.peerActions());
    }

    @Override
    public void installPeerActions(PeerActionsRegistrar registrar) {
        registrar.register(PeerAction.of(
                "flappy.room",
                "Abrir arena Flappy Bird (Sala)",
                PeerActionTarget.ALL_PEERS,
                target -> {
                    workspace.openOrFocusTab(getIdentifier(), getDisplayName());
                    if (panel != null) {
                        panel.focusMulticastRoom();
                    }
                }
        ));
        registrar.register(PeerAction.of(
                "flappy.peer",
                "Desafiar no Flappy Bird P2P",
                PeerActionTarget.SINGLE_PEER,
                target -> {
                    workspace.openOrFocusTab(getIdentifier(), getDisplayName());
                    if (target instanceof PeerTarget.Single single && panel != null) {
                        panel.focusPeerAddress(single.peer());
                    }
                }
        ));
    }

    @Override
    public List<Class<? extends Message>> getMessages() {
        return List.of(FlappyMatchMessage.class, FlappyStateMessage.class);
    }

    @Override
    public void receiveMessage(Message message) {
        SwingUtilities.invokeLater(() -> {
            if (controller == null || panel == null) {
                return;
            }
            if (message instanceof FlappyMatchMessage matchMsg) {
                controller.onMatchMessageReceived(matchMsg, panel.currentRoutingSnapshot());
            } else if (message instanceof FlappyStateMessage stateMsg) {
                controller.onStateMessageReceived(stateMsg);
            }
        });
    }

    @Override
    public void onShellTabClosed() {
        if (panel != null) {
            panel.stopGameLoop();
        }
    }
}


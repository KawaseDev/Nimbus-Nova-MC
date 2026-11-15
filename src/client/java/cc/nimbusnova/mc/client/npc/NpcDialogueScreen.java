package cc.nimbusnova.mc.client.npc;

import cc.nimbusnova.mc.client.MissionButton;
import cc.nimbusnova.mc.client.GuidedMissionClient;
import cc.nimbusnova.mc.client.NimbusNovaClient;
import cc.nimbusnova.mc.client.MissionConsoleScreen;
import cc.nimbusnova.mc.client.render.NovaRenderer;
import cc.nimbusnova.mc.client.render.NovaScreen;
import cc.nimbusnova.mc.npc.NpcDialoguePayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

@NullMarked
public final class NpcDialogueScreen extends NovaScreen {
    private final NpcDialoguePayload dialogue;
    private int topic;
    private int page;
    private int left, top, panelWidth, panelHeight;
    private List<String> lines = List.of();
    private int linesPerPage;
    private @Nullable MissionButton continueMission;

    public NpcDialogueScreen(final NpcDialoguePayload dialogue) {
        super(Component.literal("Talk with " + dialogue.name()));
        this.dialogue = dialogue;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(480, width - 24);
        panelHeight = Math.min(280, height - 24);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        final String[] choices = { "Mission briefing", "Power advice", "Oxygen advice" };
        final int buttonWidth = (panelWidth - 40) / 3;
        for (int index = 0; index < choices.length; index++) {
            final int selectedTopic = index;
            final MissionButton button = addRenderableWidget(new MissionButton(left + 14 + index * (buttonWidth + 6),
                    top + 48, buttonWidth, 24, choices[index], () -> {
                        topic = selectedTopic;
                        page = 0;
                        rebuildWidgets();
                    }));
            button.selected = topic == index;
        }

        final String message = switch (topic) {
            case 1 -> dialogue.powerHint();
            case 2 -> dialogue.oxygenHint();
            default -> dialogue.briefing();
        };
        lines = new ArrayList<>();
        String line = "";
        for (final String word : message.split(" ")) {
            final String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && NovaRenderer.width(candidate, 11) > panelWidth - 32) {
                lines.add(line);
                line = word;
            } else {
                line = candidate;
            }
        }

        if (!line.isEmpty())
            lines.add(line);

        linesPerPage = Math.max(2, (panelHeight - 144) / 14);
        final MissionButton previous = addRenderableWidget(new MissionButton(left + 14, top + panelHeight - 36,
                70, 22, "Previous", () -> {
                    page--;
                    rebuildWidgets();
                }));
        previous.active = page > 0;
        final MissionButton next = addRenderableWidget(new MissionButton(left + 90, top + panelHeight - 36,
                55, 22, "Next", () -> {
                    page++;
                    rebuildWidgets();
                }));
        next.active = (page + 1) * linesPerPage < lines.size();
        continueMission = addRenderableWidget(new MissionButton(left + panelWidth - 104, top + panelHeight - 36, 90, 22, "Next task", () -> {
            final var mission = NimbusNovaClient.missionSnapshot();
            if (mission != null && mission.state().phase().ended())
                minecraft.setScreenAndShow(new MissionConsoleScreen(3));
            else if (GuidedMissionClient.progress() != null && GuidedMissionClient.progress().stage().equals("ROLE"))
                minecraft.setScreenAndShow(new MissionConsoleScreen(1));
            else if (GuidedMissionClient.progress() != null && GuidedMissionClient.progress().ready())
                minecraft.setScreenAndShow(new MissionConsoleScreen());
            else
                onClose();
        }));
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        final var mission = NimbusNovaClient.missionSnapshot();
        if (continueMission != null)
            continueMission.setMessage(Component.literal(mission != null && mission.state().phase().ended() ? "Mission report"
                    : GuidedMissionClient.progress() != null && GuidedMissionClient.progress().stage().equals("ROLE") ? "Choose role"
                    : GuidedMissionClient.progress() != null && GuidedMissionClient.progress().ready() ? "Launch tablet" : "Next task"));
        final Color background = new Color(30, 28, 42, 245), text = new Color(248, 245, 235), muted = new Color(190, 187, 200);
        NovaRenderer.window(graphics, left, top, panelWidth, panelHeight, background);
        NovaRenderer.title(graphics, left, top, panelWidth, dialogue.name());
        NovaRenderer.text(graphics, GuidedMissionClient.progress() == null ? "Your astronaut guide"
                        : GuidedMissionClient.progress().title(), left + 16, top + 33, 9, muted);
        for (int line = 0; line < linesPerPage && page * linesPerPage + line < lines.size(); line++)
            NovaRenderer.text(graphics, lines.get(page * linesPerPage + line), left + 16, top + 88 + line * 14, 11, text);

        NovaRenderer.text(graphics, "Page " + (page + 1) + " / " + Math.max(1, Math.ceilDiv(lines.size(), linesPerPage)),
                left + 16, top + panelHeight - 52, 9, muted);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

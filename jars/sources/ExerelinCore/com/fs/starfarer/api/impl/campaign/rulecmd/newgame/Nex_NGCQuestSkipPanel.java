package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.questskip.QuestChainSkipEntry;
import exerelin.campaign.questskip.QuestSkipEntry;
import exerelin.campaign.ui.CustomPanelPluginWithInput;
import exerelin.campaign.ui.FramedCustomPanelPlugin;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.StringHelper;

import java.util.ArrayList;
import java.util.List;

import static com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import static com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;


public class Nex_NGCQuestSkipPanel {

    public static class QuestSkipDelegate implements CustomDialogDelegate {

        FramedCustomPanelPlugin plugin = new FramedCustomPanelPlugin(0.25f, Misc.getBasePlayerColor(), true);

        protected void addQuestPanel(TooltipMakerAPI scrollable, CustomPanelAPI outer, QuestChainSkipEntry chain) {

            TooltipCreator tooltip = null;
            if (chain.tooltipCreatorClass != null) {
                tooltip = (TooltipCreator)NexUtils.instantiateClassByName(chain.tooltipCreatorClass);
            }
            else if (chain.tooltip != null) {
                tooltip = NexUtilsGUI.createSimpleTextTooltip(chain.tooltip, 450);
            }

            CustomPanelAPI buttonPanel = Nex_NGCPopulateCustomPanelOptions.prepOption(outer, scrollable, chain.name, chain.image, null, tooltip);

            final List<ButtonAPI> buttons = new ArrayList<>();
            TooltipMakerAPI lastHolder = null;

            for (QuestSkipEntry entry : chain.quests) {
                if (entry.plugin != null && !entry.plugin.shouldShow()) continue;

                String name = entry.name;

                lastHolder = Nex_NGCPopulateCustomPanelOptions.initRadioButton("nex_skipQuest_" + entry.id, name,
                        false, buttonPanel, lastHolder, buttons);
                // bigger buttons
                float width = Math.round(Nex_NGCPopulateCustomPanelOptions.BUTTON_WIDTH * 1.8f);
                float height = Nex_NGCPopulateCustomPanelOptions.ITEM_HEIGHT;
                lastHolder.getPosition().setSize(width, height);
                ButtonAPI button = buttons.get(buttons.size() - 1);
                button.getPosition().setSize(width, height);
                button.setChecked(entry.isEnabled);

                // add button tooltip if needed
                TooltipCreator tooltip2 = null;
                if (entry.tooltipCreatorClass != null) {
                    tooltip2 = (TooltipCreator)NexUtils.instantiateClassByName(entry.tooltipCreatorClass);
                }
                else if (entry.tooltip != null) {
                    tooltip2 = NexUtilsGUI.createSimpleTextTooltip(entry.tooltip, 450);
                }
                if (tooltip2 != null) {
                    lastHolder.addTooltipToPrevious(tooltip2, TooltipLocation.BELOW);
                }
            }
            final List<SkipQuestButtonEntry> buttonEntries = new ArrayList<>();

            int index = 0;
            CustomPanelPluginWithInput plugin = (CustomPanelPluginWithInput)this.getCustomPanelPlugin();
            for (QuestSkipEntry entry : chain.quests) {
                SkipQuestButtonEntry button = new SkipQuestButtonEntry(buttons.get(index), entry, chain, buttonEntries);
                buttonEntries.add(button);
                plugin.addButton(button);

                index++;
            }
        }

        @Override
        public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
            // iterate over quest chain defs
            // for each def, create a panel
            // put a row of buttons in each panel, each corresponding to a quest

            TooltipMakerAPI scrollable = panel.createUIElement(panel.getPosition().getWidth(), panel.getPosition().getHeight(), true);
            scrollable.setParaOrbitronLarge();
            scrollable.addPara(StringHelper.getString("exerelin_ngc", "panelHeaderSkipStory"), 3);
            scrollable.setParaFontDefault();

            for (QuestChainSkipEntry chain : QuestChainSkipEntry.getEntries()) {
                if (chain.plugin != null && !chain.plugin.shouldShow()) continue;
                addQuestPanel(scrollable, panel, chain);
            }

            //scrollable.addSectionHeading("wallahi", Alignment.MID, 10);

            panel.addUIElement(scrollable);
        }

        @Override
        public boolean hasCancelButton() {
            return false;
        }

        @Override
        public String getConfirmText() {
            return null;
        }

        @Override
        public String getCancelText() {
            return null;
        }

        @Override
        public void customDialogConfirm() {

        }

        @Override
        public void customDialogCancel() {

        }

        @Override
        public CustomUIPanelPlugin getCustomPanelPlugin() {
            return plugin;
        }
    }

    public static class SkipQuestButtonEntry extends CustomPanelPluginWithInput.ButtonEntry {

        public String questId;
        public QuestSkipEntry quest;
        public QuestChainSkipEntry questChain;
        public List<SkipQuestButtonEntry> allButtons;

        public SkipQuestButtonEntry(ButtonAPI button, QuestSkipEntry quest, QuestChainSkipEntry chainEntry, List<SkipQuestButtonEntry> allButtons) {
            super(button, "nex_skipQuest_" + quest.id);
            this.questId = quest.id;
            this.quest = quest;
            this.questChain = chainEntry;
            this.allButtons = allButtons;
        }

        @Override
        public void onToggle() {
            boolean on = this.state;
            setEnabled(on, true);
        }

        protected void updateOtherQuests(boolean on) {
            for (SkipQuestButtonEntry other : allButtons) {
                if (other == this) continue;

                boolean sameChain = this.questChain == other.questChain;

                if (on) {
                    if (quest.idsToForceOnWhenEnabled.contains(other.questId)) {
                        other.setEnabled(true, !sameChain);
                    }
                    if (quest.idsToForceOffWhenEnabled.contains(other.questId)) {
                        other.setEnabled(false, !sameChain);
                    }
                } else {
                    if (quest.idsToForceOnWhenDisabled.contains(other.questId)) {
                        other.setEnabled(true, !sameChain);
                    }
                    if (quest.idsToForceOffWhenDisabled.contains(other.questId)) {
                        other.setEnabled(false, !sameChain);
                    }
                }
            }
        }

        /**
         * Call this when toggling a quest from outside (e.g. another quest).
         * @param enabled
         * @param updateOtherQuests Enable/disable other quests as appropriate, based on {@code idsToForceOnWhenEnabled} etc.
         *                          Warning: May cause infinite loops.
         * @return
         */
        public void setEnabled(boolean enabled, boolean updateOtherQuests) {
            setState(enabled);
            quest.isEnabled = enabled;
            if (quest.plugin != null) {
                if (enabled) quest.plugin.onEnabled();
                else quest.plugin.onDisabled();
            }
            if (updateOtherQuests) {
                updateOtherQuests(enabled);
            }
        }
    }
}

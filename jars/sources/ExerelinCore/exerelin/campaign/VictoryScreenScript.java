package exerelin.campaign;

import java.util.Map;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.SectorManager.VictoryType;
import java.util.HashMap;

// adapted from UpdateNotificationScript in LazyWizard's Version Checker
public class VictoryScreenScript implements EveryFrameScript
{
    private boolean isDone = false;
    private String faction = "player_npc";
    private VictoryType victoryType = VictoryType.CONQUEST;

    public VictoryScreenScript(String faction, VictoryType victoryType)
    {
        this.faction = faction;
        this.victoryType = victoryType;
    }

    @Override
    public boolean isDone()
    {
        return isDone;
    }

    @Override
    public boolean runWhilePaused()
    {
        return true;
    }

    
    @Override
    public void advance(float amount)
    {
        // Don't do anything while in a menu/dialog
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        if (Global.getSector().isInNewGameAdvance() || ui.isShowingDialog())
        {
            return;
        }

        if (!isDone)
        {
            ui.showInteractionDialog(new VictoryDialog(faction, victoryType), Global.getSector().getPlayerFleet());
            Map<String, Object> params = new HashMap<>();
            boolean playerVictory = faction.equals(PlayerFactionStore.getPlayerFactionId());
            params.put("victorFactionId", faction);
            params.put("diplomaticVictory", false);
            params.put("playerVictory", playerVictory);
            Global.getSector().getEventManager().startEvent(
                    new CampaignEventTarget(Global.getSector().getPlayerFleet()), "exerelin_victory", params);
            
            isDone = true;
        }
    }

    private static class VictoryDialog implements InteractionDialogPlugin
    {
        
        private InteractionDialogAPI dialog;
        private TextPanelAPI text;
        private OptionPanelAPI options;
        private final String faction;
        private final VictoryType victoryType;

        private enum Menu
        {
            CREDITS,
            EXIT
        }

        private VictoryDialog(String faction, VictoryType victoryType)
        {
            this.faction = faction;
            this.victoryType = victoryType;
        }
        
        private void printCreditLine(String name, String contribution)
        {
            text.addParagraph(name + ": " + contribution);
            text.highlightInLastPara(name);
        }
        
        private void printCredits()
        {
            printCreditLine("Zaphide", "The original Exerelin");
            printCreditLine("Histidine", "Update for Starsector 0.65");
            printCreditLine("Dark.Revenant", "Much coding help; SS+ compatibility");
            printCreditLine("LazyWizard", "Omnifactory, Console Commands, Version Checker");
            printCreditLine("Psiyon", "Backgrounds");
            printCreditLine("Tartiflette", "Prism Freeport");
            printCreditLine("The SS mod community", "Various tips, pointers and feedback");
            printCreditLine("Alex, David, Stian, Ivaylo", "The Fractal Softworks team that made Starsector");
        }

        @Override
        public void init(InteractionDialogAPI dialog)
        {
            this.dialog = dialog;
            this.options = dialog.getOptionPanel();
            this.text = dialog.getTextPanel();

            //dialog.setTextWidth(Display.getWidth() * .9f);
            
            String factionName = Global.getSector().getFaction(faction).getDisplayName();
            String theFactionName = Global.getSector().getFaction(faction).getDisplayNameWithArticle();
            if (victoryType == VictoryType.CONQUEST)
            {
                text.addParagraph("Congratulations! " + Misc.ucFirst(theFactionName)
                        + " has crushed all opposition and now reigns supreme over the Exerelin cluster!");
                text.highlightInLastPara(factionName);
                text.addParagraph("You have won a conquest victory!");
                text.highlightInLastPara("conquest");
            }
            else if (victoryType == VictoryType.DIPLOMATIC)
            {
                text.addParagraph("Congratulations! " + Misc.ucFirst(theFactionName)
                        + " and its allies control the Exerelin cluster!");
                text.highlightInLastPara(factionName);
                text.addParagraph("You have won a diplomatic victory!");
                text.highlightInLastPara("diplomatic");
            }
            dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual("graphics/illustrations/terran_orbit.jpg", 640, 400));
            Global.getSoundPlayer().playUISound("music_campaign_victory_theme", 1, 1);
            
            options.addOption("Credits", Menu.CREDITS);
            options.addOption("Close", Menu.EXIT);
            dialog.setPromptText("What now?");
        }

        @Override
        public void optionSelected(String optionText, Object optionData)
        {
            if (optionText != null) {
                    text.addParagraph(optionText, Global.getSettings().getColor("buttonText"));
            }

            // Option was a menu? Go to that menu
            if (optionData == Menu.CREDITS)
            {
                printCredits();
                options.clearOptions();
                options.addOption("Close", Menu.EXIT);
            }
            else if (optionData == Menu.EXIT)
            {
                dialog.dismiss();
            }
        }

        @Override
        public void optionMousedOver(String optionText, Object optionData)
        {
        }

        @Override
        public void advance(float amount)
        {
        }

        @Override
        public void backFromEngagement(EngagementResultAPI battleResult)
        {
        }

        @Override
        public Object getContext()
        {
            return null;
        }

        @Override
        public Map<String, MemoryAPI> getMemoryMap()
        {
            return null;
        }
    }
}

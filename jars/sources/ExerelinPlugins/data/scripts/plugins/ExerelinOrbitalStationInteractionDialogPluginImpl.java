package data.scripts.plugins;

import java.awt.Color;

import com.fs.starfarer.api.campaign.*;
import data.scripts.world.exerelin.utilities.ExerelinConfig;
import data.scripts.world.exerelin.utilities.ExerelinMessageManager;
import data.scripts.world.exerelin.utilities.ExerelinMessage;
import data.scripts.world.exerelin.utilities.ExerelinUtilsFaction;
import org.lwjgl.input.Keyboard;
import org.json.JSONObject;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

public class ExerelinOrbitalStationInteractionDialogPluginImpl implements InteractionDialogPlugin, CoreInteractionListener {

    private static enum OptionId {
        INIT,
        INIT_NO_TEXT,
        TRADE_CARGO,
        TRADE_SHIPS,
        REFIT,
        REPAIR_ALL,
        PLANT_AGENT,
        DROP_OFF_PRISONER,
        PLANT_SABOTEUR,
        DISPLAY_ALLIES,
        DISPLAY_ENEMIES,
        DISPLAY_MESSAGES,
        LEAVE,
    }

    private InteractionDialogAPI dialog;
    private TextPanelAPI textPanel;
    private OptionPanelAPI options;
    private VisualPanelAPI visual;

    private CampaignFleetAPI playerFleet;
    private SectorEntityToken station;

    private static final Color HIGHLIGHT_COLOR = Global.getSettings().getColor("buttonShortcut");

    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        textPanel = dialog.getTextPanel();
        options = dialog.getOptionPanel();
        visual = dialog.getVisualPanel();

        playerFleet = Global.getSector().getPlayerFleet();
        station = (SectorEntityToken) dialog.getInteractionTarget();

        visual.setVisualFade(0.25f, 0.25f);

        dialog.setOptionOnEscape("Leave", OptionId.LEAVE);

        optionSelected(null, OptionId.INIT);
    }

    private EngagementResultAPI lastResult = null;
    public void backFromEngagement(EngagementResultAPI result) {
        // no combat here, so this won't get called
    }

    public void optionSelected(String text, Object optionData) {
        if (optionData == null) return;

        OptionId option = (OptionId) optionData;

        if (text != null) {
            textPanel.addParagraph(text, Global.getSettings().getColor("buttonText"));
        }

        switch (option) {
            case INIT:
                addText(getString("approach"));
            case INIT_NO_TEXT:
                createInitialOptions();
                if (station.getCustomInteractionDialogImageVisual() != null) {
                    visual.showImageVisual(station.getCustomInteractionDialogImageVisual());
                } else {
                    visual.showImagePortion("illustrations", "hound_hangar", 800, 800, 0, 0, 400, 400);
                }
                break;
            case TRADE_CARGO:
                addText(getString("tradeCargo"));
                options.clearOptions();
                visual.showCore(CoreUITabId.CARGO, station, station.getFaction().isNeutralFaction(), this);
                break;
            case TRADE_SHIPS:
                addText(getString("tradeShips"));
                options.clearOptions();
                visual.showCore(CoreUITabId.FLEET, station, station.getFaction().isNeutralFaction(), this);
                break;
            case REFIT:
                addText(getString("refit"));
                options.clearOptions();
                visual.showCore(CoreUITabId.REFIT, station, station.getFaction().isNeutralFaction(), this);
                break;
            case REPAIR_ALL:
                performRepairs();
                createInitialOptions();
                break;
            case PLANT_AGENT:
                Global.getSector().getPlayerFleet().getCargo().removeItems(CargoAPI.CargoItemType.RESOURCES, "agent", 1);
                station.getCargo().addItems(CargoAPI.CargoItemType.RESOURCES, "agent", 1);
                options.clearOptions();
                createInitialOptions();
                break;
            case DROP_OFF_PRISONER:
                Global.getSector().getPlayerFleet().getCargo().removeItems(CargoAPI.CargoItemType.RESOURCES, "prisoner", 1);
                station.getCargo().addItems(CargoAPI.CargoItemType.RESOURCES, "prisoner", 1);
                options.clearOptions();
                createInitialOptions();
                break;
            case PLANT_SABOTEUR:
                Global.getSector().getPlayerFleet().getCargo().removeItems(CargoAPI.CargoItemType.RESOURCES, "saboteur", 1);
                station.getCargo().addItems(CargoAPI.CargoItemType.RESOURCES, "saboteur", 1);
                options.clearOptions();
                createInitialOptions();
                break;
            case DISPLAY_ALLIES:
                this.displayRleationships(1);
                break;
            case DISPLAY_ENEMIES:
                this.displayRleationships(-1);
                break;
            case DISPLAY_MESSAGES:
                this.displayMessages();
                break;
            case LEAVE:
                Global.getSector().setPaused(false);
                dialog.dismiss();
                break;
        }
    }

    private void performRepairs() {
        addText(getString("repair"));
        float supplies = playerFleet.getCargo().getSupplies();
        float needed = playerFleet.getLogistics().getTotalRepairSupplyCost();

        textPanel.highlightLastInLastPara("" + (int) needed, HIGHLIGHT_COLOR);

        for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
            member.getStatus().repairFully();
            float max = member.getRepairTracker().getMaxCR();
            float curr = member.getRepairTracker().getBaseCR();
            if (max > curr) {
                member.getRepairTracker().applyCREvent(max - curr, "Repaired at station");
            }
        }
        if (needed > 0) {
            playerFleet.getCargo().removeSupplies(needed);
        }
    }

    private void createInitialOptions() {
        options.clearOptions();

        if (station.getFaction().isNeutralFaction() || station.getFullName().contains("Omnifactory")) {
            options.addOption("Transfer cargo or personnel", OptionId.TRADE_CARGO);
            options.setShortcut(OptionId.TRADE_CARGO, Keyboard.KEY_I, false, false, false, true);
            options.addOption("Transfer ships to or from this station", OptionId.TRADE_SHIPS);
            options.setShortcut(OptionId.TRADE_SHIPS, Keyboard.KEY_F, false, false, false, true);
            options.addOption("Make use of the dockyard's refitting facilities", OptionId.REFIT);
            options.setShortcut(OptionId.REFIT, Keyboard.KEY_R, false, false, false, true);
        } else {
            if (station.getFaction().getId().equalsIgnoreCase(Global.getSector().getPlayerFleet().getFaction().getId())
                    || (station.getFaction().getRelationship(Global.getSector().getPlayerFleet().getFaction().getId()) >= 1 && ExerelinConfig.allowTradeAtAlliedStations)
                    || (station.getFaction().getRelationship(Global.getSector().getPlayerFleet().getFaction().getId()) >= 0 && station.getFaction().getRelationship(Global.getSector().getPlayerFleet().getFaction().getId()) < 1 && ExerelinConfig.allowTradeAtNeutralStations)
                    || (station.getFaction().getRelationship(Global.getSector().getPlayerFleet().getFaction().getId()) < 0 && ExerelinConfig.allowTradeAtHostileStations))
            {
                options.addOption("Trade, or hire personnel", OptionId.TRADE_CARGO);
                options.setShortcut(OptionId.TRADE_CARGO, Keyboard.KEY_I, false, false, false, true);
                options.addOption("Buy or sell ships", OptionId.TRADE_SHIPS, null);
                options.setShortcut(OptionId.TRADE_SHIPS, Keyboard.KEY_F, false, false, false, true);
                options.addOption("Make use of the dockyard's refitting facilities", OptionId.REFIT);
                options.setShortcut(OptionId.REFIT, Keyboard.KEY_R, false, false, false, true);
            }

            if(Global.getSector().getPlayerFleet().getCargo().getQuantity(CargoAPI.CargoItemType.RESOURCES, "agent") > 0
                    && !station.getFaction().getId().equalsIgnoreCase(Global.getSector().getPlayerFleet().getFaction().getId()))
                options.addOption("Plant agent on station", OptionId.PLANT_AGENT);

            if(Global.getSector().getPlayerFleet().getCargo().getQuantity(CargoAPI.CargoItemType.RESOURCES, "prisoner") > 0
                    && !station.getFaction().getId().equalsIgnoreCase(Global.getSector().getPlayerFleet().getFaction().getId()))
                options.addOption("Drop prisoner off at station", OptionId.DROP_OFF_PRISONER);

            if(Global.getSector().getPlayerFleet().getCargo().getQuantity(CargoAPI.CargoItemType.RESOURCES, "saboteur") > 0
                    && !station.getFaction().getId().equalsIgnoreCase(Global.getSector().getPlayerFleet().getFaction().getId()))
                options.addOption("Plant saboteur on station", OptionId.PLANT_SABOTEUR);
        }

        if (station.getFaction().getRelationship(Global.getSector().getPlayerFleet().getFaction().getId()) >= 1
                || station.getFaction().getId().equalsIgnoreCase(Global.getSector().getPlayerFleet().getFaction().getId())
                || station.getFaction().isNeutralFaction()) {
            float needed = playerFleet.getLogistics().getTotalRepairSupplyCost();
            float supplies = playerFleet.getCargo().getSupplies();
            options.addOption("Repair your ships at the station's dockyard", OptionId.REPAIR_ALL);
            options.setShortcut(OptionId.REPAIR_ALL, Keyboard.KEY_A, false, false, false, true);

            if (needed <= 0) {
                options.setEnabled(OptionId.REPAIR_ALL, false);
                options.setTooltip(OptionId.REPAIR_ALL, getString("repairTooltipAlreadyRepaired"));
            } else if (supplies < needed) {
                options.setEnabled(OptionId.REPAIR_ALL, false);
                options.setTooltip(OptionId.REPAIR_ALL, getString("repairTooltipNotEnough"));
                options.setTooltipHighlightColors(OptionId.REPAIR_ALL, HIGHLIGHT_COLOR, HIGHLIGHT_COLOR);
                options.setTooltipHighlights(OptionId.REPAIR_ALL, "" + (int) needed, "" + (int) supplies);
            } else {
                options.setTooltip(OptionId.REPAIR_ALL, getString("repairTooltip"));
                options.setTooltipHighlightColors(OptionId.REPAIR_ALL, HIGHLIGHT_COLOR, HIGHLIGHT_COLOR);
                options.setTooltipHighlights(OptionId.REPAIR_ALL, "" + (int) needed, "" + (int) supplies);
            }
        }

        //display diplomacy reports and message history
        if(!station.getFaction().getId().equalsIgnoreCase("abandoned") && !station.getFaction().getId().equalsIgnoreCase("rebel"))
        {
            options.addOption("Alliance Report", OptionId.DISPLAY_ALLIES);
            options.addOption("Enemy Report", OptionId.DISPLAY_ENEMIES);
        }
        options.addOption("Display Messages", OptionId.DISPLAY_MESSAGES);
        options.addOption("Leave", OptionId.LEAVE);
    }


    private OptionId lastOptionMousedOver = null;
    public void optionMousedOver(String optionText, Object optionData) {

    }

    public void advance(float amount) {

    }

    private void addText(String text) {
        textPanel.addParagraph(text);
    }

    private void appendText(String text) {
        textPanel.appendToLastParagraph(" " + text);
    }

    private String getString(String id) {
        String str = Global.getSettings().getString("stationInteractionDialog", id);

        String fleetOrShip = "fleet";
        if (playerFleet.getFleetData().getMembersListCopy().size() == 1) {
            fleetOrShip = "ship";
            if (playerFleet.getFleetData().getMembersListCopy().get(0).isFighterWing()) {
                fleetOrShip = "fighter wing";
            }
        }
        str = str.replaceAll("\\$fleetOrShip", fleetOrShip);
        str = str.replaceAll("\\$stationName", station.getFullName());

        float needed = playerFleet.getLogistics().getTotalRepairSupplyCost();
        float supplies = playerFleet.getCargo().getSupplies();
        str = str.replaceAll("\\$supplies", "" + (int) supplies);
        str = str.replaceAll("\\$repairSupplyCost", "" + (int) needed);

        return str;
    }


    public Object getContext() {
        return null;
    }

    public void coreUIDismissed() {
        optionSelected(null, OptionId.INIT_NO_TEXT);
    }

    public void displayRleationships(int value)
    {
        if(value == 1)
        {
            // Display allies
            List<String> allies = ExerelinUtilsFaction.getFactionsAlliedWithFaction(this.station.getFaction().getId());
            if(allies.size() > 0)
                textPanel.addParagraph("Current allies of " + this.station.getFaction().getDisplayName() + ":");
            else
                textPanel.addParagraph(this.station.getFaction().getDisplayName() + " has no allies currently.");

            for(int i = 0; i < allies.size(); i++)
            {
                if(!allies.get(i).equalsIgnoreCase(this.station.getFaction().getId()))
                    textPanel.addParagraph(Global.getSector().getFaction(allies.get(i)).getDisplayName());
            }
        }
        else if(value == -1)
        {
            // Display enemies
            List<String> enemies = ExerelinUtilsFaction.getFactionsAtWarWithFaction(this.station.getFaction().getId());
            if(enemies.size() > 2) // Always at war with Rebel, Abandoned
                textPanel.addParagraph(this.station.getFaction().getDisplayName() + " is currently at war with:");
            else
                textPanel.addParagraph(this.station.getFaction().getDisplayName() + " has no enemies currently.");

            for(int i = 0; i < enemies.size(); i++)
            {
                if(!enemies.get(i).equalsIgnoreCase("rebel")
                        && !enemies.get(i).equalsIgnoreCase("abandoned")
                        && !enemies.get(i).equalsIgnoreCase("gedune_drone"))
                    textPanel.addParagraph(Global.getSector().getFaction(enemies.get(i)).getDisplayName());
            }
        }
    }

    public void displayMessages()
    {
        List<ExerelinMessage> messages = ((ExerelinMessageManager)Global.getSector().getPersistentData().get("ExerelinMessageManager")).getMessages();

        for(ExerelinMessage message : messages)
        {
            if(message.color == null)
                textPanel.addParagraph(message.message);
            else
                textPanel.addParagraph(message.message, message.color);
        }
    }
}




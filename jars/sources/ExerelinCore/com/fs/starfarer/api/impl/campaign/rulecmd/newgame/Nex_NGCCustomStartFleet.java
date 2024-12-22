package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_VisualCustomPanel;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.ui.CPButtonPressRelayPlugin;
import exerelin.campaign.ui.CustomPanelPluginWithInput;
import exerelin.campaign.ui.InteractionDialogCustomPanelPlugin;
import exerelin.campaign.ui.ProgressBar;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.StringHelper;
import lombok.extern.log4j.Log4j;
import org.json.JSONArray;
import org.json.JSONException;

import java.awt.*;
import java.util.List;
import java.util.*;

@Log4j
public class Nex_NGCCustomStartFleet extends BaseCommandPlugin {

    public static final float PANEL_WIDTH = 1024;
    public static final float PANEL_HEIGHT = 720;
    public static final float SHIP_PANEL_WIDTH = 96;
    public static final float SHIP_PANEL_HEIGHT = 128;
    public static final float HEADER_HEIGHT = 64;
    public static final String MEMORY_KEY_SHIP_MAP = "$nex_customStartFleet";
    public static final int[] COST_THRESHOLDS;
    public static final Color[] COST_COLORS = new Color[] {
            Color.WHITE, Color.CYAN, Color.GREEN, Misc.getHighlightColor(), Color.ORANGE, Color.MAGENTA, Misc.getNegativeHighlightColor()
    };

    static {
        try {
            JSONArray array = Global.getSettings().getJSONArray("nex_customStartFleetThresholds");
            COST_THRESHOLDS = new int[array.length()];
            for (int i=0; i<array.length(); i++) {
                int val = array.getInt(i);
                COST_THRESHOLDS[i] = val;
            }
        } catch (JSONException jex) {
            throw new RuntimeException("Failed to load cost thresholds", jex);
        }
    }
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		createDialog(dialog, memoryMap, PlayerFactionStore.getPlayerFactionIdNGC());
		return true;
	}

    public static void createDialog(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap, String factionId) {
        dialog.showCustomDialog(PANEL_WIDTH, PANEL_HEIGHT, new CustomStartFleetDelegate(dialog, factionId, memoryMap));
    }

    public static String getString(String id) {
        return StringHelper.getString("exerelin_ngc", id);
    }

    public static class CustomStartFleetDelegate implements CustomDialogDelegate {

        protected InteractionDialogAPI dialog;
        protected InteractionDialogCustomPanelPlugin plugin;
        protected Map<String, Integer> ships;
        protected FactionAPI faction;
        protected MemoryAPI mem;
        protected Map<String, MemoryAPI> memoryMap;
        protected CustomPanelAPI panel;
        protected CustomPanelAPI header;
        protected int numShips;
        protected float cost;

        public CustomStartFleetDelegate(InteractionDialogAPI dialog, String factionId, Map<String, MemoryAPI> memoryMap) {
            this.dialog = dialog;
            faction = Global.getSector().getFaction(factionId);
            this.memoryMap = memoryMap;
            mem = memoryMap.get(MemKeys.LOCAL);
            if (mem.contains(MEMORY_KEY_SHIP_MAP)) {
                //log.info("Existing ship map found");
                ships = (Map<String, Integer>)mem.get(MEMORY_KEY_SHIP_MAP);
            } else {
                //log.info("No ship map found, creating");
                ships = new LinkedHashMap<>();
                mem.set(MEMORY_KEY_SHIP_MAP, ships, 0);
            }

            plugin = new InteractionDialogCustomPanelPlugin(0, false);
            plugin.isCheckButtonsEveryFrame = false;
        }

        public float updateCost() {
            float cost = 0;
            int numShips = 0;
            for (String ship : ships.keySet()) {
                int count = ships.get(ship);
                if (count <= 0) continue;
                numShips += count;
                cost += Global.getSettings().getHullSpec(ship).getBaseValue() * count;
            }
            this.cost = cost;
            this.numShips = numShips;
            return cost;
        }

        public int getCostPowerLevel() {
            int level = 0;
            for (int threshold : COST_THRESHOLDS) {
                if (cost <= threshold) return level;
                level++;
            }
            return level;
        }

        public Color getCostPowerColor(int power) {
            return COST_COLORS[power];
        }

        protected CustomPanelAPI createHeader(CustomPanelAPI panel) {
            float pad = 3;

            if (header != null) {
                panel.removeComponent(header);
            }
            updateCost();

            header = panel.createCustomPanel(PANEL_WIDTH - 4, HEADER_HEIGHT, null);
            TooltipMakerAPI headerTopTT = header.createUIElement(PANEL_WIDTH - 2, 24, false);
            headerTopTT.addSectionHeading(getString("customFleetHeader"), faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, 0);
            headerTopTT.addPara(getString("customFleetVariantDesc"), pad);
            header.addUIElement(headerTopTT).inTL(0, 0);

            TooltipMakerAPI headerLeftTT = header.createUIElement(PANEL_WIDTH/3, HEADER_HEIGHT - 24, false);
            String numShipsStr = getString("customFleetShipCount") + Global.getSettings().getInt("maxShipsInFleet");
            headerLeftTT.addPara(numShipsStr, 0, Misc.getHighlightColor(), numShips + "");
            String costStr = getString("customFleetShipCost");
            headerLeftTT.addPara(costStr, pad, Misc.getHighlightColor(), Misc.getDGSCredits(this.cost));
            header.addUIElement(headerLeftTT).belowLeft(headerTopTT, pad);

            int costPower = getCostPowerLevel();
            Color color = getCostPowerColor(costPower);
            float progress = cost/COST_THRESHOLDS[COST_THRESHOLDS.length - 2] * 100;

            TooltipMakerAPI headerRightTT = header.createUIElement(PANEL_WIDTH/3, HEADER_HEIGHT - 24, false);
            ProgressBar.addBarLTR(headerRightTT, "", Alignment.LMID, null, PANEL_WIDTH/6, 16, 1, 1, progress, 0, null,
                    faction.getDarkUIColor(), Color.BLACK, color);
            headerRightTT.addPara(Misc.ucFirst(getString("customFleetCostDesc" + costPower)), color, 0);
            header.addUIElement(headerRightTT).rightOfTop(headerLeftTT, 0);

            panel.addComponent(header).inTL(1, 1);

            return panel;
        }

        @Override
        public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
            this.panel = panel;
            float pad = 3;
            float opad = 10;
            if (ships.isEmpty()) {
                List<String> knownShips = new ArrayList<>(faction.isPlayerFaction() ?
                        Global.getSector().getFaction(Factions.INDEPENDENT).getKnownShips()
                        : faction.getKnownShips());
                Collections.sort(knownShips, shipComparator);
                for (String ship : knownShips) {
                    ShipHullSpecAPI spec = Global.getSettings().getHullSpec(ship);
                    if (spec.hasTag(Tags.RESTRICTED)) continue;

                    // check that this ship has valid variants
                    // or we could just add the Hull variant in that case?
                    if (Global.getSettings().getHullIdToVariantListMap().get(ship).isEmpty()) continue;

                    ships.put(ship, 0);
                }
            }
            createHeader(panel);

            TooltipMakerAPI tooltip = panel.createUIElement(PANEL_WIDTH, PANEL_HEIGHT - HEADER_HEIGHT - opad, true);

            int maxPerRow = (int)(PANEL_WIDTH/SHIP_PANEL_WIDTH);
            int numRows = (int)Math.ceil((float)ships.size()/maxPerRow);
            //log.info("Number of rows: " + numRows);

            CustomPanelAPI mid = panel.createCustomPanel(PANEL_WIDTH, (SHIP_PANEL_HEIGHT + pad) * numRows + opad, null);

            List<CustomPanelAPI> shipPanels = new ArrayList<>();
            for (String ship : ships.keySet()) {
                int currCount = ships.get(ship);
                CustomPanelAPI shipPanel = createShipPanel(mid, ship, currCount);
                NexUtilsGUI.placeElementInRows(mid, shipPanel, shipPanels, maxPerRow, pad);
                shipPanels.add(shipPanel);
            }
            tooltip.addCustom(mid, pad);

            //CustomPanelAPI footer = panel.createCustomPanel(PANEL_WIDTH, 96, null);
            //tooltip.addCustom(footer, pad);

            panel.addUIElement(tooltip).inTL(0, HEADER_HEIGHT + opad);
        }

        @Override
        public boolean hasCancelButton() {
            return true;
        }

        @Override
        public String getConfirmText() {
            return StringHelper.getString("confirm", true);
        }

        @Override
        public String getCancelText() {
            return StringHelper.getString("cancel", true);
        }

        public void addStartingShips() {
            CharacterCreationData data = (CharacterCreationData)mem.get("$characterData");
            List<String> variantIds = new ArrayList<>();
            for (String ship : ships.keySet()) {
                int count = ships.get(ship);
                for (int i=0; i<count; i++) {
                    List<String> variants = Global.getSettings().getHullIdToVariantListMap().get(ship);
                    String variantId = NexUtils.getRandomListElement(variants);
                    if (variantId == null) variantId = ship + "_Hull";
                    variantIds.add(variantId);
                }
            }
            NGCAddStartingShipsByFleetType.generateFleetFromVariantIds(dialog, data, "CUSTOM", variantIds);
        }

        public boolean haveAnyShips() {
            for (String ship : ships.keySet()) {
                if (ships.get(ship) > 0) return true;
            }
            return false;
        }

        @Override
        public void customDialogConfirm() {
            if (!haveAnyShips()) return;
            
            Nex_VisualCustomPanel.clearPanel(dialog, memoryMap);

            new NGCClearStartingGear().execute(null, dialog, new ArrayList<Token>(), memoryMap);
            addStartingShips();

            mem.set("$nex_lastSelectedFleetType", "CUSTOM");
            NGCAddStartingShipsByFleetType.addStartingDModScript(mem);
            ExerelinSetupData.getInstance().startFleetType = NexFactionConfig.StartFleetType.CUSTOM;
            FireBest.fire(null, dialog, memoryMap, "ExerelinNGCStep4");
            FireBest.fire(null, dialog, memoryMap, "ExerelinNGCStep4Plus");
        }

        @Override
        public void customDialogCancel() {
            //cleanup();
        }

        @Override
        public CustomUIPanelPlugin getCustomPanelPlugin() {
            return plugin;
        }

        public void modifyShipCount(String hullId, int change, LabelAPI label) {
            int newVal = ships.get(hullId) + change;
            if (newVal < 0) newVal = 0;
            ships.put(hullId, newVal);
            label.setText(newVal + "");
            label.setHighlightColors(newVal > 0 ? Misc.getTextColor() : Misc.getGrayColor());

            createHeader(panel);
        }

        public CustomPanelAPI createShipPanel(CustomPanelAPI outer, final String hullId, int currCount) {
            float pad = 3;
            CustomPanelAPI shipPanel = outer.createCustomPanel(SHIP_PANEL_WIDTH, SHIP_PANEL_HEIGHT, null);
            TooltipMakerAPI tooltip = shipPanel.createUIElement(SHIP_PANEL_WIDTH, SHIP_PANEL_HEIGHT, false);
            //log.info("Adding hull ID " + hullId);

            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, hullId + "_Hull");
            List<FleetMemberAPI> fleetMemberList = new ArrayList<>();
            fleetMemberList.add(member);
            tooltip.addShipList(1, 1, SHIP_PANEL_WIDTH, faction.getBaseUIColor(), fleetMemberList, 0);

            tooltip.addPara(Misc.getDGSCredits(member.getBaseValue()), Misc.getHighlightColor(), 0).setAlignment(Alignment.MID);

            CustomPanelAPI countPanel = outer.createCustomPanel(SHIP_PANEL_WIDTH, 16, new CPButtonPressRelayPlugin(plugin));
            TooltipMakerAPI countHolder = countPanel.createUIElement(SHIP_PANEL_WIDTH - 16 - 16, 12, false);
            final LabelAPI count = countHolder.addPara(currCount + "", 0);
            count.setAlignment(Alignment.MID);
            count.setHighlight(currCount + "");
            count.setHighlightColors(currCount > 0 ? Misc.getTextColor() : Misc.getGrayColor());
            countPanel.addUIElement(countHolder).inMid();

            // plus/minus buttons
            TooltipMakerAPI btnMinusHolder = countPanel.createUIElement(16, 12, false);
            String btnId = "minus_" + hullId;
            final ButtonAPI btnMinus = btnMinusHolder.addButton("-", btnId, 16, 12, pad);
            if (currCount <= 0) btnMinus.setEnabled(false);
            plugin.addButton(new CustomPanelPluginWithInput.ButtonEntry(btnMinus, btnId) {
                @Override
                public void onToggle() {
                    modifyShipCount(hullId, -1, count);
                    if (ships.get(hullId) <= 0) btnMinus.setEnabled(false);
                }
            });
            countPanel.addUIElement(btnMinusHolder).inLMid(0);

            TooltipMakerAPI btnPlusHolder = countPanel.createUIElement(16, 12, false);
            btnId = "plus_" + hullId;
            final ButtonAPI btnPlus = btnPlusHolder.addButton("+", btnId, 16, 12, pad);
            plugin.addButton(new CustomPanelPluginWithInput.ButtonEntry(btnPlus, btnId) {
                @Override
                public void onToggle() {
                    modifyShipCount(hullId, 1, count);
                    btnMinus.setEnabled(true);
                }
            });
            countPanel.addUIElement(btnPlusHolder).inRMid(16 - 2);
            tooltip.addCustom(countPanel, 0);

            shipPanel.addUIElement(tooltip).inTL(0, 0);
            return shipPanel;
        }
    }

    public static Comparator<String> shipComparator = new Comparator<String>() {
        @Override
        public int compare(String o1, String o2) {
            ShipHullSpecAPI spec1 = Global.getSettings().getHullSpec(o1);
            ShipHullSpecAPI spec2 = Global.getSettings().getHullSpec(o2);
            return compare(spec2, spec1);
        }

        public int compare(ShipHullSpecAPI one, ShipHullSpecAPI two) {
            int compare = one.getHullSize().compareTo(two.getHullSize());
            if (compare != 0) return compare;
            return Float.compare(one.getBaseValue(), two.getBaseValue());
        }
    };
}

package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.StringHelper;
import exerelin.world.factionsetup.FactionSetupHandler;
import exerelin.world.factionsetup.FactionSetupItemPlugin;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class Nex_NGCarePackage extends BaseCommandPlugin {

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        String arg = params.get(0).getString(memoryMap);

        switch (arg)
        {
            case "select":
                selectItems(dialog);
                return true;
            case "destroy":
                removePods(dialog);
                return true;
            case "viewPlanet":
                dialog.getVisualPanel().showCore(CoreUITabId.CARGO, getPlayerHome(), new NexUtilsGUI.NullCoreInteractionListener());
                return true;
        }

        return false;
    }

    protected SectorEntityToken getPlayerHome() {
        SectorEntityToken playerHome = null;
        List<MarketAPI> markets = NexUtilsFaction.getFactionMarkets(Factions.PLAYER);
        if (!markets.isEmpty()) playerHome = markets.get(0).getPrimaryEntity();

        return playerHome;
    }

    protected void removePods(InteractionDialogAPI dialog) {
        Misc.fadeAndExpire(dialog.getInteractionTarget());
        dialog.dismiss();
    }

    protected void selectItems(InteractionDialogAPI dialog)
    {
        SectorEntityToken home = getPlayerHome();
        CargoAPI copy = Global.getFactory().createCargo(false);
        //copy.addAll(cargo);
        for (FactionSetupHandler.FactionSetupItemDef def : FactionSetupHandler.DEFS) {
            if (def.requireStartingColony && home == null) continue;
            SpecialItemData special = new SpecialItemData("nex_factionSetupItem", def.id);
            copy.addSpecial(special, def.count);
        }
        copy.sort();

        final float sideWidth = 210f;
        final float screenWidth = Global.getSettings().getScreenWidth() * 3/4;
        final float screenHeight = Global.getSettings().getScreenHeight() * 4/5;
        dialog.showCargoPickerDialog(getString("pickerHeader"),
                Misc.ucFirst(StringHelper.getString("confirm")),
                Misc.ucFirst(StringHelper.getString("cancel")),
                false, sideWidth, screenWidth, screenHeight,
                copy, new CargoPickerListener() {
                    public void pickedCargo(CargoAPI cargo) {
                        FactionSetupHandler.clearSelectedItems();
                        cargo.sort();
                        for (CargoStackAPI stack : cargo.getStacksCopy()) {
                            SpecialItemData data = stack.getSpecialDataIfSpecial();
                            FactionSetupHandler.addSelectedItem(data);
                        }
                        FactionSetupHandler.applyItems();
                        //picked = true;
                        removePods(dialog);
                    }
                    public void cancelledCargoSelection() {

                    }
                    public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo, CargoStackAPI pickedUp, boolean pickedUpFromSource, CargoAPI combined) {

                        int cost = getCargoCost(combined);
                        int max = Global.getSettings().getInt("nex_factionSetupMaxPoints");

                        float opad = 10f;

                        String str = getString("pickerFancyText");
                        panel.setParaFontVictor14();
                        panel.addPara(str, opad);
                        panel.setParaFontDefault();
                        panel.addImage(Global.getSector().getPlayerFaction().getLogo(), 205, 3);

                        str = getString("pickerCost");
                        Color color = cost > max ? Misc.getNegativeHighlightColor() : Misc.getPositiveHighlightColor();
                        panel.addPara(str, 	opad * 1f, color, "" + cost, "" + max);

                        str = getString("pickerTip");
                        panel.addPara(str, Misc.getGrayColor(), opad);
                    }

                    public int getCargoCost(CargoAPI cargo) {
                        int cost = 0;
                        for (CargoStackAPI stack : cargo.getStacksCopy()) {
                            FactionSetupItemPlugin plugin = (FactionSetupItemPlugin)stack.getPlugin();
                            cost += plugin.getItem().getCost() * stack.getSize();
                        }
                        return cost;
                    }
                });
    }

    protected String getString(String id)
    {
        return StringHelper.getString("nex_factionSetup", id);
    }
}

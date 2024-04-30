package exerelin.campaign.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.SectorManager;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.plugins.ExerelinModPlugin;

import java.awt.*;

public class EconomicAIModule extends StrategicAIModule {
    public EconomicAIModule(StrategicAI ai, StrategicDefManager.ModuleType module) {
        super(ai, module);
    }
    /*
        Things that should be in the report:
            - National net income
            - Commodities where we depend on imports
            - Competitors
     */

    @Override
    public void generateReport(TooltipMakerAPI tooltip, CustomPanelAPI holder, float width) {
        float pad = 3;
        float opad = 10;
        Color hl = Misc.getHighlightColor();
        String factionId = ai.getFactionId();

        EconomyInfoHelper helper = EconomyInfoHelper.getInstance();

        if (ExerelinModPlugin.isNexDev) tooltip.addPara(StrategicAI.getString("intelPara_income"), opad, hl, Misc.getDGSCredits(helper.getFactionNetIncome(factionId)));
		tooltip.addPara(StrategicAI.getString("intelPara_competitors"), opad);
		for (String otherFactionId : SectorManager.getLiveFactionIdsCopy()) {
			if (factionId.equals(otherFactionId)) continue;
			int compFactor = helper.getCompetitionFactor(factionId, otherFactionId);
			if (compFactor < 20) continue;
			
			FactionAPI otherFaction = Global.getSector().getFaction(otherFactionId);
			String sprite = otherFaction.getCrest();
            TooltipMakerAPI iwt = tooltip.beginImageWithText(sprite, 24);
            LabelAPI label = iwt.addPara("%s: %s", 0, hl, Misc.ucFirst(otherFaction.getDisplayName()), compFactor + "");
            label.setHighlightColors(otherFaction.getBaseUIColor(), hl);
            tooltip.addImageWithText(pad);
		}

        // list imports
        /*
        Map<String, Integer> imports = helper.getCommoditiesImportedByFaction(factionId);
        tooltip.addPara("We are reliant on imports of the following commodities: ", opad);
        tooltip.setBulletedListMode("- ");
        for (String commodityId : imports.keySet()) {
            int importAmount = imports.get(commodityId);
            CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodityId);
            String sprite = spec.getIconName();
            TooltipMakerAPI iwt = tooltip.beginImageWithText(sprite, 20);
            LabelAPI label = iwt.addPara(spec.getName() + ": " + importAmount, 0, hl, importAmount + "");
            tooltip.addImageWithText(pad);
        }
        tooltip.setBulletedListMode(null);
        */

        tooltip.addSpacer(opad);

        super.generateReport(tooltip, holder, width);
    }
}

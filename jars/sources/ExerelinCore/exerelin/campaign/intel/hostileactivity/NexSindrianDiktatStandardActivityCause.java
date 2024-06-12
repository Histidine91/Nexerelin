package exerelin.campaign.intel.hostileactivity;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.intel.events.*;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.MapParams;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;

import java.awt.*;
import java.util.List;

/**
 * Just {@code SindrianDiktatStandardActivityCause} with externalized strings and handling for Sindrian Fuel Company faction names.
 */
public class NexSindrianDiktatStandardActivityCause extends SindrianDiktatStandardActivityCause {

    public NexSindrianDiktatStandardActivityCause(HostileActivityEventIntel intel) {
        super(intel);
    }

    @Override
    public TooltipMakerAPI.TooltipCreator getTooltip() {
        return new BaseFactorTooltip() {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                float opad = 10f;

                Color h = Misc.getHighlightColor();
                Color tc = Misc.getTextColor();
                FactionAPI faction = Global.getSector().getFaction(Factions.DIKTAT);
                Color c = faction.getBaseUIColor();
                String nameLong = faction.getDisplayNameLong();
                String nameShort = NexHostileActivityManager.getString(Global.getSettings().getModManager().isModEnabled("PAGSM") ?
                        "diktat_factionNameShortSFC" : "diktat_factionNameShort");

                String str = String.format(NexHostileActivityManager.getString("diktat_causeDesc1"), nameLong);
                tooltip.addPara(str, 0f);

                List<TriTachyonStandardActivityCause.CompetitorData> comp = computePlayerCompetitionData();
                FactionAPI player = Global.getSector().getFaction(Factions.PLAYER);

                tooltip.beginTable(player, 20f, StringHelper.getString("commodity"),
                        getTooltipWidth(tooltipParam) - 150f, StringHelper.getString("production"), 150f);
                for (final TriTachyonStandardActivityCause.CompetitorData data : comp) {
                    tooltip.addRow(Alignment.LMID, tc, Misc.ucFirst(data.spec.getLowerCaseName()),
                            Alignment.MID, h, "" + (int) data.competitorMaxProd);
                }
                tooltip.addTable("", 0, opad);
                tooltip.addSpacer(5f);

                str = NexHostileActivityManager.getString("diktat_causeDesc2");
                str = StringHelper.substituteToken(str, "$faction", nameShort);
                tooltip.addPara(str, opad, h,
                        NexHostileActivityManager.getString("diktat_causeDesc2Highlight"), "" + MIN_COMPETITOR_PRODUCTION);

                str = NexHostileActivityManager.getString("diktat_causeDesc3");
                str = StringHelper.substituteToken(str, "$faction", nameShort);
                tooltip.addPara(str, opad, h, Global.getSettings().getSpecialItemSpec(Items.SYNCHROTRON).getName());

                MarketAPI sindria = SindrianDiktatHostileActivityFactor.getSindria(false);
                if (sindria != null && sindria.getStarSystem() != null) {
                    MapParams params = new MapParams();
                    params.showSystem(sindria.getStarSystem());
                    float w = tooltip.getWidthSoFar();
                    float ht = Math.round(w / 1.6f);
                    params.positionToShowAllMarkersAndSystems(true, Math.min(w, ht));
                    UIPanelAPI map = tooltip.createSectorMap(w, ht, params, sindria.getName() + " (" + sindria.getStarSystem().getNameWithLowercaseTypeShort() + ")");
                    tooltip.addCustom(map, opad);
                }
            }
        };
    }
}

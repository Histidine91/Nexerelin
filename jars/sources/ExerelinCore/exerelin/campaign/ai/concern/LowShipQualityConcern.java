package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.ShipQuality;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.world.industry.HeavyIndustry;

import java.util.ArrayList;
import java.util.List;

public class LowShipQualityConcern extends BaseStrategicConcern implements HasIndustryToBuild, HasIndustryTarget {

    public static final float BASE_DESIRED_QUALITY = 0.2f;
    public static final float BASE_PRIORITY = 150;

    @Override
    public boolean generate() {
        if (!getExistingConcernsOfSameType().isEmpty()) return false;

        // not much to do with this one?
        if (!isValid()) return false;

        update();
        return true;
    }

    @Override
    public boolean isValid() {
        return getQualityFromIndustry() < getWantedQualityFromIndustry();
    }

    public void update() {
        float qual = getQualityFromIndustry();
        float wanted = getWantedQualityFromIndustry();
        //Global.getLogger(this.getClass()).info(String.format("Quality %s, wanted %s", qual, wanted));
        if (qual >= wanted) {
            end();
            return;
        }
        float delta = wanted - qual * 400;

        super.update();
        priority.modifyFlat("delta", delta, StrategicAI.getString("statQualityDeficit", true));
    }

    protected float getWantedQualityFromIndustry() {
        // if we have a high quality from doctrine, we don't need industry contribution as much
        // actually screw that, if we have a high quality setting in doctrine that's because we're quality addicts and want EVEN MORE quality
        return BASE_DESIRED_QUALITY;    // + (0.25f - ai.getFaction().getDoctrine().getShipQualityContribution());
    }

    protected float getQualityFromIndustry() {
        for (MarketAPI market : Misc.getFactionMarkets(ai.getFactionId())) {
            if (market.isHidden()) continue;
            return ShipQuality.getInstance().getQualityData(market).quality.computeEffective(0);
        }

        return 0;
    }

    @Override
    public void modifyActionPriority(StrategicAction action) {
        // taking a military action when our ship quality is this bad is... contraindicated
        if (action.getDef().hasTag(SAIConstants.TAG_MILITARY)) {
            action.getPriority().modifyMult("badAction", 0.5f, StrategicAI.getString("statBadAction"));
        }
    }

    @Override
    public void reapplyPriorityModifiers() {
        super.reapplyPriorityModifiers();

        int numWars = DiplomacyManager.getFactionsAtWarWithFaction(ai.getFactionId(), false, true, false).size();
        if (numWars == 0) return;
        float warMult = 1 + (.2f * numWars);

        priority.modifyFlat("base", BASE_PRIORITY, StrategicAI.getString("statBase", true));
        priority.modifyMult("wars", warMult, StrategicAI.getString("statNumWars", true));
    }

    @Override
    public String getIndustryIdToBuild() {
        return Industries.HEAVYINDUSTRY;
    }

    @Override
    public List<Industry> getTargetIndustries() {
        return null;
    }

    @Override
    public List<String> getTargetIndustryIds() {
        return new ArrayList<String>(HeavyIndustry.HEAVY_INDUSTRY);
    }
}

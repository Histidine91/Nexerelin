package exerelin.campaign.ai.action.industry;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.ColonyManager;
import exerelin.campaign.ai.concern.BaseStrategicConcern;
import exerelin.campaign.ai.concern.HasIndustryToBuild;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsMarket;
import exerelin.world.NexMarketBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static exerelin.campaign.ColonyManager.QueuedIndustry;

public class BuildDefenseAction extends BuildIndustryAction {

    @Override
    public boolean generate() {
        boolean result = super.generate();
        //if (!result) Global.getLogger(this.getClass()).info("Build defense action generation failed");
        return result;
    }

    @Override
    protected boolean buildOrUpgrade() {
        boolean exists = market.hasIndustry(industryId);
        ColonyManager.getManager().queueIndustry(market, industryId, exists ? QueuedIndustry.QueueType.UPGRADE : QueuedIndustry.QueueType.NEW);
        ColonyManager.getManager().processNPCConstruction(market);

        return true;
    }

    @Override
    protected String pickIndustry(MarketAPI market) {
        if (concern instanceof HasIndustryToBuild) {
            return ((HasIndustryToBuild)concern).getIndustryIdToBuild();
        }

        WantedDefenseType neededType = getNeededDefenseType(market);
        if (neededType == null) return null;

        // convert needed defense type to thing we want to build
        switch (neededType) {
            case BASE:
                Industry patrol = NexUtilsMarket.getIndustryWithTag(market, Industries.TAG_PATROL);
                Industry military = NexUtilsMarket.getIndustryWithTag(market, Industries.TAG_MILITARY);
                if (military != null) return military.getId();
                else if (patrol != null) return patrol.getId();
                break;
            case GROUND:
                Industry groundDef = market.getIndustry(Industries.GROUNDDEFENSES);
                Industry groundDefOther = NexUtilsMarket.getIndustryWithTag(market, Industries.TAG_GROUNDDEFENSES);
                if (groundDef != null) return groundDef.getId();
                if (groundDefOther != null) return groundDefOther.getId();
                break;
            case STATION:
                Pair<Industry, Integer> currStationAndTier = NexMarketBuilder.getCurrentStationAndTier(market);
                if (currStationAndTier != null) {
                    return currStationAndTier.one.getId();
                } else {
                    return NexConfig.getFactionConfig(ai.getFactionId()).getRandomDefenceStation(null, 0);
                }
        }

        return null;
    }

    /**
     * Gets the 'desire' of the three defense types (ground, station, base) and returns the type with the highest desire.
     * @param market
     * @return
     */
    protected WantedDefenseType getNeededDefenseType(MarketAPI market) {
        float sd = BaseStrategicConcern.getSpaceDefenseValue(market);
        float gd = BaseStrategicConcern.getGroundDefenseValue(market);
        int size = market.getSize();

        float spaceRatio = sd/size;
        float groundRatio = gd/size;

        float groundDesire = getGroundDefDesire(market);
        if (groundRatio > spaceRatio) groundDesire /= 2;

        float baseDesire = getBaseDesire(market);
        float stationDesire = getStationDesire(market);
        if (spaceRatio > groundRatio) {
            baseDesire /= 2;
            stationDesire /= 2;
        }

        List<Pair<WantedDefenseType, Float>> sorted = new ArrayList<>();
        if (groundDesire > 0) sorted.add(new Pair<>(WantedDefenseType.GROUND, groundDesire));
        if (baseDesire > 0) sorted.add(new Pair<>(WantedDefenseType.BASE, baseDesire));
        if (stationDesire > 0) sorted.add(new Pair<>(WantedDefenseType.STATION, stationDesire));
        if (sorted.isEmpty()) return null;

        Collections.sort(sorted, new NexUtils.PairWithFloatComparator(true));
        return sorted.get(0).one;
    }

    protected float getGroundDefDesire(MarketAPI market) {
        int size = market.getSize();
        Industry groundDef = market.getIndustry(Industries.GROUNDDEFENSES);
        Industry heavyBatteries = market.getIndustry(Industries.HEAVYBATTERIES);
        Industry groundDefOther = NexUtilsMarket.getIndustryWithTag(market, Industries.TAG_GROUNDDEFENSES);

        // have heavy batteries, probably already maxed out
        if (heavyBatteries != null) {
            if (groundDefOther != null && NexUtilsMarket.canUpgradeIndustry(groundDefOther)) {
                return 1;
            } else return 0;
        }
        // have ground def, upgrading it might be good
        else if (groundDef != null) {
            return 1;
        }
        // no ground def
        // if we already have a knockoff ground def from some mod and can upgrade it, consider doing so
        // else, building a proper ground def is high priority
        else {
            if (groundDefOther != null && NexUtilsMarket.canUpgradeIndustry(groundDefOther)) {
                return 1;
            } else return 1 * size/1.5f;
        }
    }

    protected float getBaseDesire(MarketAPI market) {
        int size = market.getSize();
        Industry patrol = NexUtilsMarket.getIndustryWithTag(market, Industries.TAG_PATROL);
        Industry military = NexUtilsMarket.getIndustryWithTag(market, Industries.TAG_MILITARY);
        Industry command = NexUtilsMarket.getIndustryWithTag(market, Industries.TAG_COMMAND);

        int patrolTier = 0;
        if (command != null) patrolTier = 3;
        else if (military != null) patrolTier = 2;
        else if (patrol != null) patrolTier = 1;

        switch (patrolTier) {
            case 3:
                return 0;  // max level already
            case 2:
                if (!NexUtilsMarket.canUpgradeIndustry(military)) return 0;
                return 1 * (size >= 6 ? 2 : 1);
            case 1:
                if (Misc.getMaxIndustries(market) <= Misc.getNumIndustries(market)) {
                    return 0;   // have patrol HQ, can't upgrade further due to industry limit
                }
                if (!NexUtilsMarket.canUpgradeIndustry(patrol)) return 0;
                return 1;   // potential upgrade from patrol HQ to military base, go ahead
            case 0:
                // not even a patrol HQ, better build 1
                return 1 * (size > 3 ? size/2f : 0);
            default:
                return 0;
        }
    }

    protected float getStationDesire(MarketAPI market) {
        int size = market.getSize();
        Pair<Industry, Integer> currStationAndTier = NexMarketBuilder.getCurrentStationAndTier(market);
        if (currStationAndTier == null) {   // no station yet, prioritize building one
            return 1.5f * (size > 3 ? size/2f : 0);
        }

        int stationTier = currStationAndTier.two;
        int quota = size/2;

        if (stationTier >= 3) return 0; // max size
        if (!NexUtilsMarket.canUpgradeIndustry(currStationAndTier.one)) return 0;   // cannot upgrade
        float score = 1.5f; // preferred over bases
        if (stationTier < quota) score *= 2;
        return score;
    }

    @Override
    protected MarketAPI pickMarketFallback(String industryId) {
        // todo: which market needs the given industry the most?
        return null;
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        if (!concern.getDef().hasTag("canBuildDefense")) return false;
        if (!super.canUse(concern)) return false;

        return true;
    }

    public enum WantedDefenseType {BASE, STATION, GROUND}
}

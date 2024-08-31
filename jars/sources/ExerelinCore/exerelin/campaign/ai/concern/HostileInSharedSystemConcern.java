package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.utilities.NexUtilsFaction;

import java.util.*;

public class HostileInSharedSystemConcern extends MarketRelatedConcern {

    public static final int MAX_MARKETS_FOR_PICKER = 4;

    @Override
    public boolean generate() {
        List<Pair<MarketAPI, Float>> hostiles = new ArrayList<>();
        Set alreadyConcernMarkets = getExistingConcernItems();

        Set<LocationAPI> toCheck = new HashSet<>();
        for (MarketAPI market : Misc.getFactionMarkets(ai.getFaction())) {
            toCheck.add(market.getContainingLocation());
        }

        boolean weArePirate = NexUtilsFaction.isPirateFaction(ai.getFactionId());
        for (LocationAPI loc : toCheck) {
            for (MarketAPI market : Global.getSector().getEconomy().getMarkets(loc)) {
                if (market.isHidden()) continue;
                if (alreadyConcernMarkets.contains(market)) continue;
                if (!repCheck(market)) continue;
                boolean theyArePirate = NexUtilsFaction.isPirateFaction(market.getFactionId());
                if (weArePirate != theyArePirate) continue;

                float value = getMarketValue(market)/1000f + market.getSize() * 100;
                value /= SAIConstants.MARKET_VALUE_DIVISOR;
                value *= 2;
                if (value < SAIConstants.MIN_MARKET_VALUE_PRIORITY_TO_CARE) continue;

                hostiles.add(new Pair<>(market, value));
            }
        }

        if (hostiles.isEmpty()) return false;

        Collections.sort(hostiles, MARKET_PAIR_COMPARATOR);

        WeightedRandomPicker<Pair<MarketAPI, Float>> picker = new WeightedRandomPicker<>();
        int max = Math.min(hostiles.size(), MAX_MARKETS_FOR_PICKER);
        for (int i=0; i<max; i++) {
            Pair<MarketAPI, Float> entry = hostiles.get(i);
            picker.add(entry, entry.two);
        }
        Pair<MarketAPI, Float> chosen = picker.pick();

        if (chosen != null) {
            market = chosen.one;
            priority.modifyFlat("value", chosen.two, StrategicAI.getString("statValue", true));
        }

        return market != null;
    }

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        if (otherConcern instanceof HostileInSharedSystemConcern) {
            return otherConcern.getMarket() == this.market;
        }
        return false;
    }

    public boolean repCheck(MarketAPI market) {
        RepLevel atBest = RepLevel.HOSTILE;
        if (DiplomacyTraits.hasTrait(ai.getFactionId(), DiplomacyTraits.TraitIds.PARANOID)) {
            atBest = RepLevel.INHOSPITABLE;
        }
        return market.getFaction().isAtBest(ai.getFactionId(), atBest);
    }

    protected boolean havePresenceInSystem() {
        for (MarketAPI otherMarket : Global.getSector().getEconomy().getMarkets(market.getContainingLocation())) {
            if (otherMarket.getFaction() == ai.getFaction()) return true;
        }
        return false;
    }

    @Override
    public boolean isValid() {
        if (market == null) return false;
        if (!repCheck(market)) return false;
        boolean theyArePirate = NexUtilsFaction.isPirateFaction(market.getFactionId());
        if (NexUtilsFaction.isPirateFaction(ai.getFactionId()) != theyArePirate) return false;

        return havePresenceInSystem();
    }

    @Override
    public LabelAPI createTooltipDesc(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        LabelAPI label = super.createTooltipDesc(tooltip, holder, pad);
        if (DiplomacyTraits.hasTrait(ai.getFactionId(), DiplomacyTraits.TraitIds.PARANOID)) {
            DiplomacyTraits.TraitDef def = DiplomacyTraits.getTrait(DiplomacyTraits.TraitIds.PARANOID);
            tooltip.addPara(StrategicAI.getString("concernDesc_paranoidHigherRepLevel"), pad, def.color, def.name);
        }

        return label;
    }
}

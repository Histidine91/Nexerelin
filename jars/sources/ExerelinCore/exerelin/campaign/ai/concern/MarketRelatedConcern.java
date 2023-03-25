package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.utilities.NexUtils;
import exerelin.utilities.StringHelper;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public abstract class MarketRelatedConcern extends BaseStrategicConcern {
    @Override
    public Set getExistingConcernItems() {
        Set<MarketAPI> markets = new HashSet<>();
        for (StrategicConcern concern : getExistingConcernsOfSameType()) {
            markets.add(concern.getMarket());
        }
        return markets;
    }

    @Override
    public String getName() {
        return super.getName() + " - " + (market != null ? market.getName() : "<error>");
    }

    @Override
    public LabelAPI createTooltipDesc(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        LabelAPI label = super.createTooltipDesc(tooltip, holder, pad);
        label.setText(StringHelper.substituteToken(label.getText(), "$size", market.getSize() + ""));

        return label;
    }

    @Override
    public void update() {
        super.update();

        if (market != null && !market.isInEconomy()) {
            end();
        }
    }

    public static final Comparator MARKET_PAIR_COMPARATOR = new NexUtils.PairWithFloatComparator(true);
}

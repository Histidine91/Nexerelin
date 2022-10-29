package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import exerelin.campaign.econ.FleetPoolManager;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.extern.log4j.Log4j;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

@Log4j
public abstract class DiplomacyConcern extends BaseStrategicConcern {

    @Override
    public LabelAPI createTooltipDesc(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        if (faction == null) return null;
        String str = getDef().desc;
        str = StringHelper.substituteFactionTokens(str, faction);
        Color hl = faction.getBaseUIColor();
        return tooltip.addPara(str, pad, hl, faction.getDisplayNameWithArticleWithoutArticle());
    }

    @Override
    public String getIcon() {
        //if (faction != null) return faction.getCrest();
        return super.getIcon();
    }

    @Override
    public String getName() {
        return super.getName() + " - " + (faction != null ? faction.getDisplayName() : "<error>");
    }

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        if (otherConcern instanceof DiplomacyConcern) {
            DiplomacyConcern dc = (DiplomacyConcern)otherConcern;
            return dc.getClass() == this.getClass() && dc.getFaction() == this.faction;
        }
        return false;
    }

    @Override
    public Set getExistingConcernItems() {
        Set<FactionAPI> factions = new HashSet<>();
        for (StrategicConcern concern : getExistingConcernsOfSameType()) {
            factions.add(concern.getFaction());
        }
        return factions;
    }

    @Override
    public boolean isValid() {
        return faction != null;
    }

    // TODO: should count allies as well
    protected float getFactionStrength(FactionAPI faction) {
        int size = NexUtilsFaction.getFactionMarketSizeSum(faction.getId());
        float lastFleetPoolIncrement = FleetPoolManager.getPointsLastTick(faction);
        float strength = size + lastFleetPoolIncrement * 5;
        //log.info(String.format("Faction %s has size %s, last pool increment %.2f", faction.getId(), size, lastFleetPoolIncrement));
        return strength;
    }
}

package exerelin.campaign.questskip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.missions.hub.BaseMissionHub;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionCreator;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.ReflectionUtils;

import java.util.List;

public abstract class BaseQuestSkipPlugin implements QuestSkipPlugin {

    protected QuestSkipEntry quest;
    protected QuestChainSkipEntry chain;

    @Override
    public void init() {}

    @Override
    public void onEnabled() {}

    @Override
    public void onDisabled() {}

    @Override
    public void onNewGame() {}

    @Override
    public void onNewGameAfterProcGen() {}

    @Override
    public void onNewGameAfterEconomyLoad() {}

    @Override
    public void onNewGameAfterTimePass() {}

    @Override
    public boolean shouldShow() {
        return true;
    }

    @Override
    public void setQuest(QuestSkipEntry quest) {
        this.quest = quest;
    }

    @Override
    public QuestSkipEntry getQuest() {
        return quest;
    }

    @Override
    public void setQuestChain(QuestChainSkipEntry chain) {
        this.chain = chain;
    }

    @Override
    public QuestChainSkipEntry getQuestChain() {
        return chain;
    }

    @Override
    public void applyMemKeys() {
        if (quest != null && quest.isEnabled) quest.applyMemKeys();
        if (chain != null) {
            for (QuestSkipEntry quest : chain.quests) {
                if (!quest.isEnabled) continue;
                quest.applyMemKeys();
            }
        }
    }

    protected void makeNonStoryCritical(String marketId, String reason) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        if (market == null) return;
        Misc.makeNonStoryCritical(market, reason);
    }

    public static FleetMemberAPI addShipToPlayerFleet(String variantId) {
        ShipVariantAPI variant = Global.getSettings().getVariant(variantId).clone();
        return addShipToPlayerFleet(variant);
    }

    public static FleetMemberAPI addShipToPlayerFleet(ShipVariantAPI variant) {
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
        pf.getFleetData().addFleetMember(member);

        float crew = member.getMinCrew();
        float supplies = member.getCargoCapacity() * 0.5f;
        float fuel = member.getFuelCapacity();
        pf.getCargo().addCrew((int)crew);
        pf.getCargo().addSupplies(supplies);
        pf.getCargo().addFuel(fuel);
        member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
        return member;
    }

    protected void setMissionDone(String missionId, PersonAPI hubPerson) {
        BaseMissionHub hub = (BaseMissionHub)BaseMissionHub.get(hubPerson);
        hub.updateMissionCreatorsFromSpecs();
        List<HubMissionCreator> creators = (List<HubMissionCreator>) ReflectionUtils.getIncludingSuperclasses("creators", hub, hub.getClass());
        for (HubMissionCreator creator : creators) {
            if (missionId.equals(creator.getSpecId())) {
                creator.setNumCompleted(1);
                break;
            }
        }
    }

    /**
     * Fixed version of {@code getRelToPlayer().setLevel()}. See https://fractalsoftworks.com/forum/index.php?topic=5061.msg449203#msg449203
     * @param person
     * @param level
     */
    protected void setRepLevelFixed(PersonAPI person, RepLevel level) {
        if (level == RepLevel.NEUTRAL) {
            person.getRelToPlayer().setRel(0);
        } else if (level.isPositive()) {
            person.getRelToPlayer().setRel(level.getMin() + 0.01f);
        } else {
            person.getRelToPlayer().setRel(-level.getMin() - 0.01f);
        }
    }
}

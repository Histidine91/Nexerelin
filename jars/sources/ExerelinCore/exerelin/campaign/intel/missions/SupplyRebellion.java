package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.intel.EventCancelReason;
import exerelin.campaign.intel.rebellion.RebellionCreator;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.*;

import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_PlayerOutpost.COST_HEIGHT;

public class SupplyRebellion extends HubMissionWithBarEvent {

    // at size 3?
    public static final Map<String, Integer> BASE_COMMODITIES_REQUIRED = new HashMap<>();
    static {
        BASE_COMMODITIES_REQUIRED.put(Commodities.SUPPLIES, 25);
        BASE_COMMODITIES_REQUIRED.put(Commodities.HAND_WEAPONS, 5);
        BASE_COMMODITIES_REQUIRED.put(Commodities.MARINES, 2);
    }
    public static float COMMODITY_PRICE_MULT = 1.2f;

    public static float PROB_COMPLICATIONS = 0.5f;
    public static float PROB_BAR_UNDERWORLD = 0.2f;

    public static float MISSION_DAYS = 120f;

    public static enum Stage {
        TALK_TO_PERSON,
        COMPLETED,
        FAILED,
        //FAILED_NO_PENALTY // use an EventCancelReason instead
    }

    protected Map<String, Integer> commoditiesRequired = new HashMap<>();

    protected MarketAPI deliveryMarket;
    protected PersonAPI deliveryContact;
    //protected EventCancelReason cancelReason;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        //genRandom = Misc.random;
        if (barEvent) {
            if (rollProbability(PROB_BAR_UNDERWORLD)) {
                setGiverRank(Ranks.CITIZEN);
                setGiverPost(pickOne(Ranks.POST_GANGSTER,
                        Ranks.POST_FENCE, Ranks.POST_CRIMINAL));
                setGiverImportance(pickImportance());
                setGiverTags(Tags.CONTACT_UNDERWORLD);
                setGiverFaction(Factions.PIRATES);
            } else {
                String post = pickOne(Ranks.POST_BASE_COMMANDER, Ranks.POST_STATION_COMMANDER, Ranks.POST_ADMINISTRATOR,
                        Ranks.POST_SENIOR_EXECUTIVE);
                setGiverPost(post);
                if (post.equals(Ranks.POST_SENIOR_EXECUTIVE) || post.equals(Ranks.POST_ADMINISTRATOR)) {
                    setGiverImportance(pickHighImportance());
                } else {
                    setGiverImportance(pickImportance());
                }
                setGiverTags(Tags.CONTACT_MILITARY);
            }
            findOrCreateGiver(createdAt, false, false);
        }

        PersonAPI person = getPerson();
        if (person == null) return false;
        MarketAPI market = person.getMarket();
        if (market == null) return false;
        String factionId = person.getFaction().getId();

        if (!setPersonMissionRef(person, "$nex_supReb_ref")) {
            return false;
        }

        if (barEvent) {
            setGiverIsPotentialContactOnSuccess();
        }

        List<FactionAPI> otherFactions = new ArrayList<>();
        for (String ofid : SectorManager.getLiveFactionIdsCopy()) {
            if (ofid.equals(factionId)) continue;
            FactionAPI otherFaction = Global.getSector().getFaction(ofid);
            if (otherFaction.isPlayerFaction()) continue;
            if (otherFaction.isAtWorst(factionId, RepLevel.SUSPICIOUS)) continue;
            otherFactions.add(otherFaction);
        }
        
        Random random = genRandom;
        if (random == null) random = new Random();
        Map<String, Object> targetData = CovertOpsManager.getManager().pickTarget(person.getFaction(), otherFactions,
                CovertOpsManager.CovertActionType.INSTIGATE_REBELLION, random);
        deliveryMarket = (MarketAPI)targetData.get("market");

        if (deliveryMarket == null) return false;
        if (deliveryMarket.isInvalidMissionTarget()) return false;

        if (true || NexUtilsFaction.isPirateFaction(factionId)) {
            deliveryContact = findOrCreateCriminal(deliveryMarket, true);
        } else {
            deliveryContact = findOrCreateTrader(deliveryMarket.getFactionId(), deliveryMarket, true);
        }
        deliveryContact.setFaction(factionId);
        deliveryContact.addTag(Tags.CONTACT_UNDERWORLD);
        ensurePersonIsInCommDirectory(deliveryMarket, deliveryContact);
        //setPersonIsPotentialContactOnSuccess(deliveryContact);

        if (deliveryContact == null || !setPersonMissionRef(deliveryContact, "$nex_supReb_ref")) {
            return false;
        }
        setPersonDoGenericPortAuthorityCheck(deliveryContact);
        //setFlag(deliveryContact, "$requiresDiscretionToDeal", false);
        makeImportant(deliveryContact, "$nex_supReb_needsCommodity", Stage.TALK_TO_PERSON);

        int size = deliveryMarket.getSize();
        int mult = (int)Math.round(Math.pow(2, size - 3));
        for (String commodityId : BASE_COMMODITIES_REQUIRED.keySet()) {
            commoditiesRequired.put(commodityId, BASE_COMMODITIES_REQUIRED.get(commodityId) * mult);
        }

        setCreditReward(CreditReward.VERY_LOW);
        creditReward *= size;
        creditReward /= 2;
        setCreditReward(creditReward + Math.round(getTotalCost() * COMMODITY_PRICE_MULT));

        if (size <= 4) {
            setRepFactionChangesLow();
            setRepPersonChangesMedium();
        } else if (size <= 6) {
            setRepFactionChangesMedium();
            setRepPersonChangesHigh();
        } else {
            setRepFactionChangesHigh();
            setRepPersonChangesVeryHigh();
        }

        setStartingStage(Stage.TALK_TO_PERSON);
        setSuccessStage(Stage.COMPLETED);
        setFailureStage(Stage.FAILED);
        addNoPenaltyFailureStages(EventCancelReason.NOT_IN_ECONOMY, EventCancelReason.RELATIONS_TOO_HIGH);
        
        connectWithMarketDecivilized(Stage.TALK_TO_PERSON, EventCancelReason.NOT_IN_ECONOMY, deliveryMarket);
        //setStageOnMarketDecivilized(Stage.FAILED_NO_PENALTY, createdAt);
        connectWithCustomCondition(Stage.TALK_TO_PERSON, EventCancelReason.RELATIONS_TOO_HIGH,
                new DontRebelConditionChecker(deliveryMarket, person.getFaction(), this));

        setStageOnMemoryFlag(Stage.COMPLETED, deliveryContact, "$nex_supReb_completed");
        setTimeLimit(Stage.FAILED, MISSION_DAYS, null);

        if (getQuality() < 0.5f) {
            setRepFactionChangesVeryLow();
        } else {
            setRepFactionChangesLow();
        }
        setRepPersonChangesMedium();

        addTag(RebellionIntel.getString("intelTag"));

        return true;
    }

    protected void updateInteractionDataImpl() {
        set("$nex_supReb_barEvent", isBarEvent());

        set("$nex_supReb_underworld", getPerson().hasTag(Tags.CONTACT_UNDERWORLD));
        set("$nex_supReb_payment", Misc.getDGSCredits(creditReward));
        set("$nex_supReb_manOrWoman", getPerson().getManOrWoman());
        //set("$nex_supReb_heOrShe", getPerson().getHeOrShe());
        //set("$nex_supReb_HeOrShe", getPerson().getHeOrShe().substring(0, 1).toUpperCase() + getPerson().getHeOrShe().substring(1));

        set("$nex_supReb_personName", deliveryContact.getNameString());
        set("$nex_supReb_personPost", deliveryContact.getPost().toLowerCase());
        set("$nex_supReb_PersonPost", Misc.ucFirst(deliveryContact.getPost()));
        set("$nex_supReb_marketName", deliveryMarket.getName());
        set("$nex_supReb_marketColor", deliveryMarket.getTextColorForFactionOrPlanet());
        set("$nex_supReb_marketOnOrAt", deliveryMarket.getOnOrAt());
        set("$nex_supReb_dist", getDistanceLY(deliveryMarket));
    }

    public int getTotalCost() {
        float cost = getMaterialsCost(Commodities.SUPPLIES)
                + getMaterialsCost(Commodities.HAND_WEAPONS)
                + getMaterialsCost(Commodities.MARINES);
        return Math.round(cost);
    }

    public float getMaterialsCost(String commodityId) {
        return getCommodityRequired(commodityId) * Global.getSettings().getCommoditySpec(commodityId).getBasePrice();
    }

    public int getCommodityRequired(String commodityId) {
        if (commoditiesRequired.containsKey(commodityId))
            return commoditiesRequired.get(commodityId);
        return 0;
    }

    public void addCommodities(CargoAPI cargo) {
        for (String commodityId : commoditiesRequired.keySet())
        {
            int needed = commoditiesRequired.get(commodityId);
            cargo.addCommodity(commodityId, needed);
        }
    }

    public void removeCommodities(CargoAPI cargo, boolean player, InteractionDialogAPI dialog) {
        TextPanelAPI text = dialog != null ? dialog.getTextPanel() : null;
        for (String commodityId : commoditiesRequired.keySet())
        {
            int have = Math.round(cargo.getCommodityQuantity(commodityId));
            if (have <= 0) continue;

            int needed = commoditiesRequired.get(commodityId);
            if (needed > have) needed = have;
            cargo.removeCommodity(commodityId, needed);
            if (player) AddRemoveCommodity.addCommodityLossText(commodityId, needed, text);
        }
    }

    public CargoAPI getCargoForDisplay() {
        CargoAPI cargo = Global.getFactory().createCargo(true);
        addCommodities(cargo);
        return cargo;
    }

    protected ResourceCostPanelAPI makeCostPanel(TextPanelAPI text, Color color, Color color2)
    {
        ResourceCostPanelAPI cost = text.addCostPanel(RebellionIntel.getString("costPanelTitle"),
                COST_HEIGHT, color, color2);
        cost.setNumberOnlyMode(true);
        cost.setWithBorder(false);
        cost.setAlignment(Alignment.LMID);
        cost.setComWidthOverride(120);
        return cost;
    }

    protected boolean addCostEntry(ResourceCostPanelAPI cost, String commodityId)
    {
        int needed = commoditiesRequired.get(commodityId);
        int available = (int) Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(commodityId);
        Color curr = Global.getSector().getPlayerFaction().getColor();
        if (needed > available) {
            curr = Misc.getNegativeHighlightColor();
        }
        cost.addCost(commodityId, "" + needed + " (" + available + ")", curr);
        return available >= needed;
    }

    public boolean addCostPanel(InteractionDialogAPI dialog) {
        FactionAPI player = Global.getSector().getPlayerFaction();
        Color color = player.getColor();
        Color darkColor = player.getDarkUIColor();
        ResourceCostPanelAPI panel = makeCostPanel(dialog.getTextPanel(), color, darkColor);
        boolean enough = true;
        enough &= addCostEntry(panel, Commodities.SUPPLIES);
        enough &= addCostEntry(panel, Commodities.HAND_WEAPONS);
        enough &= addCostEntry(panel, Commodities.MARINES);
        panel.update();

        return enough;
    }

    public void deliver(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap)
    {
        removeCommodities(Global.getSector().getPlayerFleet().getCargo(), true, dialog);
        applyRebellionEffects();
        setCurrentStage(Stage.COMPLETED, dialog, memoryMap);
    }

    public void applyRebellionEffects() {
        RebellionIntel curr = RebellionIntel.getOngoingEvent(deliveryMarket);

        String factionId = deliveryContact.getFaction().getId();
        if (curr != null) {
            float marinePoints = getCommodityRequired(Commodities.MARINES) * RebellionIntel.VALUE_MARINES;
            float weaponPoints = getCommodityRequired(Commodities.HAND_WEAPONS) * RebellionIntel.VALUE_WEAPONS;
            float supplyPoints = getCommodityRequired(Commodities.SUPPLIES) * RebellionIntel.VALUE_SUPPLIES;

            float points = marinePoints + weaponPoints + supplyPoints;
            points *= 1.5f;

            curr.modifyPoints(points, true);
        } else {
            RebellionCreator.getInstance().createRebellion(deliveryMarket, factionId, false);
        }

        // file your Iron Shell taxes
        if (Global.getSettings().getModManager().isModEnabled("timid_xiv")
                && (Factions.HEGEMONY.equals(factionId) || "ironshell".equals(factionId)))
        {
            float hardModeMult = SectorManager.getManager().isHardMode() ? 0.9f : 1f;
            float val = Math.round(getTotalCost() * COMMODITY_PRICE_MULT);
            float taxOffset = Math.abs(val * 0.08f * hardModeMult);

            Global.getSector().getMemoryWithoutUpdate().set("$EIS_taxburden",
                    Global.getSector().getMemoryWithoutUpdate().getFloat("$EIS_taxburden") - taxOffset);
            Global.getSector().getMemoryWithoutUpdate().set("$EIS_BMHISTax",
                    Global.getSector().getMemoryWithoutUpdate().getFloat("$EIS_BMHISTax") - taxOffset);
        }
    }

    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params,
                                 Map<String, MemoryAPI> memoryMap) {
        switch (action) {
            case "showCostPanel":
                addCostPanel(dialog);
                return true;
            case "showCostPanelAndCheckCost":
                boolean enough = addCostPanel(dialog);
                memoryMap.get(MemKeys.LOCAL).set("$nex_supReb_haveEnough", enough, 0);
                return true;
            case "deliver":
                deliver(dialog, memoryMap);
                return true;
        }
        return false;
    }

    @Override
    public void addDescriptionForCurrentStage(TooltipMakerAPI info, float width, float height) {
        super.addDescriptionForCurrentStage(info, width, height);
        float opad = 10f;

        if (currentStage instanceof EventCancelReason) {
            String str = ((EventCancelReason)currentStage).getReason();
            if (str == null) return;
            str = StringHelper.substituteToken(str, "$market", deliveryMarket.getName());
            str = StringHelper.substituteFactionTokens(str, getPerson().getFaction());
            info.addPara(str, opad);
        }
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;

        if (currentStage == Stage.TALK_TO_PERSON) {
            String str = RebellionIntel.getString("stageDesc");
            str = StringHelper.substituteToken(str,"$onOrAt", deliveryMarket.getOnOrAt());
            info.addPara(str, opad, deliveryMarket.getTextColorForFactionOrPlanet(), deliveryMarket.getName());
            info.showCargo(getCargoForDisplay(), 10, true, opad);
        }
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (currentStage == Stage.TALK_TO_PERSON) {
            info.addPara(RebellionIntel.getString("nextStepText"), pad,
                    deliveryMarket.getTextColorForFactionOrPlanet(), deliveryMarket.getName());
            return true;
        }
        return false;
    }

    @Override
    public String getBaseName() {
        return RebellionIntel.getString("missionName") + ": " + deliveryMarket.getName();
    }

    @Override
    public void accept(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.accept(dialog, memoryMap);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(deliveryMarket.getFactionId());
        return tags;
    }

    public static class DontRebelConditionChecker implements ConditionChecker {

        protected MarketAPI market;
        protected FactionAPI rebelFaction;
        protected SupplyRebellion mission;

        public DontRebelConditionChecker(MarketAPI market, FactionAPI rebelFaction, @Nullable SupplyRebellion mission) {
            this.market = market;
            this.rebelFaction = rebelFaction;
            this.mission = mission;
        }

        @Override
        public boolean conditionsMet() {
            return (rebelFaction.isAtWorst(market.getFactionId(), RepLevel.FAVORABLE));
        }
    }
}

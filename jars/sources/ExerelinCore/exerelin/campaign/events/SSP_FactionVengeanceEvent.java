package exerelin.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.EncounterOption;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParams;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class SSP_FactionVengeanceEvent extends BaseEventPlugin {

    public static final Map<String, Float> FACTION_ADJUST = new HashMap<>(4);
	public static final Set<String> EXCEPTION_LIST = new HashSet<>(Arrays.asList(new String[] {
		Factions.DERELICT, Factions.REMNANTS, Factions.INDEPENDENT, 
		Factions.SCAVENGERS, Factions.NEUTRAL, Factions.LUDDIC_PATH
	}));

    public static Logger log = Global.getLogger(SSP_FactionVengeanceEvent.class);

    static {
        FACTION_ADJUST.put(Factions.TRITACHYON, 1.1f);
        FACTION_ADJUST.put("blackrock_driveyards", 1.15f);
        FACTION_ADJUST.put("cabal", 1.25f);
        FACTION_ADJUST.put("templars", 1.5f);
    }
	
	protected Map<String, Object> params = new HashMap<>();
    private float daysLeft;
    private VengeanceDef def;
    private int duration;
    private boolean ended = false;
    private int escalationLevel;
    private CampaignFleetAPI fleet;
    private boolean foundPlayerYet = false;
    private final IntervalUtil interval = new IntervalUtil(0.4f, 0.6f);
    private final IntervalUtil interval2 = new IntervalUtil(1f, 2f);
    private float timeSpentLooking = 0f;
    private boolean trackingMode = false;
	
	protected void setEscalationStage()
	{
		float escalation = RevengeanceManagerEvent.getOngoingEvent().getVengeanceEscalation(faction.getId());
		/*
		if (faction.getRelToPlayer().getRel() <= -0.9)
			escalation = 2;
		else if (faction.getRelToPlayer().getRel() <= -0.7)
			escalation = 1;
		*/
		
		 // FIXME: nicer handling
        if (escalation < 1f || def.ravingMadFleet == null || def.ravingMadName == null) {
            escalationLevel = 0;
        } else if (escalation < 2f || def.starkRavingMadFleet == null || def.starkRavingMadName == null) {
            escalationLevel = 1;
        } else {
            escalationLevel = 2;
        }
	}
	
    @Override
    public void advance(float amount) {
        if (eventTarget != null) {
            setTarget(eventTarget);
        }

        if (!isEventStarted()) {
            return;
        }
        if (isDone()) {
            return;
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null) {
            return;
        }

        if (!fleet.isAlive()) {
            endEvent();
            return;
        }

        if (!fleet.getFaction().isHostileTo(Factions.PLAYER)) {
            endEvent();
            return;
        }

        /* Advance faster and faster if they lost you */
        float days = Global.getSector().getClock().convertToDays(amount);
        if (foundPlayerYet) {
            timeSpentLooking += days;
            daysLeft -= days * (2f + (escalationLevel * timeSpentLooking / duration));
        } else {
            daysLeft -= days;
        }
        interval.advance(days);
        interval2.advance(days);

        if (interval2.intervalElapsed()) {
            if (fleet.getAI().getCurrentAssignmentType() == FleetAssignment.PATROL_SYSTEM &&
                    ((ModularFleetAIAPI) fleet.getAI()).getTacticalModule().getTarget() != playerFleet) {
                ((ModularFleetAIAPI) fleet.getAI()).getTacticalModule().setPriorityTarget(playerFleet, 1000, false);
                ((ModularFleetAIAPI) fleet.getAI()).getTacticalModule().setTarget(playerFleet);
            }
        }

        if (!interval.intervalElapsed()) {
            return;
        }

        boolean playerVisible = false;
        boolean fleetVisible = false;
        if (fleet.getContainingLocation().equals(playerFleet.getContainingLocation())) {
            playerVisible = playerFleet.isVisibleToSensorsOf(fleet);
            fleetVisible = fleet.isVisibleToSensorsOf(playerFleet);
        }
        if (playerVisible && fleetVisible) {
            foundPlayerYet = true;
        }

        if (trackingMode && fleet.getContainingLocation().equals(playerFleet.getContainingLocation())) {
            if (Misc.getDistance(fleet.getLocation(), playerFleet.getLocation()) <= 1000f + 1.5f * Math.max(
                    fleet.getMaxSensorRangeToDetect(playerFleet),
                    playerFleet.getMaxSensorRangeToDetect(fleet))) {
                trackingMode = false;
            }
        }

        EncounterOption option = fleet.getAI().pickEncounterOption(null, playerFleet);
        if (option == EncounterOption.ENGAGE || option == EncounterOption.HOLD_VS_STRONGER) {
            if (playerVisible || foundPlayerYet) {
                if (fleet.getContainingLocation().equals(playerFleet.getContainingLocation()) && !trackingMode) {
                    if (fleet.getAI().getCurrentAssignmentType() != FleetAssignment.PATROL_SYSTEM) {
                        fleet.clearAssignments();
                        fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, playerFleet, 1000, "hunting your fleet");
                        fleet.getAbility(Abilities.EMERGENCY_BURN).activate();
                        ((ModularFleetAIAPI) fleet.getAI()).getTacticalModule().setPriorityTarget(playerFleet, 1000,
                                                                                                  false);
                    }
                } else {
                    trackingMode = true;
                    if (fleet.getContainingLocation().equals(playerFleet.getContainingLocation())) {
                        if (fleet.getAI().getCurrentAssignmentType() != FleetAssignment.INTERCEPT) {
                            fleet.clearAssignments();
                            fleet.addAssignment(FleetAssignment.INTERCEPT, playerFleet, 1000, 
									StringHelper.getFleetAssignmentString("intercepting", playerFleet.getName()));
                        }
                    } else {
                        if (fleet.getAI().getCurrentAssignmentType() != FleetAssignment.DELIVER_CREW) {
                            fleet.clearAssignments();
                            fleet.addAssignment(FleetAssignment.DELIVER_CREW, playerFleet, 1000,
                                                StringHelper.getFleetAssignmentString("intercepting", playerFleet.getName()));
                        }
                    }
                }
            } else {
                if (fleet.getAI().getCurrentAssignmentType() != FleetAssignment.DELIVER_CREW) {
                    fleet.clearAssignments();
                    fleet.addAssignment(FleetAssignment.DELIVER_CREW, playerFleet, 1000, "intercepting your fleet");
                }
            }
        } else {
            endEvent();
            return;
        }

        if (!fleetVisible || !playerVisible) {
            if (daysLeft <= 0f) {
                endEvent();
            }
        }
    }

    @Override
    public String getCurrentImage() {
        return faction.getLogo();
    }

    @Override
    public String getCurrentMessageIcon() {
        return faction.getCrest();
    }

    @Override
    public CampaignEventCategory getEventCategory() {
        return CampaignEventCategory.EVENT;
    }

    @Override
    public String getEventIcon() {
        return faction.getCrest();
    }

    @Override
    public String getEventName() {
        switch (escalationLevel) {
            default:
            case 0:
                return Misc.ucFirst(faction.getDisplayName()) + " " + def.madName;
            case 1:
                return Misc.ucFirst(faction.getDisplayName()) + " " + def.ravingMadName;
            case 2:
                return Misc.ucFirst(faction.getDisplayName()) + " " + def.starkRavingMadName;
        }
    }

    @Override
    public Color[] getHighlightColors(String stageId) {
        Color[] colors = new Color[1];
        colors[0] = Misc.getHighlightColor();
        return colors;
    }

    @Override
    public String[] getHighlights(String stageId) {
        List<String> result = new ArrayList<>(1);
        addTokensToList(result, "$duration");
        return result.toArray(new String[result.size()]);
    }

    @Override
    public Map<String, String> getTokenReplacements() {
        Map<String, String> map = super.getTokenReplacements();
        map.put("$duration", Misc.getAtLeastStringForDays(duration));
        switch (escalationLevel) {
            default:
            case 0:
                map.put("$fleetType", def.madFleet.toLowerCase());
                map.put("$aFleetType", def.madFleetSingle.toLowerCase());
                map.put("$FleetType", Misc.ucFirst(def.madFleet.toLowerCase()));
                map.put("$AFleetType", Misc.ucFirst(def.madFleetSingle.toLowerCase()));
                break;
            case 1:
                map.put("$fleetType", def.ravingMadFleet.toLowerCase());
                map.put("$aFleetType", def.ravingMadFleetSingle.toLowerCase());
                map.put("$FleetType", Misc.ucFirst(def.ravingMadFleet.toLowerCase()));
                map.put("$AFleetType", Misc.ucFirst(def.ravingMadFleetSingle.toLowerCase()));
                break;
            case 2:
                map.put("$fleetType", def.starkRavingMadFleet.toLowerCase());
                map.put("$aFleetType", def.starkRavingMadFleetSingle.toLowerCase());
                map.put("$FleetType", Misc.ucFirst(def.starkRavingMadFleet.toLowerCase()));
                map.put("$AFleetType", Misc.ucFirst(def.starkRavingMadFleetSingle.toLowerCase()));
                break;
        }
        if (faction.getDisplayNameIsOrAre().contentEquals("is")) {
            map.put("$factionHasOrHave", "has");
        } else {
            map.put("$factionHasOrHave", "have");
        }
        return map;
    }

    @Override
    public void init(String eventType, CampaignEventTarget eventTarget) {
        super.init(eventType, eventTarget, false);
        
		if (!RevengeanceManagerEvent.isRevengeanceEnabled()) {
            endEvent();
            return;
        }
		
        def = VengeanceDef.getDef(faction.getId());
        if (def == null) {
            endEvent();
            return;
        }
    }
	
    @Override
    public boolean isDone() {
        return ended;
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        if (!isEventStarted()) {
            return;
        }
        if (isDone()) {
            return;
        }

        if (!battle.isPlayerInvolved() || !battle.isInvolved(fleet) || battle.onPlayerSide(fleet)) {
            return;
        }

        float before = 0f;
        List<CampaignFleetAPI> side = battle.getSnapshotSideFor(fleet);
        for (CampaignFleetAPI sideFleet : side) {
            before += sideFleet.getFleetPoints();
        }
        before = Math.max(1f, before);
        float after = 0f;
        side = battle.getSideFor(fleet);
        for (CampaignFleetAPI sideFleet : side) {
            after += sideFleet.getFleetPoints();
        }
        float loss = Math.max(0f, 1f - (after / before));
    }

    @Override
    public void startEvent() {
        if (eventTarget != null) {
            setTarget(eventTarget);
        }

        super.startEvent(true);
		
		if (!RevengeanceManagerEvent.isRevengeanceEnabled()) {
            endEvent();
            return;
        }

        def = VengeanceDef.getDef(faction.getId());
        if (def == null) {
            endEvent();
            return;
        }

        if (faction.isAtWorst(Factions.PLAYER, RepLevel.HOSTILE)) {
            //endEvent();
            //return;
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null) {
            endEvent();
            return;
        }

        float distance = Misc.getDistanceToPlayerLY(entity);
        float escalation = RevengeanceManagerEvent.getOngoingEvent().getVengeanceEscalation(faction.getId());
        if (escalation < 1f || def.ravingMadFleet == null || def.ravingMadName == null) {
            escalationLevel = 0;
            duration = Math.max(30,
                                Math.min(90,
                                         Math.round((20f + distance) * MathUtils.getRandomNumberInRange(0.5f, 0.75f))));
        } else if (escalation < 2f || def.starkRavingMadFleet == null || def.starkRavingMadName == null) {
            escalationLevel = 1;
            duration = Math.max(60, Math.min(120,
                                             Math.round((20f + distance) * MathUtils.getRandomNumberInRange(1f, 1.25f))));
        } else {
            escalationLevel = 2;
            duration = Math.max(90, Math.min(150,
                                             Math.round((20f + distance) * MathUtils.getRandomNumberInRange(1.5f, 1.75f))));
        }
        daysLeft = duration;

        float player = ExerelinUtilsFleet.calculatePowerLevel(playerFleet) * 0.1f;
        Float mod = FACTION_ADJUST.get(faction.getId());
        if (mod == null) {
            mod = 1f;
        }
        int combat, freighter, tanker, utility;
        float bonus;
        switch (escalationLevel) {
            default:
            case 0:
                combat = Math.round(Math.max(6f, player * MathUtils.getRandomNumberInRange(0.5f, 0.75f) / mod));
                combat = Math.min(30, combat);
                freighter = Math.round(combat / 10f);
                tanker = Math.round(combat / 15f);
                utility = Math.round(combat / 20f);
                bonus = 0.1f;
                break;
            case 1:
                if (player < 16f) {
                    combat = Math.round(Math.max(9f, player * MathUtils.getRandomNumberInRange(0.75f, 1f) / mod));
                } else {
                    combat =
                    Math.round((14f / mod) + (player - 16f) * MathUtils.getRandomNumberInRange(0.5f, 0.75f) / mod);
                }
                combat = Math.min(45, combat);
                freighter = Math.round(combat / 10f);
                tanker = Math.round(combat / 15f);
                utility = Math.round(combat / 20f);
                bonus = 0.3f;
                break;
            case 2:
                if (player < 24f) {
                    combat = Math.round(Math.max(12f, player * MathUtils.getRandomNumberInRange(1f, 1.25f) / mod));
                } else if (player < 48f) {
                    combat = Math.round((27f / mod) + (player - 24f) * MathUtils.getRandomNumberInRange(0.75f, 1f) /
                    mod);
                } else {
                    combat =
                    Math.round((48f / mod) + (player - 48f) * MathUtils.getRandomNumberInRange(0.5f, 0.75f) / mod);
                }
                combat = Math.min(60, combat);
                freighter = Math.round(combat / 10f);
                tanker = Math.round(combat / 15f);
                utility = Math.round(combat / 20f);
                bonus = 0.5f;
                break;
        }

        int total = combat + freighter + tanker + utility;
        if (total > 25 && total <= 50) {
            bonus += 0.25f;
        } else if (total > 50 && total <= 100) {
            bonus += 0.5f;
        } else if (total > 100) {
            bonus += 0.75f;
        }
        final float finalBonus = bonus;

        final int finalCombat = combat;
        final int finalFreighter = freighter;
        final int finalTanker = tanker;
        final int finalUtility = utility;
		FleetParams params = new FleetParams(null, // location
												market, // market
												faction.getId(),
												null, // fleet's faction, if different from above, which is also used for source market picking
												FleetTypes.PATROL_LARGE,
												finalCombat, // combatPts
												finalFreighter, // freighterPts
												finalTanker, // tankerPts
												0f, // transportPts
												0f, // linerPts
												0f, // civilianPts
												finalUtility, // utilityPts
												finalBonus, // qualityBonus
												-1f, // qualityOverride
												1f + finalBonus, // officer num mult
												Math.round(finalBonus * 10f));
        fleet = ExerelinUtilsFleet.customCreateFleet(faction, params);

        if (fleet == null) {
            endEvent();
            return;
        }

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_TYPE, "vengeanceFleet");
        fleet.getMemoryWithoutUpdate().set("$escalation", (float) escalationLevel);
        switch (escalationLevel) {
            default:
            case 0:
                fleet.setName(def.madFleet);
                if (total > 100) {
                    fleet.getFlagship().getCaptain().setRankId(Ranks.SPACE_ADMIRAL);
                    fleet.getFlagship().getCaptain().setPostId(Ranks.POST_FLEET_COMMANDER);
                } else if (total > 50) {
                    fleet.getFlagship().getCaptain().setRankId(Ranks.SPACE_CAPTAIN);
                    fleet.getFlagship().getCaptain().setPostId(Ranks.POST_FLEET_COMMANDER);
                } else {
                    fleet.getFlagship().getCaptain().setRankId(Ranks.SPACE_COMMANDER);
                    fleet.getFlagship().getCaptain().setPostId(Ranks.POST_FLEET_COMMANDER);
                }
                break;
            case 1:
                fleet.setName(def.ravingMadFleet);
                if (total > 100) {
                    fleet.getFlagship().getCaptain().setRankId(Ranks.SPACE_ADMIRAL);
                    fleet.getFlagship().getCaptain().setPostId(Ranks.POST_FLEET_COMMANDER);
                } else {
                    fleet.getFlagship().getCaptain().setRankId(Ranks.SPACE_CAPTAIN);
                    fleet.getFlagship().getCaptain().setPostId(Ranks.POST_FLEET_COMMANDER);
                }
                break;
            case 2:
                fleet.setName(def.starkRavingMadFleet);
                fleet.getFlagship().getCaptain().setRankId(Ranks.SPACE_ADMIRAL);
                fleet.getFlagship().getCaptain().setPostId(Ranks.POST_FLEET_COMMANDER);
                break;
        }
        if (playerFleet.getContainingLocation() != market.getContainingLocation()) {
            market.getPrimaryEntity().getContainingLocation().addEntity(fleet);
            fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);

            fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, market.getPrimaryEntity(), 2f + (float) Math.random() *
                                2f,
                                "orbiting " + market.getName());
        } else {
            Vector2f loc = Misc.pickHyperLocationNotNearPlayer(market.getLocationInHyperspace(),
                                                               Global.getSettings().getMaxSensorRange() + 500f);
            Global.getSector().getHyperspace().addEntity(fleet);
            fleet.setLocation(loc.x, loc.y);
        }

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SAW_PLAYER_WITH_TRANSPONDER_ON, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);

        float extraExtremeScale;
        switch (escalationLevel) {
            default:
            case 0:
                extraExtremeScale = 1f + player / 24f;
                break;
            case 1:
                extraExtremeScale = 1f + player / 48f;
                break;
            case 2:
                extraExtremeScale = 1f + player / 96f;
                break;
        }
        switch (escalationLevel) {
            default:
            case 0:
                if (def.ravingMadName == null) {
                    Global.getSector().reportEventStage(this, "mad", market.getPrimaryEntity(),
                                                        MessagePriority.CLUSTER);
                } else {
                    Global.getSector().reportEventStage(this, "mad", market.getPrimaryEntity(), MessagePriority.CLUSTER);
                }
                break;
            case 1:
                if (def.starkRavingMadName == null) {
                    Global.getSector().reportEventStage(this, "raving_mad", market.getPrimaryEntity(),
                                                        MessagePriority.SECTOR);
                } else {
                    Global.getSector().reportEventStage(this, "raving_mad", market.getPrimaryEntity(),
                                                        MessagePriority.SECTOR);
                }
                break;
            case 2:
                Global.getSector().reportEventStage(this, "stark_raving_mad", market.getPrimaryEntity(),
                                                    MessagePriority.ENSURE_DELIVERY);
                break;
        }
        log.info("Started event of escalation level " + escalationLevel + " for " + faction.getDisplayName());
    }

    private void endEvent() {
        ended = true;
        if (fleet != null && fleet.isAlive()) {
            fleet.clearAssignments();
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, market.getPrimaryEntity(), 1000,
                                StringHelper.getFleetAssignmentString("returningTo", market.getName()));
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, false);
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, false);
            ((ModularFleetAIAPI) fleet.getAI()).getTacticalModule().forceTargetReEval();
        }
    }

    public static enum VengeanceDef {

		GENERIC("",
                 "Grudge", "Hunter Fleet", "a Hunter Fleet",
                 "Vendetta", "Executors", "Executors",
                 null, null, null,	//"Revenge", "Kill-Fleet", "a Kill-Fleet",
                 0.5f),
		
        HEGEMONY(Factions.HEGEMONY,
                 "Grudge", "Hunter Fleet", "a Hunter Fleet",
                 "Vendetta", "Executors", "Executors",
                 "Revenge", "Kill-Fleet", "a Kill-Fleet",
                 0.75f),
        TRITACHYON(Factions.TRITACHYON,
                   "Grudge", "Hunter Fleet", "a Hunter Fleet",
                   "Vendetta", "Executors", "Executors",
                   null, null, null, 0.5f),
        DIKTAT(Factions.DIKTAT,
               "Grudge", "Hunter Fleet", "a Hunter Fleet",
               "Vendetta", "Executors", "Executors",
               "Revenge", "Kill-Fleet", "a Kill-Fleet",
               1f),
        PERSEAN(Factions.PERSEAN,
                "Grudge", "Hunter Fleet", "a Hunter Fleet",
                "Vendetta", "Executors", "Executors",
                null, null, null, 0.5f),
        LUDDIC_CHURCH(Factions.LUDDIC_CHURCH,
                      "Excommunication", "Evangelists", "Evangelists",
                      "Interdiction", "Inquisition", "an Inquisition",
                      "Jihad", "Crusade Fleet", "a Crusade Fleet",
                      0.5f),
        PIRATES(Factions.PIRATES,
                "Grudge", "Enforcers", "Enforcers",
                "Vendetta", "Hit-Fleet", "a Hit-Fleet",
                "Revenge", "Horde", "a Horde",
                0.33f),
        CABAL("cabal",
              "Grudge", "Executors", "Executors",
              "Elimination", "Kill-Fleet", "a Kill-Fleet",
              null, null, null, 0.25f),
        IMPERIUM("interstellarimperium",
                 "Grudge", "Hunter Fleet", "a Hunter Fleet",
                 "Vendetta", "Executors", "Executors",
                 "Revenge", "Kill-Fleet", "a Kill-Fleet",
                 0.75f),
        CITADEL("citadeldefenders",
                "Grudge", "Enforcers", "Enforcers",
                null, null, null, null, null, null, 0.5f),
        BLACKROCK("blackrock_driveyards",
                  "Grudge", "Hunter Fleet", "a Hunter Fleet",
                  "Vendetta", "Executors", "Executors",
                  "Revenge", "Kill-Fleet", "a Kill-Fleet",
                  0.5f),
        EXIGENCY("exigency",
                 "Grudge", "Hunter Fleet", "a Hunter Fleet",
                 "Vendetta", "Executors", "Executors",
                 "Revenge", "Kill-Fleet", "a Kill-Fleet",
                 1f),
        AHRIMAN("exipirated",
                "Grudge", "Enforcers", "Enforcers",
                "Vendetta", "Hit-Fleet", "a Hit-Fleet",
                "Revenge", "Reavers", "Reavers",
                0.5f),
        TEMPLARS("templars",
                 "Excommunication", "Evangelists", "Evangelists",
                 "Interdiction", "Inquisition", "an Inquisition",
                 "Damnation", "Crusade Fleet", "a Crusade Fleet",
                 0.5f),
        SHADOWYARDS("shadow_industry",
                    "Grudge", "Hunter Fleet", "a Hunter Fleet",
                    "Vendetta", "Executors", "Executors",
                    null, null, null, 0.5f),
        MAYORATE("mayorate",
                 "Grudge", "Enforcers", "Enforcers",
                 "Vendetta", "Hit-Fleet", "a Hit-Fleet",
                 "Revenge", "Kill-Fleet", "a Kill-Fleet",
                 0.75f),
        JUNK_PIRATES("junk_pirates",
                     "Grudge", "Enforcers", "Enforcers",
                     "Vendetta", "Hit-Fleet", "a Hit-Fleet",
                     null, null, null, 0.5f),
        PACK("pack",
             "Grudge", "Hunter Fleet", "a Hunter Fleet",
             "Vendetta", "Executors", "Executors",
             null, null, null, 0.5f),
        ASP_SYNDICATE("syndicate_asp",
                      "Grudge", "Enforcers", "a Hunter Fleet",
                      "Vendetta", "Hit-Fleet", "a Hit-Fleet",
                      "Revenge", "Kill-Fleet", "a Kill-Fleet",
                      0.75f),
        SCY("SCY",
            "Grudge", "Seeker Fleet", "a Seeker Fleet",
            null, null, null, null, null, null, 0.5f),
        TIANDONG("tiandong",
                 "Grudge", "Enforcers", "Enforcers",
                 "Vendetta", "Hit-Fleet", "a Hit-Fleet",
                 null, null, null, 0.5f),
        DIABLE("diableavionics",
               "Grudge", "Hunter Fleet", "a Hunter Fleet",
               "Vendetta", "Executors", "Executors",
               "Revenge", "Kill-Fleet", "a Kill-Fleet",
               1f),
        ORA("ORA",
            "Grudge", "Enforcers", "Enforcers",
            null, null, null, null, null, null, 0.5f);

        final String faction;
        final String madName;
        final String madFleet;
        final String madFleetSingle;
        final String ravingMadName;
        final String ravingMadFleet;
        final String ravingMadFleetSingle;
        final String starkRavingMadName;
        final String starkRavingMadFleet;
        final String starkRavingMadFleetSingle;
        final float vengefulness;

        private VengeanceDef(String faction, String madName, String madFleet, String madFleetSingle,
                             String ravingMadName, String ravingMadFleet,
                             String ravingMadFleetSingle, String starkRavingMadName, String starkRavingMadFleet,
                             String starkRavingMadFleetSingle,
                             float vengefulness) {
            this.faction = faction;
            this.madName = madName;
            this.madFleet = madFleet;
            this.madFleetSingle = madFleetSingle;
            this.ravingMadName = ravingMadName;
            this.ravingMadFleet = ravingMadFleet;
            this.ravingMadFleetSingle = ravingMadFleetSingle;
            this.starkRavingMadName = starkRavingMadName;
            this.starkRavingMadFleet = starkRavingMadFleet;
            this.starkRavingMadFleetSingle = starkRavingMadFleetSingle;
            this.vengefulness = vengefulness;
        }
		
		private VengeanceDef(String faction, boolean hasRavingMad, boolean hasStarkRavingMad,
                             float vengefulness) {
            this.faction = faction;
            this.madName = "";
            this.madFleet = "";
            this.madFleetSingle = "";
			if (hasRavingMad)
			{
				this.ravingMadName = "";
				this.ravingMadFleet = "";
				this.ravingMadFleetSingle = "";
				if (hasStarkRavingMad)
				{
					this.starkRavingMadName = "";
					this.starkRavingMadFleet = "";
					this.starkRavingMadFleetSingle = "";
				}
				else
				{
					this.starkRavingMadName = null;
					this.starkRavingMadFleet = null;
					this.starkRavingMadFleetSingle = null;
				}
			}
			else
			{
				this.ravingMadName = null;
				this.ravingMadFleet = null;
				this.ravingMadFleetSingle = null;
				this.starkRavingMadName = null;
				this.starkRavingMadFleet = null;
				this.starkRavingMadFleetSingle = null;
			}
            this.vengefulness = vengefulness;
        }

        static VengeanceDef getDef(String faction) {
            for (VengeanceDef def : VengeanceDef.values()) {
                if (def.faction.contentEquals(faction)) {
                    return def;
                }
            }
            return VengeanceDef.GENERIC;
        }
		
		// TODO
		static String getName(int escalationLevel)
		{
			return "";
		}
    }
}
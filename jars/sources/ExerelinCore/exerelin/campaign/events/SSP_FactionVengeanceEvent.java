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
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
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
        Factions.SCAVENGERS, Factions.NEUTRAL	//, Factions.LUDDIC_PATH
    }));

    public static Logger log = Global.getLogger(SSP_FactionVengeanceEvent.class);

    static {
        FACTION_ADJUST.put(Factions.TRITACHYON, 1.1f);
        FACTION_ADJUST.put("blackrock_driveyards", 1.15f);
        FACTION_ADJUST.put("cabal", 1.25f);
        FACTION_ADJUST.put("templars", 1.5f);
    }
    
    protected float daysLeft;
    protected VengeanceDef def;
    protected int duration;
    protected boolean ended = false;
    protected int escalationLevel;
    protected CampaignFleetAPI fleet;
    protected boolean foundPlayerYet = false;
    protected final IntervalUtil interval = new IntervalUtil(0.4f, 0.6f);
    protected final IntervalUtil interval2 = new IntervalUtil(1f, 2f);
    protected float timeSpentLooking = 0f;
    protected boolean trackingMode = false;
    
    protected void setEscalationStage()
    {
        escalationLevel = RevengeanceManagerEvent.getOngoingEvent().getVengeanceEscalation(faction.getId());
        
        /*
        if (faction.getRelToPlayer().getRel() <= -0.9)
            escalation = 2;
        else if (faction.getRelToPlayer().getRel() <= -0.7)
            escalation = 1;
        */
        
        if (escalationLevel > def.maxLevel) escalationLevel = def.maxLevel;
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
        
        // fleet took too many losses, quit
        if (fleet.getMemoryWithoutUpdate().contains("$startingFP") && fleet.getFleetPoints() < 0.4 * fleet.getMemoryWithoutUpdate().getFloat("$startingFP"))
        {
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
        
        String targetName = StringHelper.getString("yourFleet");

        EncounterOption option = fleet.getAI().pickEncounterOption(null, playerFleet);
        if (option == EncounterOption.ENGAGE || option == EncounterOption.HOLD_VS_STRONGER) {
            if (playerVisible || foundPlayerYet) {
                if (fleet.getContainingLocation().equals(playerFleet.getContainingLocation()) && !trackingMode) {
                    if (fleet.getAI().getCurrentAssignmentType() != FleetAssignment.PATROL_SYSTEM) {
                        fleet.clearAssignments();
                        fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, playerFleet, 1000,
                                StringHelper.getFleetAssignmentString("hunting", targetName));
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
                                    StringHelper.getFleetAssignmentString("intercepting", targetName));
                        }
                    } else {
                        if (fleet.getAI().getCurrentAssignmentType() != FleetAssignment.DELIVER_CREW) {
                            fleet.clearAssignments();
                            fleet.addAssignment(FleetAssignment.DELIVER_CREW, playerFleet, 1000,
                                                StringHelper.getFleetAssignmentString("trailing", targetName));
                        }
                    }
                }
            } else {
                if (fleet.getAI().getCurrentAssignmentType() != FleetAssignment.DELIVER_CREW) {
                    fleet.clearAssignments();
                    fleet.addAssignment(FleetAssignment.DELIVER_CREW, playerFleet, 1000, 
                            StringHelper.getFleetAssignmentString("trailing", targetName));
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
        return Misc.ucFirst(faction.getDisplayName()) + " " + def.getName(faction.getId(), escalationLevel);
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

        String name = def.getFleetName(faction.getId(), escalationLevel).toLowerCase();
        String nameSingle = def.getFleetNameSingle(faction.getId(), escalationLevel).toLowerCase();
        map.put("$fleetType", name);
        map.put("$aFleetType", nameSingle);
        map.put("$FleetType", Misc.ucFirst(name));
        map.put("$AFleetType", Misc.ucFirst(nameSingle));
        
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
        setEscalationStage();
		switch (escalationLevel) {
			case 0:
				duration = Math.max(60,
						Math.min(120,
								Math.round((20f + distance) * MathUtils.getRandomNumberInRange(0.75f, 1f))));
				break;
			case 1:
				duration = Math.max(90, Math.min(150,
						Math.round((20f + distance) * MathUtils.getRandomNumberInRange(1.25f, 1.75f))));
				break;
			default:
				duration = Math.max(120, Math.min(180,
						Math.round((20f + distance) * MathUtils.getRandomNumberInRange(2f, 2.5f))));
				break;
		}
        daysLeft = duration;

        float player = ExerelinUtilsFleet.calculatePowerLevel(playerFleet) * 0.1f;
        Float mod = FACTION_ADJUST.get(faction.getId());
        if (mod == null) {
            mod = 1f;
        }
        int capBonus = (int)(ExerelinUtilsFleet.getPlayerLevelFPBonus() + 0.5f);
        int combat, freighter, tanker, utility;
        float bonus;
        switch (escalationLevel) {
            default:
            case 0:
                combat = Math.round(Math.max(6f, player * MathUtils.getRandomNumberInRange(0.5f, 0.75f) / mod));
                combat = Math.min(30 + capBonus, combat);
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
                combat = Math.min(45 + capBonus, combat);
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
                combat = Math.min(60 + capBonus, combat);
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
        
        float sizeMult = ExerelinConfig.getExerelinFactionConfig(faction.getId()).vengeanceFleetSizeMult;
        combat *= sizeMult;
        freighter *= sizeMult;
        tanker *= sizeMult;
        utility *= sizeMult;
        
        final float finalBonus = bonus;

        final int finalCombat = combat;
        final int finalFreighter = freighter;
        final int finalTanker = tanker;
        final int finalUtility = utility;
        FleetParamsV3 params = new FleetParamsV3(market, // market
                                                "vengeanceFleet",
                                                finalCombat, // combatPts
                                                finalFreighter, // freighterPts
                                                finalTanker, // tankerPts
                                                0f, // transportPts
                                                0f, // linerPts
                                                finalUtility, // utilityPts
                                                finalBonus // qualityMod
                                                );
        fleet = ExerelinUtilsFleet.customCreateFleet(faction, params);

        if (fleet == null) {
            endEvent();
            return;
        }

        //fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_TYPE, "vengeanceFleet");
        fleet.getMemoryWithoutUpdate().set("$escalation", (float) escalationLevel);
        fleet.getMemoryWithoutUpdate().set("$startingFP", fleet.getFleetPoints());
        fleet.setName(def.getFleetName(faction.getId(), escalationLevel));
        switch (escalationLevel) {
            default:
            case 0:
                
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
                if (total > 100) {
                    fleet.getFlagship().getCaptain().setRankId(Ranks.SPACE_ADMIRAL);
                    fleet.getFlagship().getCaptain().setPostId(Ranks.POST_FLEET_COMMANDER);
                } else {
                    fleet.getFlagship().getCaptain().setRankId(Ranks.SPACE_CAPTAIN);
                    fleet.getFlagship().getCaptain().setPostId(Ranks.POST_FLEET_COMMANDER);
                }
                break;
            case 2:
                fleet.getFlagship().getCaptain().setRankId(Ranks.SPACE_ADMIRAL);
                fleet.getFlagship().getCaptain().setPostId(Ranks.POST_FLEET_COMMANDER);
                break;
        }
        if (playerFleet.getContainingLocation() != market.getContainingLocation()) {
            market.getPrimaryEntity().getContainingLocation().addEntity(fleet);
            fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);

            fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, market.getPrimaryEntity(), 2f + (float) Math.random() *
                                2f,
                                StringHelper.getFleetAssignmentString("orbiting", market.getName()));
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
                if (def.maxLevel == 0) {
                    Global.getSector().reportEventStage(this, "mad", market.getPrimaryEntity(),
                                                        MessagePriority.CLUSTER);
                } else {
                    Global.getSector().reportEventStage(this, "mad", market.getPrimaryEntity(), MessagePriority.CLUSTER);
                }
                break;
            case 1:
                if (def.maxLevel == 1) {
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

    protected void endEvent() {
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

        GENERIC("", 1, 0.5f),
        
        HEGEMONY(Factions.HEGEMONY, 2, 0.75f),
        TRITACHYON(Factions.TRITACHYON, 1, 0.5f),
        DIKTAT(Factions.DIKTAT, 2, 1f),
        PERSEAN(Factions.PERSEAN, 1, 0.5f),
        LUDDIC_CHURCH(Factions.LUDDIC_CHURCH, 2, 0.5f),
        LUDDIC_PATH(Factions.LUDDIC_PATH, 2, 1f),
        PIRATES(Factions.PIRATES, 2, 0.33f),
        CABAL("cabal", 1, 0.25f),
        IMPERIUM("interstellarimperium", 2, 0.75f),
        CITADEL("citadeldefenders", 0, 0.5f),
        BLACKROCK("blackrock_driveyards", 2, 0.5f),
        EXIGENCY("exigency", 2, 1f),
        AHRIMAN("exipirated",2, 0.5f),
        TEMPLARS("templars", 2, 0.5f),
        SHADOWYARDS("shadow_industry", 1, 0.5f),
        MAYORATE("mayorate", 2, 0.75f),
        JUNK_PIRATES("junk_pirates", 1, 0.5f),
        PACK("pack", 1, 0.5f),
        ASP_SYNDICATE("syndicate_asp", 2, 0.75f),
        DME("dassault_mikoyan", 2, 0.75f),
        SCY("SCY", 0, 0.5f),
        TIANDONG("tiandong", 1, 0.5f),
        DIABLE("diableavionics", 1, 1f),
        ORA("ORA", 0, 0.5f);

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
        final int maxLevel;
        
        // legacy constructor with non-external names
        @Deprecated
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
            
            if (starkRavingMadName != null)
                maxLevel = 2;
            else if (ravingMadName != null)
                maxLevel = 1;
            else
                maxLevel = 0;
        }
        
        private VengeanceDef(String faction, int maxLevel, float vengefulness) {
            this.faction = faction;
            this.madName = "";
            this.madFleet = "";
            this.madFleetSingle = "";
            if (maxLevel >= 1)
            {
                this.ravingMadName = "";
                this.ravingMadFleet = "";
                this.ravingMadFleetSingle = "";
            }
            else
            {
                this.ravingMadName = null;
                this.ravingMadFleet = null;
                this.ravingMadFleetSingle = null;
            }
            if (maxLevel >= 2)
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
            this.vengefulness = vengefulness;
            this.maxLevel = maxLevel;
        }

        static VengeanceDef getDef(String faction) {
            for (VengeanceDef def : VengeanceDef.values()) {
                if (def.faction.contentEquals(faction)) {
                    return def;
                }
            }
            return VengeanceDef.GENERIC;
        }
        
        boolean isValidString(String str)
        {
            return str != null && !str.isEmpty();
        }
        
        String getName(String faction, int escalationLevel)
        {
			if (faction == null) faction = this.faction;
            String name = "";
            ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(faction);
            if (conf.vengeanceLevelNames.size() > escalationLevel)
            {
                name = conf.vengeanceLevelNames.get(escalationLevel);
                if (isValidString(name))
                    return name;
            }
            switch (escalationLevel)
            {
                case 0:
                    if (isValidString(madName)) return madName;
                case 1:
                    if (isValidString(ravingMadName)) return ravingMadName;
                case 2:
                    if (isValidString(starkRavingMadName)) return starkRavingMadName;
            }
            
            return StringHelper.getString("exerelin_fleets", "vengeanceLevel" + escalationLevel);
        }
        
        String getFleetName(String faction, int escalationLevel)
        {
			if (faction == null) faction = this.faction;
            String name = "";
            ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(faction);
            if (conf.vengeanceFleetNames.size() > escalationLevel)
            {
                name = conf.vengeanceFleetNames.get(escalationLevel);
                if (isValidString(name))
                    return name;
            }
            switch (escalationLevel)
            {
                case 0:
                    if (isValidString(madFleet)) return madFleet;
                case 1:
                    if (isValidString(ravingMadFleet)) return ravingMadFleet;
                case 2:
                    if (isValidString(starkRavingMadFleet)) return starkRavingMadFleet;
            }
            
            return StringHelper.getString("exerelin_fleets", "vengeanceFleet" + escalationLevel);
        }
        
        String getFleetNameSingle(String faction, int escalationLevel)
        {
			if (faction == null) faction = this.faction;
            String name = "";
            ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(faction);
            if (conf.vengeanceFleetNamesSingle.size() > escalationLevel)
            {
                name = conf.vengeanceFleetNamesSingle.get(escalationLevel);
                if (isValidString(name))
                    return name;
            }
            switch (escalationLevel)
            {
                case 0:
                    if (isValidString(madFleetSingle)) return madFleetSingle;
                case 1:
                    if (isValidString(ravingMadFleetSingle)) return ravingMadFleetSingle;
                case 2:
                    if (isValidString(starkRavingMadFleetSingle)) return starkRavingMadFleetSingle;
            }
            
            return StringHelper.getString("exerelin_fleets", "vengeanceFleet" + escalationLevel + "Single");
        }
    }
}
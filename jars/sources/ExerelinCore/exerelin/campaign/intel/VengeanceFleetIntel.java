package exerelin.campaign.intel;

import exerelin.campaign.RevengeanceManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.EncounterOption;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.fleets.InvasionFleetManager;
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

public class VengeanceFleetIntel extends BaseIntelPlugin {

    public static final Map<String, Float> FACTION_ADJUST = new HashMap<>(4);
    public static final Set<String> EXCEPTION_LIST = new HashSet<>(Arrays.asList(new String[] {
        Factions.DERELICT, Factions.REMNANTS, Factions.INDEPENDENT, 
        Factions.SCAVENGERS, Factions.NEUTRAL	//, Factions.LUDDIC_PATH
    }));
	public static final boolean ALWAYS_SPAWN_ONSITE = true;

    public static Logger log = Global.getLogger(VengeanceFleetIntel.class);

    static {
        FACTION_ADJUST.put(Factions.TRITACHYON, 1.1f);
        FACTION_ADJUST.put("blackrock_driveyards", 1.15f);
        FACTION_ADJUST.put("cabal", 1.25f);
        FACTION_ADJUST.put("templars", 1.5f);
    }
    
	protected VengeanceDef def;
	protected String factionId;
	protected MarketAPI market;
	protected EndReason endReason;
	protected boolean assembling = true;
	protected boolean over = false;
	protected float daysToLaunch;
	protected final float daysToLaunchFixed;
    protected float daysLeft;
    protected int duration;
    protected int escalationLevel;
    protected CampaignFleetAPI fleet;
    protected boolean foundPlayerYet = false;
    protected final IntervalUtil interval = new IntervalUtil(0.4f, 0.6f);
    protected final IntervalUtil interval2 = new IntervalUtil(1f, 2f);
    protected float timeSpentLooking = 0f;
    protected boolean trackingMode = false;
	
	public VengeanceFleetIntel(String factionId, MarketAPI market, int escalationLevel) {
		this.factionId = factionId;
		this.market = market;
		def = VengeanceDef.getDef(factionId);
		
		if (escalationLevel < 0) escalationLevel = 0;
		this.escalationLevel = Math.min(escalationLevel, def.maxLevel);
		daysToLaunch = 15 + (this.escalationLevel * 5);	// TODO: maybe something nicer
		daysToLaunchFixed = daysToLaunch;
	}
    
	protected FactionAPI getFaction()
	{
		return Global.getSector().getFaction(factionId);
	}
	
	protected String getString(String id)
	{
		return StringHelper.getString("nex_vengeance", id);
	}
	
	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}
	
	protected String getName() {
		String str = getString("intelTitle");
		str = StringHelper.substituteToken(str, "$level", (escalationLevel + 1) + "");
		return str;
	}
	
	// bullet points
	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		Color c = getTitleColor(mode);
		info.addPara(getName(), c, 0f);
		bullet(info);
		
		FactionAPI faction = Global.getSector().getFaction(factionId);
		
		float initPad = 3, pad = 0;
		Color tc = getBulletColorForMode(mode);
		String name = Misc.ucFirst(faction.getDisplayName());
		info.addPara(name, initPad, tc, faction.getBaseUIColor(), name);
				
		String key = "intelBullet";
		Map<String, String> sub = new HashMap<>();
		
		if (over)
		{
			switch (endReason)
			{
				case FAILED_TO_SPAWN:
					key += "FailedToSpawn";
					break;
				case DEFEATED:
					key += "Defeated";
					break;
				case EXPIRED:
					key += "Expired";
					break;
				case NO_LONGER_HOSTILE:
					key += "NoLongerHostile";
					break;
			}
			addBullet(info, key, sub, pad, tc);
		}
		else if (assembling)
		{
			key += "Assembling";
			String dtl = getDays(daysToLaunch) + " " + getDaysString(daysToLaunch);
			sub.put("$days", dtl);
			if (!isMarketKnown())
			{
				key += "UnknownLoc";
				addBullet(info, key, sub, pad, tc, getDays(daysToLaunch));
			}
			else
			{
				sub.put("$market", market.getName());
				addBullet(info, key, sub, pad, tc, market.getName(), getDays(daysToLaunch));
			}
		}
		else
		{
			key += "Launched";
			sub.put("$market", market.getName());
			addBullet(info, key, sub, pad, tc, market.getName());
		}
	}
	
	protected void addBullet(TooltipMakerAPI info, String key, Map<String, String> sub, float pad, Color color, String... highlights)
	{
		String str = getString(key);
		str = StringHelper.substituteTokens(str, sub);
		
		info.addPara(str, pad, color, Misc.getHighlightColor(), highlights);
	}
	
	// sidebar text description
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		FactionAPI faction = getFaction();
		
		//if (fleet != null)
		//	info.addImages(width, 128, opad, opad, faction.getCrest(), fleet.getCommander().getPortraitSprite());
		//else
			info.addImage(faction.getLogo(), width, 128, opad);
		
		String str = getString("intelDesc" + escalationLevel);
		str = StringHelper.substituteToken(str, "$theFaction", faction.getDisplayNameWithArticle(), true);
		str = StringHelper.substituteToken(str, "$aFleetType", def.getFleetNameSingle(factionId, escalationLevel));
		
		LabelAPI para = info.addPara(str, opad);
		para.setHighlight(faction.getDisplayNameWithArticleWithoutArticle());
		para.setHighlightColor(faction.getBaseUIColor());
		
		info.addSectionHeading(StringHelper.getString("status", true), 
				faction.getBaseUIColor(), faction.getDarkUIColor(), Alignment.MID, opad);
		
		String key = "intelStatus";
		Map<String, String> sub = new HashMap<>();
		List<String> highlights = new ArrayList<>();
		Color hl = Misc.getHighlightColor();
		
		if (over)
		{
			switch (endReason)
			{
				case FAILED_TO_SPAWN:
					key += "FailedToSpawn";
					break;
				case DEFEATED:
					key += "Defeated";
					break;
				case EXPIRED:
					key += "Expired";
					break;
				case NO_LONGER_HOSTILE:
					key += "NoLongerHostile";
					sub.put("$theFaction", faction.getDisplayNameWithArticle());
					sub.put("$TheFaction", Misc.ucFirst(faction.getDisplayNameWithArticle()));
					sub.put("$isOrAre", faction.getDisplayNameIsOrAre());
					hl = faction.getBaseUIColor();
					highlights.add(faction.getDisplayNameWithArticleWithoutArticle());
					break;
			}
		}
		else if (assembling)
		{
			key += "Assembling";
			sub.put("$days", getDays(daysToLaunch) + " " + getDaysString(daysToLaunch));
			if (!isMarketKnown())
			{
				sub.put("$market", StringHelper.getString("anUnknownLocation"));
			}
			else
			{
				sub.put("$market", market.getName());
				highlights.add(market.getName());
			}
			highlights.add(getDays(daysToLaunch));
		}
		else
		{
			key += "Active";
			String durStr = Misc.getAtLeastStringForDays(duration);
			sub.put("$duration", durStr);
			highlights.add(durStr);
		}
		str = getString(key);
		str = StringHelper.substituteTokens(str, sub);
		
		info.addPara(str, opad, hl, highlights.toArray(new String[0]));
	}
	
	protected boolean isMarketKnown()
	{
		if (Global.getSettings().isDevMode()) return true;
		return market.getPrimaryEntity().isVisibleToPlayerFleet();
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(factionId);
		tags.add(Tags.INTEL_MILITARY);
		return tags;
	}

	@Override
	public String getIcon() {
		return getFaction().getCrest();
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return getFaction();
	}

	@Override
	protected float getBaseDaysAfterEnd() {
		return 30;
	}
	
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		if (assembling && isMarketKnown()) return market.getPrimaryEntity();
		return null;
	}
    
    @Override
    public void advanceImpl(float amount) {
		if (over) {
            return;
        }
		
        if (!getFaction().isHostileTo(Factions.PLAYER)) {
            endEvent(EndReason.NO_LONGER_HOSTILE);
            return;
        }
		
        if (assembling) {
			if (!market.getFactionId().equals(factionId))
				endEvent(EndReason.FAILED_TO_SPAWN);
			
			daysToLaunch -= Global.getSector().getClock().convertToDays(amount);
			if (daysToLaunch < 0)
			{
				assembling = false;
				fleet = spawnFleet();
				if (fleet != null) 
					sendUpdateIfPlayerHasIntel(null, false);
				else
					endEvent(EndReason.FAILED_TO_SPAWN);
			}
			return;
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null) {
            return;
        }

        if (!fleet.isAlive()) {
            endEvent(EndReason.DEFEATED);
            return;
        }
        
        // fleet took too many losses, quit
        if (fleet.getMemoryWithoutUpdate().contains("$startingFP") && fleet.getFleetPoints() < 0.4 * fleet.getMemoryWithoutUpdate().getFloat("$startingFP"))
        {
            endEvent(EndReason.DEFEATED);
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
            endEvent(EndReason.DEFEATED);
            return;
        }

        if (!fleetVisible || !playerVisible) {
            if (daysLeft <= 0f) {
                endEvent(EndReason.EXPIRED);
            }
        }
    }
	
	/*
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
	*/
	
    public void startEvent() {
        
        if (!RevengeanceManager.isRevengeanceEnabled()) {
            endEvent(EndReason.OTHER, 0);
            return;
        }

        def = VengeanceDef.getDef(factionId);
        if (def == null) {
            endEvent(EndReason.OTHER, 0);
            return;
        }

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet == null) {
            endEvent(EndReason.OTHER, 0);
            return;
        }

        float distance = Misc.getDistanceToPlayerLY(market.getPrimaryEntity());
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
		
        Global.getSector().getIntelManager().addIntel(this);
		Global.getSector().addScript(this);
        log.info("Started event of escalation level " + escalationLevel + " for " + getFaction().getDisplayName());
    }
	
	protected CampaignFleetAPI spawnFleet()
	{
		if (!market.getFactionId().equals(factionId))
			return null;
		if (!market.isInEconomy() || !market.getPrimaryEntity().isAlive())
			return null;
		
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		float player = ExerelinUtilsFleet.calculatePowerLevel(playerFleet) * 0.4f;
        Float mod = FACTION_ADJUST.get(factionId);
        if (mod == null) {
            mod = 1f;
        }
        int capBonus = Math.round(ExerelinUtilsFleet.getPlayerLevelFPBonus());
		float sizeMult = InvasionFleetManager.getFactionDoctrineFleetSizeMult(market.getFaction());
        int combat, freighter, tanker, utility;
        float bonus;
        switch (escalationLevel) {
            default:
            case 0:
                combat = Math.round(Math.max(30f, player * MathUtils.getRandomNumberInRange(0.5f, 0.75f) / mod));
                combat = Math.min(120 + capBonus, combat);
                combat *= sizeMult;
                freighter = Math.round(combat / 20f);
                tanker = Math.round(combat / 30f);
                utility = Math.round(combat / 40f);
                bonus = 0.1f;
                break;
            case 1:
                if (player < 80f) {
                    combat = Math.round(Math.max(45f, player * MathUtils.getRandomNumberInRange(0.75f, 1f) / mod));
                } else {
                    combat =
                    Math.round((70f / mod) + (player - 80f) * MathUtils.getRandomNumberInRange(0.5f, 0.75f) / mod);
                }
                combat = (int)Math.min(210 + capBonus * 1.5f, combat);
                combat *= sizeMult;
                freighter = Math.round(combat / 20f);
                tanker = Math.round(combat / 30f);
                utility = Math.round(combat / 40f);
                bonus = 0.3f;
                break;
            case 2:
                if (player < 120f) {
                    combat = Math.round(Math.max(60f, player * MathUtils.getRandomNumberInRange(1f, 1.25f) / mod));
                } else if (player < 240f) {
                    combat = Math.round((135f / mod) + (player - 120f) * MathUtils.getRandomNumberInRange(0.75f, 1f) /
                    mod);
                } else {
                    combat =
                    Math.round((240f / mod) + (player - 240f) * MathUtils.getRandomNumberInRange(0.5f, 0.75f) / mod);
                }
                combat = Math.min(300 + capBonus * 2, combat);
                combat *= sizeMult;
                freighter = Math.round(combat / 20f);
                tanker = Math.round(combat / 30f);
                utility = Math.round(combat / 40f);
                bonus = 0.5f;
                break;
        }
        
        int total = combat + freighter + tanker + utility;
        /*
        if (total > 125 && total <= 250) {
            bonus += 0.25f;
        } else if (total > 250 && total <= 500) {
            bonus += 0.5f;
        } else if (total > 500) {
            bonus += 0.75f;
        }
		*/
        
        sizeMult = ExerelinConfig.getExerelinFactionConfig(factionId).vengeanceFleetSizeMult;
		sizeMult *= ExerelinConfig.vengeanceFleetSizeMult;
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
        params.ignoreMarketFleetSizeMult = true;	// only use doctrine size, not source market size
        params.modeOverride = ShipPickMode.PRIORITY_THEN_ALL;
        fleet = ExerelinUtilsFleet.customCreateFleet(getFaction(), params);

        if (fleet == null)
            return null;

        //fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_TYPE, "vengeanceFleet");
        fleet.getMemoryWithoutUpdate().set("$escalation", (float) escalationLevel);
        fleet.getMemoryWithoutUpdate().set("$startingFP", fleet.getFleetPoints());
        fleet.setName(def.getFleetName(factionId, escalationLevel));
        switch (escalationLevel) {
            default:
            case 0:
                
                if (total > 500) {
                    fleet.getFlagship().getCaptain().setRankId(Ranks.SPACE_ADMIRAL);
                    fleet.getFlagship().getCaptain().setPostId(Ranks.POST_FLEET_COMMANDER);
                } else if (total > 250) {
                    fleet.getFlagship().getCaptain().setRankId(Ranks.SPACE_CAPTAIN);
                    fleet.getFlagship().getCaptain().setPostId(Ranks.POST_FLEET_COMMANDER);
                } else {
                    fleet.getFlagship().getCaptain().setRankId(Ranks.SPACE_COMMANDER);
                    fleet.getFlagship().getCaptain().setPostId(Ranks.POST_FLEET_COMMANDER);
                }
                break;
            case 1:
                if (total > 500) {
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
        if (ALWAYS_SPAWN_ONSITE || playerFleet.getContainingLocation() != market.getContainingLocation()) {
            market.getPrimaryEntity().getContainingLocation().addEntity(fleet);
            fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);

            fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, market.getPrimaryEntity(), 
					2f + escalationLevel + (float) Math.random() * 2f,
                                StringHelper.getFleetAssignmentString("orbiting", market.getName()));
        } else {
            Vector2f loc = Misc.pickHyperLocationNotNearPlayer(market.getLocationInHyperspace(),
                                                               Global.getSettings().getMaxSensorRange() + 500f);
            Global.getSector().getHyperspace().addEntity(fleet);
            fleet.setLocation(loc.x, loc.y);
        }

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SAW_PLAYER_WITH_TRANSPONDER_ON, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_LOW_REP_IMPACT, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
		
		return fleet;
	}
	
	protected void endEvent(EndReason reason) {
		endEvent(reason, getBaseDaysAfterEnd());
	}
	
    protected void endEvent(EndReason reason, float time) {
        over = true;
		endReason = reason;
        if (fleet != null && fleet.isAlive()) {
            fleet.clearAssignments();
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, market.getPrimaryEntity(), 1000,
                                StringHelper.getFleetAssignmentString("returningTo", market.getName()));
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, false);
            fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, false);
            ((ModularFleetAIAPI) fleet.getAI()).getTacticalModule().forceTargetReEval();
        }
		
		sendUpdateIfPlayerHasIntel(null, false);
		
		endAfterDelay(time);
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

        public final String faction;
        public final String madName;
        public final String madFleet;
        public final String madFleetSingle;
        public final String ravingMadName;
        public final String ravingMadFleet;
        public final String ravingMadFleetSingle;
        public final String starkRavingMadName;
        public final String starkRavingMadFleet;
        public final String starkRavingMadFleetSingle;
        public final float vengefulness;
        public final int maxLevel;
        
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

        public static VengeanceDef getDef(String faction) {
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
            
            return StringHelper.getString("nex_vengeance", "vengeanceLevel" + escalationLevel);
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
            
            return StringHelper.getString("nex_vengeance", "vengeanceFleet" + escalationLevel);
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
            
            return StringHelper.getString("nex_vengeance", "vengeanceFleet" + escalationLevel + "Single");
        }
    }
	
	public enum EndReason {
		FAILED_TO_SPAWN, DEFEATED, EXPIRED, NO_LONGER_HOSTILE, OTHER
	}
}
package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.OrbitalStation;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers;
import com.fs.starfarer.api.impl.campaign.rulecmd.missions.Nex_GBTutMission;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.TransmitterTrapSpecial;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.intel.groundbattle.GBUtils;
import exerelin.campaign.intel.groundbattle.GroundBattleCampaignListener;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel.BattleOutcome;
import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.utilities.NexUtilsFleet;
import exerelin.utilities.NexUtilsGUI;
import exerelin.utilities.StringHelper;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class GroundBattleTutorial extends HubMissionWithSearch implements GroundBattleCampaignListener
{	
	public static final String PERSON_CONTACT = "nex_rossDiamond";
	public static final String MEM_KEY_ENEMY_FLEET = "$nex_gbTut_enemyFleet";
	public static final String PLANET_ID = "ilm";	// change if we want a different planet
	public static final String ENEMY_FACTION = Factions.PIRATES;
	
	public static enum Stage {
		START,	// travel to planet and talk to Diamond
		BATTLE,	// ground battle, and space battle if it hasn't happend already
		TALK_TO_CONTACT_AFTER,	// talk to Diamond again after winning
		COMPLETED,
		FAILED,
	}
	
	//public static List<SectorEntityToken> system_jumppoints; 
	
	protected PlanetAPI planet;
	protected PersonAPI contact;
	protected GroundBattleIntel battle;
	
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {		
		// if this mission type was already accepted by the player, abort
		if (!setGlobalReference("$nex_gbTut_ref")) {
			return false;
		}
				
		if (!Nex_GBTutMission.isAllowed(createdAt)) {
			return false;
		}
		
		planet = Global.getSector().getEconomy().getMarket(PLANET_ID).getPlanetEntity();
		if (planet == null) return false;				
				
		setStartingStage(Stage.START);
		addSuccessStages(Stage.COMPLETED);
		addFailureStages(Stage.FAILED);
		
		addInvasionFleet();
		//setStoryMission();
		
		// don't use a completion stage trigger, it can't be trusted https://fractalsoftworks.com/forum/index.php?topic=5061.msg392175#msg392175
		/*
		beginStageTrigger(Stage.COMPLETED);
		triggerSetGlobalMemoryValue("$nex_gbTut_missionCompleted", true);
		endTrigger();
		*/
		
		setCreditReward(CreditReward.HIGH);

		contact = createContact(planet.getMarket());
		Misc.makeStoryCritical(planet.getMarket(), "nex_gbTut");
		
		makeImportant(planet, "$nex_gbTut_targetPlanet", Stage.START, Stage.BATTLE, Stage.TALK_TO_CONTACT_AFTER);
		makeImportant(contact, "$nex_gbTut_contact", Stage.START, Stage.TALK_TO_CONTACT_AFTER);
		
		return true;
	}
	
	// workaround to make the map display work
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map, Object currentStage) {
		return planet;
	}
	
	@Override
	protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog, List<Token> params,
								Map<String, MemoryAPI> memoryMap) {
		switch (action) {
			case "startBattle":
				for (CampaignFleetAPI fleet : planet.getContainingLocation().getFleets()) {
					if (fleet.getMemoryWithoutUpdate().contains(MEM_KEY_ENEMY_FLEET)) {
						TransmitterTrapSpecial.makeFleetInterceptPlayer(fleet, true, false, true, 1000f);
					}
				}
				
				// create the battle and print it to text panel
				createBattle();
				Global.getSector().getIntelManager().addIntelToTextPanel(battle, dialog.getTextPanel());
				
				Global.getSector().getListenerManager().addListener(this);
				
				setCurrentStage(Stage.BATTLE, dialog, dialog.getPlugin().getMemoryMap());
				checkStageChangesAndTriggers(dialog, memoryMap);
				updateInteractionData(dialog, memoryMap);
				
				return true;
			case "openIntel":
				dialog.getVisualPanel().showCore(CoreUITabId.INTEL, planet, battle, new NexUtilsGUI.NullCoreInteractionListener());
				return true;
			case "updateData":
				checkStageChangesAndTriggers(dialog, memoryMap);
				updateInteractionData(dialog, memoryMap);
				return true;
			case "endMission":
				setCurrentStage(Stage.COMPLETED, dialog, dialog.getPlugin().getMemoryMap());
				Global.getSector().getMemoryWithoutUpdate().set("$nex_gbTut_missionCompleted", true);
				return true;
			default:
				break;
		}
		return false;
	}
	
	protected GroundBattleIntel createBattle() {
		MarketAPI market = planet.getMarket();
		FactionAPI attacker = Global.getSector().getFaction(ENEMY_FACTION);
		float existingDam = GBUtils.getGarrisonDamageMemory(market);
		float dam = existingDam + 0.2f;
		dam = Math.min(dam, 0.5f);
		GBUtils.setGarrisonDamageMemory(market, dam);
		
		battle = new GroundBattleIntel(planet.getMarket(), attacker, planet.getFaction());
		battle.setEndIfPeace(false);
		battle.init();
		
		float strength = GBUtils.estimateTotalDefenderStrength(battle, true);
		int marines = Math.round(strength * 0.65f);
		int heavies = Math.round(strength * 0.4f / GroundUnit.HEAVY_COUNT_DIVISOR);
		marines += heavies * GroundUnit.CREW_PER_MECH;
		battle.autoGenerateUnits(marines, heavies, attacker, true, false);
		
		battle.playerJoinBattle(false, false);
		battle.start();
		battle.runAI(true, false);	// deploy starting attacker units
		battle.setImportant(true);
		
		return battle;
	}
	
	protected void addInvasionFleet() {
		FactionAPI faction = Global.getSector().getFaction(Factions.MERCENARY);
		float maxPointsForFaction = faction.getApproximateMaxFPPerFleet(FactionAPI.ShipPickMode.PRIORITY_THEN_ALL);
		
		float playerStr = NexUtilsFleet.calculatePowerLevel(Global.getSector().getPlayerFleet()) * 0.7f;
		int capBonus = Math.round(NexUtilsFleet.getPlayerLevelFPBonus());

		int combat = Math.round(playerStr/5 * MathUtils.getRandomNumberInRange(0.6f, 0.7f) + capBonus);
		
		beginWithinHyperspaceRangeTrigger(planet, 3f, false, Stage.START);
		triggerCreateFleet(FleetSize.MEDIUM, FleetQuality.VERY_HIGH, Factions.MERCENARY, 
				"exerelinInvasionFleet", planet.getStarSystem());
		triggerSetFleetFaction(ENEMY_FACTION);
		triggerMakeFleetIgnoreOtherFleetsExceptPlayer();
		triggerMakeFleetIgnoredByOtherFleets();
		
		// attempt at strength scaling
		//triggerAutoAdjustFleetStrengthModerate();
		float fraction = Math.max(combat/maxPointsForFaction/0.75f, HubMissionWithTriggers.FleetSize.SMALL.maxFPFraction);
		fraction *= 0.55f;
		fraction = Math.min(fraction, 1);
		triggerSetFleetSizeFraction(fraction);
		
		getPreviousCreateFleetAction().transportMult = 0.2f;	// troop transport FP equal to 1/5 combat FP
		
		triggerMakeHostile();
		triggerMakeNoRepImpact();
		triggerFleetAllowLongPursuit();
		
		triggerPickLocationAroundEntity(planet, 500f);
		triggerSpawnFleetAtPickedLocation(MEM_KEY_ENEMY_FLEET, null);
		triggerOrderFleetPatrol(true, planet);
		triggerFleetMakeImportant(null, Stage.BATTLE);
		triggerRunScriptAfterDelay(0, new Script() {
			@Override
			public void run() {
				Industry station = Misc.getStationIndustry(planet.getMarket());
				if (station != null) {
					OrbitalStation.disrupt(station);
				}
			}
		
		});
		endTrigger();
	}
	
	protected void updateInteractionDataImpl() {
		/*
		set("$nex_gbTut_planetId", planet.getId());
		set("$nex_gbTut_planetName", planet.getName());
		set("$nex_gbTut_systemName", planet.getStarSystem().getNameWithNoType());
		set("$nex_gbTut_dist", getDistanceLY(planet));
		set("$nex_gbTut_reward", Misc.getWithDGS(getCreditsReward()));
		*/
		set("$nex_gbTut_stage", getCurrentStage());
		set("$nex_gbTut_planetName", planet.getName());
		set("$nex_gbTut_marketName", planet.getMarket().getName());
	}
	
	@Override
	public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		if (currentStage == Stage.START) {
			String text = getString("intelDesc1");
			text = StringHelper.substituteToken(text, "$rank", contact.getRank());
			text = StringHelper.substituteToken(text, "$name", contact.getNameString());
			info.addPara(getGoToPlanetTextPre(planet) +	", " + text, opad);
		} else if (currentStage == Stage.BATTLE) {
			String text = getString("intelDesc2");
			info.addPara(text, opad, planet.getMarket().getTextColorForFactionOrPlanet(), planet.getName());
		} else if (currentStage == Stage.TALK_TO_CONTACT_AFTER) {
			String text = getString("intelDesc3");
			text = StringHelper.substituteToken(text, "$goToText", getGoToPlanetTextShort(planet));
			text = StringHelper.substituteToken(text, "$rank", contact.getRank());
			text = StringHelper.substituteToken(text, "$name", contact.getName().getLast());
			info.addPara(text, opad, planet.getMarket().getTextColorForFactionOrPlanet(), planet.getName());
		}
	}

	@Override
	public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
		if (currentStage == Stage.START) {
			info.addPara(getGoToPlanetTextShort(planet), tc, pad);
			return true;
		} else if (currentStage == Stage.BATTLE) {
			String text = getString("intelBullet2");
			info.addPara(text, pad, tc, planet.getMarket().getTextColorForFactionOrPlanet(), planet.getName());
			return true;
		} else if (currentStage == Stage.TALK_TO_CONTACT_AFTER) {
			String text = getString("intelBullet3");
			text = StringHelper.substituteToken(text, "$name", contact.getNameString());
			text = getGoToPlanetTextShort(planet) + " " + text;
			info.addPara(text, tc, pad);
			return true;
		}
		return false;
	}

	@Override
	public String getBaseName() {
		return getString("missionName");
	}
	
	@Override
	protected void notifyEnding() {
		super.notifyEnding();
		Misc.makeNonStoryCritical(planet.getMarket(), "nex_gbTut");
		planet.getMarket().removePerson(contact);
		planet.getMarket().getCommDirectory().removePerson(contact);
		Global.getSector().getListenerManager().removeListener(this);
	}
		

	@Override
	public void reportBattleStarted(GroundBattleIntel battle) {}

	@Override
	public void reportBattleBeforeTurn(GroundBattleIntel battle, int turn) {}

	@Override
	public void reportBattleAfterTurn(GroundBattleIntel battle, int turn) {}

	@Override
	public void reportBattleEnded(GroundBattleIntel battle) {
		if (battle != this.battle) return;
		
		// TODO: make stage progress based on outcome
		if (battle.getOutcome() == BattleOutcome.DEFENDER_VICTORY) {
			setCurrentStage(Stage.TALK_TO_CONTACT_AFTER, null, null);
		} else {
			setCurrentStage(Stage.FAILED, null, null);
		}
	}

	@Override
	public void reportPlayerJoinedBattle(GroundBattleIntel battle) {}

	public static PersonAPI createContact(MarketAPI market) {
		PersonAPI person = Global.getFactory().createPerson();
		person.setId(PERSON_CONTACT);
		person.setImportance(PersonImportance.MEDIUM);
		person.setVoice(Voices.SOLDIER);
		person.setFaction(Factions.PERSEAN);
		person.setGender(FullName.Gender.MALE);
		person.setRankId(Ranks.SPACE_CAPTAIN);
		person.setPostId(Ranks.POST_OFFICER);
		person.getName().setFirst(getString("contactName1"));
		person.getName().setLast(getString("contactName2"));
		person.setPortraitSprite("graphics/portraits/portrait_league06.png");
		//person.addTag(Tags.CONTACT_MILITARY);
		Global.getSector().getImportantPeople().addPerson(person);
		market.addPerson(person);
		market.getCommDirectory().addPerson(person);
		
		return person;
	}
	
	protected static String getString(String id) {
		return StringHelper.getString("nex_groundBattle_tutorial", id);
	}
}
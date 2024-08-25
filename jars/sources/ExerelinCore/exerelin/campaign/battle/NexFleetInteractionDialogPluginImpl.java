package exerelin.campaign.battle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.BattleAPI.BattleSide;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.EngagementOutcome;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI.SkillLevelAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.SectorManager;
import exerelin.campaign.battle.NexFleetEncounterContext.EscapedOfficerData;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;
import java.util.*;

/*
Changes from vanilla:
- Officer death mechanic
- Modified pull in of fleets for ally battles
*/

public class NexFleetInteractionDialogPluginImpl extends FleetInteractionDialogPluginImpl {

	public static Logger log = Global.getLogger(NexFleetInteractionDialogPluginImpl.class);
	public static final boolean MODIFIED_PULL_IN = true;	// there's an every frame script version in case this plugin isn't running
	
	protected static final String STRING_HELPER_CAT = "exerelin_officers";
	protected static final Color NEUTRAL_COLOR = Global.getSettings().getColor("textNeutralColor");
	protected boolean recoveredOfficers = false;

	private Map<FleetMemberAPI, Float[]> disabledOrDestroyedMembers = new HashMap<>();

	protected String getTextString(String id)
	{
		return StringHelper.getString(STRING_HELPER_CAT, id);
	}
	
	public NexFleetInteractionDialogPluginImpl() {
		super();
		context = new NexFleetEncounterContext();
	}

	public NexFleetInteractionDialogPluginImpl(FIDConfig params) {
		super(params);
		this.config = params;
		context = new NexFleetEncounterContext();
	}
	
	@Override
	protected void restoreOrigCaptains() {
		if (origCaptains != null) {
			List<OfficerDataAPI> lostOfficers = ((NexFleetEncounterContext) context).getPlayerLostOfficers();
			Set<PersonAPI> noRestore = new HashSet<>();
			for (OfficerDataAPI officer : lostOfficers) {
				noRestore.add(officer.getPerson());
			}
			
			for (FleetMemberAPI member : origCaptains.keySet()) {
				PersonAPI captain = origCaptains.get(member);
				if (captain != null && !noRestore.contains(captain)) {
					member.setCaptain(captain);
				}
			}
		}
	}

	@Override
	public void backFromEngagement(EngagementResultAPI result) {
		log.debug("Back From Engagement");
		super.backFromEngagement(result);

		boolean totalDefeat = !playerFleet.isValidPlayerFleet();
		boolean mutualDestruction = context.getLastEngagementOutcome() == EngagementOutcome.MUTUAL_DESTRUCTION;

		List<EscapedOfficerData> officersEscaped = ((NexFleetEncounterContext) context).getPlayerOfficersEscaped();
		List<OfficerDataAPI> officersMIA = ((NexFleetEncounterContext) context).getPlayerOfficersMIA();
		List<OfficerDataAPI> officersKIA = ((NexFleetEncounterContext) context).getPlayerOfficersKIA();
		if (!officersEscaped.isEmpty() && !totalDefeat && !mutualDestruction) {
			List<String> escaped = new ArrayList<>(officersEscaped.size());
			for (EscapedOfficerData officer : officersEscaped) {
				escaped.add(officer.officer.getPerson().getName().getFullName());
			}

			String s1, s2;
			if (escaped.size() == 1) {
				s1 = getTextString("officer");
				s2 = StringHelper.getStringAndSubstituteToken(STRING_HELPER_CAT, "hisOrHerShip", 
						"$pronoun", StringHelper.getHisOrHer(officersEscaped.get(0).officer.getPerson()));
			} else {
				s1 = getTextString("officers");
				s2 = getTextString("theirShips");
			}
			//"Your $officers $officerNames escaped the destruction of $theirShips"
			String str = getTextString("battle_escaped") + ".";
			str = StringHelper.substituteToken(str, "$officers", s1, true);
			str = StringHelper.substituteToken(str, "$officerNames", Misc.getAndJoined(escaped.toArray(new String[escaped.size()])));
			str = StringHelper.substituteToken(str, "$theirShips", s2);
			
			addText(str);
		}
		if ((!officersMIA.isEmpty() || !officersKIA.isEmpty()) && !totalDefeat && !mutualDestruction) {
			int lost = officersMIA.size() + officersKIA.size();

			String text;
			String s1;
			if (officersKIA.isEmpty()) {
				if (lost == 1) {
					s1 = getTextString("officer");
				} else {
					s1 = getTextString("officers");
				}
				//"Your $officers did not report in after the battle",
				text = StringHelper.getStringAndSubstituteToken(STRING_HELPER_CAT, "battle_noReportIn", "$officers", s1, true) + ":";
			} else {
				if (lost == 1) {
					//"Your $officer was listed among the casualties";
					s1 = getTextString("officer");
					text = StringHelper.getStringAndSubstituteToken(STRING_HELPER_CAT, "battle_casualties", "$officer", s1, true) + ":";
				} else {
					s1 = getTextString("officers");
					text = StringHelper.getStringAndSubstituteToken(STRING_HELPER_CAT, "battle_casualties_plural", "$officers", s1, true) + ":";
				}
			}

			List<String> highlights = new ArrayList<>((officersMIA.size() + officersKIA.size()) * 2);
			List<Color> highlightColors = new ArrayList<>((officersMIA.size() + officersKIA.size()) * 2);
			for (OfficerDataAPI officer : officersMIA) {
				s1 = officer.getPerson().getName().getFullName();
				String s2 = getTextString("missingInAction");
				text += "\n" + s1 + " (" + officer.getPerson().getStats().getLevel() + ") - " + s2;
				highlights.add(s1);
				highlights.add(s2);
				highlightColors.add(NEUTRAL_COLOR);
				highlightColors.add(ENEMY_COLOR);
			}

			for (OfficerDataAPI officer : officersKIA) {
				s1 = officer.getPerson().getName().getFullName();
				String s2 = getTextString("killedInAction");
				text += "\n" + s1 + " (" + officer.getPerson().getStats().getLevel() + ") - " + s2;
				highlights.add(s1);
				highlights.add(s2);
				highlightColors.add(NEUTRAL_COLOR);
				highlightColors.add(ENEMY_COLOR);
			}

			addText(text);
			textPanel.highlightInLastPara(highlights.toArray(new String[highlights.size()]));
			textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[highlightColors.size()]));
		}
	}
	
	@Override
	protected void losingPath() {
		log.debug("Losing Path");
		if (!recoveredOfficers) {
			recoveredOfficers = true;

			List<OfficerDataAPI> recoverableOfficers =
								 ((NexFleetEncounterContext) context).getPlayerRecoverableOfficers();
			List<OfficerDataAPI> lostOfficers = ((NexFleetEncounterContext) context).getPlayerLostOfficers();
			List<OfficerDataAPI> unconfirmedOfficers =
								 ((NexFleetEncounterContext) context).getPlayerUnconfirmedOfficers();
			if (!lostOfficers.isEmpty() || !recoverableOfficers.isEmpty()) {
				String s1;
				if (lostOfficers.size() + recoverableOfficers.size() == 1) {
					s1 = getTextString("officer");
				} else {
					s1 = getTextString("officers");
				}
				//"The post-action report confirms that your $officers didn't make it"
				String text = StringHelper.getStringAndSubstituteToken(STRING_HELPER_CAT, "confirmDeath", "$officers", s1) + ":";

				List<String> highlights = new ArrayList<>((lostOfficers.size() + recoverableOfficers.size()) * 2);
				List<Color> highlightColors = new ArrayList<>((lostOfficers.size() + recoverableOfficers.size()) * 2);
				for (OfficerDataAPI officer : lostOfficers) {
					s1 = officer.getPerson().getName().getFullName();
					String s2;
					if (unconfirmedOfficers.contains(officer)) {
						s2 = getTextString("miaPresumedDead");
					} else {
						s2 = getTextString("killedInAction");
					}
					text += "\n" + s1 + " (" + officer.getPerson().getStats().getLevel() + ") - " + s2;
					highlights.add(s1);
					highlights.add(s2);
					highlightColors.add(NEUTRAL_COLOR);
					highlightColors.add(ENEMY_COLOR);
				}
				for (OfficerDataAPI officer : recoverableOfficers) {
					s1 = officer.getPerson().getName().getFullName();
					String s2 = getTextString("miaPresumedDead");
					text += "\n" + s1 + " (" + officer.getPerson().getStats().getLevel() + ") - " + s2;
					highlights.add(s1);
					highlights.add(s2);
					highlightColors.add(NEUTRAL_COLOR);
					highlightColors.add(ENEMY_COLOR);
				}

				addText(text);
				textPanel.highlightInLastPara(highlights.toArray(new String[highlights.size()]));
				textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[highlightColors.size()]));
				
				handleLifeInsuranceAndSPRefund(lostOfficers, recoverableOfficers);
			}
		}

		super.losingPath();
	}

	@Override
	protected void winningPath() {
		log.debug("Winning Path");
		if (!recoveredOfficers) {
			recoveredOfficers = true;

			List<OfficerDataAPI> recoverableOfficers = ((NexFleetEncounterContext) context).getPlayerRecoverableOfficers();
			List<OfficerDataAPI> lostOfficers = ((NexFleetEncounterContext) context).getPlayerLostOfficers();
			if (!recoverableOfficers.isEmpty()) {
				String s1;
				String text;
				if (recoverableOfficers.size() == 1) {
					// "Your $officers were saved from the wreckage"
					s1 = getTextString("officer");
					text = StringHelper.getStringAndSubstituteToken(STRING_HELPER_CAT, "savedFromWreckage", "$officer", s1) + ":";
				} else {
					s1 = getTextString("officers");
					text = StringHelper.getStringAndSubstituteToken(STRING_HELPER_CAT, "savedFromWreckagePlural", "$officers", s1) + ":";
				}

				List<String> highlights = new ArrayList<>(recoverableOfficers.size() * 2);
				List<Color> highlightColors = new ArrayList<>(recoverableOfficers.size() * 2);
				for (OfficerDataAPI officer : recoverableOfficers) {
					s1 = officer.getPerson().getName().getFullName();
					String s2 = "rescued";
					text += "\n" + s1 + " (" + officer.getPerson().getStats().getLevel() + ") - " + s2;
					highlights.add(s1);
					highlights.add(s2);
					highlightColors.add(NEUTRAL_COLOR);
					highlightColors.add(FRIEND_COLOR);
				}

				addText(text);
				textPanel.highlightInLastPara(highlights.toArray(new String[highlights.size()]));
				textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[highlightColors.size()]));

				((NexFleetEncounterContext) context).recoverPlayerOfficers();
			}

			if (!lostOfficers.isEmpty()) {
				String s1;
				if (lostOfficers.size() == 1) {
					s1 = getTextString("officer");
				} else {
					s1 = getTextString("officers");
				}
				//"The post-action report confirms that your $officers didn't make it"
				String text = StringHelper.getStringAndSubstituteToken(STRING_HELPER_CAT, "confirmDeath", "$officers", s1) + ":";

				List<String> highlights = new ArrayList<>(lostOfficers.size() * 2);
				List<Color> highlightColors = new ArrayList<>(lostOfficers.size() * 2);
				for (OfficerDataAPI officer : lostOfficers) {
					s1 = officer.getPerson().getName().getFullName();
					String s2 = getTextString("killedInAction");
					text += "\n" + s1 + " (" + officer.getPerson().getStats().getLevel() + ") - " + s2;
					highlights.add(s1);
					highlights.add(s2);
					highlightColors.add(NEUTRAL_COLOR);
					highlightColors.add(ENEMY_COLOR);
				}

				addText(text);
				textPanel.highlightInLastPara(highlights.toArray(new String[highlights.size()]));
				textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[highlightColors.size()]));
			}
			
			handleLifeInsuranceAndSPRefund(lostOfficers, null);
		}

		super.winningPath();
	}

	protected void handleLifeInsuranceAndSPRefund(List<OfficerDataAPI> deadOfficers, 
			List<OfficerDataAPI> miaOfficers)
	{
		List<OfficerDataAPI> officers = new ArrayList<>(deadOfficers);
		if (miaOfficers != null)
			officers.addAll(miaOfficers);
		
		int points = 0;
		for (OfficerDataAPI lost : officers) {
			for (SkillLevelAPI skill : lost.getPerson().getStats().getSkillsCopy()) {
				if (skill.getLevel() >= 2)
					points++;
			}
			if (Misc.isMentored(lost.getPerson())) {
				points++;
			}
		}
		if (points > 0) {
			String str = StringHelper.getString(STRING_HELPER_CAT, "msgStoryPointRefund");
			dialog.getTextPanel().addPara(str);
			Global.getSector().getPlayerPerson().getStats().addStoryPoints(points, dialog.getTextPanel(), false);
		}		
		
		SectorManager.getManager().addInsuredOfficers(officers);
		
		// don't call insurance from here; it breaks anywhere we can't use this fleet interaction dialog plugin
		//FactionInsuranceIntel insuranceIntel = new FactionInsuranceIntel(disabledOrDestroyedMembers, officers);
		
		officers.clear();
		disabledOrDestroyedMembers.clear();
	}
	
	// Hax: let Techpriest modify the dialog options again
	@Override
	protected void updateEngagementChoice(boolean withText) {
		super.updateEngagementChoice(withText);
		conversationDelegate.fireAll("UpdateEngagementChoice");
	}	
	
	protected static void addMemoryFlagIfNotSet(CampaignFleetAPI fleet, String memFlag)
	{
		if (!fleet.getMemoryWithoutUpdate().contains(memFlag))
			fleet.getMemoryWithoutUpdate().set(memFlag, true, 0);
	}
		
	// same as vanilla, except stations don't (usually) get pulled + anything pursuing a participating fleet gets pulled
	/**
	 * Should {@code fleet} be joined to the player battle?
	 * @param battle
	 * @param fleet Fleet to consider for pulling in
	 * @param playerFleet Player fleet
	 * @param actualOther Fleet fighting player (i.e. interaction dialog target)
	 * @return
	 */
	public static boolean shouldPullInFleet(BattleAPI battle, CampaignFleetAPI fleet,
			CampaignFleetAPI playerFleet, CampaignFleetAPI actualOther)
	{
		if (battle == fleet.getBattle()) return false;
		if (fleet.getBattle() != null) return false;

		if (fleet.isStationMode()) return false;

		float dist = Misc.getDistance(actualOther.getLocation(), fleet.getLocation());
		dist -= actualOther.getRadius();
		dist -= fleet.getRadius();

		if (fleet.getFleetData().getNumMembers() <= 0) return false;

		float baseSensorRange = playerFleet.getBaseSensorRangeToDetect(fleet.getSensorProfile());
		boolean visible = fleet.isVisibleToPlayerFleet();
		VisibilityLevel level = fleet.getVisibilityLevelToPlayerFleet();
		
		float joinRange = Misc.getBattleJoinRange();
		if (fleet.getFaction().isPlayerFaction() && !fleet.isStationMode()) {
			joinRange += Global.getSettings().getFloat("battleJoinRangePlayerFactionBonus");
		}
		if (dist >= joinRange) return false;
		if (!(dist < baseSensorRange || (visible && level != VisibilityLevel.SENSOR_CONTACT))) 
			return false;
		
		boolean ignore = fleet.getMemoryWithoutUpdate() != null && 
						fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
		//if (ignore) return false; // we'll take care of this in a bit, need to check some other stuff first
		
		boolean escorting = false;
		
		if (fleet.getAI() != null)
		{
			// pull in anyone with an assignment on one of our fleets
			FleetAssignmentDataAPI assignment = fleet.getAI().getCurrentAssignment();
			if (assignment == null) return true;
			SectorEntityToken assignTarget = assignment.getTarget();
			if (assignTarget != null && assignTarget instanceof CampaignFleetAPI)
			{
				List<CampaignFleetAPI> fleets = battle.getBothSides();
				for (CampaignFleetAPI inBattle : fleets)
				{
					if (inBattle == assignTarget)
					{
						// THI merc handling
						if (fleet.getMemoryWithoutUpdate().contains("$tiandongMercTarget")
							&& fleet.getMemoryWithoutUpdate().getFleet("$tiandongMercTarget") == inBattle)
						{
							// don't let merc join if merc is friendly to player, 
							// and escortee is not already on player side
							// (otherwise merc claims to join but doesn't, as it can't pick a side)
							// Exception: Always join if escortee is Tiandong
							if (!battle.isOnPlayerSide(inBattle)
									&& fleet.isFriendlyTo(Global.getSector().getPlayerFleet())
									&& !inBattle.getFaction().getId().equals("tiandong"))
							{
								//dialog.getTextPanel().addPara("Merc cannot join, not hostile to player");
								return false;
							}

							// Add hostile memory keys to merc if escortee is hostile to player
							if (!battle.isOnPlayerSide(inBattle))
							{
								//dialog.getTextPanel().addPara("Merc enforcing hostile mode");
								addMemoryFlagIfNotSet(fleet, MemFlags.MEMORY_KEY_MAKE_HOSTILE);
								addMemoryFlagIfNotSet(fleet, MemFlags.MEMORY_KEY_MAKE_HOSTILE_WHILE_TOFF);
								addMemoryFlagIfNotSet(fleet, MemFlags.MEMORY_KEY_LOW_REP_IMPACT);
							}
						}
						escorting = true;
						return true;
					}
				}
			}
			if (ignore) return false;
			
			if (fleet.getAI().wantsToJoin(battle, true)) 
				return true;
		}
		return false;
	}
	
	protected void printPullInText(CampaignFleetAPI fleet, BattleAPI b, BattleSide playerSide, boolean hostile) 
	{
		if (!config.straightToEngage && config.showPullInText) {
			if (b.getSide(playerSide) == b.getSideFor(fleet)) {
				textPanel.addParagraph(Misc.ucFirst(fleet.getNameWithFactionKeepCase()) + ": " 
						+ StringHelper.getString("exerelin_fleets", "supportingYourForces"));//, FRIEND_COLOR);
			} else {
				if (hostile) {
					textPanel.addParagraph(Misc.ucFirst(fleet.getNameWithFactionKeepCase()) + ": " 
						+ StringHelper.getString("exerelin_fleets", "joiningTheEnemy"));//, ENEMY_COLOR);
				} else {
					textPanel.addParagraph(Misc.ucFirst(fleet.getNameWithFactionKeepCase()) + ": " 
						+ StringHelper.getString("exerelin_fleets", "supportingOpposingSide"));
				}
			}
			textPanel.highlightFirstInLastPara(fleet.getNameWithFactionKeepCase() + ":", fleet.getFaction().getBaseUIColor());
		}
	}
	
	// vanilla with modified pulling in of nearby fleets
	@Override
	protected void pullInNearbyFleets() {
		if (!MODIFIED_PULL_IN) {
			super.pullInNearbyFleets();
			return;
		}
		
		BattleAPI b = context.getBattle();
		boolean hostile = otherFleet.getAI() != null && otherFleet.getAI().isHostileTo(playerFleet);
		if (ongoingBattle) hostile = true;
		
		if (!ongoingBattle) {
			b.join(Global.getSector().getPlayerFleet());
		}
		
		BattleSide playerSide = b.pickSide(Global.getSector().getPlayerFleet());
		
		//canDecline = otherFleet.getAI() != null && other
		
//		boolean someJoined = false;
		CampaignFleetAPI actualPlayer = Global.getSector().getPlayerFleet();
		CampaignFleetAPI actualOther = (CampaignFleetAPI) (dialog.getInteractionTarget());
		
		//textPanel.addParagraph("Projecting nearby fleet movements:");
		//textPanel.addParagraph("You encounter a ");
		pulledIn.clear();
		
		if (config.pullInStations && !b.isStationInvolved()) {
			SectorEntityToken closestEntity = null;
			CampaignFleetAPI closest = null;
			Pair<SectorEntityToken, CampaignFleetAPI> p = Misc.getNearestStationInSupportRange(actualOther);
			if (p != null) {
				closestEntity = p.one;
				closest = p.two;
			}
			
			if (closest != null) {
				BattleSide joiningSide = b.pickSide(closest, true);
				boolean canJoin = joiningSide != BattleSide.NO_JOIN;
				if (!config.pullInAllies && joiningSide == playerSide) {
					canJoin = false;
				}
				if (!config.pullInEnemies && joiningSide != playerSide) {
					canJoin = false;
				}
				if (b == closest.getBattle()) {
					canJoin = false;
				}
				if (closest.getBattle() != null) {
					canJoin = false;
				}
				
				if (canJoin) {
					if (closestEntity != null) {
						closestEntity.getMarket().reapplyIndustries(); // need to pick up station CR value, in some cases
					}
					b.join(closest);
					pulledIn.add(closest);
					
					printPullInText(closest, b, playerSide, hostile);
				}
			}
		}
				
		for (CampaignFleetAPI fleet : actualPlayer.getContainingLocation().getFleets()) {
			if (shouldPullInFleet(b, fleet, actualPlayer, actualOther)) {
				
				BattleSide joiningSide = b.pickSide(fleet, true);
				if (!config.pullInAllies && joiningSide == playerSide) continue;
				if (!config.pullInEnemies && joiningSide != playerSide) continue;
				
				b.join(fleet);
				pulledIn.add(fleet);
				//if (b.isPlayerSide(b.getSideFor(fleet))) {
				printPullInText(fleet, b, playerSide, hostile);
//				someJoined = true;
			}
		}
		
		if (otherFleet != null) otherFleet.inflateIfNeeded();
		for (CampaignFleetAPI curr : pulledIn) {
			curr.inflateIfNeeded();
		}
		
//		if (!someJoined) {
//			addText("No nearby fleets will join the battle.");
//		}
		if (!ongoingBattle) {
			b.genCombined();
			b.takeSnapshots();
			playerFleet = b.getPlayerCombined();
			otherFleet = b.getNonPlayerCombined();
			if (!config.straightToEngage) {
				showFleetInfo();
			}
		}
	}
	
	/**
	 * Debugging command?
	 * @param fleets
	 */
	public void forcePullInFleets(CampaignFleetAPI... fleets) {
		BattleAPI b = context.getBattle();
		BattleSide playerSide = b.pickSide(Global.getSector().getPlayerFleet());
		boolean hostile = otherFleet.getAI() != null && otherFleet.getAI().isHostileTo(playerFleet);
		
		List<CampaignFleetAPI> pulledInNow = new ArrayList<CampaignFleetAPI>();
		
		for (CampaignFleetAPI fleet : fleets) {
			BattleSide joiningSide = b.pickSide(fleet, true);
			if (!config.pullInAllies && joiningSide == playerSide) continue;
			if (!config.pullInEnemies && joiningSide != playerSide) continue;

			b.join(fleet);
			pulledIn.add(fleet);
			pulledInNow.add(fleet);
			log.info(String.format("%s joining battle, can join %s, joiningSide %s, fleet battle %s", 
					fleet.getName(), b.canJoin(fleet), joiningSide, fleet.getBattle()));
			printPullInText(fleet, b, playerSide, hostile);
		}
		
		for (CampaignFleetAPI curr : pulledInNow) {
			curr.inflateIfNeeded();
		}
		
		if (!ongoingBattle) {
			log.info("wololo regen battle");
			b.genCombined();
			b.takeSnapshots();
			playerFleet = b.getPlayerCombined();
			otherFleet = b.getNonPlayerCombined();
			//if (!config.straightToEngage) {
				showFleetInfo();
			//}
		}
	}
}

package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.BattleAPI.BattleSide;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.EngagementOutcome;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.FleetMemberData;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.events.FactionInsuranceEvent;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;
import org.histidine.foundation.campaign.events.FoundationInsuranceEvent;

/*
Changes from vanilla:
- Officer death mechanic
- Modified pull in of fleets for ally battles
*/

public class NexFleetInteractionDialogPluginImpl extends FleetInteractionDialogPluginImpl {

	public static Logger log = Global.getLogger(NexFleetInteractionDialogPluginImpl.class);
	
	protected static final String STRING_HELPER_CAT = "exerelin_officers";
	protected static final Color NEUTRAL_COLOR = Global.getSettings().getColor("textNeutralColor");
	protected boolean recoveredOfficers = false;
	protected FIDConfig config;	// vanilla one is private

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
	public void backFromEngagement(EngagementResultAPI result) {
		super.backFromEngagement(result);

		boolean totalDefeat = !playerFleet.isValidPlayerFleet();
		boolean mutualDestruction = context.getLastEngagementOutcome() == EngagementOutcome.MUTUAL_DESTRUCTION;

		List<OfficerDataAPI> officersEscaped = ((NexFleetEncounterContext) context).getPlayerOfficersEscaped();
		List<OfficerDataAPI> officersMIA = ((NexFleetEncounterContext) context).getPlayerOfficersMIA();
		List<OfficerDataAPI> officersKIA = ((NexFleetEncounterContext) context).getPlayerOfficersKIA();
		if (!officersEscaped.isEmpty() && !totalDefeat && !mutualDestruction) {
			List<String> escaped = new ArrayList<>(officersEscaped.size());
			for (OfficerDataAPI officer : officersEscaped) {
				escaped.add(officer.getPerson().getName().getFullName());
			}

			String s1, s2;
			if (escaped.size() == 1) {
				s1 = getTextString("officer");
				s2 = StringHelper.getStringAndSubstituteToken(STRING_HELPER_CAT, "hisOrHerShip", "$pronoun", StringHelper.getHisOrHer(officersEscaped.get(0).getPerson()));
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
				
				handleLifeInsurance(lostOfficers, recoverableOfficers);
			}
		}

		super.losingPath();
	}

	@Override
	protected void winningPath() {
		if (!recoveredOfficers) {
			recoveredOfficers = true;

			List<OfficerDataAPI> recoverableOfficers =
								 ((NexFleetEncounterContext) context).getPlayerRecoverableOfficers();
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
			
			handleLifeInsurance(lostOfficers, null);
		}

		super.winningPath();
	}
	
	protected void handleLifeInsurance(List<OfficerDataAPI> deadOfficers, List<OfficerDataAPI> miaOfficers)
	{
		List<OfficerDataAPI> officers = new ArrayList<>(deadOfficers);
		if (miaOfficers != null)
			officers.addAll(miaOfficers);
		if (Global.getSector().getEventManager().isOngoing(null, "exerelin_faction_insurance"))
		{
			FactionInsuranceEvent event = (FactionInsuranceEvent)Global.getSector().getEventManager().getOngoingEvent(
					null, "exerelin_faction_insurance");
			event.addDeadOfficers(officers);
		}
		
		if (Global.getSector().getEventManager().isOngoing(null, "foundation_insurance"))
		{
			FoundationInsuranceEvent event = (FoundationInsuranceEvent)Global.getSector().getEventManager().getOngoingEvent(
					null, "foundation_insurance");
			event.addDeadOfficers(officers);
		}
	}
	
	@Override
	public void init(InteractionDialogAPI dialog) {
		if (this.config == null) {
			MemoryAPI memory = dialog.getInteractionTarget().getMemoryWithoutUpdate();
//			if (memory.contains(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE)) {
//				this.config = (FIDConfig) memory.get(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE);
//			} else 
			if (memory.contains(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN)) {
				this.config = ((FIDConfigGen) memory.get(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN)).createConfig();
			} else {
				this.config = new FIDConfig();
			}
		}
		
		super.init(dialog);
	}
		
	// same as vanilla, except stations don't get pulled + anything pursuing a participating fleet gets pulled
	protected boolean shouldPullInFleet(BattleAPI battle, CampaignFleetAPI fleet, float dist)
	{
		//Global.getLogger(this.getClass()).info("Testing fleet for pull-in: " + fleet.getName());
		float baseSensorRange = playerFleet.getBaseSensorRangeToDetect(fleet.getSensorProfile());
		boolean visible = fleet.isVisibleToPlayerFleet();
		VisibilityLevel level = fleet.getVisibilityLevelToPlayerFleet();
//			if (dist < Misc.getBattleJoinRange() && 
//					(dist < baseSensorRange || (visible && level != VisibilityLevel.SENSOR_CONTACT))) {
//				System.out.println("2380dfwef");
//			}
		if (dist > Misc.getBattleJoinRange())
			return false;
		if (dist > baseSensorRange && !(visible && level != VisibilityLevel.SENSOR_CONTACT))
			return false;
		if (fleet.getAI() != null && fleet.getAI().wantsToJoin(battle, true))
			return true;
		if (fleet.isStationMode())
			return false;	// screw Alex's logic on this
		
		if (fleet.getAI() != null)
		{
			FleetAssignmentDataAPI assignment = fleet.getAI().getCurrentAssignment();
			if (assignment == null) return true;
			SectorEntityToken target = assignment.getTarget();
			if (target != null && target instanceof CampaignFleetAPI)
			{
				List<CampaignFleetAPI> fleets = battle.getBothSides();
				for (CampaignFleetAPI inBattle : fleets)
				{
					if (inBattle == target)
						return true;
				}
			}
		}
		return false;
	}
	
	// vanilla with modified pulling in of nearby fleets
	@Override
	protected void pullInNearbyFleets() {
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
		
		for (CampaignFleetAPI fleet : actualPlayer.getContainingLocation().getFleets()) {
			if (b == fleet.getBattle()) continue;
			if (fleet.getBattle() != null) continue;
			
			float dist = Misc.getDistance(actualOther.getLocation(), fleet.getLocation());
			dist -= actualOther.getRadius();
			dist -= fleet.getRadius();
//			if (dist < Misc.getBattleJoinRange()) {
//				System.out.println("Checking: " + fleet.getNameWithFaction());
//			}
			
			if (shouldPullInFleet(b, fleet, dist)) {
				
				BattleSide joiningSide = b.pickSide(fleet, true);
				if (!config.pullInAllies && joiningSide == playerSide) continue;
				if (!config.pullInEnemies && joiningSide != playerSide) continue;
				
				b.join(fleet);
				pulledIn.add(fleet);
				//if (b.isPlayerSide(b.getSideFor(fleet))) {
				String fleetName = Misc.ucFirst(fleet.getNameWithFactionKeepCase());
				String action;
				if (b.getSide(playerSide) == b.getSideFor(fleet)) {
					action = StringHelper.getString("exerelin_fleets", "supportingYourForces");
				} else {
					if (hostile) {
						action = StringHelper.getString("exerelin_fleets", "joiningTheEnemy");
					} else {
						action = StringHelper.getString("exerelin_fleets", "supportingOpposingSide");
					}
				}
				textPanel.addParagraph(fleetName + ": " + action + ".");//, FRIEND_COLOR);
				textPanel.highlightFirstInLastPara(fleetName + ":", fleet.getFaction().getBaseUIColor());
//				someJoined = true;
			}
		}
//		if (!someJoined) {
//			addText("No nearby fleets will join the battle.");
//		}
		if (!ongoingBattle) {
			b.genCombined();
			b.takeSnapshots();
			playerFleet = b.getPlayerCombined();
			otherFleet = b.getNonPlayerCombined();
			showFleetInfo();
		}
		
	}
}

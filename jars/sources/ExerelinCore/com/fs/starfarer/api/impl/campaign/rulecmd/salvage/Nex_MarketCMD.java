package com.fs.starfarer.api.impl.campaign.rulecmd.salvage;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.VIC_MarketCMD;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.BOMBARD;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.ENGAGE;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.GO_BACK;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RAID;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.addBombardVisual;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.getBombardDestroyThreshold;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.getSaturationBombardmentStabilityPenalty;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.getTacticalBombardmentStabilityPenalty;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.InvasionRound;
import static exerelin.campaign.InvasionRound.getString;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.campaign.intel.groundbattle.GBUtils;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

public class Nex_MarketCMD extends MarketCMD {
	
	public static final String INVADE = "nex_mktInvade";
	public static final String INVADE_CONFIRM = "nex_mktInvadeConfirm";
	public static final String INVADE_ABORT = "nex_mktInvadeAbort";
	public static final String INVADE_RESULT = "nex_mktInvadeResult";
	public static final String INVADE_RESULT_ANDRADA = "nex_mktInvadeResultAndrada";
	public static final String INVADE_GO_BACK = "nex_mktInvadeGoBack";
	public static final float FAIL_THRESHOLD_INVASION = 0.5f;
	public static final float TACTICAL_BOMBARD_FUEL_MULT = 1;	// 0.5f;
	public static final float TACTICAL_BOMBARD_DISRUPT_MULT = 1f;	// 1/3f;
	public static final float INVASION_XP_MULT = 3;
	public static final String MEMORY_KEY_BP_COOLDOWN = "$nex_raid_blueprints_cooldown";
	public static final String DATA_KEY_BPS_ALREADY_RAIDED = "nex_already_raided_blueprints";
	public static final float BASE_LOOT_SCORE = 3;
	
	public static Logger log = Global.getLogger(Nex_MarketCMD.class);
	
	protected TempDataInvasion tempInvasion = new TempDataInvasion();
	
	public Nex_MarketCMD() {
		
	}
	
	public Nex_MarketCMD(SectorEntityToken entity) {
		super(entity);
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		super.execute(ruleId, dialog, params, memoryMap);
		
		String command = params.get(0).getString(memoryMap);
		if (command == null) return false;
		
		if (command.equals("invadeMenu")) {
			invadeMenu();
		} else if (command.equals("invadeConfirm")) {
			//invadeRunRound();
			invadeInit();
		} else if (command.equals("invadeConfirm2")) {
			//invadeRunRound();
			//invadeGoToIntel();
		} else if (hasVIC() && command.equals(VIC_MarketCMD.VBombMenu))
		{
			new VIC_MarketCMD().execute(ruleId, dialog, params, memoryMap);
		}
		
		return true;
	}
	
	@Override
	protected void clearTemp() {
		super.clearTemp();
		if (tempInvasion != null) {
			tempInvasion.invasionLoot = null;
			tempInvasion.invasionValuables = null;
		}
	}
	
	protected boolean hasVIC() {
		return Global.getSettings().getModManager().isModEnabled("vic");
	}
	
	protected boolean hasII() {
		return Global.getSettings().getModManager().isModEnabled("Imperium");
	}
	
	protected boolean canVirusBomb() {
		if (!hasVIC()) return false;
		for (FleetMemberAPI shipToCheck : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy())
		{
			if (shipToCheck.getVariant().hasHullMod(VIC_MarketCMD.MOD_TO_CHECK))
			{
				return true;
			}
		}
		return false;
	}
	
	protected boolean canTitanBomb() {
		if (!hasII()) return false;
		
		if (playerFleet == null) {
			return false;
		}

		for (FleetMemberAPI member : playerFleet.getFleetData().getMembersListCopy()) {
			String hullId = member.getHullSpec().getDParentHullId();
			if (hullId == null) hullId = member.getHullSpec().getHullId();
			//log.info("Testing hull id " + hullId);
			
			if (hullId != null && hullId.contentEquals("ii_olympus")) {
				return true;
			}
		}
		return false;
	}
	
	// same as super method, but adds invade option
	@Override
	protected void showDefenses(boolean withText) {
		CampaignFleetAPI primary = getInteractionTargetForFIDPI();
		CampaignFleetAPI station = getStationFleet();
		
		boolean hasNonStation = false;
		boolean hasStation = station != null;
		boolean otherWantsToFight = false;
		BattleAPI b = null;
		FleetEncounterContext context = null;
		FleetInteractionDialogPluginImpl plugin = null;
		
		boolean ongoingBattle = false;
		
		boolean playerOnDefenderSide = false;
		boolean playerCanNotJoin = false;

		String stationType = "station";
		if (station != null) {
			FleetMemberAPI flagship = station.getFlagship();
			if (flagship != null && flagship.getVariant() != null) {
				String name = flagship.getVariant().getDesignation().toLowerCase();
				stationType = name;
			}
		}
		
		StationState state = getStationState();
		
		if (market != null) {
			Global.getSector().getEconomy().tripleStep();
		}
		
		if (primary == null) {
			if (state == StationState.NONE) {
				text.addPara(StringHelper.getString("nex_militaryOptions", "noStation"));
			} else {
				printStationState();
				text.addPara(StringHelper.getString("nex_militaryOptions", "noFleets"));
			}
		} else {
			ongoingBattle = primary.getBattle() != null;

			CampaignFleetAPI pluginFleet = primary;
			if (ongoingBattle) {
				BattleAPI.BattleSide playerSide = primary.getBattle().pickSide(playerFleet);
				CampaignFleetAPI other = primary.getBattle().getPrimary(primary.getBattle().getOtherSide(playerSide));
				if (other != null) {
					pluginFleet = other;
				}
			}
			
			FleetInteractionDialogPluginImpl.FIDConfig params = new FleetInteractionDialogPluginImpl.FIDConfig();
			params.justShowFleets = true;
			params.showPullInText = withText;
			plugin = new FleetInteractionDialogPluginImpl(params);
			//dialog.setInteractionTarget(primary);
			dialog.setInteractionTarget(pluginFleet);
			plugin.init(dialog);
//			if (ongoingBattle) {
//				plugin.setPlayerFleet(primary.getBattle().getPlayerCombined());
//			}
			dialog.setInteractionTarget(entity);
			
			
			context = (FleetEncounterContext)plugin.getContext();
			b = context.getBattle();
			
			BattleAPI.BattleSide playerSide = b.pickSide(playerFleet);
			if (playerSide != BattleAPI.BattleSide.NO_JOIN) {
				if (b.getOtherSideCombined(playerSide).isEmpty()) {
					playerSide = BattleAPI.BattleSide.NO_JOIN;
				}
			}
			playerCanNotJoin = playerSide == BattleAPI.BattleSide.NO_JOIN;
			if (!playerCanNotJoin) {
				playerOnDefenderSide = b.getSide(playerSide) == b.getSideFor(primary);
			}
			if (!ongoingBattle) {
				playerOnDefenderSide = false;
			}

			boolean otherHasStation = false;
			if (playerSide != BattleAPI.BattleSide.NO_JOIN) {
				//for (CampaignFleetAPI fleet : b.getNonPlayerSide()) {
				if (station != null) {
					for (CampaignFleetAPI fleet : b.getSideFor(station)) {
						if (!fleet.isStationMode()) {
							hasNonStation = true;
						}
					}
				} else {
					hasNonStation = true;
				}
				
				for (CampaignFleetAPI fleet : b.getOtherSide(playerSide)) {
					if (!fleet.isStationMode()) {
						//hasNonStation = true;
					} else {
						otherHasStation = true;
					}
				}
			}
			
			//otherWantsToFight = hasStation || plugin.otherFleetWantsToFight(true);
			
			// inaccurate because it doesn't include the station in the "wants to fight" calculation, but, this is tricky
			// and I don't want to break it right now
			otherWantsToFight = otherHasStation || plugin.otherFleetWantsToFight(true);
			
			if (withText) {
				if (hasStation) {
					String name = StringHelper.getString("nex_militaryOptions", "stationNameGeneric");
					if (station != null) {
						FleetMemberAPI flagship = station.getFlagship();
						if (flagship != null) {
							name = flagship.getVariant().getDesignation().toLowerCase();
							stationType = name;
							name = Misc.ucFirst(station.getFaction().getPersonNamePrefixAOrAn()) + " " + 
									station.getFaction().getPersonNamePrefix() + " " + name;
						}
					}
					text.addPara(StringHelper.getStringAndSubstituteToken("nex_militaryOptions", 
							"hasStation", "$stationName", name));
					
					
					if (hasNonStation) {
						if (ongoingBattle) {
							text.addPara(StringHelper.getString("nex_militaryOptions", "hasFleetOngoingBattle"));
						} else {
							text.addPara(StringHelper.getString("nex_militaryOptions", "hasFleetWithStation"));
						}
					}
				} else if (hasNonStation && otherWantsToFight) {
					printStationState();
					text.addPara(StringHelper.getString("nex_militaryOptions", "hasFleet"));
				} else if (hasNonStation && !otherWantsToFight) {
					printStationState();
					text.addPara(StringHelper.getString("nex_militaryOptions", "hasFleetTooSmall"));
				}
				
				plugin.printOngoingBattleInfo();
			}
		}
			
		options.clearOptions();
		
		String engageText = StringHelper.getString("nex_militaryOptions", "optionEngage");
		
		if (playerCanNotJoin) {
			engageText = "Engage the defenders";
		} else if (playerOnDefenderSide) {
			if (hasStation && hasNonStation) {
				engageText = StringHelper.getString("nex_militaryOptions", "optionAidStationAndDefenders");
			} else if (hasStation) {
				engageText = StringHelper.getString("nex_militaryOptions", "optionAidStation");
			} else {
				engageText = StringHelper.getString("nex_militaryOptions", "optionAidDefenders");
			}
		} else {
			if (ongoingBattle) {
				engageText = StringHelper.getString("nex_militaryOptions", "optionAidAttackers");
			} else {
				if (hasStation && hasNonStation) {
					engageText = StringHelper.getString("nex_militaryOptions", "optionEngageStationAndDefenders");
				} else if (hasStation) {
					engageText = StringHelper.getString("nex_militaryOptions", "optionEngageStation");
				} else {
					engageText = StringHelper.getString("nex_militaryOptions", "optionEngageDefenders");
				}
			}
		}
		engageText = StringHelper.substituteToken(engageText, "$stationType", stationType);
		
		
		options.addOption(engageText, ENGAGE);
		
		
		temp.canRaid = !hasNonStation || (hasNonStation && !otherWantsToFight);
		temp.canBombard = (!hasNonStation || (hasNonStation && !otherWantsToFight)) && !hasStation;
		//temp.canSurpriseRaid = Misc.getDaysSinceLastRaided(market) < SURPRISE_RAID_TIMEOUT;
		
		boolean couldRaidIfNotDebug = temp.canRaid;
		if (DebugFlags.MARKET_HOSTILITIES_DEBUG) {
			if (!temp.canRaid || !temp.canBombard) {
				text.addPara("(DEBUG mode: can raid and bombard anyway)");
			}
			temp.canRaid = true;
			temp.canBombard = true;
			//temp.canSurpriseRaid = true;
		}
			
//		options.addOption("Launch a raid against the colony", RAID);
//		options.addOption("Consider an orbital bombardment", BOMBARD);
		options.addOption(StringHelper.getStringAndSubstituteToken("nex_militaryOptions", 
							"optionRaid", "$market", market.getName()), RAID);
		options.addOption(StringHelper.getStringAndSubstituteToken("nex_militaryOptions", 
							"optionBombard", "$market", market.getName()), BOMBARD);
		
		if (!temp.canRaid) {
			options.setEnabled(RAID, false);
			options.setTooltip(RAID, StringHelper.getString("nex_militaryOptions", "cannotRaid"));
		}
		
//		if (!temp.canSurpriseRaid) {
////			float surpriseRaidDays = (int) (SURPRISE_RAID_TIMEOUT - Misc.getDaysSinceLastRaided(market));
////			if (surpriseRaidDays > 0) {
////				surpriseRaidDays = (int) Math.round(surpriseRaidDays);
////				if (surpriseRaidDays < 1) surpriseRaidDays = 1;
////				String days = "days";
////				if (surpriseRaidDays == 1) {
////					days = "day";
////				}
////				//text.addPara("Your ground forces commander estimates that");
////			}
//			options.setEnabled(RAID_SURPRISE, false);
//			options.setTooltip(RAID_SURPRISE, "This colony was raided within the last cycle and its ground defenses are on high alert, making a surprise raid impossible.");
//		}
		
		if (!temp.canBombard) {
			options.setEnabled(BOMBARD, false);
			options.setTooltip(BOMBARD, StringHelper.getString("nex_militaryOptions", "cannotBombard"));
		}
		
		//DEBUG = false;
		if (temp.canRaid && getRaidCooldown() > 0) {// && couldRaidIfNotDebug) {
			String daysStr = Misc.getStringForDays((int)Math.ceil(getRaidCooldown()));
			String str = StringHelper.getStringAndSubstituteToken("nex_militaryOptions", 
					"raidCooldown", "$cooldown", daysStr); 
			
			if (!DebugFlags.MARKET_HOSTILITIES_DEBUG) {
				options.setEnabled(RAID, false);
				text.addPara(str);
				text.highlightFirstInLastPara(daysStr, Misc.getHighlightColor());
				temp.canRaid = false;
			} else {
				text.addPara(str);
				text.highlightFirstInLastPara(daysStr, Misc.getHighlightColor());
				text.addPara("(DEBUG mode: can do it anyway)");
			}
			//options.setTooltip(RAID, "Need more time to organize another raid.");
		}
		
		//options.addOption("Launch a raid of the colony", RAID);
		
		
		if (context != null && otherWantsToFight && !playerCanNotJoin) {
			boolean knows = context.getBattle() != null && context.getBattle().getNonPlayerSide() != null &&
							context.getBattle().knowsWhoPlayerIs(context.getBattle().getNonPlayerSide());
			boolean lowImpact = context.isLowRepImpact();
			FactionAPI nonHostile = plugin.getNonHostileOtherFaction();
			//if (!playerFleet.getFaction().isHostileTo(otherFleet.getFaction()) && knows && !context.isEngagedInHostilities()) {
			if (nonHostile != null && knows && !lowImpact && !context.isEngagedInHostilities()) {
				String text = StringHelper.getString("nex_militaryOptions", "nonHostileWarning");
				text = StringHelper.substituteToken(text, "$faction", nonHostile.getDisplayNameLong());
				text = StringHelper.substituteToken(text, "$isOrAre", nonHostile.getDisplayNameIsOrAre());
				
				options.addOptionConfirmation(ENGAGE,
						text, 
						StringHelper.getString("yes", true), 
						StringHelper.getString("neverMind", true));
			}
		} else if (context == null || playerCanNotJoin || !otherWantsToFight) {
			options.setEnabled(ENGAGE, false);
			if (!otherWantsToFight) {
				if (ongoingBattle && playerOnDefenderSide && !otherWantsToFight) {
					options.setTooltip(ENGAGE, "The attackers are in disarray and not currently attempting to engage the station.");
				} else {
					if (playerCanNotJoin) {
						options.setTooltip(ENGAGE, "You're unable to join this battle.");
					} else if (primary == null) {
						options.setTooltip(ENGAGE, "There are no defenders to engage.");
					} else {
						options.setTooltip(ENGAGE, "The defenders are refusing to give battle to defend the colony.");
					}
				}
			}
		}
		
		boolean canVB = canVirusBomb(), canTB = canTitanBomb();
		if (canVB) {
			options.addOption(StringHelper.getStringAndSubstituteToken("nex_militaryOptions", 
							"optionBombardVirus", "$market", market.getName()), VIC_MarketCMD.VBombMenu);
		}
		if (canTB) {
			options.addOption(StringHelper.getStringAndSubstituteToken("nex_militaryOptions", 
							"optionBombardTitan", "$market", market.getName()), "iiTitanStrikeMenu");
		}
		
		if (!temp.canBombard) {
			if (canVB) {
				options.setEnabled(VIC_MarketCMD.VBombMenu, false);
				options.setTooltip(VIC_MarketCMD.VBombMenu, StringHelper.getString("nex_militaryOptions", "cannotBombard"));
			}
			if (canTB) {
				options.setEnabled("iiTitanStrikeMenu", false);
				options.setTooltip("iiTitanStrikeMenu", StringHelper.getString("nex_militaryOptions", "cannotBombard"));
			}
		}
		
		if (NexConfig.enableInvasions && InvasionRound.canInvade(entity))
		{
			options.addOption(StringHelper.getStringAndSubstituteToken("exerelin_invasion", 
					"invadeOpt", "$market", market.getName()), INVADE);
						
			if (getRaidCooldown() > 0) {
				if (!DebugFlags.MARKET_HOSTILITIES_DEBUG) {
					options.setEnabled(INVADE, false);
					tempInvasion.canInvade = false;
				}
			} 
			else if (GroundBattleIntel.isOngoing(market))
			{
				options.setEnabled(INVADE, false);
				options.setTooltip(INVADE, StringHelper.getString("nex_invasion2", "invasionAlreadyOngoing"));
				tempInvasion.canInvade = false;
			}
			else if (!temp.canRaid || !temp.canBombard)
			{
				options.setEnabled(INVADE, false);
				options.setTooltip(INVADE, StringHelper.getString("exerelin_invasion", "invadeBlocked"));
				tempInvasion.canInvade = false;
			}
		}
		
		options.addOption(StringHelper.getString("goBack", true), GO_BACK);
		options.setShortcut(GO_BACK, Keyboard.KEY_ESCAPE, false, false, false, true);
		
		
		if (plugin != null) {
			plugin.cleanUpBattle();
		}
	}
	
	protected int getEstimateNum(float num, int precision) {
		int result = Math.round(num/precision);
		result *= precision;
		
		return result;
	}
	
	protected GroundBattleIntel prepIntel() {
		GroundBattleIntel intel = new GroundBattleIntel(market, PlayerFactionStore.getPlayerFaction(), market.getFaction());
		intel.setPlayerInitiated(true);
		intel.setPlayerIsAttacker(true);
		intel.init();
		return intel;
	}
	
	protected void invadeMenu() {
		CampaignFleetAPI fleet = playerFleet;
		
		float width = 350;
		float opad = 10f;
		float small = 5f;
		
		Color h = Misc.getHighlightColor();
		
		dialog.getVisualPanel().showImagePortion("illustrations", "raid_prepare", 640, 400, 0, 0, 480, 300);

		float marines = playerFleet.getCargo().getMarines();
		float mechs = fleet.getCargo().getCommodityQuantity(Commodities.HAND_WEAPONS) * InvasionRound.HEAVY_WEAPONS_MULT;
		
		if (marines <= 0 && mechs <= 0) {
			
		}
		String str;
		TooltipMakerAPI info = text.beginTooltip();
		// non-hostile faction warning
		String is = faction.getDisplayNameIsOrAre();
		boolean hostile = faction.isHostileTo(Factions.PLAYER);
		float initPad = 0f;
		if (!hostile) {
			info = text.beginTooltip();
			info.setParaSmallInsignia();
			str = getString("nonHostileWarning");
			str = StringHelper.substituteToken(str, "$TheFaction", Misc.ucFirst(faction.getDisplayNameWithArticle()));
			str = StringHelper.substituteToken(str, "$isOrAre", is);
			info.addPara(str, initPad, faction.getBaseUIColor(), faction.getDisplayNameWithArticleWithoutArticle());
			initPad = opad;
			text.addTooltip();
		}
		
		GroundBattleIntel intel = prepIntel();
		
		float[] strEst = GBUtils.estimateDefenderStrength(intel, true);
		int precision = intel.getUnitSize().avgSize/5;
		
		info = text.beginTooltip();
		info.setParaSmallInsignia();
		info.addPara(String.format(GroundBattleIntel.getString("dialogGarrisonEstimate"), market.getName()), 10);
		info.setParaFontDefault();
		info.addPara("  - " + GroundBattleIntel.getString("dialogStrEstimateMilitia"), 3, 
				h, getEstimateNum(strEst[0], precision) + "");
		info.addPara("  - " + GroundBattleIntel.getString("dialogStrEstimateMarine"), 3, 
				h, getEstimateNum(strEst[1], precision) + "");
		info.addPara("  - " + GroundBattleIntel.getString("dialogStrEstimateHeavy"), 3, 
				h, getEstimateNum(strEst[2], precision) + "");
		
		text.addTooltip();

		boolean hasForces = marines > 0;
		if (!hasForces) {
			text.addPara(GroundBattleIntel.getString("dialogNoForces"));
		} else {
			precision /= 10;
			if (precision < 1) precision = 1;
			float[] strEstPlayer = GBUtils.estimatePlayerStrength();
			info = text.beginTooltip();
			info.setParaSmallInsignia();
			info.addPara(String.format(GroundBattleIntel.getString("dialogPlayerEstimate"), 
					market.getName()), 3);
			info.setParaFontDefault();
			info.addPara("  - " + GroundBattleIntel.getString("dialogStrEstimateMarine"), 3,
					h, getEstimateNum(strEstPlayer[0], precision) + "");
			info.addPara("  - " + GroundBattleIntel.getString("dialogStrEstimateHeavy"), 3,
					h, getEstimateNum(strEstPlayer[1], precision) + "");
			text.addTooltip();
		}
		text.addPara(GroundBattleIntel.getString("dialogEstimateHelp"));
		
		
		if (Misc.isStoryCritical(market)) {
			text.setFontSmallInsignia();
			str = getString("storyCriticalWarning");
			str = StringHelper.substituteToken(str, "$market", market.getName());
			LabelAPI para = text.addPara(str);
			para.setHighlight(market.getName(), getString("storyCriticalWarningHighlight"));
			para.setHighlightColors(market.getFaction().getBaseUIColor(), Global.getSector().getPlayerFaction().getBaseUIColor());
			text.setFontInsignia();
		}
		
		options.clearOptions();
		
		options.addOption(getString("invasionProceed"), INVADE_CONFIRM);
		
		// FIXME: magic number
		if (!hasForces) {
			str = GroundBattleIntel.getString("dialogNoForces");
			options.setTooltip(INVADE_CONFIRM, str);
			options.setEnabled(INVADE_CONFIRM, false);
			options.setTooltipHighlightColors(INVADE_CONFIRM, h);
		}
			
		options.addOption(Misc.ucFirst(StringHelper.getString("goBack")), INVADE_GO_BACK);
		options.setShortcut(INVADE_GO_BACK, Keyboard.KEY_ESCAPE, false, false, false, true);
	}
	
	protected void invadeInit() {
				
		CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
		impact.delta = market.getSize() * -0.01f * 1f;
		// not now, we also need to look at requested fleets
		//impact.ensureAtBest = tempInvasion.success ? RepLevel.VENGEFUL : RepLevel.HOSTILE;
		impact.ensureAtBest = RepLevel.HOSTILE;
		Global.getSector().adjustPlayerReputation(
				new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.CUSTOM, 
					impact, null, text, true, true),
					faction.getId());
		
		GroundBattleIntel intel = prepIntel();
		
		Global.getSector().getIntelManager().addIntelToTextPanel(intel, text);
		intel.start();
		
		if (!DebugFlags.MARKET_HOSTILITIES_DEBUG) {
			Misc.increaseMarketHostileTimeout(market, 30f);
		}

		Misc.setFlagWithReason(market.getMemoryWithoutUpdate(), "$nex_recentlyInvaded", 
							   Factions.PLAYER, true, 60f);
		dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$tradeMode", "NONE", 0);
		
		options.addOption(StringHelper.getString("nex_invasion2", "dialogOpenIntel"), "nex_mktInvadeConfirm2");
	}
		
	public TempData getTempData() {
		return temp;
	}
	
	// TODO see if the following changes from vanilla are still needed: Check blueprint cooldown; use externalised strings
	@Override
	protected void raidValuable() {
		super.raidValuable();
	}
		
	public static int getBombardDisruptDuration(BombardType type) {
		float dur = Global.getSettings().getFloat("bombardDisruptDuration");
		if (type == BombardType.TACTICAL)
			dur *= TACTICAL_BOMBARD_DISRUPT_MULT;
		return (int) dur;
	}
	
	public static int getBombardmentCost(MarketAPI market, CampaignFleetAPI fleet, BombardType type) {
		int result = MarketCMD.getBombardmentCost(market, fleet);
		if (type == BombardType.TACTICAL)
			result *= TACTICAL_BOMBARD_FUEL_MULT;
		
		return result;
	}
	
	// Difference from vanilla: No insta-hostile if third party is vengeful against target faction
	// or market is small
	@Override
	protected void bombardSaturation() {
		temp.bombardType = BombardType.SATURATION;

		temp.willBecomeHostile.clear();
		temp.willBecomeHostile.add(faction);
		
		boolean hidden = market.isHidden();
		
		List<FactionAPI> nonHostile = new ArrayList<FactionAPI>();
		List<FactionAPI> vengeful = new ArrayList<>();
		
		if (!hidden) {
			for (FactionAPI faction : Global.getSector().getAllFactions()) {
				if (temp.willBecomeHostile.contains(faction)) continue;

				if (faction.getCustomBoolean(Factions.CUSTOM_CARES_ABOUT_ATROCITIES)) {
					if (faction.getRelationshipLevel(market.getFaction()) == RepLevel.VENGEFUL)
					{
						vengeful.add(faction);
					}
					else {
						boolean hostile = faction.isHostileTo(Factions.PLAYER);
						temp.willBecomeHostile.add(faction);
						if (!hostile) {
							nonHostile.add(faction);
						}
					}
				}
			}
		}
		
		float opad = 10f;
		float small = 5f;
		
		Color h = Misc.getHighlightColor();
		Color b = Misc.getNegativeHighlightColor();
		
		
		int dur = getBombardDisruptDuration(temp.bombardType);
		
		List<Industry> targets = new ArrayList<Industry>();
		for (Industry ind : market.getIndustries()) {
			if (!ind.getSpec().hasTag(Industries.TAG_NO_SATURATION_BOMBARDMENT)) {
				if (ind.getDisruptedDays() >= dur * 0.8f) continue;
				targets.add(ind);
			}
		}
		temp.bombardmentTargets.clear();
		temp.bombardmentTargets.addAll(targets);
		
		boolean destroy = market.getSize() <= getBombardDestroyThreshold();
		if (Misc.isStoryCritical(market)) destroy = false;
		
		int fuel = (int) playerFleet.getCargo().getFuel();
		if (destroy) {
			text.addPara(StringHelper.getString("nex_bombardment", "satBombDescDestroy"));
		} else {
			text.addPara(StringHelper.getString("nex_bombardment", "satBombDesc"));
		}		
		
		
//		TooltipMakerAPI info = text.beginTooltip();
//		info.setParaFontDefault();
//		
//		info.setBulletedListMode(BaseIntelPlugin.INDENT);
//		float initPad = 0f;
//		for (Industry ind : targets) {
//			//info.addPara(ind.getCurrentName(), faction.getBaseUIColor(), initPad);
//			info.addPara(ind.getCurrentName(), initPad);
//			initPad = 3f;
//		}
//		info.setBulletedListMode(null);
//		
//		text.addTooltip();
		

		if (hidden) {
			text.addPara(StringHelper.getStringAndSubstituteToken("nex_bombardment", 
					"satBombWarningHidden", "$market", market.getName()));
		}
		else if (nonHostile.isEmpty()) {
			text.addPara(StringHelper.getString("nex_bombardment", "satBombWarningAllHostile"));
		} 
		else if (market.getSize() <= 3 || market.getMemoryWithoutUpdate().getBoolean(ColonyExpeditionIntel.MEMORY_KEY_COLONY))
		{
			text.addPara(StringHelper.getStringAndSubstituteToken("nex_bombardment", 
					"satBombWarningSmall", "$market", market.getName()));
		} else {
			text.addPara(StringHelper.getString("nex_bombardment", "satBombWarning"));
		}
		
		if (!nonHostile.isEmpty()) {
			TooltipMakerAPI info = text.beginTooltip();
			info.setParaFontDefault();
			
			info.setBulletedListMode(BaseIntelPlugin.INDENT);
			float initPad = 0f;
			for (FactionAPI fac : nonHostile) {
				info.addPara(Misc.ucFirst(fac.getDisplayName()), fac.getBaseUIColor(), initPad);
				initPad = 3f;
			}
			info.setBulletedListMode(null);
			
			text.addTooltip();
		}
		
		if (!vengeful.isEmpty()) {
			text.addPara(StringHelper.getStringAndSubstituteToken("nex_bombardment", 
					"satBombWarningVengeful", "$theFaction", faction.getDisplayNameWithArticle()));
			
			TooltipMakerAPI info = text.beginTooltip();
			info.setParaFontDefault();
			
			info.setBulletedListMode(BaseIntelPlugin.INDENT);
			float initPad = 0f;
			for (FactionAPI fac : vengeful) {
				info.addPara(Misc.ucFirst(fac.getDisplayName()), fac.getBaseUIColor(), initPad);
				initPad = 3f;
			}
			info.setBulletedListMode(null);
			
			text.addTooltip();
		}
		
		text.addPara(StringHelper.getString("nex_bombardment", "fuelCost"),
					 h, "" + temp.bombardCost, "" + fuel);
		
		addBombardConfirmOptions();
	}
	
	// Changes from vanilla: Custom rep handling for sat bomb;
	// saturation bombardment affects disposition
	@Override
	protected void bombardConfirm() {
		
		if (temp.bombardType == null) {
			bombardNeverMind();
			return;
		}
		
		if (temp.bombardType == BombardType.TACTICAL) {
			dialog.getVisualPanel().showImagePortion("illustrations", "bombard_tactical_result", 640, 400, 0, 0, 480, 300);
		} else {
			dialog.getVisualPanel().showImagePortion("illustrations", "bombard_saturation_result", 640, 400, 0, 0, 480, 300);
		}
		
		Random random = getRandom();
		
		if (!DebugFlags.MARKET_HOSTILITIES_DEBUG) {
			float timeout = TACTICAL_BOMBARD_TIMEOUT_DAYS;
			if (temp.bombardType == BombardType.SATURATION) {
				timeout = SATURATION_BOMBARD_TIMEOUT_DAYS;
			}
			Misc.increaseMarketHostileTimeout(market, timeout);
			
			timeout *= 0.7f;
			
			for (MarketAPI curr : Global.getSector().getEconomy().getMarkets(market.getContainingLocation())) {
				if (curr == market) continue;
				boolean cares = curr.getFaction().getCustomBoolean(Factions.CUSTOM_CARES_ABOUT_ATROCITIES);
				cares &= temp.bombardType == BombardType.SATURATION;
				
				if (curr.getFaction().isNeutralFaction()) continue;
				if (curr.getFaction().isPlayerFaction()) continue;
				if (curr.getFaction().isHostileTo(market.getFaction()) && !cares) continue;
				
				Misc.increaseMarketHostileTimeout(curr, timeout);
			}
		}
		
		addMilitaryResponse();
		
		playerFleet.getCargo().removeFuel(temp.bombardCost);
		AddRemoveCommodity.addCommodityLossText(Commodities.FUEL, temp.bombardCost, text);
		
		int size = market.getSize();
		for (FactionAPI curr : temp.willBecomeHostile) {
			CustomRepImpact impact = new CustomRepImpact();
			impact.delta = market.getSize() * -0.01f * 1f;
			impact.ensureAtBest = RepLevel.HOSTILE;
			if (temp.bombardType == BombardType.SATURATION) {
				impact.delta = market.getSize() * -0.02f * 1f;
				if (curr == faction) {
					impact.ensureAtBest = RepLevel.VENGEFUL;
					impact.delta *= 2;
				}
				else if (size <= 3) {
					impact.ensureAtBest = RepLevel.NEUTRAL;
				}
				DiplomacyManager.getManager().getDiplomacyBrain(curr.getId()).reportDiplomacyEvent(
						PlayerFactionStore.getPlayerFactionId(), impact.delta);
			}
			Global.getSector().adjustPlayerReputation(
				new RepActionEnvelope(RepActions.CUSTOM, 
					impact, null, text, true, true),
					curr.getId());
		}
	
		int atrocities = (int) Global.getSector().getCharacterData().getMemoryWithoutUpdate().getFloat(MemFlags.PLAYER_ATROCITIES);
		atrocities++;
		Global.getSector().getCharacterData().getMemoryWithoutUpdate().set(MemFlags.PLAYER_ATROCITIES, atrocities);
		
		
		int stabilityPenalty = getTacticalBombardmentStabilityPenalty();
		if (temp.bombardType == BombardType.SATURATION) {
			stabilityPenalty = getSaturationBombardmentStabilityPenalty();
		}
		boolean destroy = temp.bombardType == BombardType.SATURATION && market.getSize() <= getBombardDestroyThreshold();
		if (Misc.isStoryCritical(market)) destroy = false;
		
		if (stabilityPenalty > 0 && !destroy) {
			String reason = StringHelper.getString("nex_bombardment", "unrestReason");
			if (Misc.isPlayerFactionSetUp()) {
				reason = StringHelper.getString("nex_bombardment", "unrestReason"); 
				reason = String.format(reason, playerFaction.getDisplayName());
			}
			RecentUnrest.get(market).add(stabilityPenalty, reason);
			String str = StringHelper.getStringAndSubstituteToken("nex_bombardment", 
					"effectStability", "$market", market.getName());
			text.addPara(str, Misc.getHighlightColor(), "" + stabilityPenalty);
		}
		
		if (market.hasCondition(Conditions.HABITABLE) && !market.hasCondition(Conditions.POLLUTION)) {
			market.addCondition(Conditions.POLLUTION);
		}
		
		if (!destroy) {
			for (Industry curr : temp.bombardmentTargets) {
				int dur = getBombardDisruptDuration(temp.bombardType);
				dur *= StarSystemGenerator.getNormalRandom(random, 1f, 1.25f);
				curr.setDisrupted(dur);
			}
		}
		
		
		
		if (temp.bombardType == BombardType.TACTICAL) {
			text.addPara(StringHelper.getString("nex_bombardment", "effectMilitaryDisrupt"));
			
			ListenerUtil.reportTacticalBombardmentFinished(dialog, market, temp);
		} else if (temp.bombardType == BombardType.SATURATION) {
			if (destroy) {
				DecivTracker.decivilize(market, true);
				text.addPara(StringHelper.getStringAndSubstituteToken("nex_bombardment", 
					"effectMarketDestroyed", "$market", market.getName()));
			} else {
				int prevSize = market.getSize();
				CoreImmigrationPluginImpl.reduceMarketSize(market);
				if (prevSize == market.getSize()) {
					text.addPara(StringHelper.getString("nex_bombardment", "effectAllDisrupt"));
				} else {
					text.addPara(StringHelper.getString("nex_bombardment", "effectAllDisruptAndDownsize"), 
							Misc.getHighlightColor()
							, "" + market.getSize());
				}
				
			}
			ListenerUtil.reportSaturationBombardmentFinished(dialog, market, temp);
		}
		
		if (dialog != null && dialog.getPlugin() instanceof RuleBasedDialog) {
			if (dialog.getInteractionTarget() != null &&
					dialog.getInteractionTarget().getMarket() != null) {
				Global.getSector().setPaused(false);
				dialog.getInteractionTarget().getMarket().getMemoryWithoutUpdate().advance(0.0001f);
				Global.getSector().setPaused(true);
			}
			((RuleBasedDialog) dialog.getPlugin()).updateMemory();
		}
		
		Misc.setFlagWithReason(market.getMemoryWithoutUpdate(), MemFlags.RECENTLY_BOMBARDED, 
	   			   			  Factions.PLAYER, true, 30f);

		if (destroy) {
			if (dialog != null && dialog.getPlugin() instanceof RuleBasedDialog) {
				((RuleBasedDialog) dialog.getPlugin()).updateMemory();
//				market.getMemoryWithoutUpdate().unset("$tradeMode");
//				entity.getMemoryWithoutUpdate().unset("$tradeMode");
			}
		}
		
		addBombardVisual(market.getPrimaryEntity());
		
		addBombardContinueOption();
	}
	
	@Override
	public void doGenericRaid(FactionAPI faction, float attackerStr, float maxPenalty) {
		super.doGenericRaid(faction, attackerStr, maxPenalty);
		NexUtilsMarket.reportNPCGenericRaid(market, temp);
	}
	
	@Override
	public boolean doIndustryRaid(FactionAPI faction, float attackerStr, Industry industry, float durMult) {
		boolean result = super.doIndustryRaid(faction, attackerStr, industry, durMult);
		NexUtilsMarket.reportNPCIndustryRaid(market, temp, industry);
		return result;
	}
	
	@Override
	public void doBombardment(FactionAPI faction, BombardType type) {
		super.doBombardment(faction, type);
		if (type == BombardType.TACTICAL) {
			NexUtilsMarket.reportNPCTacticalBombardment(market, temp);
		} else {
			NexUtilsMarket.reportNPCSaturationBombardment(market, temp);
		}
	}
	
	/**
	 * Like {@code getNumPicks}, but with stronger falloff effect.
	 * This allows a higher initial pMore to start with.
	 * @param random
	 * @param pAny
	 * @param pMore
	 * @param diminishFactor
	 * @return
	 */
	protected int getNumPicksDiminishing(Random random, float pAny, float pMore, 
			float diminishFactor) 
	{
		if (random.nextFloat() >= pAny) return 0;
		
		int result = 1;
		for (int i = 0; i < 10; i++) {
			if (random.nextFloat() >= pMore) break;
			result++;
			pMore *= diminishFactor;
		}
		return result;
	}
	
	public static void applyDefenderIncreaseFromRaid(MarketAPI market, float mult) {
		float e = market.getMemoryWithoutUpdate().getExpire(DEFENDER_INCREASE_KEY);
		e += getRaidDefenderIncreasePerRaid() * mult;
		float max = getRaidDefenderIncreaseMax();
		if (e > max) e = max;
		
		market.getMemoryWithoutUpdate().set(DEFENDER_INCREASE_KEY, true);
		market.getMemoryWithoutUpdate().expire(DEFENDER_INCREASE_KEY, e);
	}
	
	public static Set<String> getEverRaidedBlueprints() {
		Map<String, Object> persistent = Global.getSector().getPersistentData();
		if (!persistent.containsKey(DATA_KEY_BPS_ALREADY_RAIDED))
		{
			persistent.put(DATA_KEY_BPS_ALREADY_RAIDED, new HashSet<String>());
		}
		return (Set<String>)persistent.get(DATA_KEY_BPS_ALREADY_RAIDED);
	}
	
	public static void addEverRaidedBlueprint(String bp) {
		log.info("Adding ever-raided blueprint: " + bp);
		getEverRaidedBlueprints().add(bp);
	}
	
	public static class TempDataInvasion {
		public boolean canInvade;
		public int marinesLost = 0;
		public int mechsLost = 0;
		public int roundNum = 0;
		public float stabilityPenalty = 0;
		public boolean success = false;
		public boolean tookForSelf = true;
		
		public float invasionMult;
		public float shortageMult;
		
		public float attackerStr;
		public float defenderStr;
		
		public Map<CommodityOnMarketAPI, Float> invasionValuables;
		public CargoAPI invasionLoot;
		public int invasionCredits;
	}
}

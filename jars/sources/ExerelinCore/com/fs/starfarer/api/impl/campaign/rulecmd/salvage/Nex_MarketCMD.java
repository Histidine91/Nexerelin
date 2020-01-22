package com.fs.starfarer.api.impl.campaign.rulecmd.salvage;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ResourceCostPanelAPI;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.ShowDefaultVisual;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.BOMBARD;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.ENGAGE;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.GO_BACK;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RAID;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.addBombardVisual;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.getBombardDestroyThreshold;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.getSaturationBombardmentStabilityPenalty;
import static com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.getTacticalBombardmentStabilityPenalty;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.InvasionRound.InvasionRoundResult;
import static exerelin.campaign.InvasionRound.getString;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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
	public static final String MEMORY_KEY_BP_COOLDOWN = "$nex_raid_blueprints_cooldown";
	public static final String DATA_KEY_BPS_ALREADY_RAIDED = "nex_already_raided_blueprints";
	public static final float BASE_LOOT_SCORE = 3;
	
	public static Logger log = Global.getLogger(Nex_MarketCMD.class);
	
	protected TempDataInvasion tempInvasion = new TempDataInvasion();
	
	public Nex_MarketCMD() {
		
	}
	
	public Nex_MarketCMD(SectorEntityToken entity) {
		super(entity);
		initForInvasion(entity);
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		super.execute(ruleId, dialog, params, memoryMap);
		
		String command = params.get(0).getString(memoryMap);
		if (command == null) return false;
		
		initForInvasion(dialog.getInteractionTarget());
		
		if (command.equals("invadeMenu")) {
			invadeMenu();
		} else if (command.equals("invadeConfirm")) {
			invadeRunRound();
		} else if (command.equals("invadeAbort")) {
			invadeFinish();
		} else if (command.equals("invadeResult")) {
			invadeResult(false);
		} else if (command.equals("invadeResultAndrada")) {
			invadeResult(true);
		}
		
		return true;
	}
	
	protected void initForInvasion(SectorEntityToken entity) {
		String key = "$nex_MarketCMD_tempInvasion";
		MemoryAPI mem = market.getMemoryWithoutUpdate();
		if (mem.contains(key)) {
			tempInvasion = (TempDataInvasion) mem.get(key);
		} else {
			mem.set(key, tempInvasion, 0f);
		}
	}
	
	@Override
	protected void clearTemp() {
		super.clearTemp();
		if (tempInvasion != null) {
			tempInvasion.invasionLoot = null;
			tempInvasion.invasionValuables = null;
		}
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
			
			FleetInteractionDialogPluginImpl.FIDConfig params = new FleetInteractionDialogPluginImpl.FIDConfig();
			params.justShowFleets = true;
			params.showPullInText = withText;
			plugin = new FleetInteractionDialogPluginImpl(params);
			dialog.setInteractionTarget(primary);
			plugin.init(dialog);
			dialog.setInteractionTarget(entity);
			
			
			context = (FleetEncounterContext)plugin.getContext();
			b = context.getBattle();
			
			BattleAPI.BattleSide playerSide = b.pickSide(playerFleet);
			playerCanNotJoin = playerSide == BattleAPI.BattleSide.NO_JOIN;
			if (!playerCanNotJoin) {
				playerOnDefenderSide = b.getSide(playerSide) == b.getSideFor(primary);
			}
			if (!ongoingBattle) {
				playerOnDefenderSide = false;
			}

			if (playerSide != BattleAPI.BattleSide.NO_JOIN) {
				//for (CampaignFleetAPI fleet : b.getNonPlayerSide()) {
				for (CampaignFleetAPI fleet : b.getOtherSide(playerSide)) {
					if (!fleet.isStationMode()) {
						hasNonStation = true;
						break;
					}
				}
			}
			
			otherWantsToFight = hasStation || plugin.otherFleetWantsToFight(true);
			
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
						text.addPara(StringHelper.getString("nex_militaryOptions", "hasFleetWithStation"));
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
			//engageText = "Engage the defenders";
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
		
		boolean couldRaidIfNotDebug = temp.canRaid;
		if (DebugFlags.MARKET_HOSTILITIES_DEBUG) {
			if (!temp.canRaid || !temp.canBombard) {
				text.addPara("(DEBUG mode: can raid and bombard anyway)");
			}
			temp.canRaid = true;
			temp.canBombard = true;
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
		
		if (!temp.canBombard) {
			options.setEnabled(BOMBARD, false);
			options.setTooltip(BOMBARD, StringHelper.getString("nex_militaryOptions", "cannotBombard"));
		}
		
		//DEBUG = false;
		if (temp.canRaid && getRaidCooldown() > 0) {// && couldRaidIfNotDebug) {
			if (!DebugFlags.MARKET_HOSTILITIES_DEBUG) {
				options.setEnabled(RAID, false);
				text.addPara(StringHelper.getString("nex_militaryOptions", "raidCooldown"));
				temp.canRaid = false;
			} else {
				text.addPara(StringHelper.getString("nex_militaryOptions", "raidCooldown"));
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
		}
		
		if (InvasionRound.canInvade(entity))
		{
			options.addOption(StringHelper.getStringAndSubstituteToken("exerelin_invasion", 
					"invadeOpt", "$market", market.getName()), INVADE);
						
			if (getRaidCooldown() > 0) {
				if (!DebugFlags.MARKET_HOSTILITIES_DEBUG) {
					options.setEnabled(INVADE, false);
					tempInvasion.canInvade = false;
				}
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
	
	protected void invadeMenu() {
		tempInvasion.invasionValuables = computeInvasionValuables();
		CampaignFleetAPI fleet = playerFleet;
		
		float width = 350;
		float opad = 10f;
		float small = 5f;
		
		Color h = Misc.getHighlightColor();
		
//		dialog.getVisualPanel().showPlanetInfo(market.getPrimaryEntity());
//		dialog.getVisualPanel().finishFadeFast();
		dialog.getVisualPanel().showImagePortion("illustrations", "raid_prepare", 640, 400, 0, 0, 480, 300);

		float marines = playerFleet.getCargo().getMarines();
		float support = Misc.getFleetwideTotalMod(playerFleet, Stats.FLEET_GROUND_SUPPORT, 0f);
		if (support > marines) support = marines;
		float mechs = fleet.getCargo().getCommodityQuantity(Commodities.HAND_WEAPONS) * InvasionRound.HEAVY_WEAPONS_MULT;
		if (mechs > marines) mechs = marines;
		
		StatBonus attackerBase = new StatBonus(); 
		StatBonus defenderBase = new StatBonus(); 
		
		//defenderBase.modifyFlatAlways("base", baseDef, "Base value for a size " + market.getSize() + " colony");
		
		attackerBase.modifyFlatAlways("core_marines", marines, getString("marinesOnBoard", true));
		attackerBase.modifyFlatAlways("core_support", support, getString("groundSupportCapability", true));
		attackerBase.modifyFlatAlways("nex_mechs", mechs, getString("heavyWeaponsOnBoard", true));
		
		ExerelinFactionConfig atkConf = ExerelinConfig.getExerelinFactionConfig(PlayerFactionStore.getPlayerFactionId());
		String str = StringHelper.getStringAndSubstituteToken("exerelin_invasion", "attackBonus", "$Faction", 
				Misc.ucFirst(fleet.getFaction().getDisplayName()));
		attackerBase.modifyMult("nex_invasionAtkBonus", atkConf.invasionStrengthBonusAttack + 1, str);
		
		StatBonus attacker = playerFleet.getStats().getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD);
		
		StatBonus defender = InvasionRound.getDefenderStrengthStat(market);
		
		float attackerStr = (int) Math.round(attacker.computeEffective(attackerBase.computeEffective(0f)));
		float defenderStr = (int) Math.round(defender.computeEffective(0));
		
		tempInvasion.attackerStr = attackerStr;
		tempInvasion.defenderStr = defenderStr;
		
		TooltipMakerAPI info = text.beginTooltip();
		
		info.setParaSmallInsignia();
		
		String is = faction.getDisplayNameIsOrAre();
		boolean hostile = faction.isHostileTo(Factions.PLAYER);
		float initPad = 0f;
		if (!hostile) {
			str = getString("nonHostileWarning");
			str = StringHelper.substituteToken(str, "$TheFaction", Misc.ucFirst(faction.getDisplayNameWithArticle()));
			str = StringHelper.substituteToken(str, "$isOrAre", is);
			info.addPara(str, initPad, faction.getBaseUIColor(), faction.getDisplayNameWithArticleWithoutArticle());
			initPad = opad;
		}
		
		float sep = small;
		sep = 3f;
		str = Misc.ucFirst(getString("invasionStrength"));
		info.addPara(str + ": %s", initPad, h, "" + (int)attackerStr);
		info.addStatModGrid(width, 50, opad, small, attackerBase, true, statPrinter(false));
		if (!attacker.isUnmodified()) {
			info.addStatModGrid(width, 50, opad, sep, attacker, true, statPrinter(true));
		}
		
		str = Misc.ucFirst(getString("groundDefStrength"));
		info.addPara(str + ": %s", opad, h, "" + (int)defenderStr);
		//info.addStatModGrid(width, 50, opad, small, defenderBase, true, statPrinter());
		//if (!defender.isUnmodified()) {
			info.addStatModGrid(width, 50, opad, small, defender, true, statPrinter(true));
		//}
			
		defender.unmodifyFlat("core_addedDefStr");
		
		text.addTooltip();

		boolean hasForces = true;
		tempInvasion.invasionMult = attackerStr / Math.max(1f, (attackerStr + defenderStr));
		
		if (tempInvasion.invasionMult < 0.25f) {
			text.addPara(getString("insufficientForces"));
			hasForces = false;
		} else {
			Color eColor = h;
			if (tempInvasion.invasionMult < FAIL_THRESHOLD_INVASION) {
				eColor = Misc.getNegativeHighlightColor();
				//temp.canFail = true;
			} else if (tempInvasion.invasionMult >= 0.8f) {
				eColor = Misc.getPositiveHighlightColor();
			}
			text.addPara(Misc.ucFirst(getString("forceBalance")) + ": %s",
					eColor,
					"" + (int)Math.round(tempInvasion.invasionMult * 100f) + "%");
		}
		if (DebugFlags.MARKET_HOSTILITIES_DEBUG) {
		}
		
		options.clearOptions();
		
		options.addOption(getString("invasionProceed"), INVADE_CONFIRM);
		
		// FIXME: magic number
		if (!hasForces) {
			String pct = 25 + "%";
			str = StringHelper.substituteToken(getString("insufficientForcesTooltip"), "$percent", pct);
			options.setTooltip(INVADE_CONFIRM, str);
			options.setEnabled(INVADE_CONFIRM, false);
			options.setTooltipHighlightColors(INVADE_CONFIRM, h);
		}
			
		options.addOption(Misc.ucFirst(StringHelper.getString("goBack")), INVADE_GO_BACK);
		options.setShortcut(INVADE_GO_BACK, Keyboard.KEY_ESCAPE, false, false, false, true);
	}
	
	protected void invadeRunRound() 
	{
		MarketAPI market = dialog.getInteractionTarget().getMarket();
		dialog.getVisualPanel().showImagePortion("illustrations", "raid_disrupt_result", 640, 400, 0, 0, 480, 300);
		
		tempInvasion.roundNum += 1;
		if (tempInvasion.roundNum == 1)
		{
			if (!DebugFlags.MARKET_HOSTILITIES_DEBUG) {
				Misc.increaseMarketHostileTimeout(market, 30f);
			}

			setRaidCooldown(getRaidCooldownMax());
			
			
			Misc.setFlagWithReason(market.getMemoryWithoutUpdate(), "$nex_recentlyInvaded", 
								   Factions.PLAYER, true, 60f);
		}
		
		InvasionRoundResult result = InvasionRound.execute(playerFleet, market, 
				tempInvasion.attackerStr, tempInvasion.defenderStr, getRandom());
		tempInvasion.stabilityPenalty += InvasionRound.INSTABILITY_PER_ROUND;		
		tempInvasion.attackerStr = Math.max(result.atkStr, 0);
		tempInvasion.defenderStr = Math.max(result.defStr, 0);
				
		//losses = random.nextInt(marines / 2);
		String roundResultMsg = "";
		if (tempInvasion.defenderStr <= 0)
		{
			roundResultMsg = getString("roundResult_win");
			tempInvasion.success = true;
		}
		else if (tempInvasion.attackerStr <= 0)
		{
			roundResultMsg = getString("roundResult_lose");
		}
		else if (result.atkDam > result.defDam * 2)
		{
			roundResultMsg = getString("roundResult_good");
		}
		else if (result.atkDam >= result.defDam)
		{
			roundResultMsg = getString("roundResult_ok");
		}
		else
		{
			roundResultMsg = getString("roundResult_bad");
		}
		roundResultMsg = StringHelper.substituteToken(roundResultMsg, "$market", market.getName());
		text.addPara(roundResultMsg);
		
		// print things that happened during this round
		text.setFontSmallInsignia();
		
		if (result.losses <= 0 && result.lossesMech <= 0) {
			text.addPara(getString("noLosses"));
		}
		if (result.losses > 0) {
			playerFleet.getCargo().removeMarines(result.losses);
			tempInvasion.marinesLost = result.losses;
			AddRemoveCommodity.addCommodityLossText(Commodities.MARINES, result.losses, text);
		}
		if (result.lossesMech > 0) {
			playerFleet.getCargo().removeCommodity(Commodities.HAND_WEAPONS, result.lossesMech);
			tempInvasion.mechsLost = result.lossesMech;
			AddRemoveCommodity.addCommodityLossText(Commodities.HAND_WEAPONS, result.lossesMech, text);
		}
		
		text.setFontSmallInsignia();
		// disruption
		if (result.disrupted != null)
		{
			String name = result.disrupted.getSpec().getName();
			String durStr = Math.round(result.disruptionLength) + "";
			String str = getString("industryDisruption");
			str = StringHelper.substituteToken(str, "$industry", name);
			str = StringHelper.substituteToken(str, "$days", durStr);
			text.addPara(str, Misc.getHighlightColor(), name, durStr);
		}
		
		Color hl = tempInvasion.attackerStr > tempInvasion.defenderStr ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
		Color hl2 = Misc.getHighlightColor();
		String as = (int)tempInvasion.attackerStr + "";
		String ds = (int)tempInvasion.defenderStr + "";
		String ad = (int)-result.atkDam + "";
		String dd = (int)-result.defDam + "";
		
		String str = getString("attackerStrengthRemaining");
		str = StringHelper.substituteToken(str, "$str", as);
		str = StringHelper.substituteToken(str, "$delta", dd);
		text.addPara(str);
		Highlights h = new Highlights();
		h.setColors(hl, result.atkDam >= result.defDam ? hl2 : Misc.getNegativeHighlightColor());
		h.setText(as, dd);
		text.setHighlightsInLastPara(h);
		
		str = getString("defenderStrengthRemaining");
		str = StringHelper.substituteToken(str, "$str", ds);
		str = StringHelper.substituteToken(str, "$delta", ad);
		text.addPara(str, hl2, ds, ad);
		
		String marinesRemaining = playerFleet.getCargo().getMarines() + "";
		str = Misc.ucFirst(getString("marinesRemaining")) + ": " + marinesRemaining;
		text.addPara(str, hl2, marinesRemaining);
		int mechs = (int)playerFleet.getCargo().getCommodityQuantity(Commodities.HAND_WEAPONS);
		if (mechs > 0) {
			str = Misc.ucFirst(getString("mechsRemaining")) + ": " + mechs;
			text.addPara(str, hl2, mechs + "");
		}
		
		text.setFontInsignia();
		
		if (tempInvasion.success || tempInvasion.attackerStr <= 0)
		{
			invadeFinish();
		}
		else
		{
			if (tempInvasion.roundNum == 1)
				Global.getSoundPlayer().playUISound("nex_sfx_combat", 1f, 1f);
			options.clearOptions();
			// options: continue or leave
			options.addOption(getString("invasionContinue"), INVADE_CONFIRM);
			options.addOption(getString("invasionAbort"), INVADE_ABORT);
		}
	}
	
	/**
	 * Finish the invasion, apply final effects
	 */
	protected void invadeFinish() {
		Random random = getRandom();
		InvasionRound.finishInvasion(playerFleet, null, market, tempInvasion.roundNum, tempInvasion.success);
		
		applyDefenderIncreaseFromRaid(market);
		
		// cooldown
		setRaidCooldown(getRaidCooldownMax());
		
		// unrest
		// note that this is for GUI only, actual impact is caused in InvasionRound
		int stabilityPenalty = InvasionRound.getStabilityPenalty(market, tempInvasion.roundNum, tempInvasion.success);
		
		if (stabilityPenalty > 0) {
			text.addPara(StringHelper.substituteToken(getString("stabilityReduced"), 
					"$market", market.getName()), Misc.getHighlightColor(), "" + stabilityPenalty);
		}
		
		// reputation impact
		CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
		impact.delta = market.getSize() * -0.01f * 1f;
		// not now, we also need to look at requested fleets
		//impact.ensureAtBest = tempInvasion.success ? RepLevel.VENGEFUL : RepLevel.HOSTILE;
		impact.ensureAtBest = RepLevel.HOSTILE;
		Global.getSector().adjustPlayerReputation(
				new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.CUSTOM, 
					impact, null, text, true, true),
					faction.getId());
		
		// handle loot
		String contText = null;
		
		float targetValue = getBaseInvasionValue();
		CargoAPI result = Global.getFactory().createCargo(true);
		
		// loot
		if (tempInvasion.success)
		{
			WeightedRandomPicker<CommodityOnMarketAPI> picker = new WeightedRandomPicker<CommodityOnMarketAPI>(random);
			for (CommodityOnMarketAPI com : tempInvasion.invasionValuables.keySet()) {
				picker.add(com, tempInvasion.invasionValuables.get(com));
			}

			//float chunks = 10f;
			float chunks = tempInvasion.invasionValuables.size();
			if (chunks > 6) chunks = 6;
			for (int i = 0; i < chunks; i++) {
				float chunkValue = targetValue * 1f / chunks;
				float randMult = StarSystemGenerator.getNormalRandom(random, 0.5f, 1.5f);
				chunkValue *= randMult;

				CommodityOnMarketAPI pick = picker.pick();
				int quantity = (int) (chunkValue / pick.getCommodity().getBasePrice());
				if (quantity <= 0) continue;
				
				// handled in InvasionRound
				//pick.addTradeModMinus("invasion_" + Misc.genUID(), -quantity, BaseSubmarketPlugin.TRADE_IMPACT_DAYS);

				result.addCommodity(pick.getId(), quantity);
			}

			raidSpecialItems(result, random, true);

			result.sort();

			tempInvasion.invasionLoot = result;

			tempInvasion.invasionCredits = (int)(targetValue * 0.1f * StarSystemGenerator.getNormalRandom(random, 0.5f, 1.5f));
			if (tempInvasion.invasionCredits < 2) tempInvasion.invasionCredits = 2;

			//result.clear();
			if (result.isEmpty()) {
				text.addPara(getString("endMsgNoLoot"));
			} else {
				text.addPara(getString("endMsgLoot"));
				AddRemoveCommodity.addCreditsGainText(tempInvasion.invasionCredits, text);
				playerFleet.getCargo().getCredits().add(tempInvasion.invasionCredits);
				contText = getString("invasionSpoils");
			}

			ExerelinUtilsMarket.reportInvadeLoot(dialog, market, tempInvasion, tempInvasion.invasionLoot);
		}
		else
		{
			text.addPara(getString("endMsgDefeat"));
			contText = StringHelper.substituteToken(getString("invasionFail"), "$market", market.getName());
		}
		
		Global.getSoundPlayer().playUISound("ui_raid_finished", 1f, 1f);
		
		addContinueOptionInvasion(contText);
	}
	
	protected void addContinueOptionInvasion(String text) {
		if (text == null) text = Misc.ucFirst(StringHelper.getString("continue"));
		options.clearOptions();
		options.addOption(text, INVADE_RESULT);
		
		if (tempInvasion.success && playerFaction != PlayerFactionStore.getPlayerFaction())
		{
			String str = getString("invasionTakeForSelf");
			String marketName = market.getName();
			str = StringHelper.substituteToken(str, "$market", marketName);
			options.addOption(str, INVADE_RESULT_ANDRADA);
			
			if (!wasPlayerMarket()) {
				str = getString("takeForSelfWarning");
				str = StringHelper.substituteToken(str, "$market", marketName);
				options.addOptionConfirmation(INVADE_RESULT_ANDRADA, str, 
						Misc.ucFirst(StringHelper.getString("yes")),
						Misc.ucFirst(StringHelper.getString("no")));
			}
			else {
				str = getString("takeForSelfNoWarning");
				str = StringHelper.substituteToken(str, "$market", marketName);
				options.addOptionConfirmation(INVADE_RESULT_ANDRADA, str, 
						Misc.ucFirst(StringHelper.getString("yes")),
						Misc.ucFirst(StringHelper.getString("no")));
			}
		}
	}
	
	// same as computeRaidValuables except writes to different place
	protected Map<CommodityOnMarketAPI, Float> computeInvasionValuables() {
		Map<CommodityOnMarketAPI, Float> result = new HashMap<CommodityOnMarketAPI, Float>();
		float totalDemand = 0f;
		float totalShortage = 0f;
		for (CommodityOnMarketAPI com : market.getAllCommodities()) {
			if (com.isPersonnel()) continue;
			if (com.getCommodity().hasTag(Commodities.TAG_META)) continue;
			
			int a = com.getAvailable();
			if (a > 0) {
				float num = BaseIndustry.getSizeMult(a) * com.getCommodity().getEconUnit() * 0.5f;
				result.put(com, num);
			}
			
			float max = com.getMaxDemand();
			totalDemand += max;
			totalShortage += Math.max(0, max - a);
		}
		
		tempInvasion.shortageMult = 1f;
		if (totalShortage > 0 && totalDemand > 0) {
			tempInvasion.shortageMult = Math.max(0, totalDemand - totalShortage) / totalDemand;
		}
		
		return result;
	}
	
	// same as getBaseRaidValue except reads tempInvasion instead of temp
	protected float getBaseInvasionValue() {
		float targetValue = 0f;
		for (CommodityOnMarketAPI com : tempInvasion.invasionValuables.keySet()) {
			targetValue += tempInvasion.invasionValuables.get(com) * com.getCommodity().getBasePrice();
		}
		targetValue *= 0.1f;
		targetValue *= tempInvasion.invasionMult;
		targetValue *= tempInvasion.shortageMult;
		return targetValue;
	}
	
	@Override
	protected void raidSpecialItems(CargoAPI cargo, Random random) {
		raidSpecialItems(cargo, random, false);
	}
	
	/*
		Changes from vanilla:
			Add isInvasionArg
			Drops blueprints known to player
			Each blueprint can only be dropped once
			Filter out NO_BP_DROP blueprints _before_ adding them to picker
			Loot more small and medium weapons
	*/
	protected void raidSpecialItems(CargoAPI cargo, Random random, boolean isInvasion) 
	{
		float mult = isInvasion ? tempInvasion.invasionMult : temp.raidMult;
		float p = mult * 0.2f;
		
		boolean withBP = false;
		boolean heavyIndustry = false;
		for (Industry curr : market.getIndustries()) {
			
			if (!isInvasion) {
				String id = curr.getAICoreId();
				if (id != null && random.nextFloat() < p) {
					curr.setAICoreId(null);
					cargo.addCommodity(id, 1);
				}

				SpecialItemData special = curr.getSpecialItem();
				if (special != null && random.nextFloat() < p) {
					curr.setSpecialItem(null);
					cargo.addSpecial(special, 1);
				}
			}
			if (curr.getSpec().hasTag(Industries.TAG_USES_BLUEPRINTS)) {
				withBP = true;
			}
			if (curr.getSpec().hasTag(Industries.TAG_HEAVYINDUSTRY)) {
				heavyIndustry = true;
			}
		}
		if (withBP) {
			boolean bpCooldown = market.getMemoryWithoutUpdate().getBoolean(MEMORY_KEY_BP_COOLDOWN);
			if (bpCooldown)
				withBP = false;
			else
				market.getMemoryWithoutUpdate().set(MEMORY_KEY_BP_COOLDOWN, true,
						Global.getSettings().getFloat("nex_raidBPCooldown"));
		}
		
		market.reapplyIndustries();
		
		boolean military = market.getMemoryWithoutUpdate().getBoolean(MemFlags.MARKET_MILITARY);
		
		String ship =    "MarketCMD_ship____";
		String weapon =  "MarketCMD_weapon__";
		String fighter = "MarketCMD_fighter_";
		
		// blueprints
		if (withBP) {
			Set<String> droppedBefore = getEverRaidedBlueprints();
			boolean allowRepeat = ExerelinConfig.allowRepeatBlueprintsFromRaid;
			WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>(random);
			for (String id : market.getFaction().getKnownShips()) {
				if (!allowRepeat && droppedBefore.contains(id)) continue;
				if (Global.getSettings().getHullSpec(id).hasTag(Tags.NO_BP_DROP)) continue;
				if (Global.getSettings().getHullSpec(id).hasTag(Items.TAG_BASE_BP)) continue;
				picker.add(ship + id, 1f);
			}
			for (String id : market.getFaction().getKnownWeapons()) {
				if (!allowRepeat && droppedBefore.contains(id)) continue;
				if (Global.getSettings().getWeaponSpec(id).hasTag(Tags.NO_BP_DROP)) continue;
				if (Global.getSettings().getWeaponSpec(id).hasTag(Items.TAG_BASE_BP)) continue;
				picker.add(weapon + id, 1f);
			}
			for (String id : market.getFaction().getKnownFighters()) {
				if (!allowRepeat && droppedBefore.contains(id)) continue;
				if (Global.getSettings().getFighterWingSpec(id).hasTag(Tags.NO_BP_DROP)) continue;
				if (Global.getSettings().getFighterWingSpec(id).hasTag(Items.TAG_BASE_BP)) continue;
				picker.add(fighter + id, 1f);
			}
			
			//int num = getNumPicks(random, mult * 0.25f, mult * 0.5f);
			int num = getNumPicksDiminishing(random, 
					mult + 0.5f, 
					mult * Global.getSettings().getFloat("nex_raidBPInitialExtraMult"),
					Global.getSettings().getFloat("nex_raidBPIterationMult")
			);
			for (int i = 0; i < num && !picker.isEmpty(); i++) {
				String id = picker.pickAndRemove();
				if (id == null) continue;
				
				if (id.startsWith(ship)) {
					String specId = id.substring(ship.length());
					cargo.addSpecial(new SpecialItemData(Items.SHIP_BP, specId), 1);
					addEverRaidedBlueprint(specId);
				} else if (id.startsWith(weapon)) {
					String specId = id.substring(weapon.length());
					cargo.addSpecial(new SpecialItemData(Items.WEAPON_BP, specId), 1);
					addEverRaidedBlueprint(specId);
				} else if (id.startsWith(fighter)) {
					String specId = id.substring(fighter.length());
					cargo.addSpecial(new SpecialItemData(Items.FIGHTER_BP, specId), 1);
					addEverRaidedBlueprint(specId);
				}
			}
		}
		
		// modspecs
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>(random);
		for (String id : market.getFaction().getKnownHullMods()) {
			if (playerFaction.knowsHullMod(id) && !DebugFlags.ALLOW_KNOWN_HULLMOD_DROPS) continue;
			picker.add(id, 1f);
		}
		
		// more likely to get at least one modspec, but not likely to get many
		int num = getNumPicks(random, mult + 0.5f, mult * 0.25f);
		for (int i = 0; i < num && !picker.isEmpty(); i++) {
			String id = picker.pickAndRemove();
			if (id == null) continue;
			cargo.addSpecial(new SpecialItemData(Items.MODSPEC, id), 1);
		}
		
		
		// weapons and fighters
		picker = new WeightedRandomPicker<String>(random);
		for (String id : market.getFaction().getKnownWeapons()) {
			WeaponSpecAPI w = Global.getSettings().getWeaponSpec(id);
			if (w.hasTag("no_drop")) continue;
			if (w.getAIHints().contains(WeaponAPI.AIHints.SYSTEM)) continue;
			
			if (!military && !heavyIndustry && 
					(w.getTier() > 1 || w.getSize() == WeaponAPI.WeaponSize.LARGE)) continue;
			
			picker.add(weapon + id, w.getRarity());
		}
		for (String id : market.getFaction().getKnownFighters()) {
			FighterWingSpecAPI f = Global.getSettings().getFighterWingSpec(id);
			if (f.hasTag(Tags.WING_NO_DROP)) continue;
			
			if (!military && !heavyIndustry && f.getTier() > 0) continue;
			
			picker.add(fighter + id, f.getRarity());
		}
		
		
		num = getNumPicks(random, mult + 0.5f, mult * 0.25f);
		if (military || heavyIndustry) {
			num += Math.round(market.getCommodityData(Commodities.SHIPS).getAvailable() * mult);
		}
		
		for (int i = 0; i < num && !picker.isEmpty(); i++) {
			String id = picker.pickAndRemove();
			if (id == null) continue;
			
			if (id.startsWith(weapon)) {
				String weaponId = id.substring(weapon.length());
				int count = 1;
				WeaponSpecAPI w = Global.getSettings().getWeaponSpec(weaponId);
				if (w.getSize() == WeaponSize.SMALL)
					count = 2 + random.nextInt(3);
				else if (w.getSize() == WeaponSize.MEDIUM)
					count = 1 + random.nextInt(2);
				
				cargo.addWeapons(weaponId, count);
			} else if (id.startsWith(fighter)) {
				cargo.addFighters(id.substring(fighter.length()), 1);
			}
		}
	}
	
	/*
	public float getShipBlueprintSizeValue(String hullId) {
		ShipHullSpecAPI spec = Global.getSettings().getHullSpec(hullId);
		float rarity = spec.getRarity();
		switch (spec.getHullSize()) {
			case DESTROYER:
				return 2;
			case CRUISER:
				return 3;
			case CAPITAL_SHIP:
				return 4;
			default:
				return 1;
		}
	}
	
	public float getWeaponBlueprintSizeValue(String weaponId) {
		switch (Global.getSettings().getWeaponSpec(weaponId).getSize()) {
			case SMALL:
				return 0.75f;
			case MEDIUM:
				return 1;
			case LARGE:
				return 1.5f;
			default:
				return 1;
		}
	}
	*/
	
	/**
	 * Show loot if applicable, cleanup and exit
	 * @param tookForSelf True if we decided to take the market for ourselves, 
	 * instead of turning it over to commissioning faction
	 * Note: this is always false if we have no commission
	 */
	protected void invadeResult(boolean tookForSelf)
	{
		tempInvasion.tookForSelf = tookForSelf;
		
		if (tempInvasion.invasionLoot != null) {
			if (tempInvasion.invasionLoot.isEmpty()) {
				finishedInvade();
			} else {
				invadeShowLoot();
			}
			return;
		} else {
			finishedInvade();
		}
	}
	
	protected void finishedInvade() {
		clearTemp();
		//showDefenses(true);
	
		new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);
		
		FactionAPI conqueror = PlayerFactionStore.getPlayerFaction();
		if (tempInvasion.tookForSelf && tempInvasion.success)
		{
			conqueror = playerFaction;
			if (!wasPlayerMarket()) {
				CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
				impact.delta = -0.05f * market.getSize();
				//impact.ensureAtBest = RepLevel.SUSPICIOUS;
				impact.limit = RepLevel.INHOSPITABLE;
				Global.getSector().adjustPlayerReputation(new CoreReputationPlugin.RepActionEnvelope(
						CoreReputationPlugin.RepActions.CUSTOM, impact, null, text, true), 
						PlayerFactionStore.getPlayerFactionId());
			}
		}
		if (tempInvasion.success)
			InvasionRound.conquerMarket(market, conqueror, true);
		
		// report rebellion
		RebellionIntel rebel = RebellionIntel.getOngoingEvent(market);
		if (rebel != null) {
			text.addPara(getString("rebellion"));
			Global.getSector().getIntelManager().addIntelToTextPanel(rebel, text);
		}
		
		//FireAll.fire(null, dialog, memoryMap, "MarketPostOpen");
		dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$menuState", "main", 0);
		if (market.isPlanetConditionMarketOnly()) {
			dialog.getInteractionTarget().getMemoryWithoutUpdate().unset("$hasMarket");
		}
		else
			dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$tradeMode", "OPEN", 0);
		
		if (tempInvasion.success) {
			((RuleBasedDialog)dialog.getPlugin()).updateMemory();
			FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
		}
		
		else
			dialog.dismiss();
	}
	
	protected void invadeShowLoot() {
		dialog.getVisualPanel().showLoot(Misc.ucFirst(StringHelper.getString("spoils")),
				tempInvasion.invasionLoot, false, true, true, new CoreInteractionListener() {
			public void coreUIDismissed() {
				//dialog.dismiss();
				finishedInvade();
			}
		});
	}
	
	public TempData getTempData() {
		return temp;
	}
	
	// Changes from vanilla: Checks blueprint cooldown; externalized strings
	@Override
	protected void raidValuable() {
		temp.raidType = RaidType.VALUABLE;
		
		temp.raidValuables = computeRaidValuables();
		
		boolean withBP = false;
		boolean withCores = false;
		boolean heavyIndustry = false;
		for (Industry curr : market.getIndustries()) {
			if (curr.getSpec().hasTag(Industries.TAG_USES_BLUEPRINTS)) {
				withBP = true;
			}
			if (curr.getSpec().hasTag(Industries.TAG_HEAVYINDUSTRY)) {
				heavyIndustry = true;
			}
			if (curr.getAICoreId() != null) {
				withCores = true;
			}
		}
		boolean military = market.getMemoryWithoutUpdate().getBoolean(MemFlags.MARKET_MILITARY);
		
		// prevent an IllegalAccessError
		final Map<CommodityOnMarketAPI, Float> raidValuablesCopy = new HashMap<>(temp.raidValuables);
		
		List<CommodityOnMarketAPI> sorted = new ArrayList<CommodityOnMarketAPI>(temp.raidValuables.keySet());
		Collections.sort(sorted, new Comparator<CommodityOnMarketAPI>() {
			public int compare(CommodityOnMarketAPI o1, CommodityOnMarketAPI o2) {
				return (int) Math.signum(raidValuablesCopy.get(o2) - raidValuablesCopy.get(o1));
			}
		});
		
		if (sorted.isEmpty()) {
			text.addPara(StringHelper.getString("nex_raidDialog", "expectedSpoilsNone"));
			addNeverMindOption();
			return;
		}
		
		int count = 0;
		List<String> names = new ArrayList<String>();
		List<CommodityOnMarketAPI> coms = new ArrayList<CommodityOnMarketAPI>();
		for (CommodityOnMarketAPI com : sorted) {
			names.add(com.getCommodity().getLowerCaseName());
			coms.add(com);
			count++;
			if (count >= 5) break;
		}
		
		String list = Misc.getAndJoined(names);
		
		text.addPara(StringHelper.getString("nex_raidDialog", "expectedSpoilsNone"));
		
		String is = "is";
		String item = "item";
		if (names.size() > 1) {
			is = "are";
			item = "items";
		}
		
		Color h = Misc.getHighlightColor();
		float targetValue = getBaseRaidValue();
		targetValue = Misc.getRounded(targetValue);
		
		String [] highlights = new String[names.size() + 1];
		for (int i = 0; i < names.size(); i++) {
			if (i == 0) {
				highlights[i] = Misc.ucFirst(names.get(i));
			} else {
				highlights[i] = names.get(i);
			}
		}
		highlights[highlights.length - 1] = Misc.getDGSCredits(targetValue);
		
//		heavyIndustry = true;
//		withBP = true;
		
//		text.addPara(Misc.ucFirst(list) + " " + is + " the most likely " + item + 
//				" to be obtained. The estimated value is projected to be around "
//				+ highlights[highlights.length - 1] + ".", 
//				h, highlights);
		
		ResourceCostPanelAPI cost = text.addCostPanel(StringHelper.getString("nex_raidDialog", "expectedSpoilsHeader", true), 
				SalvageEntity.COST_HEIGHT, playerFaction.getBaseUIColor(), playerFaction.getDarkUIColor());
		//cost.setNumberOnlyMode(true);
		cost.setWithBorder(false);
		cost.setAlignment(Alignment.LMID);
		cost.setComWidthOverride(SalvageEntity.COST_HEIGHT);
		for (CommodityOnMarketAPI com: coms) {
			cost.addCost(com.getId(), "");
		}
		cost.update();
		
		String extra = "";
		if (temp.shortageMult < 0.75f) {
			extra = " " + StringHelper.getString("nex_raidDialog", "expectedSpoilsValueReduced");
		}
		text.addPara(StringHelper.getString("nex_raidDialog", "expectedSpoilsValue") + " "
				+ highlights[highlights.length - 1] + "." + extra, 
				h, highlights[highlights.length - 1]);
		
		String weps = StringHelper.getString("nex_raidDialog", "spoilsItemWeapons");
		String modspecs = StringHelper.getString("nex_raidDialog", "spoilsItemModspecs");
		String blueprints = StringHelper.getString("nex_raidDialog", "spoilsItemBlueprints");
		String aiCores = StringHelper.getString("nex_raidDialog", "spoilsItemAICores");
		
		if (heavyIndustry && withBP) {
			text.addPara(StringHelper.getString("nex_raidDialog", "spoilsHIandBP"), h,
						 weps, modspecs, blueprints);
		} else if (withBP) {
			text.addPara(StringHelper.getString("nex_raidDialog", "spoilsBP"), h,
						 blueprints);
		} else if (heavyIndustry) {
			// shouldn't happen in vanilla since heavy industry has the "uses_blueprints" tag
			text.addPara(StringHelper.getString("nex_raidDialog", "spoilsHI"), h,
						 weps, modspecs);
		} else if (military) {
			text.addPara(StringHelper.getString("nex_raidDialog", "spoilsMilitary"), h,
						 weps, modspecs);
		} else {
		}
		
		boolean bpCooldown = market.getMemoryWithoutUpdate().getBoolean(MEMORY_KEY_BP_COOLDOWN);
		if (heavyIndustry && withBP && bpCooldown) {
			String str1 = StringHelper.getString("nex_raidDialog", "spoilsNoBlueprints");
			String str2 = StringHelper.getString("nex_raidDialog", "spoilsNoBlueprints2");
			text.addPara(str1 + " " + str2, Misc.getNegativeHighlightColor(), str2);
		}
		
		if (withCores) {
			text.addPara(StringHelper.getString("nex_raidDialog", "spoilsAICores"), h, aiCores);
		}
		
		
		text.addPara(StringHelper.getString("nex_raidDialog", "dialogConfirmText"));
		
		addConfirmOptions();
	}
	
	
	
	public static int getBombardDisruptDuration(BombardType type) {
		float dur = Global.getSettings().getFloat("bombardDisruptDuration");
		if (type == BombardType.TACTICAL)
			dur *= TACTICAL_BOMBARD_DISRUPT_MULT;
		return (int) dur;
	}
	
	public static int getBombardmentCost(MarketAPI market, CampaignFleetAPI fleet, BombardType type) {
		float str = getDefenderStr(market);
		int result = (int) (str * Global.getSettings().getFloat("bombardFuelFraction"));
		if (result < 2) result = 2;
		if (fleet != null) {
			float bomardBonus = Misc.getFleetwideTotalMod(fleet, Stats.FLEET_BOMBARD_COST_REDUCTION, 0f);
			result -= bomardBonus;
			if (result < 0) result = 0;
		}
		if (type == BombardType.TACTICAL)
			result *= TACTICAL_BOMBARD_FUEL_MULT;
		
		return result;
	}
	
	// Difference from vanilla: No insta-hostile if target is vengeful or market is small
	@Override
	protected void bombardSaturation() {
		
		temp.bombardType = BombardType.SATURATION;
		temp.bombardCost = getBombardmentCost(market, playerFleet, temp.bombardType);

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
		
		int fuel = (int) playerFleet.getCargo().getFuel();
		if (destroy) {
			text.addPara(StringHelper.getString("nex_bombardment", "satBombDescDestroy"));
		} else {
			text.addPara(StringHelper.getString("nex_bombardment", "satBombDesc"));
		}		
		
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
	
	// Changes from vanilla: Custom rep handling for sat bomb; don't pollute habitable worlds; 
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
			if (temp.bombardType == BombardType.TACTICAL) {
				Misc.increaseMarketHostileTimeout(market, 60f);
			} else {
				Misc.increaseMarketHostileTimeout(market, 120f);
			}
		}
		
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
	
		int stabilityPenalty = getTacticalBombardmentStabilityPenalty();
		if (temp.bombardType == BombardType.SATURATION) {
			stabilityPenalty = getSaturationBombardmentStabilityPenalty();
		}
		boolean destroy = temp.bombardType == BombardType.SATURATION && market.getSize() <= getBombardDestroyThreshold();
		
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
		
		if (temp.bombardType == BombardType.SATURATION && market.hasCondition(Conditions.HABITABLE) && !market.hasCondition(Conditions.POLLUTION)) {
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
				
				// move this to outside the if check; see http://fractalsoftworks.com/forum/index.php?topic=15628.msg252000#msg252000
				// ListenerUtil.reportSaturationBombardmentFinished(dialog, market, temp);
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

	/**
	 * Was this market originally owned by the player?
	 * @return
	 */
	protected boolean wasPlayerMarket() {
		String origOwner = ExerelinUtilsMarket.getOriginalOwner(market);
		boolean originallyPlayer = origOwner == null || origOwner.equals(Factions.PLAYER);
		return originallyPlayer;
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

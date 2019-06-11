package com.fs.starfarer.api.impl.campaign.rulecmd.salvage;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
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
import com.fs.starfarer.api.ui.LabelAPI;
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
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
	
	private void initForInvasion(SectorEntityToken entity) {
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
				text.addPara("The colony has no orbital station or nearby fleets to defend it.");
			} else {
				printStationState();
				text.addPara("There are no nearby fleets to defend the colony.");
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
					String name = "An orbital station";
					if (station != null) {
						FleetMemberAPI flagship = station.getFlagship();
						if (flagship != null) {
							name = flagship.getVariant().getDesignation().toLowerCase();
							stationType = name;
							name = Misc.ucFirst(station.getFaction().getPersonNamePrefixAOrAn()) + " " + 
									station.getFaction().getPersonNamePrefix() + " " + name;
						}
					}
					text.addPara(name + " dominates the orbit and prevents any " +
								 "hostile action, aside from a quick raid, unless it is dealt with.");
					
					
					if (hasNonStation) {
						text.addPara("The defending ships present are, with the support of the station, sufficient to prevent " +
									 "raiding as well.");
					}
				} else if (hasNonStation && otherWantsToFight) {
					printStationState();
					text.addPara("Defending ships are present in sufficient strength to prevent any hostile action " +
					"until they are dealt with.");
				} else if (hasNonStation && !otherWantsToFight) {
					printStationState();
					text.addPara("Defending ships are present, but not in sufficient strength " +
								 "to want to give battle or prevent any hostile action you might take.");
				}
				
				plugin.printOngoingBattleInfo();
			}
		}
			
		options.clearOptions();
		
		String engageText = "Engage the defenders";
		
		if (playerCanNotJoin) {
			engageText = "Engage the defenders";
		} else if (playerOnDefenderSide) {
			if (hasStation && hasNonStation) {
				engageText = "Aid the " + stationType + " and its defenders";
			} else if (hasStation) {
				engageText = "Aid the " + stationType + "";
			} else {
				engageText = "Aid the defenders";
			}
		} else {
			if (ongoingBattle) {
				engageText = "Aid the attacking forces";
			} else {
				if (hasStation && hasNonStation) {
					engageText = "Engage the " + stationType + " and its defenders";
				} else if (hasStation) {
					engageText = "Engage the " + stationType + "";
				} else {
					engageText = "Engage the defenders";
				}
			}
		}
		
		
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
		options.addOption("Launch a raid against " + market.getName(), RAID);
		options.addOption("Consider an orbital bombardment of " + market.getName(), BOMBARD);
		
		if (!temp.canRaid) {
			options.setEnabled(RAID, false);
			options.setTooltip(RAID, "The presence of enemy fleets that are willing to offer battle makes a raid impossible.");
		}
		
		if (!temp.canBombard) {
			options.setEnabled(BOMBARD, false);
			options.setTooltip(BOMBARD, "All defenses must be defeated to make a bombardment possible.");
		}
		
		//DEBUG = false;
		if (temp.canRaid && getRaidCooldown() > 0) {// && couldRaidIfNotDebug) {
			if (!DebugFlags.MARKET_HOSTILITIES_DEBUG) {
				options.setEnabled(RAID, false);
				text.addPara("Your forces will be able to organize another raid within a day or so.");
				temp.canRaid = false;
			} else {
				text.addPara("Your forces will be able to organize another raid within a day or so.");
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
				options.addOptionConfirmation(ENGAGE,
						"The " + nonHostile.getDisplayNameLong() + 
						" " + nonHostile.getDisplayNameIsOrAre() + 
						" not currently hostile, and you have been positively identified. " +
						"Are you sure you want to engage in open hostilities?", "Yes", "Never mind");
			}
		} else if (context == null || playerCanNotJoin || !otherWantsToFight) {
			options.setEnabled(ENGAGE, false);
		}
		
		if (InvasionRound.canInvade(entity))
		{
			options.addOption("Invade the market", INVADE);
			
			// attempt to reorder
			// don't do this; it'll forget which options are disabled
			/*
			List opts = options.getSavedOptionList();
			Object lastOpt = opts.get(opts.size() - 1);
			opts.remove(lastOpt);
			opts.add(opts.size() - 1, lastOpt);
			options.restoreSavedOptions(opts);
			options.setShortcut(GO_BACK, Keyboard.KEY_ESCAPE, false, false, false, true);	// put back the hotkey
			*/
			
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
		
		options.addOption("Go back", GO_BACK);
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
		
		StatBonus attackerBase = new StatBonus(); 
		StatBonus defenderBase = new StatBonus(); 
		
		//defenderBase.modifyFlatAlways("base", baseDef, "Base value for a size " + market.getSize() + " colony");
		
		attackerBase.modifyFlatAlways("core_marines", marines, getString("marinesOnBoard", true));
		attackerBase.modifyFlatAlways("core_support", support, getString("groundSupportCapability", true));
		
		ExerelinFactionConfig atkConf = ExerelinConfig.getExerelinFactionConfig(PlayerFactionStore.getPlayerFactionId());
		String str = StringHelper.getStringAndSubstituteToken("exerelin_invasion", "attackBonus", "$Faction", 
				Misc.ucFirst(fleet.getFaction().getDisplayName()));
		attackerBase.modifyMult("nex_invasionAtkBonus", atkConf.invasionStrengthBonusAttack + 1, str);
		
		StatBonus attacker = playerFleet.getStats().getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD);
		StatBonus defender = market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD);
		
		ExerelinFactionConfig defConf = ExerelinConfig.getExerelinFactionConfig(market.getFactionId());
		str = StringHelper.getStringAndSubstituteToken("exerelin_invasion", "defendBonus", "$Faction", 
				Misc.ucFirst(market.getFaction().getDisplayName()));
		defender.modifyMult("nex_invasionDefBonus", defConf.invasionStrengthBonusDefend + 1, str);
		
		String increasedDefensesKey = "core_addedDefStr";
		float added = getDefenderIncreaseValue(market);
		if (added > 0) {
			defender.modifyFlat(increasedDefensesKey, added, getString("defenderPreparedness", true));
		}
		
		float attackerStr = (int) Math.round(attacker.computeEffective(attackerBase.computeEffective(0f)));
		float defenderStr = (int) Math.round(defender.computeEffective(defenderBase.computeEffective(0f)));
		
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
			
		defender.unmodifyFlat(increasedDefensesKey);
		
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
		
		if (result.losses <= 0) {
			text.addPara(getString("noLosses"));
		} else {
			playerFleet.getCargo().removeMarines(result.losses);
			tempInvasion.marinesLost = result.losses;
			AddRemoveCommodity.addCommodityLossText(Commodities.MARINES, result.losses, text);
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
		
		if (!tempInvasion.success)
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

			raidSpecialItemsReduced(result, random);

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
	
	/**
	 * Loot rare items, but not AI cores or stuff like nanoforge/synchrotron
	 * @param cargo
	 * @param random
	 */
	protected void raidSpecialItemsReduced(CargoAPI cargo, Random random) {
		float p = tempInvasion.invasionMult * 0.2f;
		
		boolean withBP = false;
		boolean heavyIndustry = false;
		for (Industry curr : market.getIndustries()) {
			if (curr.getSpec().hasTag(Industries.TAG_USES_BLUEPRINTS)) {
				withBP = true;
			}
			if (curr.getSpec().hasTag(Industries.TAG_HEAVYINDUSTRY)) {
				heavyIndustry = true;
			}
		}
		market.reapplyIndustries();
		
		boolean military = market.getMemoryWithoutUpdate().getBoolean(MemFlags.MARKET_MILITARY);
		
		String ship =    "MarketCMD_ship____";
		String weapon =  "MarketCMD_weapon__";
		String fighter = "MarketCMD_fighter_";
		
		// blueprints
		if (withBP) {
			WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>();
			for (String id : market.getFaction().getKnownShips()) {
				if (playerFaction.knowsShip(id)) continue;
				picker.add(ship + id, 1f);
			}
			for (String id : market.getFaction().getKnownWeapons()) {
				if (playerFaction.knowsWeapon(id)) continue;
				picker.add(weapon + id, 1f);
			}
			for (String id : market.getFaction().getKnownFighters()) {
				if (playerFaction.knowsFighter(id)) continue;
				picker.add(fighter + id, 1f);
			}
			
			int num = getNumPicks(random, tempInvasion.invasionMult + 0.5f, tempInvasion.invasionMult * 0.5f);
			for (int i = 0; i < num && !picker.isEmpty(); i++) {
				String id = picker.pickAndRemove();
				if (id == null) continue;
				
				if (id.startsWith(ship)) {
					String specId = id.substring(ship.length());
					if (Global.getSettings().getHullSpec(specId).hasTag(Tags.NO_BP_DROP)) continue;
					cargo.addSpecial(new SpecialItemData(Items.SHIP_BP, specId), 1);
				} else if (id.startsWith(weapon)) {
					String specId = id.substring(weapon.length());
					if (Global.getSettings().getWeaponSpec(specId).hasTag(Tags.NO_BP_DROP)) continue;
					cargo.addSpecial(new SpecialItemData(Items.WEAPON_BP, specId), 1);
				} else if (id.startsWith(fighter)) {
					String specId = id.substring(fighter.length());
					if (Global.getSettings().getFighterWingSpec(specId).hasTag(Tags.NO_BP_DROP)) continue;
					cargo.addSpecial(new SpecialItemData(Items.FIGHTER_BP, specId), 1);
				}
			}
		}
		
		// modspecs
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>();
		for (String id : market.getFaction().getKnownHullMods()) {
			if (playerFaction.knowsHullMod(id) && !DebugFlags.ALLOW_KNOWN_HULLMOD_DROPS) continue;
			picker.add(id, 1f);
		}
		
		// more likely to get at least one modspec, but not likely to get many
		int num = getNumPicks(random, tempInvasion.invasionMult + 0.5f, tempInvasion.invasionMult * 0.25f);
		for (int i = 0; i < num && !picker.isEmpty(); i++) {
			String id = picker.pickAndRemove();
			if (id == null) continue;
			cargo.addSpecial(new SpecialItemData(Items.MODSPEC, id), 1);
		}
		
		
		// weapons and fighters
		picker = new WeightedRandomPicker<String>();
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
		
		
		num = getNumPicks(random, tempInvasion.invasionMult + 0.5f, tempInvasion.invasionMult * 0.25f);
		if (military || heavyIndustry) {
			num += Math.round(market.getCommodityData(Commodities.SHIPS).getAvailable() * tempInvasion.invasionMult);
		}
		
		for (int i = 0; i < num && !picker.isEmpty(); i++) {
			String id = picker.pickAndRemove();
			if (id == null) continue;
			
			if (id.startsWith(weapon)) {
				cargo.addWeapons(id.substring(weapon.length()), 1);
			} else if (id.startsWith(fighter)) {
				cargo.addFighters(id.substring(fighter.length()), 1);
			}
		}
		
	}
	
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
	
	// Change from vanilla: handle fuel cost differences between tactical and strategic bombardment
	@Override
	protected void bombardMenu() {
		float width = 350;
		float opad = 10f;
		float small = 5f;
		
		Color h = Misc.getHighlightColor();
		Color b = Misc.getNegativeHighlightColor();
		
		dialog.getVisualPanel().showImagePortion("illustrations", "bombard_prepare", 640, 400, 0, 0, 480, 300);

		StatBonus defender = market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD);
		
		float bomardBonus = Misc.getFleetwideTotalMod(playerFleet, Stats.FLEET_BOMBARD_COST_REDUCTION, 0f);
		String increasedBombardKey = "core_addedBombard";
		StatBonus bombardBonusStat = new StatBonus();
		if (bomardBonus > 0) {
			bombardBonusStat.modifyFlat(increasedBombardKey, -bomardBonus, "Specialized fleet bombardment capability");
		}
		
		float defenderStr = (int) Math.round(defender.computeEffective(0f));
		defenderStr -= bomardBonus;
		if (defenderStr < 0) defenderStr = 0;
		
		temp.defenderStr = defenderStr;
		
		TooltipMakerAPI info = text.beginTooltip();
		
		info.setParaSmallInsignia();
		
		String has = faction.getDisplayNameHasOrHave();
		String is = faction.getDisplayNameIsOrAre();
		boolean hostile = faction.isHostileTo(Factions.PLAYER);
		boolean tOn = playerFleet.isTransponderOn();
		float initPad = 0f;
		if (!hostile) {
			if (tOn) {
				info.addPara(Misc.ucFirst(faction.getDisplayNameWithArticle()) + " " + is + 
						" not currently hostile. A bombardment is a major enough hostile action that it can't be concealed, " +
						"regardless of transponder status.",
						initPad, faction.getBaseUIColor(), faction.getDisplayNameWithArticleWithoutArticle());
			}
			initPad = opad;
		}

		info.addPara("Starship fuel can be easily destabilized, unlocking the destructive " +
				"potential of the antimatter it contains. Ground defenses can counter " +
				"a bombardment, though in practice it only means that more fuel is required to achieve " +
				"the same result.", initPad);
				
		
		if (bomardBonus > 0) {
			info.addPara("Effective ground defense strength: %s", opad, h, "" + (int)defenderStr);
		} else {
			info.addPara("Ground defense strength: %s", opad, h, "" + (int)defenderStr);
		}
		info.addStatModGrid(width, 50, opad, small, defender, true, statPrinter(true));
		if (!bombardBonusStat.isUnmodified()) {
			info.addStatModGrid(width, 50, opad, 3f, bombardBonusStat, true, statPrinter(false));
		}
		
		text.addTooltip();

//		text.addPara("A tactical bombardment will only hit military targets and costs less fuel. A saturation " +
//				"bombardment will devastate the whole colony, and only costs marginally more fuel, as the non-military " +
//				"targets don't have nearly the same degree of hardening.");
		
		int costTac = getBombardmentCost(market, playerFleet, BombardType.TACTICAL);
		int costSat = getBombardmentCost(market, playerFleet, BombardType.SATURATION);
		temp.bombardCost = costSat;
		
		int fuel = (int) playerFleet.getCargo().getFuel();
		boolean canBombard = fuel > temp.bombardCost;
		boolean canBombardTac = fuel >= costTac;
		boolean canBombardSat = fuel >= costSat;
		
		/*
		LabelAPI label = text.addPara("A tactical bombardment requires %s fuel. A saturation bombardment requires %s fuel. " +
									  "You have %s fuel.",
				h, "" + costTac, "" + costSat, "" + fuel);
		label.setHighlight("" + costTac, "" + costSat, "" + fuel);
		label.setHighlightColors(canBombardTac ? h : b, canBombardSat ? h : b, h);
		*/
		
		LabelAPI label = text.addPara("A bombardment requires %s fuel. " +
									  "You have %s fuel.",
				h, "" + temp.bombardCost, "" + fuel);
		label.setHighlight("" + temp.bombardCost, "" + fuel);
		label.setHighlightColors(canBombard ? h : b, h);

		options.clearOptions();
		
		options.addOption("Prepare a tactical bombardment", BOMBARD_TACTICAL);
		options.addOption("Prepare a saturation bombardment", BOMBARD_SATURATION);
		
		if (DebugFlags.MARKET_HOSTILITIES_DEBUG) {
			canBombardTac = true;
			canBombardSat = true;
		}
		if (!canBombardTac) {
			options.setEnabled(BOMBARD_TACTICAL, false);
			options.setTooltip(BOMBARD_TACTICAL, "Not enough fuel.");
		}
		if (!canBombardSat) {
			options.setEnabled(BOMBARD_SATURATION, false);
			options.setTooltip(BOMBARD_SATURATION, "Not enough fuel.");
		}

		options.addOption("Go back", RAID_GO_BACK);
		options.setShortcut(RAID_GO_BACK, Keyboard.KEY_ESCAPE, false, false, false, true);
	}
	
	// Changes from vanilla: Uses custom disruption time and fuel costs
	@Override
	protected void bombardTactical() {
		
		temp.bombardType = BombardType.TACTICAL; 
		temp.bombardCost = getBombardmentCost(market, playerFleet, temp.bombardType);
		
		boolean hostile = faction.isHostileTo(Factions.PLAYER);
		temp.willBecomeHostile.clear();
		temp.willBecomeHostile.add(faction);
		
		float opad = 10f;
		float small = 5f;
		
		Color h = Misc.getHighlightColor();
		Color b = Misc.getNegativeHighlightColor();
		
		
		int dur = getBombardDisruptDuration(temp.bombardType);
		
		List<Industry> targets = new ArrayList<Industry>();
		for (Industry ind : market.getIndustries()) {
			if (ind.getSpec().hasTag(Industries.TAG_TACTICAL_BOMBARDMENT)) {
				if (ind.getDisruptedDays() >= dur * 0.8f) continue;
				targets.add(ind);
			}
		}
		temp.bombardmentTargets.clear();
		temp.bombardmentTargets.addAll(targets);
		
		if (targets.isEmpty()) {
			text.addPara(market.getName() + " does not have any undisrupted military targets that would be affected by a tactical bombardment.");
			addBombardNeverMindOption();
			return;	
		}
		
		int fuel = (int) playerFleet.getCargo().getFuel();
		text.addPara("A tactical bombardment will destabilize the colony, and will also disrupt the " +
				"following military targets for approximately %s days:",
					 h, "" + dur);
		
		TooltipMakerAPI info = text.beginTooltip();
		
		info.setParaSmallInsignia();
		info.setParaFontDefault();
		
		info.setBulletedListMode(BaseIntelPlugin.INDENT);
		float initPad = 0f;
		for (Industry ind : targets) {
			//info.addPara(ind.getCurrentName(), faction.getBaseUIColor(), initPad);
			info.addPara(ind.getCurrentName(), initPad);
			initPad = 3f;
		}
		info.setBulletedListMode(null);
		
		text.addTooltip();
		
		text.addPara("The bombardment requires %s fuel. " +
					 "You have %s fuel.",
					 h, "" + Math.round(temp.bombardCost),
					 "" + fuel);
		
		addBombardConfirmOptions();
	}
	
	// Difference from vanilla: No insta-hostile if target is vengeful or market is small
	@Override
	protected void bombardSaturation() {
		
		temp.bombardType = BombardType.SATURATION;
		temp.bombardCost = getBombardmentCost(market, playerFleet, temp.bombardType);

		temp.willBecomeHostile.clear();
		temp.willBecomeHostile.add(faction);
		
		List<FactionAPI> nonHostile = new ArrayList<FactionAPI>();
		List<FactionAPI> vengeful = new ArrayList<>();
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
			text.addPara("A saturation bombardment of a colony this size will destroy it utterly.");
		} else {
			text.addPara("A saturation bombardment will destabilize the colony, reduce its population, " +
					"and disrupt all operations for a long time.");
		}		

		if (nonHostile.isEmpty()) {
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
		
		text.addPara("The bombardment requires %s fuel. " +
					 "You have %s fuel.",
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
			String reason = "Recently bombarded";
			if (Misc.isPlayerFactionSetUp()) {
				reason = playerFaction.getDisplayName() + " bombardment";
			}
			RecentUnrest.get(market).add(stabilityPenalty, reason);
			text.addPara("Stability of " + market.getName() + " reduced by %s.",
					Misc.getHighlightColor(), "" + stabilityPenalty);
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
			text.addPara("Military operations disrupted.");
			
			ListenerUtil.reportTacticalBombardmentFinished(dialog, market, temp);
		} else if (temp.bombardType == BombardType.SATURATION) {
			if (destroy) {
				DecivTracker.decivilize(market, true);
				text.addPara(market.getName() + " destroyed.");
			} else {
				int prevSize = market.getSize();
				CoreImmigrationPluginImpl.reduceMarketSize(market);
				if (prevSize == market.getSize()) {
					text.addPara("All operations disrupted.");
				} else {
					text.addPara("All operations disrupted. Colony size reduced to %s.", 
							Misc.getHighlightColor()
							, "" + market.getSize());
				}
				
				ListenerUtil.reportSaturationBombardmentFinished(dialog, market, temp);
			}
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
	
	// this was just to debug Tiandong
	/*
	@Override
	protected int getNumPicks(Random random, float pAny, float pMore) {
		return 20;
	}
	*/

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
	
	public static class TempDataInvasion {
		public boolean canInvade;
		public int marinesLost = 0;
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

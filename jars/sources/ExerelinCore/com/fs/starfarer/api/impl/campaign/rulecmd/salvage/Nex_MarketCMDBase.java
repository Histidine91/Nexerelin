package com.fs.starfarer.api.impl.campaign.rulecmd.salvage;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CoreInteractionListener;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.IndustryPickerListener;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ResourceCostPanelAPI;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.BattleAPI.BattleSide;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.combat.MutableStat.StatMod;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.BaseFIDDelegate;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfig;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.ShowDefaultVisual;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.BombardType;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RaidType;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.StatModValueGetter;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.utilities.StringHelper;

/**
 * This is just base game MarketCMD, but with public TempData variables and protected bombardment methods
 * Doesn't work, the listener relies on MarketCMD.TempData which is completely useless for inheritance
 */
public class Nex_MarketCMDBase extends BaseCommandPlugin {
	
	public static String ENGAGE = "mktEngage";
	
	public static String RAID = "mktRaid";
	//public static String RAID_RARE = "mktRaidRare";
	public static String RAID_VALUABLE = "mktRaidValuable";
	public static String RAID_DISRUPT = "mktRaidDisrupt";
	public static String RAID_GO_BACK = "mktRaidGoBack";
	
	public static String RAID_CONFIRM = "mktRaidConfirm";
	public static String RAID_NEVER_MIND = "mktRaidNeverMind";
	public static String RAID_RESULT = "mktRaidResult";
	
	public static String INVADE = "mktInvade";
	public static String GO_BACK = "mktGoBack";
	
	public static String BOMBARD = "mktBombard";
	public static String BOMBARD_TACTICAL = "mktBombardTactical";
	public static String BOMBARD_SATURATION = "mktBombardSaturation";
	public static String BOMBARD_CONFIRM = "mktBombardConfirm";
	public static String BOMBARD_NEVERMIND = "mktBombardNeverMind";
	public static String BOMBARD_RESULT = "mktBombardResult";
	
	public static String DEBT_RESULT_CONTINUE = "marketCmd_checkDebtContinue";
	
	
	
	
	public static float FAIL_THRESHOLD = 0.4f;
	
	protected CampaignFleetAPI playerFleet;
	protected SectorEntityToken entity;
	protected FactionAPI playerFaction;
	protected FactionAPI entityFaction;
	protected TextPanelAPI text;
	protected OptionPanelAPI options;
	protected CargoAPI playerCargo;
	protected MemoryAPI memory;
	protected MarketAPI market;
	protected InteractionDialogAPI dialog;
	protected Map<String, MemoryAPI> memoryMap;
	protected FactionAPI faction;

	protected TempData temp = new TempData();
	
	public Nex_MarketCMDBase() {
	}
	
	protected void clearTemp() {
		if (temp != null) {
			temp.raidType = null;
			temp.bombardType = null;
			temp.raidLoot = null;
			temp.raidValuables = null;
			temp.target = null;
			temp.willBecomeHostile.clear();
			temp.bombardmentTargets.clear();
			//temp.canFail = false;
			//temp.failProb = 0f;
		}
	}
	
	public Nex_MarketCMDBase(SectorEntityToken entity) {
		init(entity);
	}
	
	protected void init(SectorEntityToken entity) {
		memory = entity.getMemoryWithoutUpdate();
		this.entity = entity;
		playerFleet = Global.getSector().getPlayerFleet();
		playerCargo = playerFleet.getCargo();
		
		playerFaction = Global.getSector().getPlayerFaction();
		entityFaction = entity.getFaction();
		
		faction = entity.getFaction();
		
		market = entity.getMarket();
		
		//DebugFlags.MARKET_HOSTILITIES_DEBUG = false;
		//market.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PLAYER_HOSTILE_ACTIVITY_NEAR_MARKET, true, 0.1f);
		
		String key = "$MarketCMD_temp";
		MemoryAPI mem = market.getMemoryWithoutUpdate();
		if (mem.contains(key)) {
			temp = (TempData) mem.get(key);
		} else {
			mem.set(key, temp, 0f);
		}
	}

	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		this.dialog = dialog;
		this.memoryMap = memoryMap;
		
		String command = params.get(0).getString(memoryMap);
		if (command == null) return false;
		
		entity = dialog.getInteractionTarget();
		init(entity);
		
		memory = getEntityMemory(memoryMap);
		
		text = dialog.getTextPanel();
		options = dialog.getOptionPanel();
		
		if (command.equals("showDefenses")) {
			clearTemp();
			//new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);
			showDefenses(true);
		} else if (command.equals("goBackToDefenses")) {
			clearTemp();
			//new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);
			showDefenses(true);
			//dialog.getVisualPanel().finishFadeFast();
		} else if (command.equals("engage")) {
			engage();
		} else if (command.equals("raidMenu")) {
			raidMenu();
//		} else if (command.equals("raidRare")) {
//			raidRare();
		} else if (command.equals("raidValuable")) {
			raidValuable();
		} else if (command.equals("raidDisrupt")) {
			raidDisrupt();
		} else if (command.equals("raidConfirm")) {
			raidConfirm();
		} else if (command.equals("raidNeverMind")) {
			raidNeverMind();
		} else if (command.equals("raidResult")) {
			raidResult();
		} else if (command.equals("bombardMenu")) {
			bombardMenu();
		} else if (command.equals("bombardTactical")) {
			bombardTactical();
		} else if (command.equals("bombardSaturation")) {
			bombardSaturation();
		} else if (command.equals("bombardConfirm")) {
			bombardConfirm();
		} else if (command.equals("bombardNeverMind")) {
			bombardNeverMind();
		} else if (command.equals("bombardResult")) {
			bombardResult();
		} else if (command.equals("checkDebtEffect")) {
			return checkDebtEffect();
		} else if (command.equals("applyDebtEffect")) {
			applyDebtEffect();
		}
		
		return true;
	}

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
			
			FIDConfig params = new FIDConfig();
			params.justShowFleets = true;
			params.showPullInText = withText;
			plugin = new FleetInteractionDialogPluginImpl(params);
			dialog.setInteractionTarget(primary);
			plugin.init(dialog);
			dialog.setInteractionTarget(entity);
			
			
			context = (FleetEncounterContext)plugin.getContext();
			b = context.getBattle();
			
			BattleSide playerSide = b.pickSide(playerFleet);
			playerCanNotJoin = playerSide == BattleSide.NO_JOIN;
			if (!playerCanNotJoin) {
				playerOnDefenderSide = b.getSide(playerSide) == b.getSideFor(primary);
			}
			if (!ongoingBattle) {
				playerOnDefenderSide = false;
			}

			if (playerSide != BattleSide.NO_JOIN) {
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
		
		options.addOption("Go back", GO_BACK);
		options.setShortcut(GO_BACK, Keyboard.KEY_ESCAPE, false, false, false, true);
		
		
		if (plugin != null) {
			plugin.cleanUpBattle();
		}
		
	}
	
	public static float getRaidStr(CampaignFleetAPI fleet) {
		float attackerStr = fleet.getCargo().getMaxPersonnel() * 0.25f;
		float support = Misc.getFleetwideTotalMod(fleet, Stats.FLEET_GROUND_SUPPORT, 0f);
		attackerStr += Math.min(support, attackerStr);
		
		StatBonus stat = fleet.getStats().getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD);
		attackerStr = stat.computeEffective(attackerStr);
		
		return attackerStr;
	}
	public static float getDefenderStr(MarketAPI market) {
		StatBonus stat = market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD);
		float defenderStr = (int) Math.round(stat.computeEffective(0f));
		return defenderStr;
	}
	
	public static float getRaidEffectiveness(MarketAPI market, CampaignFleetAPI fleet) {
		return getRaidEffectiveness(market, getRaidStr(fleet));
	}
	public static float getRaidEffectiveness(MarketAPI market, float attackerStr) {
		float defenderStr = getDefenderStr(market);
		return attackerStr / Math.max(1f, (attackerStr + defenderStr));
	}
	
	protected void raidMenu() {
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
		
		attackerBase.modifyFlatAlways("core_marines", marines, "Marines on board");
		attackerBase.modifyFlatAlways("core_support", support, "Fleet capability for ground support");
		
		StatBonus attacker = playerFleet.getStats().getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD);
		StatBonus defender = market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD);
		
		String increasedDefensesKey = "core_addedDefStr";
		float added = getDefenderIncreaseValue(market);
		if (added > 0) {
			defender.modifyFlat(increasedDefensesKey, added, "Increased defender preparedness");
		}
		
		float attackerStr = (int) Math.round(attacker.computeEffective(attackerBase.computeEffective(0f)));
		float defenderStr = (int) Math.round(defender.computeEffective(defenderBase.computeEffective(0f)));
		
		temp.attackerStr = attackerStr;
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
						" not currently hostile. Your fleet's transponder is on, and carrying out a raid " +
						"will result in open hostilities.",
						initPad, faction.getBaseUIColor(), faction.getDisplayNameWithArticleWithoutArticle());
			} else {
				info.addPara(Misc.ucFirst(faction.getDisplayNameWithArticle()) + " " + is + 
						" not currently hostile. Your fleet's transponder is off, and carrying out a raid " +
						"will only result in a minor penalty to your standing.",
						initPad, faction.getBaseUIColor(), faction.getDisplayNameWithArticleWithoutArticle());
			}
			initPad = opad;
		}
		
		float sep = small;
		sep = 3f;
		info.addPara("Raid strength: %s", initPad, h, "" + (int)attackerStr);
		info.addStatModGrid(width, 50, opad, small, attackerBase, true, statPrinter(false));
		if (!attacker.isUnmodified()) {
			info.addStatModGrid(width, 50, opad, sep, attacker, true, statPrinter(true));
		}
		
		
		info.addPara("Ground defense strength: %s", opad, h, "" + (int)defenderStr);
		//info.addStatModGrid(width, 50, opad, small, defenderBase, true, statPrinter());
		//if (!defender.isUnmodified()) {
			info.addStatModGrid(width, 50, opad, small, defender, true, statPrinter(true));
		//}
			
		defender.unmodifyFlat(increasedDefensesKey);
		
		text.addTooltip();

		boolean hasForces = true;
		boolean canDisrupt = true;
		temp.raidMult = attackerStr / Math.max(1f, (attackerStr + defenderStr));
		//temp.raidMult = 1f;
		
		
		
		if (temp.raidMult < 0.01f) {
			text.addPara("You do not have the forces to carry out an effective raid.");
			hasForces = false;
		} else {
			//temp.failProb = 0f;
			Color eColor = h;
			if (temp.raidMult < FAIL_THRESHOLD) {
				eColor = Misc.getNegativeHighlightColor();
				canDisrupt = false;
				//temp.canFail = true;
			} else if (temp.raidMult >= 0.7f) {
				eColor = Misc.getPositiveHighlightColor();
			}
//			text.addPara("Projected raid effectiveness: %s. " +
//					"This will determine the outcome of the raid, " +
//					"as well as the casualties suffered by your forces, if any.",
//					eColor,
//					"" + (int)Math.round(temp.raidMult * 100f) + "%");
			text.addPara("Projected raid effectiveness: %s",
					eColor,
					"" + (int)Math.round(temp.raidMult * 100f) + "%");
			if (!canDisrupt) {
				text.addPara("The ground defenses are too strong for your forces to be able to cause long-term disruption.");
			}
//			if (canDisrupt) {
//			} else {
//				text.addPara("Projected raid effectiveness: %s. " +
//						"This will determine the outcome of the raid, " +
//						"as well as the casualties suffered by your forces, if any.",
//						eColor,
//						"" + (int)Math.round(temp.raidMult * 100f) + "%");
//			}
		}
		
		if (DebugFlags.MARKET_HOSTILITIES_DEBUG) {
			canDisrupt = true;
		}
		
		options.clearOptions();
		
		//options.addOption("Try to acquire rare items, such as blueprints", RAID_RARE);
		options.addOption("Try to acquire valuables, such as commodities, blueprints, and other items", RAID_VALUABLE);
		options.addOption("Disrupt the operations of a specific industry or facility", RAID_DISRUPT);
		
		if (!hasForces) {
			options.setEnabled(RAID_VALUABLE, false);
			//options.setEnabled(RAID_RARE, false);
		}
		
		if (!hasForces || !canDisrupt) {
			options.setEnabled(RAID_DISRUPT, false);
			if (!canDisrupt) {
				String pct = "" + (int)Math.round(FAIL_THRESHOLD * 100f) + "%";
				options.setTooltip(RAID_DISRUPT, "Requires at least " + pct + " raid effectiveness.");
				options.setTooltipHighlights(RAID_DISRUPT, pct);
				options.setTooltipHighlightColors(RAID_DISRUPT, h);
			}
		}
	
		options.addOption("Go back", RAID_GO_BACK);
		options.setShortcut(RAID_GO_BACK, Keyboard.KEY_ESCAPE, false, false, false, true);
	}
	
//	protected void raidRare() {
//		
//	}
	
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
		
		List<CommodityOnMarketAPI> sorted = new ArrayList<CommodityOnMarketAPI>(temp.raidValuables.keySet());
		Collections.sort(sorted, new Comparator<CommodityOnMarketAPI>() {
			public int compare(CommodityOnMarketAPI o1, CommodityOnMarketAPI o2) {
				return (int) Math.signum(temp.raidValuables.get(o2) - temp.raidValuables.get(o1));
			}
		});
		
		if (sorted.isEmpty()) {
			text.addPara("After careful consideration, there do not appear to be any targets likely to yield much of value.");
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
		
		text.addPara("The commander of your ground forces designates several warehouses and " +
		 			 "other locations most likely to yield valuable items.");
		
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
		
		ResourceCostPanelAPI cost = text.addCostPanel("Expected spoils", 
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
			extra = " The value is lower than it otherwise might be due to the colony suffering from various shortages.";
		}
		text.addPara("The estimated value of the common commodities obtained is projected to be around "
				+ highlights[highlights.length - 1] + "." + extra, 
				h, highlights[highlights.length - 1]);
		
		if (heavyIndustry && withBP) {
			text.addPara("In addition, the colony has well-developed heavy industry, increasing the possibility of " +
						 "obtaining some ship weapons and modspecs, as well as blueprints.", h,
						 "ship weapons", "modspecs", "blueprints");
		} else if (heavyIndustry) {
			// shouldn't happen in vanilla since heavy industry has the "uses_blueprints" tag
			text.addPara("In addition, the colony has well-developed heavy industry, increasing the possibility of " +
						 "obtaining some ship weapons and modspecs.", h,
						 "ship weapons", "modspecs");
		} else if (military) {
			text.addPara("In addition, the colony has a military presence, increasing the possibility of " +
						 "obtaining some ship weapons and modspecs.", h,
						 "ship weapons", "modspecs");
		} else {
		}
		
		if (withCores) {
			text.addPara("There are also trace indications of AI cores being used, which might be obtained as well.", h, "AI cores");
		}
		
		
		text.addPara("Your forces are ready to go, awaiting your final confirmation.");
		
		addConfirmOptions();
	}
	
	protected void addBombardConfirmOptions() {
		options.clearOptions();
		options.addOption("Launch bombardment", BOMBARD_CONFIRM);
		options.addOption("Never mind", BOMBARD_NEVERMIND);
		options.setShortcut(BOMBARD_NEVERMIND, Keyboard.KEY_ESCAPE, false, false, false, true);
		
		List<FactionAPI> nonHostile = new ArrayList<FactionAPI>();
		for (FactionAPI faction : temp.willBecomeHostile) {
			boolean hostile = faction.isHostileTo(Factions.PLAYER);
			if (!hostile) {
				nonHostile.add(faction);
			}
		}
		
		if (nonHostile.size() == 1) {
			FactionAPI faction = nonHostile.get(0);
			options.addOptionConfirmation(BOMBARD_CONFIRM,
					"The " + faction.getDisplayNameLong() + 
					" " + faction.getDisplayNameIsOrAre() + 
					" not currently hostile, and will become hostile if you carry out the bombardment. " +
					"Are you sure?", "Yes", "Never mind");
		} else if (nonHostile.size() > 1) {
			options.addOptionConfirmation(BOMBARD_CONFIRM,
					"Multiple factions that are not currently hostile " +
					"will become hostile if you carry out the bombardment. " +
					"Are you sure?", "Yes", "Never mind");
		}
	}
	
	protected void raidDisrupt() {
		temp.raidType = RaidType.DISRUPT;
		
		List<Industry> targets = new ArrayList<Industry>();
		for (Industry curr : market.getIndustries()) {
			if (curr.getSpec().hasTag(Industries.TAG_UNRAIDABLE)) continue;
			float dur = computeBaseDisruptDuration(curr);
			if (dur <= 0) continue;
			targets.add(curr);
		}
		
		if (targets.isEmpty()) {
			text.addPara("There are no industries or facilities present that could be disrupted by a raid.");
			addNeverMindOption();
			return;
		}
		
		dialog.showIndustryPicker("Select raid target", "Select", market, targets, new IndustryPickerListener() {
			public void pickedIndustry(Industry industry) {
				raidDisruptIndustryPicked(industry);
			}
			public void cancelledIndustryPicking() {
				
			}
		});
	}
	
	protected float computeBaseDisruptDuration(Industry ind) {
		float dur = temp.raidMult * (Global.getSettings().getFloat("raidDisruptDuration") - ind.getDisruptedDays());
		return (int) dur;
	}
	
	public static int getBombardDestroyThreshold() {
		return Global.getSettings().getInt("bombardSaturationDestroySize");
		
	}
	public static int getBombardDisruptDuration(BombardType type) {
		float dur = Global.getSettings().getFloat("bombardDisruptDuration");
		if (type == BombardType.TACTICAL)
			dur /= 3;
		return (int) dur;
	}
	
	protected void raidDisruptIndustryPicked(Industry target) {
		temp.target = target;
		text.addParagraph("Target: " + target.getCurrentName(), Global.getSettings().getColor("buttonText"));
		
		float dur = computeBaseDisruptDuration(target);
		
		Color h = Misc.getHighlightColor();
		
		float already = target.getDisruptedDays();
		if (already > 0) {
			text.addPara(target.getNameForModifier() + " operations are already disrupted, and a raid will have " +
					"reduced effect.");
		}
		
		text.addPara("Your ground forces commander estimates that given the relative force strengths, " +
				" the raid should disrupt all " + target.getCurrentName() + " operations for at least %s days.",
				h, "" + (int) Misc.getRounded(dur));
		
		text.addPara("Your forces are ready to go, awaiting your final confirmation.");
		
		options.clearOptions();
		
		addConfirmOptions();
	}
	
	
	protected void addNeverMindOption() {
		options.clearOptions();
		options.addOption("Never mind", RAID_NEVER_MIND);
		options.setShortcut(RAID_NEVER_MIND, Keyboard.KEY_ESCAPE, false, false, false, true);
	}
	
	protected void addBombardNeverMindOption() {
		options.clearOptions();
		options.addOption("Never mind", BOMBARD_NEVERMIND);
		options.setShortcut(BOMBARD_NEVERMIND, Keyboard.KEY_ESCAPE, false, false, false, true);
	}
	
	protected void addContinueOption() {
		addContinueOption(null);
	}
	protected void addContinueOption(String text) {
		if (text == null) text = "Continue";
		options.clearOptions();
		options.addOption(text, RAID_RESULT);
	}
	
	protected Map<CommodityOnMarketAPI, Float> computeRaidValuables() {
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
		
		temp.shortageMult = 1f;
		if (totalShortage > 0 && totalDemand > 0) {
			temp.shortageMult = Math.max(0, totalDemand - totalShortage) / totalDemand;
		}
		
		return result;
	}

	
	public static final String DEFENDER_INCREASE_KEY = "$core_defenderIncrease";
	public static float getDefenderIncreaseRaw(MarketAPI market) {
		float e = market.getMemoryWithoutUpdate().getExpire(DEFENDER_INCREASE_KEY);
		if (e < 0) e = 0;
		return e;
	}
	
	public static void applyDefenderIncreaseFromRaid(MarketAPI market) {
		float e = market.getMemoryWithoutUpdate().getExpire(DEFENDER_INCREASE_KEY);
		e += getRaidDefenderIncreasePerRaid();
		float max = getRaidDefenderIncreaseMax();
		if (e > max) e = max;
		
		market.getMemoryWithoutUpdate().set(DEFENDER_INCREASE_KEY, true);
		market.getMemoryWithoutUpdate().expire(DEFENDER_INCREASE_KEY, e);
	}
	
	public static float getDefenderIncreaseValue(MarketAPI market) {
		float e = getDefenderIncreaseRaw(market);
		float f = getRaidDefenderIncreaseFraction();
		float min = getRaidDefenderIncreaseMin();
		
		float base = PopulationAndInfrastructure.getBaseGroundDefenses(market.getSize());
		float incr = Math.max(base * f, min);
		
		float per = getRaidDefenderIncreasePerRaid();
		
		return (int)(incr * e / per);
	}
	
	protected static float getRaidDefenderIncreasePerRaid() {
		return Global.getSettings().getFloat("raidDefenderIncreasePerRaid");
	}
	protected static float getRaidDefenderIncreaseMax() {
		return Global.getSettings().getFloat("raidDefenderIncreaseMax");
	}
	protected static float getRaidDefenderIncreaseFraction() {
		return Global.getSettings().getFloat("raidDefenderIncreaseFraction");
	}
	protected static float getRaidDefenderIncreaseMin() {
		return Global.getSettings().getFloat("raidDefenderIncreaseMin");
	}
	
	
	protected float getRaidCooldownMax() {
		return Global.getSettings().getFloat("raidCooldownDays");
	}
	
	protected void setRaidCooldown(float cooldown) {
		String key = "$raid_cooldown";
		Global.getSector().getMemoryWithoutUpdate().set(key, true, cooldown);
	}
	
	protected float getRaidCooldown() {
		String key = "$raid_cooldown";
		return Global.getSector().getMemoryWithoutUpdate().getExpire(key);
	}
	
	protected Random getRandom() {
		String key = "$raid_random";
		MemoryAPI mem = market.getMemoryWithoutUpdate();
		Random random = null;
		if (mem.contains(key)) {
			random = (Random) mem.get(key);
		} else {
			random = new Random();
		}
		mem.set(key, random, 30f);
		
		return random;
	}
	
	protected float getBaseRaidValue() {
		float targetValue = 0f;
		for (CommodityOnMarketAPI com : temp.raidValuables.keySet()) {
			targetValue += temp.raidValuables.get(com) * com.getCommodity().getBasePrice();
		}
		targetValue *= 0.1f;
		targetValue *= temp.raidMult;
		targetValue *= temp.shortageMult;
		return targetValue;
	}
	
	protected void raidConfirm() {
		if (temp.raidType == null) {
			raidNeverMind();
			return;
		}
		
		if (temp.raidType == RaidType.VALUABLE) {
			dialog.getVisualPanel().showImagePortion("illustrations", "raid_valuables_result", 640, 400, 0, 0, 480, 300);
		} else if (temp.raidType == RaidType.DISRUPT) {
			dialog.getVisualPanel().showImagePortion("illustrations", "raid_disrupt_result", 640, 400, 0, 0, 480, 300);
		}
		
		Random random = getRandom();
		//random = new Random();
		
		if (!DebugFlags.MARKET_HOSTILITIES_DEBUG) {
			Misc.increaseMarketHostileTimeout(market, 30f);
		}
		
		applyDefenderIncreaseFromRaid(market);
		
		setRaidCooldown(getRaidCooldownMax());

		String reason = "Recently raided";
		if (Misc.isPlayerFactionSetUp()) {
			reason = playerFaction.getDisplayName() + " raid";
		}
		//RecentUnrest.get(market).add(3, Misc.ucFirst(reason));
		int stabilityPenalty = applyRaidStabiltyPenalty(market, reason, temp.raidMult);
		Misc.setFlagWithReason(market.getMemoryWithoutUpdate(), MemFlags.RECENTLY_RAIDED, 
							   Factions.PLAYER, true, 30f);
		
		int marines = playerFleet.getCargo().getMarines();
		float probOfLosses = 1f - temp.raidMult * 0.5f;
		if (probOfLosses < 0.1f) {
			probOfLosses = 0.1f;
		}
		
		if (temp.defenderStr > 1000f * (1f + 0.25f * random.nextFloat())) {
			probOfLosses = 1f; // if there are enough defenses, /some/ losses are almost certain
		}
		
		int losses = 0;
		if (random.nextFloat() < probOfLosses) {
			float lossMult = 1f - temp.raidMult;
			if (lossMult < 0.01f) {
				lossMult = 0.01f;
			}
			float lossBase = Math.min(marines, temp.defenderStr);
			losses = (int) (lossBase * 0.5f * lossMult * random.nextFloat());
		}
		
		//losses = random.nextInt(marines / 2);
		
		if (losses <= 0) {
			text.addPara("Your raiding forces have not suffered any losses.");
		} else {
			//text.addPara("You've lost %s marines during the raid.", Misc.getNegativeHighlightColor(), "" + losses);
			//text.addPara("You've lost %s marines during the raid.", Misc.getHighlightColor(), "" + losses);
			text.addPara("You forces have suffered casualties during the raid.", Misc.getHighlightColor(), "" + losses);
			playerFleet.getCargo().removeMarines(losses);
			temp.marinesLost = losses;
			AddRemoveCommodity.addCommodityLossText(Commodities.MARINES, losses, text);
		}
		
		boolean tOn = playerFleet.isTransponderOn();
		boolean hostile = faction.isHostileTo(Factions.PLAYER);
		CustomRepImpact impact = new CustomRepImpact();
		impact.delta = market.getSize() * -0.01f * 1f;
		if (!hostile && tOn) {
			impact.ensureAtBest = RepLevel.HOSTILE;
		}
		Global.getSector().adjustPlayerReputation(
				new RepActionEnvelope(RepActions.CUSTOM, 
					impact, null, text, true, true),
					faction.getId());
		
		if (stabilityPenalty > 0) {
			text.addPara("Stability of " + market.getName() + " reduced by %s.",
					Misc.getHighlightColor(), "" + stabilityPenalty);
		}
		
		String contText = null;
		if (temp.raidType == RaidType.VALUABLE) {
			float targetValue = getBaseRaidValue();
			CargoAPI result = Global.getFactory().createCargo(true);
			
			WeightedRandomPicker<CommodityOnMarketAPI> picker = new WeightedRandomPicker<CommodityOnMarketAPI>(random);
			for (CommodityOnMarketAPI com : temp.raidValuables.keySet()) {
				picker.add(com, temp.raidValuables.get(com));
			}
			
			//float chunks = 10f;
			float chunks = temp.raidValuables.size();
			if (chunks > 6) chunks = 6;
			for (int i = 0; i < chunks; i++) {
				float chunkValue = targetValue * 1f / chunks;
				float randMult = StarSystemGenerator.getNormalRandom(random, 0.5f, 1.5f);
				chunkValue *= randMult;
				
				CommodityOnMarketAPI pick = picker.pick();
				int quantity = (int) (chunkValue / pick.getCommodity().getBasePrice());
				if (quantity <= 0) continue;
				
				pick.addTradeModMinus("raid_" + Misc.genUID(), -quantity, BaseSubmarketPlugin.TRADE_IMPACT_DAYS);
				
				result.addCommodity(pick.getId(), quantity);
			}
			
			raidSpecialItems(result, random);
			
			result.sort();
			
			temp.raidLoot = result;
			
			temp.raidCredits = (int)(targetValue * 0.1f * StarSystemGenerator.getNormalRandom(random, 0.5f, 1.5f));
			if (temp.raidCredits < 2) temp.raidCredits = 2;
			
			//result.clear();
			if (result.isEmpty()) {
				text.addPara("Unfortunately, the raid was not successful in acquiring any meaningful quantity of goods.");
			} else {
				text.addPara("The raid was successful in obtaining a quantity of various items, as well as some credits.");
				AddRemoveCommodity.addCreditsGainText(temp.raidCredits, text);
				playerFleet.getCargo().getCredits().add(temp.raidCredits);
				contText = "Pick through the spoils";
			}
			
			ListenerUtil.reportRaidForValuablesFinishedBeforeCargoShown(dialog, market, temp, temp.raidLoot);
			
		} else if (temp.raidType == RaidType.DISRUPT) {
			float dur = computeBaseDisruptDuration(temp.target);
			dur *= StarSystemGenerator.getNormalRandom(random, 1f, 1.25f);
			if (dur < 2) dur = 2;
			float already = temp.target.getDisruptedDays();
			temp.target.setDisrupted(already + dur);
			
			text.addPara("The raid was successful in disrupting " + temp.target.getNameForModifier() + " operations." +
					" It will take at least %s days for normal operations to resume.",
					Misc.getHighlightColor(), "" + (int) Math.round(already + dur));
			
			ListenerUtil.reportRaidToDisruptFinished(dialog, market, temp, temp.target);
		}
		
		Global.getSoundPlayer().playUISound("ui_raid_finished", 1f, 1f);
		
		addContinueOption(contText);
	}
	
	protected void raidSpecialItems(CargoAPI cargo, Random random) {
		float p = temp.raidMult * 0.2f;
		
		boolean withBP = false;
		boolean heavyIndustry = false;
		for (Industry curr : market.getIndustries()) {
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
			
			int num = getNumPicks(random, temp.raidMult * 0.25f, temp.raidMult * 0.5f);
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
		int num = getNumPicks(random, temp.raidMult * 0.5f, temp.raidMult * 0.25f);
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
			if (w.getAIHints().contains(AIHints.SYSTEM)) continue;
			
			if (!military && !heavyIndustry && 
					(w.getTier() > 1 || w.getSize() == WeaponSize.LARGE)) continue;
			
			picker.add(weapon + id, w.getRarity());
		}
		for (String id : market.getFaction().getKnownFighters()) {
			FighterWingSpecAPI f = Global.getSettings().getFighterWingSpec(id);
			if (f.hasTag(Tags.WING_NO_DROP)) continue;
			
			if (!military && !heavyIndustry && f.getTier() > 0) continue;
			
			picker.add(fighter + id, f.getRarity());
		}
		
		
		num = getNumPicks(random, temp.raidMult * 0.5f, temp.raidMult * 0.25f);
		if (military || heavyIndustry) {
			num += Math.round(market.getCommodityData(Commodities.SHIPS).getAvailable() * temp.raidMult);
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
	
	protected int getNumPicks(Random random, float pAny, float pMore) {
		if (random.nextFloat() >= pAny) return 0;
		
		int result = 1;
		for (int i = 0; i < 10; i++) {
			if (random.nextFloat() >= pMore) break;
			result++;
		}
		return result;
	}
	
	
	protected void raidNeverMind() {
		raidMenu();
	}
	
	
	protected void raidShowLoot() {
		dialog.getVisualPanel().showLoot("Spoils", temp.raidLoot, false, true, true, new CoreInteractionListener() {
			public void coreUIDismissed() {
				//dialog.dismiss();
				finishedRaidOrBombard();
			}
		});
	}
	
	
	protected void printStationState() {
		StationState state = getStationState();
		if (state == StationState.REPAIRS || state == StationState.UNDER_CONSTRUCTION) {
			CampaignFleetAPI fleet = Misc.getStationBaseFleet(market);
			String name = "orbital station";
			if (fleet != null) {
				FleetMemberAPI flagship = fleet.getFlagship();
				if (flagship != null) {
					name = flagship.getVariant().getDesignation().toLowerCase();
				}
			}
			if (state == StationState.REPAIRS) {
				text.addPara("The " + name + " has suffered extensive damage and is not currently combat-capable.");
			} else {
				text.addPara("The " + name + " is under construction and is not currently combat-capable.");
			}
		}
	}

	
	protected void engage() {
		final SectorEntityToken entity = dialog.getInteractionTarget();
		final MemoryAPI memory = getEntityMemory(memoryMap);

		final CampaignFleetAPI primary = getInteractionTargetForFIDPI();
		
		dialog.setInteractionTarget(primary);
		
		final FIDConfig config = new FIDConfig();
		config.leaveAlwaysAvailable = true;
		config.showCommLinkOption = false;
		config.showEngageText = false;
		config.showFleetAttitude = false;
		config.showTransponderStatus = false;
		//config.showWarningDialogWhenNotHostile = false;
		config.alwaysAttackVsAttack = true;
		config.impactsAllyReputation = true;
//		config.impactsEnemyReputation = false;
//		config.pullInAllies = false;
//		config.pullInEnemies = false;
//		config.lootCredits = false;
		
//		config.firstTimeEngageOptionText = "Engage the automated defenses";
//		config.afterFirstTimeEngageOptionText = "Re-engage the automated defenses";
		config.noSalvageLeaveOptionText = "Continue";
		
		config.dismissOnLeave = false;
		config.printXPToDialog = true;
		
		config.straightToEngage = true;
		
		CampaignFleetAPI station = getStationFleet();
		config.playerAttackingStation = station != null;
		
		final FleetInteractionDialogPluginImpl plugin = new FleetInteractionDialogPluginImpl(config);
		
		final InteractionDialogPlugin originalPlugin = dialog.getPlugin();
		config.delegate = new BaseFIDDelegate() {
			@Override
			public void notifyLeave(InteractionDialogAPI dialog) {
				if (primary.isStationMode()) {
					primary.getMemoryWithoutUpdate().clear();
					primary.clearAssignments();
					//primary.deflate();
				}
				
				dialog.setPlugin(originalPlugin);
				dialog.setInteractionTarget(entity);
				
				boolean quickExit = entity.hasTag(Tags.NON_CLICKABLE);
				
				if (!Global.getSector().getPlayerFleet().isValidPlayerFleet() || quickExit) {
					//dialog.getVisualPanel().setVisualFade(0, 0);
					dialog.hideVisualPanel();
					dialog.getVisualPanel().finishFadeFast();
					dialog.hideTextPanel();
					dialog.dismiss();
					return;
				}
				
				if (plugin.getContext() instanceof FleetEncounterContext) {
					FleetEncounterContext context = (FleetEncounterContext) plugin.getContext();
					if (context.didPlayerWinEncounter()) {
						// may need to do something here re: station being defeated & timed out
						
						//FireBest.fire(null, dialog, memoryMap, "BeatDefendersContinue");
					} else {
						//dialog.dismiss();
					}
					
					showDefenses(context.isEngagedInHostilities());
				} else {
					showDefenses(false);
				}
				dialog.getVisualPanel().finishFadeFast();
				
				//dialog.dismiss();
			}
			@Override
			public void battleContextCreated(InteractionDialogAPI dialog, BattleCreationContext bcc) {
				//bcc.aiRetreatAllowed = false;
				bcc.objectivesAllowed = false;
			}
			@Override
			public void postPlayerSalvageGeneration(InteractionDialogAPI dialog, FleetEncounterContext context, CargoAPI salvage) {
			}
			
		};
		
		dialog.setPlugin(plugin);
		plugin.init(dialog);
		
	}

	protected CampaignFleetAPI getStationFleet() {
		CampaignFleetAPI station = Misc.getStationFleet(market);
		if (station == null) return null;
		
		if (station.getFleetData().getMembersListCopy().isEmpty()) return null;
		
		return station;
	}
	
	protected CampaignFleetAPI getInteractionTargetForFIDPI() {
		CampaignFleetAPI primary = getStationFleet();
		if (primary == null) {
			CampaignFleetAPI best = null;
			float minDist = Float.MAX_VALUE;
			for (CampaignFleetAPI fleet : Misc.getNearbyFleets(entity, 2000)) {
				if (fleet.getBattle() != null) continue;
				
				if (fleet.getFaction() != market.getFaction()) continue;
				if (fleet.getFleetData().getNumMembers() <= 0) continue;
				
				float dist = Misc.getDistance(entity.getLocation(), fleet.getLocation());
				dist -= entity.getRadius();
				dist -= fleet.getRadius();
				
				if (dist < Misc.getBattleJoinRange() ) {
					if (dist < minDist) {
						best = fleet;
						minDist = dist;
					}
				}
			}
			primary = best;
		} else {
			//primary.setLocation(entity.getLocation().x, entity.getLocation().y);
		}
		return primary;
	}
	
	public static enum StationState {
		NONE,
		OPERATIONAL,
		UNDER_CONSTRUCTION,
		REPAIRS
	}
	
	protected StationState getStationState() {
		CampaignFleetAPI fleet = Misc.getStationFleet(market);
		boolean destroyed = false;
		if (fleet == null) {
			fleet = Misc.getStationBaseFleet(market);
			if (fleet != null) {
				destroyed = true;
			}
		}
		
		if (fleet == null) return StationState.NONE;
		
		MarketAPI market = Misc.getStationMarket(fleet);
		if (market != null) {
			for (Industry ind : market.getIndustries()) {
				if (ind.getSpec().hasTag(Industries.TAG_STATION)) {
					if (ind.isBuilding() && !ind.isDisrupted() && !ind.isUpgrading()) {
						return StationState.UNDER_CONSTRUCTION;
					}
				}
			}
		}
		
		if (destroyed) return StationState.REPAIRS;
		
		return StationState.OPERATIONAL;
	}
	
	
	public static int applyRaidStabiltyPenalty(MarketAPI target, String desc, float re) {
		int penalty = 0;
		if (re >= 0.75f) penalty = 3;
		else if (re >= 0.5f) penalty = 2;
		else if (re >= 0.25f) penalty = 1;
		if (penalty > 0) {
			RecentUnrest.get(target).add(penalty, desc);
		}
		return penalty;
	}
	
	public static int applyRaidStabiltyPenalty(MarketAPI target, String desc, float re, float maxPenalty) {
		int penalty = Math.round((0.5f + maxPenalty) * re);
		if (penalty > 0) {
			RecentUnrest.get(target).add(penalty, desc);
		}
		return penalty;
	}
	
	
	public static StatModValueGetter statPrinter(final boolean withNegative) {
		return new StatModValueGetter() {
			public String getPercentValue(StatMod mod) {
				String prefix = mod.getValue() > 0 ? "+" : "";
				return prefix + (int)(mod.getValue()) + "%";
			}
			public String getMultValue(StatMod mod) {
				return Strings.X + "" + Misc.getRoundedValue(mod.getValue());
			}
			public String getFlatValue(StatMod mod) {
				String prefix = mod.getValue() > 0 ? "+" : "";
				return prefix + (int)(mod.getValue()) + "";
			}
			public Color getModColor(StatMod mod) {
				if (withNegative && mod.getValue() < 1f) return Misc.getNegativeHighlightColor();
				return null;
			}
		};
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
			result /= 2;
		
		return result;
	}
	
	public static int getTacticalBombardmentStabilityPenalty() {
		return (int) Global.getSettings().getFloat("bombardTacticalStability");
	}
	public static int getSaturationBombardmentStabilityPenalty() {
		return (int) Global.getSettings().getFloat("bombardSaturationStability");
	}
	

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
		boolean canBombardTac = fuel >= costTac;
		boolean canBombardSat = fuel >= costSat;
		
		LabelAPI label = text.addPara("A tactical bombardment requires %s fuel. A saturation bombardment requires %s fuel. " +
									  "You have %s fuel.",
				h, "" + costTac, "" + costSat, "" + fuel);
		label.setHighlight("" + costSat, "" + fuel);
		label.setHighlightColors(canBombardTac ? h : b, canBombardSat ? h : b, h);

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
	
	
	protected void addConfirmOptions() {
		options.clearOptions();
		options.addOption("Launch raid", RAID_CONFIRM);
		options.addOption("Never mind", RAID_NEVER_MIND);
		options.setShortcut(RAID_NEVER_MIND, Keyboard.KEY_ESCAPE, false, false, false, true);
		
		boolean tOn = playerFleet.isTransponderOn();
		boolean hostile = faction.isHostileTo(Factions.PLAYER);
		if (tOn && !hostile) {
			options.addOptionConfirmation(RAID_CONFIRM,
					"The " + faction.getDisplayNameLong() + 
					" " + faction.getDisplayNameIsOrAre() + 
					" not currently hostile, and you have been positively identified. " +
					"Are you sure you want to engage in open hostilities?", "Yes", "Never mind");
		}
	}
	
	protected void bombardTactical() {
		
		temp.bombardType = BombardType.TACTICAL; 
		
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
					 h, "" + temp.bombardCost, "" + fuel);
		
		addBombardConfirmOptions();
	}
	
	protected void bombardSaturation() {
		
		temp.bombardType = BombardType.SATURATION;

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
				boolean hostile = faction.isHostileTo(Factions.PLAYER);
				temp.willBecomeHostile.add(faction);
				if (!hostile) {
					nonHostile.add(faction);
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
		

		if (nonHostile.isEmpty()) {
			text.addPara(StringHelper.getString("nex_bombardment", "satBombWarningAllHostile"));
		} else if (market.getSize() <= 3) {
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
			text.addPara(StringHelper.getString("nex_bombardment", "satBombWarningVengeful"));
			
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
	
		for (FactionAPI curr : temp.willBecomeHostile) {
			CustomRepImpact impact = new CustomRepImpact();
			impact.delta = market.getSize() * -0.01f * 1f;
			impact.ensureAtBest = RepLevel.HOSTILE;
			if (temp.bombardType == BombardType.SATURATION) {
				if (curr == faction) {
					impact.ensureAtBest = RepLevel.VENGEFUL;
				}
				else if (market.getSize() <= 3) {
					impact.ensureAtBest = RepLevel.NEUTRAL;
				}
				impact.delta = market.getSize() * -0.02f * 1f;
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
		boolean destroy = temp.bombardType == BombardType.SATURATION 
				&& market.getSize() <= getBombardDestroyThreshold();
		
		if (stabilityPenalty > 0 && !destroy) {
			String reason = "Recently bombarded";
			if (Misc.isPlayerFactionSetUp()) {
				reason = playerFaction.getDisplayName() + " bombardment";
			}
			RecentUnrest.get(market).add(stabilityPenalty, reason);
			text.addPara("Stability of " + market.getName() + " reduced by %s.",
					Misc.getHighlightColor(), "" + stabilityPenalty);
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
	
	
	protected void bombardNeverMind() {
		bombardMenu();		
	}

	protected void raidResult() {
		if (temp.raidLoot != null) {
			if (temp.raidLoot.isEmpty()) {
//				clearTemp();
//				showDefenses(true);
				//dialog.dismiss();
				finishedRaidOrBombard();
			} else {
				raidShowLoot();
			}
			return;
		} else {
			//dialog.dismiss();
			finishedRaidOrBombard();
		}
	}
	
	protected void bombardResult() {
		//dialog.dismiss();
		finishedRaidOrBombard();
	}
	
	protected void finishedRaidOrBombard() {
		clearTemp();
		//showDefenses(true);
	
		new ShowDefaultVisual().execute(null, dialog, Misc.tokenize(""), memoryMap);
		
		//FireAll.fire(null, dialog, memoryMap, "MarketPostOpen");
		dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$menuState", "main", 0);
		if (dialog.getInteractionTarget().getMemoryWithoutUpdate().contains("$tradeMode")) {
			if (market.isPlanetConditionMarketOnly()) {
				dialog.getInteractionTarget().getMemoryWithoutUpdate().unset("$hasMarket");
			}
			dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$tradeMode", "NONE", 0);
		} else {
			// station that's now abandoned
			dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$tradeMode", "OPEN", 0);
		}
		
		FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
	}
	
	protected void addBombardContinueOption() {
		addBombardContinueOption(null);
	}
	protected void addBombardContinueOption(String text) {
		if (text == null) text = "Continue";
		options.clearOptions();
		options.addOption(text, BOMBARD_RESULT);
	}
	
	
	protected boolean checkDebtEffect() {
		String key = "$debt_effectTimeout";
		if (Global.getSector().getMemoryWithoutUpdate().contains(key)) return false;
		
		//if (true) return true;
		
		// can't exactly melt away in that small an outpost, not that it's outright desertion
		// but it's also not a great place to leave the fleet
		if (market.isPlayerOwned() && market.getSize() <= 3) return false;
		
		MonthlyReport report = SharedData.getData().getPreviousReport();
		
		
		// require 2 months of debt in a row
		if (report.getPreviousDebt() <= 0 || report.getDebt() <= 0) return false;
		
		float debt = report.getDebt() + report.getDebt();
		float income = report.getRoot().totalIncome;
		if (income < 1) income = 1;
		
		float f = debt / income;
		if (f > 1) f = 1;
		if (f < 0) f = 0;
		// don't penalize minor shortfalls
		if (f < 0.1f) return false;
		
		// and don't reduce crew below a certain minimum
		int crew = playerFleet.getCargo().getCrew();
		int marines = playerFleet.getCargo().getMarines();
		if (crew <= 10 && marines <= 10) return false;
		
		return true;
	}
	
	protected void applyDebtEffect() {
		
		MonthlyReport report = SharedData.getData().getPreviousReport();
		float debt = report.getDebt() + report.getDebt();
		float income = report.getRoot().totalIncome;
		if (income < 1) income = 1;
		
		float f = debt / income;
		if (f > 1) f = 1;
		if (f < 0) f = 0;
		
		int crew = playerFleet.getCargo().getCrew();
		int marines = playerFleet.getCargo().getMarines();
		
		float maxLossFraction = 0.03f + Math.min(f + 0.05f, 0.2f) * (float) Math.random();
		float marineLossFraction = 0.03f + Math.min(f + 0.05f, 0.2f) * (float) Math.random();
		
		
		int crewLoss = (int) (crew * maxLossFraction);
		if (crewLoss < 2) crewLoss = 2;
		
		int marineLoss = (int) (marines * marineLossFraction);
		if (marineLoss < 2) marineLoss = 2;
		
		dialog.getVisualPanel().showImagePortion("illustrations", "crew_leaving", 640, 400, 0, 0, 480, 300);
		
		text.addPara("The lack of consistent pay over the last few months has caused discontent among your crew. " +
				"A number take this opportunity to leave your employment.");
		
		if (crewLoss < crew) {
			playerFleet.getCargo().removeCrew(crewLoss);
			AddRemoveCommodity.addCommodityLossText(Commodities.CREW, crewLoss, text);
		}
		if (marineLoss <= marines) {
			playerFleet.getCargo().removeCrew(marineLoss);
			AddRemoveCommodity.addCommodityLossText(Commodities.MARINES, marineLoss, text);
		}
		
		String key = "$debt_effectTimeout";
		Global.getSector().getMemoryWithoutUpdate().set(key, true, 30f + (float) Math.random() * 10f);
		
		options.clearOptions();
		options.addOption("Continue", DEBT_RESULT_CONTINUE);
	}

	
	public void doGenericRaid(FactionAPI faction, float attackerStr) {
		doGenericRaid(faction, attackerStr, 3f);
	}
	
	public void doGenericRaid(FactionAPI faction, float attackerStr, float maxPenalty) {
		// needed for pirate raids not to stack
		// not needed anymore, but doesn't hurt anything
		if (Misc.flagHasReason(market.getMemoryWithoutUpdate(), 
				MemFlags.RECENTLY_RAIDED, faction.getId())) {
			return;
		}
		
		float re = getRaidEffectiveness(market, attackerStr);
		if (maxPenalty == 3) {
			applyRaidStabiltyPenalty(market, Misc.ucFirst(faction.getPersonNamePrefix()) + " raid", re);
		} else {
			applyRaidStabiltyPenalty(market, Misc.ucFirst(faction.getPersonNamePrefix()) + " raid", re, maxPenalty);
		}
		//RecentUnrest.get(market).add(3, Misc.ucFirst(faction.getPersonNamePrefix()) + " raid");
		
		Misc.setFlagWithReason(market.getMemoryWithoutUpdate(), MemFlags.RECENTLY_RAIDED, 
							   faction.getId(), true, 30f);
	}
	
	public boolean doIndustryRaid(FactionAPI faction, float attackerStr, Industry industry, float durMult) {
		temp.raidType = RaidType.DISRUPT;
		temp.target = industry;
		
		StatBonus defenderBase = new StatBonus(); 
		
		StatBonus defender = market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD);
		String increasedDefensesKey = "core_addedDefStr";
		float added = getDefenderIncreaseValue(market);
		if (added > 0) {
			defender.modifyFlat(increasedDefensesKey, added, "Increased defender preparedness");
		}
		float defenderStr = (int) Math.round(defender.computeEffective(defenderBase.computeEffective(0f)));
		temp.attackerStr = attackerStr;
		temp.defenderStr = defenderStr;
		
		boolean hasForces = true;
		boolean canDisrupt = true;
		temp.raidMult = attackerStr / Math.max(1f, (attackerStr + defenderStr));
		
		if (temp.raidMult < 0.01f) {
			hasForces = false;
		}
		if (temp.raidMult < FAIL_THRESHOLD) {
			canDisrupt = false;
		}
		if (!canDisrupt) return false;
		
		
		Random random = getRandom();
		
		applyDefenderIncreaseFromRaid(market);
		
		String reason = faction.getDisplayName() + " raid";
		
		applyRaidStabiltyPenalty(market, reason, temp.raidMult);
		Misc.setFlagWithReason(market.getMemoryWithoutUpdate(), MemFlags.RECENTLY_RAIDED, 
							   faction.getId(), true, 30f);
		
		if (temp.target != null) {
			float dur = computeBaseDisruptDuration(temp.target);
			dur *= StarSystemGenerator.getNormalRandom(random, 1f, 1.25f);
			dur *= durMult;
			if (dur < 2) dur = 2;
			float already = temp.target.getDisruptedDays();
			temp.target.setDisrupted(already + dur);
		}
		
		return true;
	}
	
	
	public void doBombardment(FactionAPI faction, BombardType type) {
		temp.bombardType = type;
		
		Random random = getRandom();
	
		int dur = getBombardDisruptDuration(temp.bombardType);
		
		int stabilityPenalty = getTacticalBombardmentStabilityPenalty();
		if (temp.bombardType == BombardType.SATURATION) {
			stabilityPenalty = getSaturationBombardmentStabilityPenalty();
			
			List<Industry> targets = new ArrayList<Industry>();
			for (Industry ind : market.getIndustries()) {
				if (!ind.getSpec().hasTag(Industries.TAG_NO_SATURATION_BOMBARDMENT)) {
					if (ind.getDisruptedDays() >= dur * 0.8f) continue;
					targets.add(ind);
				}
			}
			temp.bombardmentTargets.clear();
			temp.bombardmentTargets.addAll(targets);
		} else {
			List<Industry> targets = new ArrayList<Industry>();
			for (Industry ind : market.getIndustries()) {
				if (ind.getSpec().hasTag(Industries.TAG_TACTICAL_BOMBARDMENT)) {
					if (ind.getDisruptedDays() >= dur * 0.8f) continue;
					targets.add(ind);
				}
			}
			temp.bombardmentTargets.clear();
			temp.bombardmentTargets.addAll(targets);
		}
		
		
		if (stabilityPenalty > 0) {
			String reason = faction.getDisplayName() + " bombardment";
			RecentUnrest.get(market).add(stabilityPenalty, reason);
		}
		
		if (market.hasCondition(Conditions.HABITABLE) && !market.hasCondition(Conditions.POLLUTION)) {
			market.addCondition(Conditions.POLLUTION);
		}
		
		for (Industry curr : temp.bombardmentTargets) {
			dur = getBombardDisruptDuration(temp.bombardType);
			dur *= StarSystemGenerator.getNormalRandom(random, 1f, 1.25f);
			curr.setDisrupted(dur);
		}
		
		if (temp.bombardType == BombardType.TACTICAL) {
		} else if (temp.bombardType == BombardType.SATURATION) {
			boolean destroy = market.getSize() <= getBombardDestroyThreshold();
			if (destroy) {
				DecivTracker.decivilize(market, true);
			} else {
				CoreImmigrationPluginImpl.reduceMarketSize(market);
			}
		}
		
		
		Misc.setFlagWithReason(market.getMemoryWithoutUpdate(), MemFlags.RECENTLY_BOMBARDED, 
				   			   faction.getId(), true, 30f);
		
		addBombardVisual(market.getPrimaryEntity());
	}
	
	
//	public static interface BombardmentVisualPlugin extends GenericPlugin {
//		void addBombardmentVisual(SectorEntityToken target);
//	}
	
	public static void addBombardVisual(SectorEntityToken target) {
		if (target != null && target.isInCurrentLocation()) {
			int num = (int) (target.getRadius() * target.getRadius() / 300f);
			num *= 2;
			if (num > 150) num = 150;
			if (num < 10) num = 10;
			target.addScript(new BombardmentAnimation(num, target));
		}
	}
	
	public static class BombardmentAnimation implements EveryFrameScript {
		public BombardmentAnimation(int num, SectorEntityToken target) {
			this.num = num;
			this.target = target;
		}
		int num = 0;
		SectorEntityToken target;
		int added = 0;
		float elapsed = 0;
		public boolean runWhilePaused() {
			return false;
		}
		public boolean isDone() {
			return added >= num;
		}
		public void advance(float amount) {
			elapsed += amount * (float) Math.random();
			if (elapsed < 0.03f) return;
			
			elapsed = 0f;
			
			int curr = (int) Math.round(Math.random() * 4);
			if (curr < 1) curr = 0;
			
			Color color = new Color(255, 165, 100, 255);
			
			Vector2f vel = new Vector2f();
			
			if (target.getOrbit() != null && 
					target.getCircularOrbitRadius() > 0 && 
					target.getCircularOrbitPeriod() > 0 && 
					target.getOrbitFocus() != null) {
				float circumference = 2f * (float) Math.PI * target.getCircularOrbitRadius();
				float speed = circumference / target.getCircularOrbitPeriod();
				
				float dir = Misc.getAngleInDegrees(target.getLocation(), target.getOrbitFocus().getLocation()) + 90f;
				vel = Misc.getUnitVectorAtDegreeAngle(dir);
				vel.scale(speed / Global.getSector().getClock().getSecondsPerDay());
			}
			
			for (int i = 0; i < curr; i++) {
				float glowSize = 50f + 50f * (float) Math.random();
				float angle = (float) Math.random() * 360f;
				float dist = (float) Math.sqrt(Math.random()) * target.getRadius();
				
				float factor = 0.5f + 0.5f * (1f - (float)Math.sqrt(dist / target.getRadius()));;
				glowSize *= factor;
				Vector2f loc = Misc.getUnitVectorAtDegreeAngle(angle);
				loc.scale(dist);
				Vector2f.add(loc, target.getLocation(), loc);
				
				Color c2 = Misc.scaleColor(color, factor);
				//c2 = color;
				Misc.addHitGlow(target.getContainingLocation(), loc, vel, glowSize, c2);
				added++;
				
				if (i == 0) {
					dist = Misc.getDistance(loc, Global.getSector().getPlayerFleet().getLocation());
					if (dist < HyperspaceTerrainPlugin.STORM_STRIKE_SOUND_RANGE) {
						float volumeMult = 1f - (dist / HyperspaceTerrainPlugin.STORM_STRIKE_SOUND_RANGE);
						volumeMult = (float) Math.sqrt(volumeMult);
						volumeMult *= 0.1f * factor;
						if (volumeMult > 0) {
							Global.getSoundPlayer().playSound("mine_explosion", 1f, 1f * volumeMult, loc, Misc.ZERO);
						}
					}
				}
			}
		}
	}
	
}

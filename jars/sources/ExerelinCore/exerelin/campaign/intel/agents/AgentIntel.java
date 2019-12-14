package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.CovertOpsManager.CovertActionType;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.lwjgl.input.Keyboard;

public class AgentIntel extends BaseIntelPlugin {
	
	public static final int[] XP_LEVELS = new int[] {
		0, 2000, 5000, 10000, 20000, 40000
	};
	public static final int MAX_LEVEL = XP_LEVELS.length - 1;
	
	protected static final Object UPDATE_RECRUITED = new Object();
	protected static final Object UPDATE_ARRIVED = new Object();
	protected static final Object UPDATE_LEVEL_UP = new Object();
	protected static final Object UPDATE_INJURY_RECOVERED = new Object();
	protected static final Object UPDATE_LOST = new Object();
	protected static final Object UPDATE_ABORTED = new Object();
	protected static final String BUTTON_ORDERS = "orders";
	protected static final String BUTTON_ABORT = "abort";
	protected static final String BUTTON_QUEUE_ORDER = "orders2";
	protected static final String BUTTON_CANCEL_QUEUE = "abortQueue";
	
	protected static final String BUTTON_DISMISS = "dismiss";
	
	protected PersonAPI agent;
	protected MarketAPI market;
	protected FactionAPI faction;
	protected CovertActionIntel currentAction, lastAction, nextAction;
	protected int level;
	protected int xp;
	protected long lastActionTimestamp;
	protected boolean isDead = false;
	protected boolean isDismissed = false;
	protected boolean wantLevelUpNotification = false;
	
	
	public AgentIntel(PersonAPI agent, FactionAPI faction, int level) {
		this.agent = agent;
		this.level = level;
		this.faction = faction;
		xp = XP_LEVELS[level - 1];
	}
	
	public void init() {
		Global.getSector().getIntelManager().addIntel(this);
		Global.getSector().addScript(this);
		CovertOpsManager.getManager().addAgent(this);
	}
	
	public void gainXP(int xp) {
		this.xp += xp;
		int newLevel = getLevelForCurrentXP();
		if (newLevel > level) {
			level = newLevel;
			this.sendUpdateIfPlayerHasIntel(UPDATE_LEVEL_UP, false);
		}
	}
	
	public static int getSalary(int level) {
		return ExerelinConfig.agentBaseSalary + ExerelinConfig.agentSalaryPerLevel * (level - 1);
	}
	
	public int getLevel() {
		return level;
	}
	
	public boolean isDeadOrDismissed() {
		return isDead || isDismissed;
	}
	
	public int getLevelForCurrentXP() {
		for (int i=1; i < XP_LEVELS.length; i++) {
			int xpNeeded = XP_LEVELS[i];
			if (xp < xpNeeded)
				return i;
		}
		return MAX_LEVEL;
	}
	
	public int getXPToNextLevel() {
		if (level >= MAX_LEVEL)
			return 0;
		return (int)(XP_LEVELS[level] - xp);
	}
	
	public MarketAPI getMarket() {
		return market;
	}
	
	public void setMarket(MarketAPI market) {
		this.market = market;
	}
	
	public CovertActionIntel getCurrentAction() {
		return currentAction;
	}
	
	public void setCurrentAction(CovertActionIntel currentAction) {
		this.currentAction = currentAction;
	}
	
	public void setQueuedAction(CovertActionIntel action) {
		if (nextAction != null)
			nextAction.abort();
		nextAction = action;
	}
	
	public PersonAPI getAgent() {
		return agent;
	}
	
	@Override
	protected void advanceImpl(float amount) {
		super.advanceImpl(amount);
	}
	
	protected void pushActionQueue() {
		if (nextAction != null) {
			currentAction = nextAction;
			nextAction = null;
			currentAction.activate();
		}
		else
			currentAction = null;
	}
	
	public void notifyActionCompleted() {
		lastAction = currentAction;
		lastActionTimestamp = Global.getSector().getClock().getTimestamp();
		pushActionQueue();
	}
	
	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		Color c = getTitleColor(mode);

		info.addPara(getName(), c, 0);
		
		if (isDead || isDismissed) return;

		Color tc = getBulletColorForMode(mode);
		Color hl = Misc.getHighlightColor();
		float pad = 3;
		
		bullet(info);
		
		if (listInfoParam == UPDATE_RECRUITED) {
			
		} else if (listInfoParam == UPDATE_ARRIVED) {
			String marketName = market.getName();
			info.addPara(marketName, pad, tc, market.getTextColorForFactionOrPlanet(), marketName);
		} else if (listInfoParam == UPDATE_ABORTED) {
			info.addPara(getString("intelAborted"), pad);
		} else if (listInfoParam == UPDATE_LEVEL_UP) {
			info.addPara(getString("intelLevelUp"), pad, hl, level + "");
		} else if (listInfoParam == UPDATE_INJURY_RECOVERED) {
			info.addPara(getString("intelRecovered"), pad);
		} else if (listInfoParam == UPDATE_LOST) {
			
		} else {
			info.addPara(StringHelper.getString("level", true) + " " + level, pad, tc, hl, level + "");
			if (market != null)
				info.addPara(market.getName(), 0, tc, market.getTextColorForFactionOrPlanet(), 
						market.getName());
			if (currentAction != null)
				currentAction.addCurrentActionBullet(info, tc, 0);
		}
	}
	
	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		Color h = Misc.getHighlightColor();
		FactionAPI faction = updateAgentDisplayedFaction();
		FactionAPI pf = Global.getSector().getPlayerFaction();
				
		// images
		info.addImages(width, 128, opad, opad, agent.getPortraitSprite(), faction.getCrest());
		
		// agent basic information
		String key = "intelDescName";
		if (isDead) key = "intelDescLost";
		else if (isDismissed) key = "intelDescDismissed";
		String str = getString(key);
		str = StringHelper.substituteToken(str, "$name", agent.getNameString());
		info.addPara(str, opad, h, level + "");
		
		if (isDead || isDismissed) return;
		
		// agent location
		if (market != null) {
			FactionAPI mktFaction = market.getFaction();
			boolean isHyper = market.getContainingLocation().isHyperspace();
			Map<String, String> sub = new HashMap<>();
			sub.put("$name", agent.getName().getLast());
			sub.put("$onOrAt", market.getOnOrAt());
			sub.put("$market", market.getName());
			//sub.put("$size", market.getSize() + "");
			sub.put("$faction", mktFaction.getDisplayName());
			if (!isHyper)
				sub.put("$location", market.getContainingLocation().getNameWithLowercaseType());
			str = getString(isHyper? "intelDescLocationHyper" : "intelDescLocation");
			str = StringHelper.substituteTokens(str, sub);
			LabelAPI label = info.addPara(str, opad);
			label.setHighlight(market.getName(), mktFaction.getDisplayName());
			label.setHighlightColors(h, mktFaction.getBaseUIColor());
		}
		
		// agent level
		if (level < MAX_LEVEL) {
			info.addPara(getString("intelDescXP"), opad, h, (int)xp + "", getXPToNextLevel() + "");
		} else {
			// no need to display current XP if we're at max anyway
			//info.addPara(getString("intelDescXPMax"), opad, h, level + "");
		}
		
		if (currentAction != null) {
			// current action progress
			info.addSectionHeading(getString("intelDescCurrAction"), Alignment.MID, opad);
			currentAction.addCurrentActionPara(info, opad);
			
			// success chance
			if (currentAction.showSuccessChance()) {
				MutableStat success = currentAction.getSuccessChance();
				float successF = success.getModifiedValue();
				Color chanceCol = Misc.getHighlightColor();
				if (successF >= 70f)
					chanceCol = Misc.getPositiveHighlightColor();
				else if (successF <= 40f)
					chanceCol = Misc.getNegativeHighlightColor();

				String successStr = String.format("%.0f", successF) + "%";
				info.addPara(StringHelper.getString("nex_agentActions", "dialogInfoSuccessChance"), 
						opad, chanceCol, successStr);
			}
			
			// time remaining
			String daysNum = Math.round(currentAction.daysRemaining) + "";
			String daysStr = RaidIntel.getDaysString(currentAction.daysRemaining);
			str = getString("intelDescCurrActionDays") + ".";
			str = StringHelper.substituteToken(str, "$daysStr", daysStr);
			info.addPara(str, opad, h, daysNum);
			
			if (currentAction.getDefId().equals(CovertActionType.PROCURE_SHIP))
			{
				info.addButton(StringHelper.getString("nex_agentActions", 
						"intelButton_procureShipSetDestination", true), 
					ProcureShip.BUTTON_CHANGE_DESTINATION, pf.getBaseUIColor(), pf.getDarkUIColor(),
					(int)(width), 20f, opad * 2f);
			}
			
			// abort button
			if (currentAction.canAbort()) {
				ButtonAPI button = info.addButton(StringHelper.getString("abort", true), 
					BUTTON_ABORT, pf.getBaseUIColor(), pf.getDarkUIColor(),
					(int)(width), 20f, opad * 2f);
				//button.setShortcut(Keyboard.KEY_T, true);
			}
		} else {
			// idle message, button for new orders
			info.addPara(getString("intelDescIdle"), opad, h);
			ButtonAPI button = info.addButton(getString("intelButtonOrders"), 
					BUTTON_ORDERS, pf.getBaseUIColor(), pf.getDarkUIColor(),
					(int)(width), 20f, opad);
			button.setShortcut(Keyboard.KEY_T, true);
		}
		
		// TODO
		if (nextAction != null) {
			info.addSectionHeading(getString("intelDescNextAction"), Alignment.MID, opad);
			nextAction.addCurrentActionPara(info, opad);
			
			// success chance
			if (nextAction.showSuccessChance()) {
				MutableStat success = nextAction.getSuccessChance();
				float successF = success.getModifiedValue();
				Color chanceCol = Misc.getHighlightColor();
				if (successF >= 70f)
					chanceCol = Misc.getPositiveHighlightColor();
				else if (successF <= 40f)
					chanceCol = Misc.getNegativeHighlightColor();

				String successStr = String.format("%.0f", successF) + "%";
				info.addPara(StringHelper.getString("nex_agentActions", "dialogInfoSuccessChance"), 
						opad, chanceCol, successStr);
			}
			
			// abort button
			if (nextAction.canAbort()) {
				ButtonAPI button = info.addButton(StringHelper.getString("abort", true), 
					BUTTON_CANCEL_QUEUE, pf.getBaseUIColor(), pf.getDarkUIColor(),
					(int)(width), 20f, opad * 2f);
			}
		} else if (currentAction != null) {
			ButtonAPI button = info.addButton(getString("intelButtonOrdersQueue"), 
					BUTTON_QUEUE_ORDER, pf.getBaseUIColor(), pf.getDarkUIColor(),
					(int)(width), 20f, opad);
		}
		
		// local report
		if (market != null) {
			info.addSectionHeading(getString("intelHeaderLocalReport"),	Alignment.MID, opad * 2);
			
			// basic info
			str = getString("intelDescLocalReport1");
			String marketName = market.getName();
			String sizeStr = market.getSize() + "";
			String stabStr = (int)market.getStabilityValue() + "";
			LabelAPI label = info.addPara(str, opad, h, marketName, sizeStr, stabStr);
			label.setHighlight(marketName, sizeStr, stabStr);
			label.setHighlightColors(market.getFaction().getBaseUIColor(), h, h);
			
			// defensive strength
			str = getString("intelDescLocalReport2");
			String spaceStr =  String.format("%.1f", InvasionFleetManager.estimatePatrolStrength(null, 
					market.getFaction(), market.getStarSystem(), 0) 
					+ InvasionFleetManager.estimateStationStrength(market));
			String groundStr = String.format("%.1f", InvasionRound.getDefenderStrength(market, 1));
			info.addPara(str, opad, h, spaceStr, groundStr);
			
			// alert level
			float alertLevel = CovertOpsManager.getAlertLevel(market);
			if (alertLevel > 0) {
				str = getString("intelDescLocalReport3");
				String alertLevelStr = String.format("%.0f", CovertOpsManager.getAlertLevel(market) * 100) + "%";
				info.addPara(str, opad, alertLevel > 0.4 ? Misc.getNegativeHighlightColor() : h, alertLevelStr);
			}
			
			// potential income
			if (!market.isPlayerOwned()) {
				String net = Misc.getDGSCredits(market.getNetIncome());
				String income = Misc.getDGSCredits(market.getIndustryIncome() + market.getExportIncome(false));
				String expense = Misc.getDGSCredits(market.getIndustryUpkeep());
				
				str = getString("intelDescLocalReport4");
				str = StringHelper.substituteToken(str, "$net", net);
				str = StringHelper.substituteToken(str, "$income", income);
				str = StringHelper.substituteToken(str, "$expenses", expense);
				label = info.addPara(str, opad);
				label.setHighlight(net, income, expense);
				label.setHighlightColors(
						market.getNetIncome() > 0? h : Misc.getNegativeHighlightColor(),
						Misc.getPositiveHighlightColor(),
						Misc.getNegativeHighlightColor()
				);
			}
		}
		
		if (lastAction != null) {
			info.addSectionHeading(getString("intelHeaderLastMessage"),
				Alignment.MID, opad);
			lastAction.addLastMessagePara(info, opad);
			info.addPara(Misc.getAgoStringForTimestamp(lastActionTimestamp) + ".", opad);
		}
		
		// dismiss button
		ButtonAPI button = info.addButton(StringHelper.getString("dismiss", true), 
					BUTTON_DISMISS, pf.getBaseUIColor(), pf.getDarkUIColor(),
					(int)(width), 20f, opad * 2f);
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (buttonId == BUTTON_ORDERS) {
			ui.showDialog(null, new AgentOrdersDialog(this, market, ui, false));
		} else if (buttonId == BUTTON_QUEUE_ORDER) {
			// If we are travelling somewhere, set that as our market
			MarketAPI targetMarket = this.market;
			if (currentAction != null && currentAction instanceof Travel) {
				targetMarket = ((Travel)currentAction).market;
			}
			ui.showDialog(null, new AgentOrdersDialog(this, targetMarket, ui, true));
		} else if (buttonId == BUTTON_ABORT) {
			currentAction.abort();
			pushActionQueue();
		} else if (buttonId == BUTTON_CANCEL_QUEUE) {
			nextAction.abort();
			setQueuedAction(null);
		} else if (buttonId == ProcureShip.BUTTON_CHANGE_DESTINATION) {
			ui.showDialog(null, new ProcureShipDestinationDialog(this, (ProcureShip)currentAction, market, ui));
		} else if (buttonId == BUTTON_DISMISS) {
			if (currentAction != null)
				currentAction.abort();
			currentAction = null;
			nextAction = null;
			lastAction = null;
			isDismissed = true;
			//sendUpdateIfPlayerHasIntel(UPDATE_DISMISSED, false);
			endAfterDelay();
			CovertOpsManager.getManager().removeAgent(this);
		}
		super.buttonPressConfirmed(buttonId, ui);
	}
	
	@Override
	public boolean doesButtonHaveConfirmDialog(Object buttonId) {
		return buttonId == BUTTON_ABORT || buttonId == BUTTON_CANCEL_QUEUE || buttonId == BUTTON_DISMISS;
	}
	
	@Override
	public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
		if (buttonId == BUTTON_ABORT) {
			int credits = currentAction.getAbortRefund();
			String creditsStr = Misc.getWithDGS(credits);
			String str = getString("intelPromptAbort");
			
			prompt.addPara(str, 0, Misc.getHighlightColor(), creditsStr);
		} else if (buttonId == BUTTON_CANCEL_QUEUE) {
			int credits = nextAction.getAbortRefund();
			String creditsStr = Misc.getWithDGS(credits);
			String str = getString("intelPromptAbortQueued");
			
			prompt.addPara(str, 0, Misc.getHighlightColor(), creditsStr);
		} else if (buttonId == BUTTON_DISMISS) {
			String str = getString("intelPromptDismiss");
			str = StringHelper.substituteToken(str, "$agentName", agent.getNameString());
			prompt.addPara(str, 0);
		}
	}
	
	protected String getName() {
		String str = StringHelper.getStringAndSubstituteToken("nex_agents", "intelTitle", "$name", agent.getNameString());
		if (isDead) {
			str += " - " + getString("lost", true);
		} else if (isDismissed) {
			str += " - " + getString("dismissed", true);
		} else if (listInfoParam == UPDATE_RECRUITED) {
			str += " - " + getString("recruited", true);
		} else if (listInfoParam == UPDATE_ARRIVED) {
			str += " - " + getString("intelTitleArrived");
		} else if (listInfoParam == UPDATE_LEVEL_UP) {
			str += " - " + getString("intelTitleLevelUp");
		}
		return str;
	}
	
	@Override
	public String getIcon() {
		return agent.getPortraitSprite();
	}
	
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		if (market != null)
			return market.getPrimaryEntity();
		if (currentAction != null && currentAction.getDefId().equals(CovertActionType.TRAVEL))
		{
			Travel travel = (Travel)currentAction;
			if (travel.from != null) {
				return travel.from.getPrimaryEntity();
			}
		}
		return null;
	}
	
	@Override
	public List<ArrowData> getArrowData(SectorMapAPI map) {
		if (currentAction != null && currentAction.getDefId().equals(CovertActionType.TRAVEL))
		{
			Travel travel = (Travel)currentAction;
			if (travel.from == null || travel.from.getPrimaryEntity() == null
					|| travel.market == null || travel.market.getPrimaryEntity() == null) {
				return null;
			}
			
			List<ArrowData> result = new ArrayList<ArrowData>();
			ArrowData arrow = new ArrowData(travel.from.getPrimaryEntity(), travel.market.getPrimaryEntity());
			arrow.color = Global.getSector().getPlayerFaction().getColor();
			arrow.width = 10f;
			result.add(arrow);
			
			return result;
		}
		
		return null;
	}
	
	public FactionAPI updateAgentDisplayedFaction() {
		FactionAPI faction = Global.getSector().getFaction(Factions.INDEPENDENT);
		
		if (Misc.isPlayerFactionSetUp()) {
			faction = Global.getSector().getPlayerFaction();
		}
		else if (!PlayerFactionStore.getPlayerFaction().isPlayerFaction()) {
			faction = PlayerFactionStore.getPlayerFaction();
		}
		agent.setFaction(faction.getId());
		return faction;
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(StringHelper.getString("nex_agents", "agents", true));
		return tags;
	}
	
	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_agents", id, ucFirst);
	}
}

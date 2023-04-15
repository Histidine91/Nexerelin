package exerelin.campaign.intel.diplomacy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.DiplomacyManager.DiplomacyEventDef;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.action.StrategicActionDelegate;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.Setter;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DiplomacyIntel extends BaseIntelPlugin implements StrategicActionDelegate {
	
	protected String eventId;
	protected String factionId1;
	protected String factionId2;
	protected MarketAPI market;
	@Getter protected ExerelinReputationAdjustmentResult reputation;
	protected float storedRelation;
	protected boolean isWar;
	protected boolean isPeace;
	private boolean seenByPlayer = false;
	private boolean shouldEndWhenSeen = false;

	@Getter	@Setter	protected StrategicAction strategicAction;
	protected Long timestampActual = Global.getSector().getClock().getTimestamp();

	public DiplomacyIntel(String eventId, String factionId1, String factionId2, MarketAPI market, ExerelinReputationAdjustmentResult reputation)
	{
		this.eventId = eventId;
		this.factionId1 = factionId1;
		this.factionId2 = factionId2;
		this.market = market;
		this.reputation = reputation;

		FactionAPI faction1 = getFaction(factionId1);
		storedRelation = faction1.getRelationship(factionId2);

		isWar = !reputation.wasHostile && reputation.isHostile;
		isPeace = reputation.wasHostile && !reputation.isHostile;
	}

	@Override
	public void endAfterDelay() {
		if (!seenByPlayer) {
			shouldEndWhenSeen = true;
			return; // don't do anything if not seen by player
		}
		super.endAfterDelay();
	}

	@Override
	public void reportMadeVisibleToPlayer() {
		seenByPlayer = true;
		if (shouldEndWhenSeen) {
			endAfterDelay();
		}
		//	Global.getSector().getCampaignUI().addMessage("reportMadeVisibleToPlayer() called in DiplomacyIntel");
	}

	public void addEvent() {
		boolean notify = shouldNotify();
		String commFacId = Misc.getCommissionFactionId();
		if (commFacId == null) commFacId = "";
		if (!notify && ExerelinModPlugin.isNexDev) {
			Global.getSector().getCampaignUI().addMessage("Suppressed diplomacy notification " 
					+ getName() + " due to filter level");
		}
		if (NexConfig.nexIntelQueued<=0 || NexConfig.nexIntelQueued==1) {
			if (NexConfig.nexIntelQueued<=0 ||
					isWar ||
					isPeace ||
					Factions.PLAYER.equals(factionId1) ||
					Factions.PLAYER.equals(factionId2) ||
					commFacId.equals(factionId1) ||
					commFacId.equals(factionId2)) {
				Global.getSector().getIntelManager().addIntel(this, !notify);
			}
			else {
				Global.getSector().getIntelManager().queueIntel(this);
			}
		}
		else {
			Global.getSector().getIntelManager().queueIntel(this);
		}
		Global.getSector().addScript(this);
		endAfterDelay();
	}
	//if nexIntelQueued is less or equal to 0, all intel will be added. if nexIntelQueued is 1, only the referenced
	//intel will be added. if nexIntelQueued is 2 or higher, all intel will be queued.
	
	protected static FactionAPI getFaction(String id)
	{
		return Global.getSector().getFaction(id);
	}
	
	protected DiplomacyEventDef getEventDef()
	{
		return DiplomacyManager.getEventByStage(eventId);
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {
		float pad = 0;
		NexUtilsFaction.addFactionNamePara(info, initPad, tc, getFaction(factionId1));
		NexUtilsFaction.addFactionNamePara(info, pad, tc, getFaction(factionId2));
		
		String relation = NexUtilsReputation.getRelationStr(storedRelation);
		Color relColor = NexUtilsReputation.getRelColor(storedRelation);
		String str = StringHelper.getStringAndSubstituteToken("exerelin_diplomacy", "intelRepCurrentShort",
				"$relationStr", relation);
		info.addPara(str, pad, tc, relColor, relation);
	}
	
	@Override
	public Color getTitleColor(ListInfoMode mode) {
		return Misc.getBasePlayerColor();
	}
	
	// text sidebar
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		FactionAPI faction1 = getFaction(factionId1);
		FactionAPI faction2 = getFaction(factionId2);
		
		info.addImages(width, 96, opad, opad, faction1.getLogo(), faction2.getLogo());
		
		List<Pair<String, String>> replace = new ArrayList<>();
		replace.add(new Pair<>("$market", market.getName()));
		replace.add(new Pair<>("$onOrAt", market.getOnOrAt()));
		
		StringHelper.addFactionNameTokensCustom(replace, "faction", faction1);
		StringHelper.addFactionNameTokensCustom(replace, "otherFaction", faction2);
		
		DiplomacyEventDef def = getEventDef();
		if (def == null)
		{
			info.addPara("ERROR: Failed to get diplomacy event def " + eventId, opad);
		}
		else
		{
			// flavor text
			String str = StringHelper.substituteTokens(def.desc, replace);
			LabelAPI para = info.addPara(str, opad);
			List<String> hl = new ArrayList<>();
			
			// highlights require a specific order that we can't count on
			/*
			addFactionHighlights(hl, faction1);
			addFactionHighlights(hl, faction2);
			para.setHighlight(hl.toArray(new String[0]));
			para.setHighlightColors(getFactionHighlightColors(faction1, faction2));
			*/
		}

		if (strategicAction != null) {
			info.addPara(StrategicAI.getString("intelPara_actionDelegateDesc"), opad, Misc.getHighlightColor(), strategicAction.getConcern().getName());
			info.addButton(StrategicAI.getString("btnGoIntel"), StrategicActionDelegate.BUTTON_GO_INTEL, width, 24, 3);
		}
		
		info.addSectionHeading(StringHelper.getString("exerelin_diplomacy", "intelHeader2"),
				Alignment.MID, opad);
		
		// display relationship change from event, and relationship following event
		addRelationshipChangePara(info, factionId1, factionId2, storedRelation, reputation, opad);
		
		// days ago
		if (timestampActual != null)
			info.addPara(Misc.getAgoStringForTimestamp(timestampActual) + ".", opad);
		
		// display current relationship
		String currRel = NexUtilsReputation.getRelationStr(faction1, faction2);
		String str = StringHelper.getString("exerelin_diplomacy", "intelRepCurrent");
		str = StringHelper.substituteToken(str, "$relationStr", currRel);
		info.addPara(str, opad, faction1.getRelColor(factionId2), currRel);
	}
	
	/**
	 * Creates a paragraph detailing the effect of a relationship change between two factions.
	 * @param info
	 * @param factionId1
	 * @param factionId2
	 * @param relations The final relationship after the change.
	 * @param adjustResult
	 * @param pad Padding.
	 */
	public static void addRelationshipChangePara(TooltipMakerAPI info, 
			String factionId1, String factionId2, float relations,
			ReputationAdjustmentResult adjustResult, float pad)
	{
		FactionAPI faction1 = getFaction(factionId1);
		FactionAPI faction2 = getFaction(factionId2);
		
		Color deltaColor = adjustResult.delta > 0 ? Global.getSettings().getColor("textFriendColor") : Global.getSettings().getColor("textEnemyColor");
		String delta = (int)Math.abs(adjustResult.delta * 100) + "";
		String newRel = NexUtilsReputation.getRelationStr(relations);
		String fn1 = NexUtilsFaction.getFactionShortName(factionId1);
		String fn2 = NexUtilsFaction.getFactionShortName(factionId2);
		String str = StringHelper.getString("exerelin_diplomacy", adjustResult.delta > 0 ?
				"intelRepResultPositive" : "intelRepResultNegative");
		str = StringHelper.substituteToken(str, "$faction1", fn1);
		str = StringHelper.substituteToken(str, "$faction2", fn2);
		str = StringHelper.substituteToken(str, "$deltaAbs", delta);
		str = StringHelper.substituteToken(str, "$newRelationStr", newRel);
		
		LabelAPI para = info.addPara(str, pad);
		para.setHighlight(fn1, fn2, delta, newRel);
		para.setHighlightColors(faction1.getBaseUIColor(), faction2.getBaseUIColor(), 
				deltaColor, NexUtilsReputation.getRelColor(relations));
	}
	
	/**
	 * If false, don't add to intel notification section on main GUI.
	 * @return
	 */
	public boolean shouldNotify() {
		if (isWar || isPeace) return true;
		
		int filterLevel = NexConfig.diplomacyEventFilterLevel;
		
		if (Factions.PLAYER.equals(factionId1) || Factions.PLAYER.equals(factionId2))
			return filterLevel <= 1;
		String commFacId = Misc.getCommissionFactionId();
		if (commFacId != null) {
			if (commFacId.equals(factionId1) || commFacId.equals(factionId2))
				return filterLevel <= 1;
		}
				
		return filterLevel <= 0;
	}
	
	/*
	protected static void addFactionHighlights(List<String> hl, FactionAPI faction)
	{
		String name = ExerelinUtilsFaction.getFactionShortName(faction);
		String nameWithArticle = faction.getDisplayNameLongWithArticle();
		String nameLong = faction.getDisplayNameLong();
		String nameLongWithArticle = faction.getDisplayNameLongWithArticle();
		
		hl.add(name);
		hl.add(nameWithArticle);
		hl.add(nameLong);
		hl.add(nameLongWithArticle);
		hl.add(Misc.ucFirst(name));
		hl.add(Misc.ucFirst(nameWithArticle));
		hl.add(Misc.ucFirst(nameLong));
		hl.add(Misc.ucFirst(nameLongWithArticle));
	}
	
	protected static Color[] getFactionHighlightColors(FactionAPI faction1, FactionAPI faction2)
	{
		Color[] array = new Color[16];
		for (int i=0; i<8; i++)
		{
			array[i] = faction1.getBaseUIColor();
		}
		for (int i=8; i<16; i++)
		{
			array[i] = faction2.getBaseUIColor();
		}
		return array;
	}
	*/

	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (buttonId == StrategicActionDelegate.BUTTON_GO_INTEL && strategicAction != null) {
			Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.INTEL, strategicAction.getAI());
		}
	}

	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}
	
	public String getName() {
		String str = StringHelper.getString("exerelin_diplomacy", "intelTitle");
		DiplomacyEventDef def = getEventDef();
		if (def != null)
			str += " - " + def.name;
		
		return str;
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(StringHelper.getString("diplomacy", true));
		tags.add(factionId1);
		tags.add(factionId2);
		return tags;
	}
	
	@Override
	protected float getBaseDaysAfterEnd() {
		if (isWar || isPeace)
			return 30;
		return 15;
	}
	
	@Override
	public String getIcon() {
		String id = "diplomacy";
		if (isWar)
			id = "war";
		else if (isPeace)
			id = "peace";
		
		return Global.getSettings().getSpriteName("intel", id);
	}
	
	@Override
	public String getSortString() {
		return "Diplomacy";
	}

	@Override
	public ActionStatus getStrategicActionStatus() {
		return ActionStatus.SUCCESS;
	}

	@Override
	public float getStrategicActionDaysRemaining() {
		return -1;
	}

	@Override
	public void abortStrategicAction() {
		// too late lol, do nothing
	}

	@Override
	public String getStrategicActionName() {
		return getName();
	}
}

package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MarketTransferIntel extends BaseIntelPlugin {
	
	protected MarketAPI market;
	protected String oldFactionId;
	protected String newFactionId;
	protected boolean isPlayerInvolved;
	protected boolean isCapture;
	protected List<String> factionsToNotify;
	protected float repChange;
	
	public MarketTransferIntel(MarketAPI market, String oldFactionId, String newFactionId, 
			boolean isCapture, boolean isPlayerInvolved, List<String> factionsToNotify, float repChange)
	{
		this.market = market;
		this.oldFactionId = oldFactionId;
		this.newFactionId = newFactionId;
		this.isPlayerInvolved = isPlayerInvolved;
		this.isCapture = isCapture;
		this.factionsToNotify = factionsToNotify;
		this.repChange = repChange;
		
		if (isPlayerInvolved) {
			TextPanelAPI text = null;
			if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null)
				text = Global.getSector().getCampaignUI().getCurrentInteractionDialog().getTextPanel();
			for (String factionId : factionsToNotify) {
				FactionAPI faction = Global.getSector().getFaction(factionId);
				NexUtilsReputation.adjustPlayerReputation(faction, repChange, null, text);
			}
		}
	}
	
	protected FactionAPI getFaction(String id)
	{
		return Global.getSector().getFaction(id);
	}
	
	// bullet points
	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		Color c = Misc.getBasePlayerColor();
		info.addPara(getName(), c, 0f);
		bullet(info);

		float initPad = 3f, pad = 0;
		Color tc = getBulletColorForMode(mode);
		Color h = Misc.getHighlightColor();
		
		addFactionBullet(info, "intelTransferBullet1", newFactionId, tc, initPad);
		addFactionBullet(info, "intelTransferBullet2", oldFactionId, tc, pad);
	}
	
	
	protected void addFactionBullet(TooltipMakerAPI info, String stringId, String factionId, 
			Color color, float pad)
	{
		FactionAPI faction = getFaction(factionId);
		String factionName = faction.getDisplayName();
		String bullet = StringHelper.getString("exerelin_markets", stringId);
		bullet = StringHelper.substituteToken(bullet, "$faction", factionName);
		info.addPara(bullet, pad, color, faction.getBaseUIColor(), factionName);
	}
	
	// text desc
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		Color h = Misc.getHighlightColor();
		
		FactionAPI newFaction = getFaction(newFactionId);
		FactionAPI oldFaction = getFaction(oldFactionId);
		
		info.addImages(width, 128, opad, opad, newFaction.getCrest(), oldFaction.getCrest());
		
		String strKey = "intelCaptureDesc";
		if (!isCapture) strKey = "intelTransferDesc";
		else if (isPlayerInvolved) strKey = "intelCapturePlayerDesc";
		
		String marketName = market.getName();
		String size = market.getSize() + "";
		String newName = newFaction.getDisplayNameWithArticle();
		String oldName = oldFaction.getDisplayNameWithArticle();
		
		Map<String, String> sub = new HashMap<>();
		sub.put("$newFaction", newName);
		sub.put("$oldFaction", oldName);
		sub.put("$NewFaction", Misc.ucFirst(newName));
		sub.put("$OldFaction", Misc.ucFirst(oldName));
		sub.put("$hasOrHave", newFaction.getDisplayNameHasOrHave());
		sub.put("$market", marketName);
		sub.put("$location", market.getContainingLocation().getNameWithLowercaseType());
		sub.put("$size", size);
		if (isPlayerInvolved) 
			sub.put("$player", Global.getSector().getPlayerPerson().getNameString());
		
		String str = StringHelper.getStringAndSubstituteTokens("exerelin_markets", strKey, sub);
		
		LabelAPI para = info.addPara(str, opad);
		if (isCapture)
		{
			para.setHighlight(newFaction.getDisplayNameWithArticleWithoutArticle(), 
					marketName, size, oldFaction.getDisplayNameWithArticleWithoutArticle());
			para.setHighlightColors(newFaction.getBaseUIColor(), h, h, oldFaction.getBaseUIColor());
		}
		else
		{
			para.setHighlight(oldFaction.getDisplayNameWithArticleWithoutArticle(), 
					marketName, size, newFaction.getDisplayNameWithArticleWithoutArticle());
			para.setHighlightColors(oldFaction.getBaseUIColor(), h, h, newFaction.getBaseUIColor());
		}
		
		info.addPara(Misc.getAgoStringForTimestamp(timestamp) + ".", opad);
		
		info.addSectionHeading(StringHelper.getString("exerelin_markets", "intelTransferFactionSizeHeader"),
				newFaction.getBaseUIColor(), newFaction.getDarkUIColor(), Alignment.MID, opad);
		
		addFactionCurrentInfoPara(info, newFactionId, opad);
		addFactionCurrentInfoPara(info, oldFactionId, opad);
	}
	
	protected void addFactionCurrentInfoPara(TooltipMakerAPI info, String factionId, float pad)
	{
		Color h = Misc.getHighlightColor();
		
		FactionAPI faction = getFaction(factionId);
		String factionName = Misc.ucFirst(faction.getDisplayName());
		String numMarkets = ExerelinUtilsFaction.getFactionMarkets(factionId, true).size() + "";
		String size = ExerelinUtilsFaction.getFactionMarketSizeSum(factionId, true) + "";
		
		Map<String, String> sub = new HashMap<>();
		sub.put("$faction", factionName);
		sub.put("$num", numMarkets);
		sub.put("$size", size);
		
		String str = StringHelper.getStringAndSubstituteTokens("exerelin_markets",
				"intelTransferFactionSizeEntry", sub);
		
		LabelAPI para = info.addPara(str, pad);
		para.setHighlight(factionName, numMarkets, size);
		para.setHighlightColors(faction.getBaseUIColor(), h, h);
	}
	
	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}
	
	protected String getName() {
		String str = StringHelper.getString("exerelin_markets", isCapture ? "intelCaptureTitle" : "intelTransferTitle");
		str = StringHelper.substituteToken(str, "$market", market.getName());
		
		return str;
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(StringHelper.getString("exerelin_markets", "intelTag"));
		tags.add(oldFactionId);
		tags.add(newFactionId);
		return tags;
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return Global.getSector().getFaction(newFactionId);
	}
	
	@Override
	protected float getBaseDaysAfterEnd() {
		return 30;
	}
	
	@Override
	public String getIcon() {
		return getFaction(newFactionId).getCrest();
	}
	
	@Override
	public String getSortString() {
		return "Capture";
	}
	
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		return market.getPrimaryEntity();
	}
}

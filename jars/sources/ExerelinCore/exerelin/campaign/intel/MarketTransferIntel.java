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
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MarketTransferIntel extends BaseIntelPlugin {
	
	public static final String MEMORY_KEY_REP_GAIN_COOLDOWN = "$nex_invasionRepCooldown";
	public static final float REP_GAIN_COOLDOWN = 90;
	
	protected MarketAPI market;
	protected String oldFactionId;
	protected String newFactionId;
	protected boolean isPlayerInvolved;
	protected boolean isCapture;
	protected List<String> factionsToNotify;
	protected float repChange;
	protected Integer creditsLost;
	
	public MarketTransferIntel(MarketAPI market, String oldFactionId, String newFactionId, 
			boolean isCapture, boolean isPlayerInvolved, List<String> factionsToNotify, 
			float repChange, Integer creditsLost)
	{
		this.market = market;
		this.oldFactionId = oldFactionId;
		this.newFactionId = newFactionId;
		this.isPlayerInvolved = isPlayerInvolved;
		this.isCapture = isCapture;
		this.factionsToNotify = factionsToNotify;
		this.repChange = repChange;
		this.creditsLost = creditsLost;
		
		if (isPlayerInvolved) {
			TextPanelAPI text = null;
			if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null)
				text = Global.getSector().getCampaignUI().getCurrentInteractionDialog().getTextPanel();
			
			boolean cooldown = market.getMemoryWithoutUpdate().contains(MEMORY_KEY_REP_GAIN_COOLDOWN);
			if (!isCapture || !cooldown) {
				for (String factionId : factionsToNotify) {
					float thisRep = repChange;
					if (isCapture && NexUtilsFaction.isPirateOrTemplarFaction(factionId))
						thisRep *= 0.5f;

					FactionAPI faction = Global.getSector().getFaction(factionId);
					NexUtilsReputation.adjustPlayerReputation(faction, thisRep, null, text);
				}
				market.getMemoryWithoutUpdate().set(MEMORY_KEY_REP_GAIN_COOLDOWN, true, REP_GAIN_COOLDOWN);
			} else {
				if (text != null) {
					String str = StringHelper.getString("exerelin_invasion", "repChangeCooldownMsg");
					text.setFontSmallInsignia();
					text.addPara(str, Misc.getGrayColor());
					text.setFontInsignia();
				}
			}
		}
	}
	
	protected static FactionAPI getFaction(String id)
	{
		return Global.getSector().getFaction(id);
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {
		float pad = 0;
		addFactionBullet(info, "intelTransferBullet1", newFactionId, tc, initPad);
		addFactionBullet(info, "intelTransferBullet2", oldFactionId, tc, pad);
		if (creditsLost != null) {
			info.addPara(StringHelper.getString("exerelin_markets", "intelCaptureBulletCredits"), 
					pad, Misc.getHighlightColor(), Misc.getDGSCredits(creditsLost));
		}
	}
	
	@Override
	public Color getTitleColor(ListInfoMode mode) {
		return Misc.getBasePlayerColor();
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
		str = Misc.ucFirst(str);
		
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
		
		if (creditsLost != null) {
			info.addPara(StringHelper.getString("exerelin_markets", "intelCaptureDescCredits"),
					opad, h, Misc.getWithDGS(creditsLost));
		}
		
		info.addPara(Misc.getAgoStringForTimestamp(timestamp) + ".", opad);
		
		info.addSectionHeading(StringHelper.getString("exerelin_markets", "intelTransferFactionSizeHeader"),
				newFaction.getBaseUIColor(), newFaction.getDarkUIColor(), Alignment.MID, opad);
		
		addFactionCurrentInfoPara(info, newFactionId, opad);
		addFactionCurrentInfoPara(info, oldFactionId, opad);
	}
	
	public static void addFactionCurrentInfoPara(TooltipMakerAPI info, String factionId, float pad)
	{
		Color h = Misc.getHighlightColor();
		
		FactionAPI faction = getFaction(factionId);
		String factionName = Misc.ucFirst(faction.getDisplayName());
		String numMarkets = NexUtilsFaction.getFactionMarkets(factionId, true).size() + "";
		String size = NexUtilsFaction.getFactionMarketSizeSum(factionId, true) + "";
		
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

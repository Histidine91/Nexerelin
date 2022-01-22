package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class FactionSpawnedOrEliminatedIntel extends BaseIntelPlugin {
	
	protected String factionId;
	protected MarketAPI market;
	protected EventType type;
	// TODO: always false currently as we don't know whether the player has actually won
	protected boolean playerVictory;
	protected boolean playerDefeat;
	
	
	public FactionSpawnedOrEliminatedIntel(String factionId, EventType type, 
			MarketAPI market, boolean playerVictory, boolean playerDefeat)
	{
		this.factionId = factionId;
		this.type = type;
		this.market = market;
		this.playerVictory = playerVictory;
		this.playerDefeat = playerDefeat;
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {		
		FactionAPI faction = Global.getSector().getFaction(factionId);
		String name = Misc.ucFirst(faction.getDisplayName());
		info.addPara(name, initPad, tc, faction.getBaseUIColor(), name);
	}
	
	@Override
	public Color getTitleColor(ListInfoMode mode) {
		return Misc.getBasePlayerColor();
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		FactionAPI faction = Global.getSector().getFaction(factionId);
		
		info.addImage(faction.getLogo(), width, 128, opad);
		
		String strKey;
		switch (type)
		{
			default:
			case ELIMINATED:
				strKey = "intel_descEliminated";
				break;
			case SPAWNED:
				strKey = "intel_descSpawned";
				break;
			case RESPAWNED:
				strKey = "intel_descRespawned";
				break;
		}
		
		Map<String, String> replace = new HashMap<>();
		replace.put("$theFaction", faction.getDisplayNameWithArticle());
		replace.put("$TheFaction", Misc.ucFirst(faction.getDisplayNameWithArticle()));
		replace.put("$hasOrHave", faction.getDisplayNameHasOrHave());
		replace.put("$isOrAre", faction.getDisplayNameIsOrAre());
		replace.put("$market", market == null ? "<" + StringHelper.getString("unknown") + ">" 
				: market.getName());
		String str = StringHelper.getStringAndSubstituteTokens("exerelin_factions", 
					strKey, replace);
		
		info.addPara(str, opad, faction.getBaseUIColor(), faction.getDisplayNameWithArticleWithoutArticle());
		
		if (playerVictory)
		{
			FactionAPI playerFaction = PlayerFactionStore.getPlayerFaction();
			str = StringHelper.getString("exerelin_factions", "intel_descWon");
			replace = new HashMap<>();
			replace.put("$theFaction", faction.getDisplayNameWithArticle());
			replace.put("$TheFaction", Misc.ucFirst(faction.getDisplayNameWithArticle()));
			replace.put("$playerFaction", playerFaction.getDisplayName());
			replace.put("$PlayerFaction", Misc.ucFirst(playerFaction.getDisplayName()));
			info.addPara(str, opad, playerFaction.getBaseUIColor(),
					playerFaction.getDisplayNameWithArticleWithoutArticle());
		}
		else if (playerDefeat)
		{
			info.addPara(StringHelper.getString("exerelin_factions", "intel_descLost"), opad);
		}
		
		info.addPara(Misc.getAgoStringForTimestamp(timestamp) + ".", opad);
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return Global.getSector().getFaction(factionId);
	}
	
	@Override
	public String getSmallDescriptionTitle() {
		return getName();
	}
	
	protected String getName() {
		String key;
		switch (type)
		{
			default:
			case ELIMINATED:
				key = "intel_titleEliminated";
				break;
			case SPAWNED:
				key = "intel_titleSpawned";
				break;
			case RESPAWNED:
				key = "intel_titleRespawned";
				break;
		}
		
		String str = StringHelper.getString("exerelin_factions", key);
		
		return str;
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(factionId);
		return tags;
	}
	
	@Override
	public String getIcon() {
		return Global.getSector().getFaction(factionId).getCrest();
	}
	
	@Override
	public String getSortString() {
		//return "aaaa";	// put at the top - doesn't work
		return "Faction Update";
	}
	
	@Override
	protected float getBaseDaysAfterEnd() {
		return 365;
	}
	
	public enum EventType {
		ELIMINATED, SPAWNED, RESPAWNED
	}
}

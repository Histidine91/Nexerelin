package exerelin.campaign.intel.diplomacy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_TransferMarket;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.intel.missions.ConquestMissionIntel;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RequestMarketIntel extends TimedDiplomacyIntel {

	public static final float COOLDOWN = 365;
	public static final String MEM_KEY_COOLDOWN = "$nex_requestMarket_cooldown";

	@Getter	protected MarketAPI market;
	protected int credits;

	public RequestMarketIntel(MarketAPI market, String factionId)
	{
		super(MathUtils.getRandomNumberInRange(90, 120));
		this.factionId = factionId;
		this.market = market;
	}
	
	public void init() {
		this.setImportant(true);
		Global.getSector().getIntelManager().addIntel(this);
		Global.getSector().addScript(this);
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {
		NexUtilsFaction.addFactionNamePara(info, initPad, tc, getFactionForUIColors());
	}

	@Override
	public void createGeneralDescription(TooltipMakerAPI info, float width, float opad) {
		Color h = Misc.getHighlightColor();

		FactionAPI faction = Global.getSector().getFaction(factionId);
		FactionAPI faction2 = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());

		info.addImages(width, 96, opad, opad, faction.getLogo(), faction2.getLogo());

		Map<String, String> replace = new HashMap<>();

		String factionName = faction.getDisplayNameWithArticle();
		String days = Math.round(DiplomacyBrain.CEASEFIRE_LENGTH) + "";
		replace.put("$days", days);
		replace.put("$market", market.getName());
		replace.put("$theFaction", factionName);
		replace.put("$TheFaction", Misc.ucFirst(factionName));
		replace.put("$isOrAre", faction.getDisplayNameIsOrAre());
		replace.put("$onOrAt", market.getOnOrAt());

		//StringHelper.addFactionNameTokensCustom(replace, "otherFaction", faction2);

		String str = StringHelper.getStringAndSubstituteTokens("exerelin_diplomacy", "intelReturnMarketDesc", replace);
		LabelAPI label = info.addPara(str, opad);
		label.setHighlight(faction.getDisplayNameWithArticleWithoutArticle(), market.getName());
		label.setHighlightColors(faction.getBaseUIColor(), h);
		str = StringHelper.getStringAndSubstituteTokens("exerelin_diplomacy", "intelReturnMarketDesc2", replace);
		info.addPara(str, opad);
		str = StringHelper.getString("exerelin_diplomacy", "intelReturnMarketDesc3");
		info.addPara(str, opad, h, Misc.getWithDGS(ConquestMissionIntel.calculateReward(market, true)), String.format("%.0f", getRepChange() * 100));
	}

	@Override
	public void createOutcomeDescription(TooltipMakerAPI info, float width, float opad) {
		FactionAPI faction = Global.getSector().getFaction(factionId);
		float pad = 3;

		boolean accepted = state == 1;
		String acceptOrReject = accepted ? StringHelper.getString("accepted") : StringHelper.getString("rejected");
		Color hl = accepted ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
		String str = StringHelper.getString("exerelin_diplomacy", "intelCeasefireDescResult");
		str = StringHelper.substituteToken(str, "$acceptedOrRejected", acceptOrReject);
		info.addPara(str, opad, hl, acceptOrReject);

		if (repResult != null) {
			// display relationship change from event, and relationship following event
			Color deltaColor = repResult.delta > 0 ? Global.getSettings().getColor("textFriendColor") : Global.getSettings().getColor("textEnemyColor");
			String delta = (int)Math.abs(repResult.delta * 100) + "";
			String newRel = NexUtilsReputation.getRelationStr(storedRelation);
			String fn = NexUtilsFaction.getFactionShortName(factionId);
			str = StringHelper.getString("exerelin_diplomacy", "intelRepResultPositivePlayer");
			str = StringHelper.substituteToken(str, "$faction", fn);
			str = StringHelper.substituteToken(str, "$deltaAbs", delta);
			str = StringHelper.substituteToken(str, "$newRelationStr", newRel);

			bullet(info);
			LabelAPI para = info.addPara(str, opad);
			para.setHighlight(fn, delta, newRel);
			para.setHighlightColors(faction.getBaseUIColor(),
					deltaColor, NexUtilsReputation.getRelColor(storedRelation));
			info.addPara(StringHelper.getString("exerelin_diplomacy", "intelReturnMarketCredits"), pad, Misc.getHighlightColor(), Misc.getDGSCredits(credits));
			unindent(info);
		}

		// days ago
		info.addPara(Misc.getAgoStringForTimestamp(timestamp) + ".", opad);
	}

	protected float getRepChange() {
		float rep = 0.02f * market.getSize() + Nex_TransferMarket.getRepChange(market, factionId).getModifiedValue()/100f;
		if (rep < 0.01f) rep = 0.01f;
		return rep;
	}

	@Override
	public void acceptImpl() {
		credits = ConquestMissionIntel.calculateReward(market, true);
		FactionAPI faction = Global.getSector().getFaction(factionId);
		float rep = getRepChange();

		SectorManager.transferMarket(market, faction, market.getFaction(), false, false, null, 0);
		repResult = NexUtilsReputation.adjustPlayerReputation(faction, rep);
		storedRelation = faction.getRelationship(PlayerFactionStore.getPlayerFactionId());
		Global.getSector().getPlayerFleet().getCargo().getCredits().add(credits);
		Global.getSoundPlayer().playUISound("ui_rep_raise", 1, 1);
	}

	@Override
	protected void rejectImpl() {}

	@Override
	public void onExpire() {
		setState(-1);
	}

	@Override
	public void endAfterDelay() {
		super.endAfterDelay();
		if (state != 1) {
			market.getMemoryWithoutUpdate().set(MEM_KEY_COOLDOWN, true, COOLDOWN);
		}
	}

	@Override
	protected void advanceImpl(float amount) {
		super.advanceImpl(amount);
	}

	@Override
	public String getSmallDescriptionTitle() {
		return getName(false);
	}

	protected String getName() {
		return getName(true);
	}
	
	public String getName(boolean withStatus) {
		String str = StringHelper.getString("exerelin_diplomacy", "intelReturnMarketTitle");
		str = String.format(str, market.getName());
		if (!withStatus) return str;

		if (listInfoParam == EXPIRED_UPDATE)
			str += " - " + StringHelper.getString("expired");
		else if (state == 1)
			str += " - " + StringHelper.getString("accepted");
		else if (state == -1)
			str += " - " + StringHelper.getString("rejected");
		
		return str;
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(Tags.INTEL_AGREEMENTS);
		tags.add(StringHelper.getString("diplomacy", true));
		tags.add(factionId);
		tags.add(PlayerFactionStore.getPlayerFactionId());
		return tags;
	}
	
	@Override
	public String getCommMessageSound() {
		return "nex_ui_ceasefire_prompt";
	}
		
	@Override
	public String getIcon() {
		return getFactionForUIColors().getCrest();
	}
	
	@Override
	public String getSortString() {
		return StringHelper.getString("diplomacy", true);
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return Global.getSector().getFaction(factionId);
	}

	@Override
	public String getStrategicActionName() {
		return getName(false);
	}
}

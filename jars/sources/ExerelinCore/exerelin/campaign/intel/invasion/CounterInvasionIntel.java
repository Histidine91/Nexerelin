package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.groundbattle.GBUtils;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.Map;
import java.util.Set;

/**
 * Used for 'counter-invasions' in response to a player invasion of the faction's market.
 */
public class CounterInvasionIntel extends InvasionIntel {
	
	public static final float SOURCE_GROUND_STR_MULT = 0.35f;
	public static final float TARGET_GROUND_STR_MAX_MULT = 1.25f;
	
	protected GroundBattleIntel triggerBattle;
	
	public CounterInvasionIntel(GroundBattleIntel triggerBattle, FactionAPI attacker, MarketAPI from, MarketAPI target, float fp, float orgDur) {
		super(attacker, from, target, fp, orgDur);
		this.triggerBattle = triggerBattle;
		setAbortIfNonHostile(false);
	}
	
	public void setMarineCount() {
		float targetStr = GBUtils.estimateTotalDefenderStrength(triggerBattle, false);
		float originStr = GBUtils.estimateTotalDefenderStrength(from, triggerBattle.getSide(true).getFaction(), true);
		
		float str = Math.min(targetStr * TARGET_GROUND_STR_MAX_MULT, originStr * SOURCE_GROUND_STR_MULT);
		
		marinesTotal = 10 * Math.round(str/10);
	}
	
	@Override
	public String getActionName() {
		return StringHelper.getString("exerelin_invasion", "counterInvasion");
	}
	
	@Override
	public String getActionNameWithArticle() {
		return StringHelper.getString("exerelin_invasion", "theCounterInvasion");
	}
	
	@Override
	public String getForceType() {
		return StringHelper.getString("exerelin_invasion", "counterInvasionForce");
	}
	
	@Override
	public String getForceTypeWithArticle() {
		return StringHelper.getString("exerelin_invasion", "theCounterInvasionForce");
	}
	
	@Override
	protected String getDescString() {
		return StringHelper.getString("exerelin_invasion", "intelDescCounterInvasion");
	}
	
	protected void addOutcomeBullet(TooltipMakerAPI info, Color color, float pad) 
	{
		if (outcome == OffensiveOutcome.NO_LONGER_HOSTILE && triggerBattle != null 
				&& triggerBattle.getOutcome() == GroundBattleIntel.BattleOutcome.DEFENDER_VICTORY)
		{
			//String str = StringHelper.getStringAndSubstituteToken("exerelin_invasion", 
			//		key, "$target", target.getName());
			//info.addPara(str, initPad, tc, other.getBaseUIColor(), target.getName());
			String str = StringHelper.getString("nex_fleetIntel", "bulletWonGroundBattle");
			str = StringHelper.substituteToken(str, "$forceType", getForceType(), true);
			str = StringHelper.substituteToken(str, "$action", getActionName(), true);
			info.addPara(str, color, pad);
		}
		else {
			super.addOutcomeBullet(info, color, pad);
		}
	}
	
	@Override
	protected boolean addCustomOutcomeDesc(TooltipMakerAPI info, Map<String, String> sub) {
		float opad = 10;
		
		if (outcome == OffensiveOutcome.NO_LONGER_HOSTILE && triggerBattle != null 
				&& triggerBattle.getOutcome() == GroundBattleIntel.BattleOutcome.DEFENDER_VICTORY)
		{
			String string = StringHelper.getString("nex_fleetIntel", "outcomeWonGroundBattle");
			string = StringHelper.substituteToken(string, "$target", target.getName());
			
			info.addPara(string, opad);
			return true;
		}
		
		return super.addCustomOutcomeDesc(info, sub);
	}
	
	@Override
	public void addStandardStrengthComparisons(TooltipMakerAPI info, MarketAPI target, FactionAPI targetFaction, boolean withGround, 
			boolean withBombard, String raid, String raids) {
		float opad = 10;
		String str = StringHelper.getString("exerelin_invasion", "intelCounterInvasionStrength");
		int marineEstimate = Math.round(marinesTotal/100) * 100;
		if (marineEstimate <= 0) marineEstimate = 100;
		
		info.addPara(str, opad, Misc.getHighlightColor(), marineEstimate + "");
	}

	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		if (triggerBattle != null && triggerBattle.isPlayerAttacker() != null) {
			tags.add(Tags.INTEL_COLONIES);
		}
		return tags;
	}
}

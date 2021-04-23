package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.BaseDisruptIndustry;
import exerelin.campaign.intel.missions.DisruptMissionManager.TargetEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

public class Nex_DisruptIndustry extends BaseDisruptIndustry {
	
	public static Logger log = Global.getLogger(Nex_DisruptIndustry.class);
	@Deprecated public static final float BASE_MARINES_REQUIRED = 120;
	@Deprecated public static final float MAX_MULT = 15;
	
	protected TargetEntry target;
	
	@Override
	protected void createBarGiver(MarketAPI createdAt) {
		List<String> posts = new ArrayList<String>();
		posts.add(Ranks.POST_AGENT);
		posts.add(Ranks.POST_TRADER);
		posts.add(Ranks.POST_MERCHANT);
		posts.add(Ranks.POST_PORTMASTER);
		posts.add(Ranks.POST_INVESTOR);
		posts.add(Ranks.POST_EXECUTIVE);
		posts.add(Ranks.POST_SENIOR_EXECUTIVE);
		if (createdAt.getSize() >= 6) {
			posts.add(Ranks.POST_ADMINISTRATOR);
		}
		String post = pickOne(posts);
		if (post == null) return;
		
		setGiverTags(Tags.CONTACT_TRADE);
		setGiverPost(post);
		findOrCreateGiver(createdAt, false, false);
	}
	
	@Override
	public MarketAPI pickMarket() {
		FactionAPI faction = getPerson().getFaction();
		int min = 0, max = 999;
		switch (getPerson().getImportance()) {
			case VERY_LOW:
				max = 4;
				break;
			case LOW:
				min = 3;
				max = 5;
				break;
			case MEDIUM:
				min = 4;
				max = 6;
				break;
			case HIGH:
				min = 5;
				break;
			case VERY_HIGH:
				min = 6;
				break;
		}
		
		TargetEntry target = DisruptMissionManager.getTarget(faction, min, max);
		if (target != null) {
			this.target = target;
			industry = target.industry;
			return target.market;
		}
		return null;
	}
	
	@Override
	protected void updateInteractionDataImpl() {
		super.updateInteractionDataImpl();
		String id = getMissionId();
		set("$" + id + "_targetFaction", market.getFaction().getDisplayName());
		set("$" + id + "_targetFactionColor", market.getFaction().getBaseUIColor());
	}
	
	// we'll pick our own target industry
	@Override
	protected String[] getTargetIndustries() {
		return new String[]{};
	}
	
	@Override
	protected CreditReward getRewardTier() {
		return CreditReward.HIGH;
	}
	
	// no longer need difficulty credit reward, vanilla has that functionality
	/*
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		boolean createResult = super.create(createdAt, barEvent);
		if (!createResult) return false;
		
		int numMarines = getMarinesRequiredToDisrupt(market, industry, disruptDays);
		float mult = numMarines/BASE_MARINES_REQUIRED;
		if (mult < 1) mult = 1;
		if (mult > MAX_MULT) mult = MAX_MULT;
		
		setCreditReward(getRewardTier());
		setCreditReward(Math.round(creditReward * mult));
		return true;
	}
	*/
	
	@Override
	public void setCurrentStage(Object next, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		super.setCurrentStage(next, dialog, memoryMap);		
	}

	@Override
	protected boolean requireFactionHostile() {
		return false;
	}
	

	@Override
	protected void addExtraTriggers(MarketAPI createdAt) {
		if (market.getSize() <= 4) {
			triggerCreateMediumPatrolAroundMarket(market, Stage.DISRUPT, 0f);
		} else if (market.getSize() <= 6) {
			triggerCreateLargePatrolAroundMarket(market, Stage.DISRUPT, 0f);
		} else {
			triggerCreateMediumPatrolAroundMarket(market, Stage.DISRUPT, 0f);
			triggerCreateLargePatrolAroundMarket(market, Stage.DISRUPT, 0f);
		}
	}
}

package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.Misc;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Exerelin_IsBaseOfficial extends BaseCommandPlugin {
	
	public static final Set<String> OFFICIAL_POSTS = new HashSet<>();
	public static final Set<String> TRADER_POSTS = new HashSet<>();
	
	static {
		OFFICIAL_POSTS.add(Ranks.POST_BASE_COMMANDER);
		OFFICIAL_POSTS.add(Ranks.POST_OUTPOST_COMMANDER);
		OFFICIAL_POSTS.add(Ranks.POST_PORTMASTER);
		OFFICIAL_POSTS.add(Ranks.POST_STATION_COMMANDER);
	
		TRADER_POSTS.add(Ranks.POST_BASE_COMMANDER);
		TRADER_POSTS.add(Ranks.POST_OUTPOST_COMMANDER);
		TRADER_POSTS.add(Ranks.POST_PORTMASTER);
		TRADER_POSTS.add(Ranks.POST_STATION_COMMANDER);
		TRADER_POSTS.add(Ranks.POST_SUPPLY_MANAGER);
		TRADER_POSTS.add(Ranks.POST_SUPPLY_OFFICER);
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		
		String post = memoryMap.get(MemKeys.LOCAL).getString("$postId");
		if (post == null) return false;
		return OFFICIAL_POSTS.contains(post);
	}
}

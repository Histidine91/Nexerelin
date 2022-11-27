package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.Misc;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Nex_IsBaseOfficial extends BaseCommandPlugin {
	
	public static final Set<String> COMMAND_POSTS = new HashSet<>();
	public static final Set<String> MILITARY_POSTS = new HashSet<>();
	public static final Set<String> TRADER_POSTS = new HashSet<>();
	public static final Set<String> ADMIN_POSTS = new HashSet<>();
	
	static {
		COMMAND_POSTS.add(Ranks.POST_BASE_COMMANDER);
		COMMAND_POSTS.add(Ranks.POST_STATION_COMMANDER);
		COMMAND_POSTS.add(Ranks.POST_OUTPOST_COMMANDER);
		COMMAND_POSTS.add(Ranks.POST_PORTMASTER);
		
		MILITARY_POSTS.add(Ranks.POST_BASE_COMMANDER);
		MILITARY_POSTS.add(Ranks.POST_STATION_COMMANDER);
		MILITARY_POSTS.add(Ranks.POST_OUTPOST_COMMANDER);
		MILITARY_POSTS.add(Ranks.POST_ADMINISTRATOR);
		
		ADMIN_POSTS.add(Ranks.POST_BASE_COMMANDER);
		ADMIN_POSTS.add(Ranks.POST_STATION_COMMANDER);
		ADMIN_POSTS.add(Ranks.POST_OUTPOST_COMMANDER);
		ADMIN_POSTS.add(Ranks.POST_PORTMASTER);
		ADMIN_POSTS.add(Ranks.POST_ADMINISTRATOR);
		ADMIN_POSTS.add("evaOfficial");	// used by some UAF admins
		
		TRADER_POSTS.add(Ranks.POST_STATION_COMMANDER);
		TRADER_POSTS.add(Ranks.POST_OUTPOST_COMMANDER);
		TRADER_POSTS.add(Ranks.POST_PORTMASTER);
		TRADER_POSTS.add(Ranks.POST_SUPPLY_MANAGER);
		TRADER_POSTS.add(Ranks.POST_SUPPLY_OFFICER);
	}
	
	// note: administrator appears in the absence of all of the following: 
	//	base commander (military base)
	//	station commander (orbital station)
	//  outpost commander (outpost)
	//	portmaster (spaceport)
	//	supply officer (base, station, outpost, spaceport)
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) 
	{
		String post;
		try {	// stupid-ass workaround for unexplained NPE when interacting with Remnant stations while non-hostile
			post = memoryMap.get(MemKeys.LOCAL).getString("$postId");
		} catch (NullPointerException ex) {
			return false;
		}
		
		if (post == null) return false;
		
		String arg = params.get(0).getString(memoryMap);
		return isOfficial(post, arg.toLowerCase(Locale.ROOT));
	}
	
	public static boolean isOfficial(String postId, String type) {
		switch (type) {
			case "command":
				return COMMAND_POSTS.contains(postId);
			case "military":
				return MILITARY_POSTS.contains(postId);
			case "admin":
				return ADMIN_POSTS.contains(postId);
			case "trade":
			case "trader":
				return TRADER_POSTS.contains(postId);
			case "any":
			default:
				return COMMAND_POSTS.contains(postId) || MILITARY_POSTS.contains(postId) 
						|| TRADER_POSTS.contains(postId) || ADMIN_POSTS.contains(postId);
		}
	}
}

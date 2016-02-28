package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.InteractionDialogImageVisual;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExerelinShowDefaultVisual extends BaseCommandPlugin {
	
	public static final Map<String, String> CUSTOM_MARKET_IMAGES = new HashMap<>();
	public static final Map<String, String> CUSTOM_MARKET_TAG_IMAGES = new HashMap<>();
	public static final Set<String> CUSTOM_MARKET_TAGS = new HashSet<>();
	
	static {
		CUSTOM_MARKET_IMAGES.put("tiandong_shanghai", "vacuum_colony");
		CUSTOM_MARKET_IMAGES.put("tiandong_macau", "orbital");
		CUSTOM_MARKET_IMAGES.put("tiandong_fuxin", "orbital");
		CUSTOM_MARKET_IMAGES.put("tiandong_chaozhou", "orbital");
		CUSTOM_MARKET_IMAGES.put("tiandong_mogui", "orbital");
		
		CUSTOM_MARKET_TAGS.add("shanghai");
		
		CUSTOM_MARKET_TAG_IMAGES.put("shanghai", "vacuum_colony");
	}

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		
		SectorEntityToken target = dialog.getInteractionTarget();
		String entityId = target.getId();
		if (CUSTOM_MARKET_IMAGES.containsKey(entityId)) {
			dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual("illustrations", CUSTOM_MARKET_IMAGES.get(entityId), 640, 400));
		} else if (target.getCustomInteractionDialogImageVisual() != null) {
			dialog.getVisualPanel().showImageVisual(target.getCustomInteractionDialogImageVisual());
		} else {
			if (target instanceof PlanetAPI) {
				dialog.getVisualPanel().showPlanetInfo((PlanetAPI) target);
			} else if (target instanceof CampaignFleetAPI) {
				CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
				CampaignFleetAPI otherFleet = (CampaignFleetAPI) target;
				//dialog.getVisualPanel().setVisualFade(0.25f, 0.25f);
				//dialog.getVisualPanel().showFleetInfo((String)null, playerFleet, (String)null, otherFleet, null);
				if (playerFleet != otherFleet)
					showFleetInfo(dialog, playerFleet, otherFleet);
			}
			else {
				MemoryAPI mem = memoryMap.get(MemKeys.MARKET);
				for (String tag : CUSTOM_MARKET_TAGS) {
					if (mem.contains(tag) && CUSTOM_MARKET_TAG_IMAGES.containsKey(tag))
						dialog.getVisualPanel().showImageVisual(new InteractionDialogImageVisual("illustrations", CUSTOM_MARKET_TAG_IMAGES.get(tag), 640, 400));
				}
			}
		}
		
		
	
		return true;
	}
	
	protected void showFleetInfo(InteractionDialogAPI dialog, CampaignFleetAPI player, CampaignFleetAPI other) {
		BattleAPI b = player.getBattle();
		if (b == null) b = other.getBattle();
		if (b != null && b.isPlayerInvolved()) {
			String titleOne = "Your forces";
			if (b.isPlayerInvolved() && b.getPlayerSide().size() > 1) {
				titleOne += ", with allies";
			}
			if (!Global.getSector().getPlayerFleet().isValidPlayerFleet()) {
				titleOne = "Allied forces";
			}
			String titleTwo = null;
			if (b.getPrimary(b.getNonPlayerSide()) != null) {
				titleTwo = b.getPrimary(b.getNonPlayerSide()).getNameWithFactionKeepCase();
			}
			if (b.getNonPlayerSide().size() > 1) titleTwo += ", with allies";
			dialog.getVisualPanel().showFleetInfo(titleOne, b.getPlayerCombined(), Misc.ucFirst(titleTwo), b.getNonPlayerCombined(), null);
		} else {
			if (b != null) {
				String titleOne = b.getPrimary(b.getSideOne()).getNameWithFactionKeepCase();
				if (b.getSideOne().size() > 1) titleOne += ", with allies";
				String titleTwo = b.getPrimary(b.getSideTwo()).getNameWithFactionKeepCase();
				if (b.getSideTwo().size() > 1) titleTwo += ", with allies";
				
				FleetEncounterContext fake = new FleetEncounterContext();
				fake.setBattle(b);
				dialog.getVisualPanel().showPreBattleJoinInfo(null, player, Misc.ucFirst(titleOne), Misc.ucFirst(titleTwo), fake);
			} else {
				dialog.getVisualPanel().showFleetInfo((String)null, player, (String)null, other, null);
			}
		}
	}

}
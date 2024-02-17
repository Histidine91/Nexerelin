package exerelin.campaign.econ;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.SectorManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import lombok.extern.log4j.Log4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Log4j
public class GroundPoolManager extends ResourcePoolManager {

	public static final float POOL_PER_MARINE = 0.1f;

	public static GroundPoolManager getManager() {
		return (GroundPoolManager) Global.getSector().getPersistentData().get("nex_groundPoolManager");
	}

	@Override
	public Map<String, Float> getCommodityValues() {
		return ResourcePoolManager.COMMODITIES_GROUND;
	}

	@Override
	public String getDataKey() {
		return "nex_groundPoolManager";
	}

	@Override
	public String getPointsLastTickMemoryKey() {
		return "$nex_groundPoolPointsLastTick";
	}
	
	/*
	============================================================================
	// start of GUI stuff
	============================================================================
	*/
	
	public void createFactionTable(TooltipMakerAPI tooltip, float width, float pad) 
	{
		width -= MARGIN;
		
		List<String> names = StringHelper.commodityIdListToCommodityNameList(COMMODITIES_SPACE.keySet());
		String str = String.format(getString("desc"), StringHelper.writeStringCollection(names, true, true));
		tooltip.addPara(str, pad, Misc.getHighlightColor(), names.toArray(new String[0]));
		
		names = StringHelper.commodityIdListToCommodityNameList(COMMODITIES_GROUND.keySet());
		str = String.format(getString("desc2"), StringHelper.writeStringCollection(names, true, true));
		tooltip.addPara(str, pad, Misc.getHighlightColor(), names.toArray(new String[0]));		
		
		tooltip.addSectionHeading(getString("tableHeader"), com.fs.starfarer.api.ui.Alignment.MID, pad);
		
		float cellWidth = 0.2f * width;
		tooltip.beginTable(getFactionForUIColors(), 20, StringHelper.getString("faction", true), 0.2f * width,
				getString("tablePool"), cellWidth,
				getString("tableIncrement"), cellWidth,
				getString("tablePool2"), cellWidth,
				getString("tableIncrement"), cellWidth
		);
		
		List<FactionAPI> factions = NexUtilsFaction.factionIdsToFactions(SectorManager.getLiveFactionIdsCopy());
		Collections.sort(factions, Nex_FactionDirectoryHelper.NAME_COMPARATOR_PLAYER_FIRST);
		
		for (FactionAPI faction : factions) {
			String factionId = faction.getId();
			NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
			if (!conf.playableFaction || conf.disableDiplomacy) continue;
			
			List<Object> rowContents = new ArrayList<>();
			
			// add faction
			//rowContents.add(com.fs.starfarer.api.ui.Alignment.MID);
			rowContents.add(faction.getBaseUIColor());
			rowContents.add(Misc.ucFirst(faction.getDisplayName()));
			
			// current pool
			float pool = getCurrentPoolInternal(factionId);
			rowContents.add(Misc.getWithDGS(pool));
			// last increment
			float increment = getPointsLastTick(faction);
			rowContents.add(String.format("%.1f", increment));
			
			// invasion points
			float invPoints = InvasionFleetManager.getManager().getSpawnCounter(factionId);
			rowContents.add(Misc.getWithDGS(invPoints));
			// last increment
			float increment2 = InvasionFleetManager.getPointsLastTick(faction);
			rowContents.add(Misc.getWithDGS(increment2));
			
			tooltip.addRow(rowContents.toArray());
		}
		
		tooltip.addTable("", 0, pad);
	}
	
	@Override
	public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
		TooltipMakerAPI superheaderHolder = panel.createUIElement(width/3, 40, false);
		TooltipMakerAPI superheader = superheaderHolder.beginImageWithText(getIcon(), 40);
		superheader.setParaOrbitronVeryLarge();
		superheader.addPara(getName(), 3);
		superheaderHolder.addImageWithText(3);
		
		panel.addUIElement(superheaderHolder).inTL(width*0.4f, 0);
		
		TooltipMakerAPI tableHolder = panel.createUIElement(width, 600, true);
		
		createFactionTable(tableHolder, width, 10);
		panel.addUIElement(tableHolder).inTL(3, 48);
	}

	@Override
	public boolean isHidden() {
		return true;
	}
}

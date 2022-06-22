package exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.RevengeanceManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.intel.fleets.VengeanceFleetIntel;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.extern.log4j.Log4j;

@Log4j
public class DebugIntel extends BaseIntelPlugin {
		
	public static final float MARGIN = 40;
	public static final String DATA_KEY = "nex_debugIntel";
	
	public DebugIntel init() {
		Global.getSector().getIntelManager().addIntel(this);
		Global.getSector().getPersistentData().put(DATA_KEY, this);
		this.setImportant(true);
		return this;
	}
	
	public static DebugIntel getIntel() {
		return (DebugIntel)Global.getSector().getPersistentData().get(DATA_KEY);
	}
	
	// runcode exerelin.DebugIntel.createIntel();
	public static DebugIntel createIntel() {
		DebugIntel intel = new DebugIntel();
		intel.init();
		Global.getSector().getPersistentData().put(DATA_KEY, intel);
		return intel;
	}
		
	/*
	============================================================================
	// start of GUI stuff
	============================================================================
	*/
	
	public void createVengeanceTable(TooltipMakerAPI tooltip, float width, float pad) 
	{
		width -= MARGIN;
		
		tooltip.addSectionHeading("Vengeance debug", com.fs.starfarer.api.ui.Alignment.MID, pad);
		
		float cellWidth = 0.2f * width;
		tooltip.beginTable(getFactionForUIColors(), 20, StringHelper.getString("faction", true), 0.2f * width,
				"Vengeance points", cellWidth,
				"Vengeance stage", cellWidth,
				"Escalation level", cellWidth,
				"Max escalation level", cellWidth
		);
		
		List<FactionAPI> factions = NexUtilsFaction.factionIdsToFactions(SectorManager.getLiveFactionIdsCopy());
		Collections.sort(factions, Nex_FactionDirectoryHelper.NAME_COMPARATOR_PLAYER_FIRST);
		RevengeanceManager man = RevengeanceManager.getManager();
		
		for (FactionAPI faction : factions) {
			String factionId = faction.getId();
			NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
			if (!conf.playableFaction || conf.disableDiplomacy) continue;
			
			List<Object> rowContents = new ArrayList<>();
			
			// add faction
			//rowContents.add(com.fs.starfarer.api.ui.Alignment.MID);
			rowContents.add(faction.getBaseUIColor());
			rowContents.add(Misc.ucFirst(faction.getDisplayName()));
			
			// current points
			float points = man.getFactionPoints(factionId);
			rowContents.add(Misc.getWithDGS(points));
			// current stage
			int stage = man.getCurrentVengeanceStage(factionId);
			rowContents.add(stage + "");
			
			// escalation level
			int escalation = man.getVengeanceEscalation(factionId);
			rowContents.add(escalation + "");
			// max escalation level
			int maxEscalation = VengeanceFleetIntel.VengeanceDef.getDef(factionId).maxLevel;
			rowContents.add(maxEscalation + "");
			
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
		
		createVengeanceTable(tableHolder, width, 10);
		panel.addUIElement(tableHolder).inTL(3, 48);
	}

	@Override
	public boolean hasSmallDescription() {
		return false;
	}

	@Override
	public boolean hasLargeDescription() {
		return true;
	}
	
	@Override
	public String getIcon() {
		return "graphics/icons/intel/discovered_entity.png";
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(Tags.INTEL_FLEET_LOG);
		return tags;
	}	

	@Override
	public IntelSortTier getSortTier() {
		return IntelSortTier.TIER_0;
	}
	
	@Override
	protected String getName() {
		return "Debug intel";
	}
	
	@Override
	public boolean isHidden() {
		return !ExerelinModPlugin.isNexDev;
	}
}

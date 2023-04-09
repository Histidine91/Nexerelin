package exerelin.debug;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommodityMarketDataAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_FactionDirectoryHelper;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.RevengeanceManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.StrategicAIListener;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.action.StrategicActionDelegate;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.campaign.intel.fleets.VengeanceFleetIntel;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import lombok.extern.log4j.Log4j;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Log4j
public class DebugIntel extends BaseIntelPlugin implements StrategicAIListener {
		
	public static final float MARGIN = 40;
	public static final String DATA_KEY = "nex_debugIntel";
	
	public DebugIntel init() {
		Global.getSector().getIntelManager().addIntel(this);
		Global.getSector().getListenerManager().addListener(this);
		Global.getSector().getPersistentData().put(DATA_KEY, this);
		this.setImportant(true);
		return this;
	}
	
	public static DebugIntel getIntel() {
		return (DebugIntel)Global.getSector().getPersistentData().get(DATA_KEY);
	}
	
	// runcode exerelin.debug.DebugIntel.createIntel();
	public static DebugIntel createIntel() {
		if (getIntel() != null) {
			getIntel().endImmediately();
		}
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
	
	public void createCommodityProfitTable(TooltipMakerAPI tooltip, float width, float pad)
	{
		width -= MARGIN;
		Color hl = Misc.getHighlightColor();
		
		tooltip.addSectionHeading("Carnegie Market Info", com.fs.starfarer.api.ui.Alignment.MID, pad);
		
		float cellWidth = 0.16f * width;
		tooltip.beginTable(getFactionForUIColors(), 20, "Commodity", 0.2f * width,
				"Market size", cellWidth,
				"Producers", cellWidth,
				"Output units", cellWidth,
				"Market per producer", cellWidth,
				"Market per unit", cellWidth
		);
		
		List<CommoditySpecAPI> commoditySpecs = Global.getSettings().getAllCommoditySpecs();
		MarketAPI testMarket = Global.getSector().getEconomy().getMarketsCopy().get(0);
		
		for (CommoditySpecAPI spec : commoditySpecs) {
			if (spec.isNonEcon() || spec.isMeta() || spec.isPersonnel()) continue;

			String commodityId = spec.getId();
			
			List<Object> rowContents = new ArrayList<>();
			
			// commodity name
			//rowContents.add(com.fs.starfarer.api.ui.Alignment.MID);
			rowContents.add(hl);
			rowContents.add(spec.getName());
			
			// market size
			CommodityMarketDataAPI data = testMarket.getCommodityData(commodityId).getCommodityMarketData();

			float size = data.getMarketValue();
			rowContents.add(Misc.getWithDGS(size));
			// number of producers
			List<EconomyInfoHelper.ProducerEntry> producers = EconomyInfoHelper.getInstance().getProducersByCommodity(commodityId);
			int numProducers = producers.size();
			rowContents.add(numProducers + "");
			// output units
			int totalOutput = 0;
			for (EconomyInfoHelper.ProducerEntry producer : producers) {
				totalOutput += producer.output;
			}
			rowContents.add(totalOutput + "");

			// market per producer
			float marketPerProducer = size/numProducers;
			rowContents.add(Misc.getWithDGS(marketPerProducer));
			// market per unit
			float marketPerUnit = size/totalOutput;
			rowContents.add(Misc.getWithDGS(marketPerUnit));
			
			tooltip.addRow(rowContents.toArray());
		}
		
		tooltip.addTable("", 0, pad);
	}

	public void createVengeanceTable(TooltipMakerAPI tooltip, float width, float pad) {
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
		
		createCommodityProfitTable(tableHolder, width, 10);
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
		tags.add(StringHelper.getString("exerelin_misc", "intelTagDebug"));
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

	@Override
	public void reportStrategyMeetingHeld(StrategicAI ai) {

	}

	public boolean allowConcern(StrategicAI ai, StrategicConcern concern) {
		return true;
	}

	@Override
	public void reportConcernAdded(StrategicAI ai, StrategicConcern concern) {
		concern.getPriority().modifyFlat("debug", 1, "Listener debug (added)");
	}

	@Override
	public void reportConcernUpdated(StrategicAI ai, StrategicConcern concern) {
		concern.getPriority().modifyFlat("debug", 1, "Listener debug (updated)");
	}

	@Override
	public void reportConcernRemoved(StrategicAI ai, StrategicConcern concern) {

	}

	public boolean allowAction(StrategicAI ai, StrategicAction action) {
		return true;
	}

	@Override
	public void reportActionAdded(StrategicAI ai, StrategicAction action) {
	}

	@Override
	public void reportActionPriorityUpdated(StrategicAI ai, StrategicAction action) {
		action.getPriority().modifyFlat("debug", 1, "Listener debug (priority update)");
	}

	@Override
	public void reportActionUpdated(StrategicAI ai, StrategicAction action, StrategicActionDelegate.ActionStatus status) {

	}

	@Override
	public void reportActionCancelled(StrategicAI ai, StrategicAction action) {

	}
}

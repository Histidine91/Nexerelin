package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.*;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.plog.PlaythroughLog;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.campaign.PlayerFactionStore;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;

public class MilestoneTracker extends BaseIntelPlugin implements ColonyInteractionListener, 
		ColonyPlayerHostileActListener, EconomyTickListener, FleetEventListener, 
		PlayerColonizationListener, SurveyPlanetListener 
{
	
	public static final String CONFIG_PATH = "data/config/exerelin/milestoneDefs.json";
	public static final String MEMORY_KEY = "$nex_milestoneTracker";
	public static final List<MilestoneDef> defs = new ArrayList<>();
	public static final Map<String, MilestoneDef> defsById = new HashMap<>();
	
	public static Logger log = Global.getLogger(MilestoneTracker.class);
	
	protected Map<String, Integer[]> completedMilestones = new HashMap<>();
	protected Map<String, Float> milestoneProgress = new HashMap<>();
	
	static {
		loadDefs();
	}
	
	public static MilestoneTracker getIntel() {
		return (MilestoneTracker)Global.getSector().getMemoryWithoutUpdate().get(MEMORY_KEY);
	}
	
	public void init() {
		Global.getSector().getMemoryWithoutUpdate().set(MEMORY_KEY, this);
		Global.getSector().getListenerManager().addListener(this);
		Global.getSector().getIntelManager().addIntel(this);
	}
	
	protected static void loadDefs() {
		defs.clear();
		defsById.clear();
		
		try {
			JSONObject json = Global.getSettings().getMergedJSONForMod(CONFIG_PATH, ExerelinConstants.MOD_ID);
			JSONObject milestonesJson = json.getJSONObject("milestones");
			Iterator iter = milestonesJson.keys();
			while (iter.hasNext()) {
				String id = (String)iter.next();
				JSONObject entry = milestonesJson.getJSONObject(id);
				
				MilestoneDef def = new MilestoneDef();
				def.id = id;
				def.name = entry.getString("name");
				def.desc = entry.getString("desc");
				def.image = entry.optString("image");
				def.points = entry.getInt("points");
				def.logString = entry.optString("logString", null);
				def.order = (float)entry.optDouble("order", 100);
				def.spoiler = entry.optBoolean("spoiler", false);
				
				defs.add(def);
				defsById.put(id, def);
			}
			Collections.sort(defs);
			
		} catch (IOException | JSONException ex) {
			log.info(ex);
		}	
	}
	
	public boolean wasMilestoneAwarded(String id) {
		return completedMilestones.containsKey(id);
	}
	
	public void awardMilestone(String id) {
		if (wasMilestoneAwarded(id))
			return;	// already awarded
		
		if (!Global.getSettings().getBoolean("nex_milestones"))
			return;	// disabled
		
		MilestoneDef def = defsById.get(id);
		if (def == null) {
			log.error("Milestone " + id + " does not exist");
			return;
		}
		
		CampaignClockAPI clock = Global.getSector().getClock();
		Integer[] date = new Integer[] {clock.getDay(), clock.getMonth(), clock.getCycle()};
		completedMilestones.put(id, date);
		
		if (def.logString != null) {
			PlaythroughLog.getInstance().addEntry(def.logString, true);
		}
		
		TextPanelAPI text = null;
		if (Global.getSector().getCampaignUI().getCurrentInteractionDialog() != null) {
			//text = Global.getSector().getCampaignUI().getCurrentInteractionDialog().getTextPanel();
		}
		Global.getSector().getPlayerStats().addStoryPoints(def.points, text, false);
		this.sendUpdateIfPlayerHasIntel(id, false);
	}
	
	public float progressMilestone(String id, float amount) {
		if (wasMilestoneAwarded(id))
			return -1;	// already awarded
		
		if (!milestoneProgress.containsKey(id)) {
			milestoneProgress.put(id, 0f);
		}
		
		float newVal = milestoneProgress.get(id) + amount;
		milestoneProgress.put(id, newVal);
		return newVal;
	}
	
	public boolean isMilestoneComplete(String id) {
		return completedMilestones.containsKey(id);
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {
		if (listInfoParam instanceof String) {
			MilestoneDef def = defsById.get((String)listInfoParam);
			info.addPara(def.name, initPad);
		}
	}
	
	public static final int ENTRY_HEIGHT = 72;
	public static final int IMAGE_HEIGHT = 48;
	public void displayMilestones(CustomPanelAPI panel, TooltipMakerAPI info, float width, boolean complete) 
	{
		float pad = 3;
		float opad = 10;
		
		Color titleColor = complete ? Misc.getPositiveHighlightColor() : Misc.getHighlightColor();
		boolean first = true;
		for (MilestoneDef def : defs) {
			if (isMilestoneComplete(def.id) != complete) continue;
			
			CustomPanelAPI row = panel.createCustomPanel(width, ENTRY_HEIGHT, null);
			
			TooltipMakerAPI image = row.createUIElement(IMAGE_HEIGHT, ENTRY_HEIGHT, false);
			if (complete || !def.spoiler)
				image.addImage(def.image, IMAGE_HEIGHT, IMAGE_HEIGHT, 3);
			
			row.addUIElement(image).inTL(0, 0);
			
			TooltipMakerAPI text = row.createUIElement(width * 0.75f - IMAGE_HEIGHT, ENTRY_HEIGHT, false);
			//text.setParaInsigniaLarge();
			String name = def.name;
			if (!complete && def.spoiler) name = "?";
			text.addPara(name, titleColor, 0);
			//text.setParaFontDefault();
			text.addPara(def.desc, 3);
			if (!complete && ExerelinModPlugin.isNexDev) {
				text.addButton(getString("grantMilestone"), def.id, 128, 16, pad);
			}
			if (complete) {
				Integer[] date = completedMilestones.get(def.id);
				String str = getString("awardDate");
				str = StringHelper.substituteToken(str, "$day", date[0] + "");
				str = StringHelper.substituteToken(str, "$month", date[1] + "");
				str = StringHelper.substituteToken(str, "$year", date[2] + "");
				text.addPara(str, pad);
			}
			
			row.addUIElement(text).rightOfTop(image, 16);
			
			TooltipMakerAPI pointsText = row.createUIElement(width * 0.25f, ENTRY_HEIGHT, false);
			pointsText.setParaOrbitronVeryLarge();
			pointsText.addPara(def.points + "", pad);
			
			row.addUIElement(pointsText).rightOfTop(text, 0);
			
			info.addCustom(row, first ? opad : pad);
			first = false;
		}		
	}
	
	@Override
	public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
		float opad = 10;
		float pad = 3;
		TooltipMakerAPI info = panel.createUIElement(width, height, true);
		
		FactionAPI faction = PlayerFactionStore.getPlayerFaction();
		
		info.addImage(faction.getLogo(), width, 128, pad);
		
		info.setParaFontVictor14();
		info.addPara(getString("textIntro"), opad);
		
		info.addSectionHeading(getString("headingComplete"), faction.getBaseUIColor(), 
			faction.getDarkUIColor(), Alignment.MID, opad);
		displayMilestones(panel, info, width, true);
		
		info.addSectionHeading(getString("headingIncomplete"), faction.getBaseUIColor(), 
			faction.getDarkUIColor(), Alignment.MID, opad);
		displayMilestones(panel, info, width, false);
				
		
		panel.addUIElement(info).inTL(0, 0);
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		awardMilestone((String)buttonId);
		ui.updateUIForItem(this);
	}
	
	@Override
	protected String getName() {
		String key = "intelTitle";
		if (listInfoParam != null)
			key = "intelTitleAwarded";
		return getString(key);
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(StringHelper.getString("exerelin_misc", "intelTagPersonal"));
		tags.add(Tags.INTEL_STORY);
		return tags;
	}

	@Override
	public String getIcon() {
		if (listInfoParam instanceof String) {
			MilestoneDef def = defsById.get((String)listInfoParam);
			return def.image;
		}
		
		return Global.getSettings().getSpriteName("intel", "reputation");
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
	public boolean isHidden() {
		return !Global.getSettings().getBoolean("nex_milestones");
	}
	
	public String getString(String id) {
		return StringHelper.getString("nex_milestones", id);
	}
	
	@Override
	public IntelSortTier getSortTier() {
		return IntelSortTier.TIER_3;
	}
	
	// runcode exerelin.campaign.intel.MilestoneTracker.debug();
	public static void debug() {
		loadDefs();
		if (getIntel() == null) {
			MilestoneTracker intel = new MilestoneTracker();
			intel.init();
		}
	}

	@Override
	public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, 
			CampaignEventListener.FleetDespawnReason reason, Object param) {}

	@Override
	public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) 
	{
		if (!battle.isPlayerInvolved()) return;
		
		awardMilestone("firstBattle");
		
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();		
		boolean won = battle.getSideFor(primaryWinner).contains(player);
		for (CampaignFleetAPI otherFleet : battle.getNonPlayerSideSnapshot()) {
			boolean remnant = otherFleet.getFaction().getId().equals(Factions.REMNANTS);
			boolean omega = otherFleet.getFaction().getId().equals(Factions.OMEGA);
			if (remnant) {
				awardMilestone("encounterRemnants");
			}
			if (won && (remnant || omega)) {
				for (FleetMemberAPI loss : Misc.getSnapshotMembersLost(otherFleet)) {
					if (loss.getHullSpec().hasTag(Tags.OMEGA) && loss.getHullSpec().getHullSize().compareTo(HullSize.CRUISER) >= 0) 
					{
						awardMilestone("defeatOmega");
						Global.getSector().getCharacterData().getMemoryWithoutUpdate().set("$nex_defeatedTesseract", true);
						break;
					}
				}
			}
			for (FleetMemberAPI loss : Misc.getSnapshotMembersLost(otherFleet)) {
				if (loss.isStation()) {
					awardMilestone("firstStation");
					if (remnant) {
						awardMilestone("defeatNexus");
					}
				}
				else if (loss.isCapital() && !loss.isCivilian()) {	// don't count station as a capital
					awardMilestone("firstCapital");
				}
				if (loss.getHullId().equals("ziggurat")) {
					awardMilestone("defeatZiggurat");
				}
			}
		}
		
		if (!wasMilestoneAwarded("eliteCrew")) {
			List<OfficerDataAPI> officers = player.getFleetData().getOfficersCopy();
			if (officers.size() >= 8) {
				int have = 0;
				for (OfficerDataAPI officer : officers) {
					int level = officer.getPerson().getStats().getLevel();
					if (level >= 5) {
						have++;
					}
				}
				if (have >= 8) awardMilestone("eliteCrew");
			}
		}
	}

	@Override
	public void reportPlayerSurveyedPlanet(PlanetAPI planet) {
		float progress = progressMilestone("survey100", 1);
		if (progress >= 100) {
			awardMilestone("survey100");
		}
	}

	@Override
	public void reportEconomyTick(int iterIndex) {
		if (wasMilestoneAwarded("colonySize5"))
			return;
		
		for (MarketAPI market : Misc.getPlayerMarkets(true)) {
			if (market.getSize() >= 5) {
				awardMilestone("colonySize5");
				return;
			}
		}
	}

	@Override
	public void reportEconomyMonthEnd() {}

	@Override
	public void reportPlayerOpenedMarket(MarketAPI market) {}

	@Override
	public void reportPlayerClosedMarket(MarketAPI market) {}

	@Override
	public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {}

	@Override
	public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
		// was gonna put a milestone here
		// but I don't have access to credit total, only the net
	}

	@Override
	public void reportPlayerColonizedPlanet(PlanetAPI market) {
		awardMilestone("firstColony");
	}
	
	// pipe from DiplomacyManager because I'm not making a CampaignEventListener for this
	public void reportPlayerReputationChanged(String factionId) {
		if (wasMilestoneAwarded("maxRep")) return;
		
		FactionAPI faction = Global.getSector().getFaction(factionId);
		if (faction.isPlayerFaction()) return;
		if (faction.getRelToPlayer().getRel() < 1) return;

		NexFactionConfig conf = NexConfig.getFactionConfig(faction.getId());
		if (!conf.playableFaction) return;

		awardMilestone("maxRep");
	}

	@Override
	public void reportPlayerAbandonedColony(MarketAPI market) {}

	@Override
	public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, 
			MarketAPI market, MarketCMD.TempData actionData, CargoAPI cargo) {
		awardMilestone("firstRaid");
	}

	@Override
	public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, 
			MarketCMD.TempData actionData, Industry industry) {
		awardMilestone("firstRaid");
	}

	@Override
	public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, 
			MarketAPI market, MarketCMD.TempData actionData) {}

	@Override
	public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, 
			MarketAPI market, MarketCMD.TempData actionData) {}
	
	public static class MilestoneDef implements Comparable<MilestoneDef> {
		public String id;
		public String name;
		public String desc;
		public String logString;	// for the history graph thing (Playthrough Log)
		public String image;
		public int points;
		public float order;
		public boolean spoiler;

		@Override
		public int compareTo(MilestoneDef other) {
			return Float.compare(order, other.order);
		}
	}
}

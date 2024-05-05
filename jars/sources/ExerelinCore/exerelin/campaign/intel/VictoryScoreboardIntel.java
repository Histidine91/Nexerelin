package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.ui.FramedCustomPanelPlugin;
import exerelin.utilities.*;
import lombok.extern.log4j.Log4j;

import java.awt.*;
import java.util.List;
import java.util.*;

@Log4j
public class VictoryScoreboardIntel extends BaseIntelPlugin {
	
	public static final float RUNNER_UP_SCORE_MULT = 1.5f;
	public static final float ALLIANCE_CONTRIB_MULT = 0.5f;
	
	public static int getNeededSizeForVictory(int total, List<ScoreEntry> sizeRanked) {
		
		float sizeFractionForVictory = Global.getSettings().getFloat("nex_sizeFractionForVictory");
		int neededPop = (int)Math.ceil(total * sizeFractionForVictory);
		int runnerUpScore = 0;
		if (sizeRanked.size() >= 2)
			runnerUpScore = sizeRanked.get(1).score;
		neededPop = (int)Math.max(neededPop, runnerUpScore * RUNNER_UP_SCORE_MULT);
				
		return neededPop;
	}
	
	/**
	 * Creates the overall scoreboard (not including header).
	 * @param outer
	 * @param width
	 * @param height
	 */
	public void createScoreboard(CustomPanelAPI outer, float width, float height) {
		try {
		List<ScoreEntry> sizeRanked = new ArrayList<>();
		List<ScoreEntry> hiRanked = new ArrayList<>();
		List<ScoreEntry> friendsRanked = new ArrayList<>();
		List<String> factions = getWinnableFactions();
		
		int[] totals = generateRankings(factions, sizeRanked, hiRanked, friendsRanked);
		float popFractionForVictory = Global.getSettings().getFloat("nex_sizeFractionForVictory");
		float hiFractionForVictory = Global.getSettings().getFloat("nex_heavyIndustryFractionForVictory");
		
		int neededPop = getNeededSizeForVictory(totals[0], sizeRanked);
		int neededHI = (int)Math.ceil(totals[1] * hiFractionForVictory);
		int neededFriends = factions.size() - 1;
		
		float subWidth = width/3 - 8;
		
		CustomPanelAPI marketScoreboard = createSubScoreboard(outer, subWidth, height, getString("headerPopulation"), 
				"graphics/icons/markets/urbanized_polity.png", neededPop, sizeRanked, 
				createTooltip(getString("tooltipReqPop"), StringHelper.toPercent(popFractionForVictory), RUNNER_UP_SCORE_MULT + "×"));
		outer.addComponent(marketScoreboard).inTL(4, 48);
		
		CustomPanelAPI hiScoreboard = createSubScoreboard(outer, subWidth, height, getString("headerHeavyIndustry"), 
				Global.getSettings().getSpriteName("marketConditions", "heavy_industry"), neededHI, hiRanked,
				createTooltip(getString("tooltipReqHI"), StringHelper.toPercent(hiFractionForVictory)));
		outer.addComponent(hiScoreboard).rightOfTop(marketScoreboard, 3);
		
		CustomPanelAPI friendsScoreboard = createSubScoreboard(outer, subWidth, height, getString("headerDiplomacy"), 
				"graphics/icons/intel/peace.png", neededFriends, friendsRanked, createTooltip(getString("tooltipReqFriend")));
		outer.addComponent(friendsScoreboard).rightOfTop(hiScoreboard, 3);
		} catch (Throwable t) {
			log.error(t.getMessage(), t);
		}
	}
	
	/**
	 * Creates a sub-scoreboard (e.g.one for population, one for heavy industry, one for friends).
	 * @param outer
	 * @param width
	 * @param height
	 * @param headerText
	 * @param headerImage
	 * @param required Score required to win the game.
	 * @param rankings Sorted list of factions/alliances and their scores.
	 * @param tooltip
	 * @return The sub-scoreboard custom panel.
	 */
	public CustomPanelAPI createSubScoreboard(CustomPanelAPI outer, float width, float height, 
			String headerText, String headerImage, int required, List<ScoreEntry> rankings, TooltipCreator tooltip) 
	{
		CustomPanelAPI subScoreboard = outer.createCustomPanel(width, height, 
				new FramedCustomPanelPlugin(0.25f, Misc.getBasePlayerColor(), true));
						
		float headerHeight = 40;
		float rowHeight = 36;
		float reqWidth = 52;
		float reqWidth2 = 40;
		LabelAPI label;
		
		// create header
		CustomPanelAPI header = outer.createCustomPanel(width, headerHeight, null);
		TooltipMakerAPI hdrImgHolder = header.createUIElement(headerHeight, headerHeight, false);
		hdrImgHolder.addImage(headerImage, headerHeight, 0);
		header.addUIElement(hdrImgHolder).inTL(0, 0);
		
		TooltipMakerAPI hdrReqHolder = header.createUIElement(reqWidth, headerHeight, false);
		hdrReqHolder.setParaOrbitronLarge();
		label = hdrReqHolder.addPara(required + "", 6);
		label.setAlignment(Alignment.RMID);
		hdrReqHolder.addTooltipToPrevious(tooltip, TooltipMakerAPI.TooltipLocation.BELOW);
		header.addUIElement(hdrReqHolder).inTR(0, 0);
		
		TooltipMakerAPI hdrReqTxtHolder = header.createUIElement(reqWidth2, headerHeight, false);
		hdrReqTxtHolder.setParaFontVictor14();
		label = hdrReqTxtHolder.addPara(getString("headerRequired"), Misc.getGrayColor(), 9);
		label.setAlignment(Alignment.BR);
		header.addUIElement(hdrReqTxtHolder).leftOfTop(hdrReqHolder, 0);
		
		TooltipMakerAPI hdrTitleHolder = header.createUIElement(width - reqWidth - reqWidth2 - headerHeight, headerHeight, false);
		hdrTitleHolder.setParaInsigniaLarge();
		label = hdrTitleHolder.addPara(headerText, 5);
		label.setAlignment(Alignment.MID);
		header.addUIElement(hdrTitleHolder).rightOfTop(hdrImgHolder, 16);
				
		subScoreboard.addComponent(header).inTL(0, 3);
		
		TooltipMakerAPI list = subScoreboard.createUIElement(width, height - headerHeight - 8, true);
		
		// create each rank row
		int lastScore = 0;
		int lastRank = 1;
		for (int i=0; i<rankings.size(); i++) {
			ScoreEntry entry = rankings.get(i);
			int rank = i + 1;
			if (entry.score == lastScore)
				rank = lastRank;
			
			CustomPanelAPI row = createRankingRow(entry, rank, subScoreboard, width - 12, rowHeight);
			list.addCustom(row, 3);
			
			lastScore = entry.score;
			lastRank = rank;
			//log.info("Added row for " + entry.getName());
		}
		
		subScoreboard.addUIElement(list).belowLeft(header, 3);
		
		return subScoreboard;
	}
	
	/**
	 * Creates a custom panel listing the current competitor and their rank and score.
	 * @param entry
	 * @param rank 1st, 2nd, 3rd, etc. (can be tied with other entries)
	 * @param outer
	 * @param width
	 * @param height
	 * @return
	 */
	public CustomPanelAPI createRankingRow(ScoreEntry entry, int rank, CustomPanelAPI outer, float width, float height) {
		float pad = 3;
		Color color = entry.getColor();
		Color rankColor;
		switch (rank) {
			case 1:
				rankColor = Misc.getPositiveHighlightColor();
				break;
			case 2:
				rankColor = Misc.getHighlightColor();
				break;
			case 3:
				rankColor = Color.CYAN;
				break;
			default:
				rankColor = Misc.getTextColor();
				break;
		}
				
		float namePad = 8;
		if (entry.alliance != null) {
			namePad = 0;
		}
		
		CustomPanelAPI row = outer.createCustomPanel(width, height, 
				new FramedCustomPanelPlugin(0.25f, color, true));
		
		TooltipMakerAPI rankHolder = row.createUIElement(height, height, false);
		rankHolder.setParaInsigniaLarge();
		rankHolder.addPara(rank + "", rankColor, pad);
		row.addUIElement(rankHolder).inTL(0, 0);
		
		String image = entry.getIcon();
		TooltipMakerAPI imageHolder = row.createUIElement(height, height, false);
		imageHolder.addImage(image, height-4, 2);
		row.addUIElement(imageHolder).rightOfTop(rankHolder, pad);
		
		float nameWidth = width - height * 1.5f - pad * 4 - 2;
		TooltipMakerAPI nameHolder = row.createUIElement(nameWidth, height*0.45f, false);
		//nameHolder.setParaSmallInsignia();
		nameHolder.addPara(entry.getName(), color, namePad);		
		row.addUIElement(nameHolder).rightOfTop(imageHolder, pad * 2);
		
		if (entry.alliance != null) {
			TooltipMakerAPI allyContribHolder = row.createUIElement(110, height/2, false);
			Color hl = entry.allianceContrib > 0 ? Misc.getPositiveHighlightColor() : Misc.getTextColor();
			allyContribHolder.addPara(getString("textAllyContrib"), 0, hl, entry.allianceContrib + "");
			row.addUIElement(allyContribHolder).belowLeft(nameHolder, 0);
			
			TooltipMakerAPI crestHolder = row.createUIElement(nameWidth - 120 - 4, height/2, false);
			String[] crests = entry.getAllianceMemberCrests();
			crestHolder.addImages(20 * crests.length, 20, 0, 3, crests);
			row.addUIElement(crestHolder).rightOfTop(allyContribHolder, 0);
		}
		
		TooltipMakerAPI scoreHolder = row.createUIElement(height*1.5f, height, false);
		scoreHolder.setParaSmallInsignia();
		LabelAPI label = scoreHolder.addPara(entry.score + "", pad);
		label.setAlignment(Alignment.RMID);
		row.addUIElement(scoreHolder).inTR(pad * 2, 0);
		
		return row;
	}
	
	public static List<String> getWinnableFactions() {
		String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
		List<String> factions = new ArrayList<>();
		for (String factionId : SectorManager.getLiveFactionIdsCopy())
		{
			if (factionId.equals(Factions.PLAYER) && Misc.getCommissionFaction() != null)
				continue;
			// don't count pirate factions unless config says so or we belong to it
			if (!NexUtilsFaction.isPirateFaction(factionId) || NexConfig.countPiratesForVictory || factionId.equals(playerAlignedFactionId))
			{
				factions.add(factionId);
			}
		}
		return factions;
	}
	
	/**
	 * Fills the list params with ordered lists of the contestant factions/alliances.
	 * @param factionsToCheck
	 * @param sizeRanked List of contestants.
	 * @param hiRanked
	 * @param friendsRanked
	 * @return An array with the total market size and total number of heavy industries in the game.
	 */
	public static int[] generateRankings(Collection<String> factionsToCheck, 
			List<ScoreEntry> sizeRanked, List<ScoreEntry> hiRanked, List<ScoreEntry> friendsRanked) 
	{
		int totalSize = 0, totalHeavyIndustries = 0;
		Map<String, Integer> factionSizes = new HashMap<>();
		Map<String, Integer> heavyIndustries = new HashMap<>();
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) 
		{
			if (market.isHidden()) continue;
			String factionId = market.getFactionId();			
			if (market.getFaction().isPlayerFaction()) {
				factionId = PlayerFactionStore.getPlayerFactionId();
			}
			
			if (!factionsToCheck.contains(factionId)) continue;
			
			totalSize += market.getSize();
			NexUtils.modifyMapEntry(factionSizes, factionId, market.getSize());
			if (NexUtilsMarket.hasHeavyIndustry(market)) {
				totalHeavyIndustries++;
				NexUtils.modifyMapEntry(heavyIndustries, factionId, 1);
			}
		}
		
		// alliances are no longer directly checked
		/*
		for (Alliance alliance : AllianceManager.getAllianceList()) {
			int size, numHI;
			if (alliance != null) {
				size = getAllianceTotalFromMap(alliance, factionSizes);
				numHI = getAllianceTotalFromMap(alliance, heavyIndustries);
				
				sizeRanked.add(new ScoreEntry(alliance, size));
				hiRanked.add(new ScoreEntry(alliance, numHI));
			}
		}
		*/
		
		for (String factionId : factionsToCheck) {
			// check faction diplomacy
			FactionAPI faction = Global.getSector().getFaction(factionId);
			int friends = 0;
			for (String otherFID : factionsToCheck) {
				if (otherFID.equals(factionId)) continue;
				if (faction.isAtBest(otherFID, RepLevel.WELCOMING)) continue;
				friends++;
			}
			if (friends > 0) {
				friendsRanked.add(new ScoreEntry(factionId, friends));
			}
			
			// check faction pop/HI
			// first see if we already checked this faction as part of its alliance
			/*
			if (AllianceManager.getFactionAlliance(factionId) != null)
				continue;
			*/
			
			Integer size = factionSizes.get(factionId);
			Integer numHI = heavyIndustries.get(factionId);
			if (size == null) continue;
			
			
			ScoreEntry entry = new ScoreEntry(factionId, size);
			
			// alliance contribution
			int allySize = 0;
			int allyIndustries = 0;
			Alliance alliance = AllianceManager.getFactionAlliance(factionId);
			if (alliance != null) {
				for (String allyId : alliance.getMembersCopy()) {
					if (allyId.equals(factionId)) continue;
					if (factionSizes.containsKey(allyId)) allySize += factionSizes.get(allyId);
					if (heavyIndustries.containsKey(allyId)) allyIndustries += heavyIndustries.get(allyId);
				}
				allySize *= ALLIANCE_CONTRIB_MULT;
				allyIndustries *= ALLIANCE_CONTRIB_MULT;
				
				entry.score += allySize;
				
				entry.setAlliance(alliance);
				entry.allianceContrib = allySize;
			}
			sizeRanked.add(entry);
			
			if (numHI != null) {
				entry = new ScoreEntry(factionId, numHI + allyIndustries);
				entry.setAlliance(alliance);
				entry.allianceContrib = allyIndustries;
				hiRanked.add(entry);
			}
		}
		
		Collections.sort(sizeRanked);
		Collections.sort(hiRanked);
		Collections.sort(friendsRanked);
		
		return new int[]{totalSize, totalHeavyIndustries};
	}
	
	@Override
	public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
		TooltipMakerAPI superheaderHolder = panel.createUIElement(width/3, 40, false);
		TooltipMakerAPI superheader = superheaderHolder.beginImageWithText(getIcon(), 40);
		superheader.setParaOrbitronVeryLarge();
		superheader.addPara(getName(), 3);
		superheaderHolder.addImageWithText(3);
		
		panel.addUIElement(superheaderHolder).inTL(width*0.4f, 0);
		
		createScoreboard(panel, width, height - 52);
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
		return Global.getSettings().getSpriteName("intel", "reputation");
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		//tags.add(StringHelper.getString("victory", true));
		tags.add(StringHelper.getString("exerelin_misc", "intelTagPersonal"));
		tags.add(Tags.INTEL_STORY);
		return tags;
	}
	

	@Override
	public IntelSortTier getSortTier() {
		return IntelSortTier.TIER_3;
	}
	
	@Override
	protected String getName() {
		return getString("title");
	}
	
	@Override
	public boolean isHidden() {
		//return SectorManager.getManager().hasVictoryOccured();
		return false;
	}
	
	protected String getString(String id) {
		return StringHelper.getString("nex_scoreboard", id);
	}
	
	public TooltipCreator createTooltip(final String str, final String... args) {
		return new TooltipCreator() {
			@Override
			public boolean isTooltipExpandable(Object tooltipParam) {
				return false;
			}

			@Override
			public float getTooltipWidth(Object tooltipParam) {
				return 360;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
				tooltip.addPara(str, 0, Misc.getHighlightColor(), args);
			}			
		};
	}
			
	public static class ScoreEntry implements Comparable<ScoreEntry> {
		public String factionId;
		public List<String> allianceMembers;
		public Alliance alliance;
		public int score;
		public int allianceContrib;
		
		public ScoreEntry(String factionId, int score) {
			this.factionId = factionId;
			this.score = score;
		}
		
		public ScoreEntry(Alliance alliance, int score) {
			this.alliance = alliance;
			this.score = score;
			allianceMembers = alliance.getMembersSorted();
			factionId = allianceMembers.get(0);			
		}
		
		public void setAlliance(Alliance alliance) {
			this.alliance = alliance;
			if (alliance != null) allianceMembers = alliance.getMembersSorted();
		}
		
		public String getIcon() {
			return Global.getSector().getFaction(factionId).getCrest();
		}
		
		public Color getColor() {
			return Global.getSector().getFaction(factionId).getBaseUIColor();
		}
		
		public String getName() {
			//if (alliance != null) return alliance.getName();
			return Global.getSector().getFaction(factionId).getDisplayName();
		}
		
		/**
		 * Excludes the entry's own faction ID (since their crest will be used as the main icon).
		 * @return
		 */
		public String[] getAllianceMemberCrests() {
			if (allianceMembers == null) return null;
			List<String> crests = new ArrayList<>();
			for (String member : allianceMembers) {
				if (member.equals(this.factionId)) continue;
				crests.add(Global.getSector().getFaction(member).getCrest());
			}	
			return crests.toArray(new String[0]);
		}

		@Override
		public int compareTo(ScoreEntry other) {
			return Integer.compare(other.score, score);
		}
	}
}

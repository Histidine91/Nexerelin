package exerelin.campaign.alliances;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.intel.AllianceIntel;
import exerelin.campaign.intel.AllianceIntel.UpdateType;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import java.awt.Color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public class Alliance 
{
	protected String name;
	protected Set<String> members;
	protected Alignment alignment;
	private AllianceIntel intel;
	public final String uuId;

	public Alliance(String name, Alignment alignment, String member1, String member2)
	{
		this.name = name;
		this.alignment = alignment;
		members = new HashSet<>();
		members.add(member1);
		members.add(member2);
		
		uuId = UUID.randomUUID().toString();
	}
	
	public void createIntel(String member1, String member2) {
		intel = createAllianceEvent(member1, member2);
	}
	
	public AllianceIntel getIntel()
	{
		return intel;
	}
	
	public void updateIntel(String factionId, String factionId2, UpdateType type)
	{
		Map<String, Object> infoParam = new HashMap<>(); 
		infoParam.put("type", type);
		infoParam.put("faction1", factionId);
		infoParam.put("faction2", factionId2);
		
		intel.sendUpdateIfPlayerHasIntel(infoParam, false);
		
		if (type == UpdateType.DISSOLVED)
			intel.endAfterDelay();
	}

	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public Alignment getAlignment() {
		return alignment;
	}

	public Set<String> getMembersCopy() {
		return new HashSet<>(members);
	}
	
	public void addMember(String factionId) {
		members.add(factionId);
	}
	
	public void removeMember(String factionId) {
		members.remove(factionId);
	}
	
	public void clearMembers() {
		members.clear();
	}
	
	public List<MarketAPI> getAllianceMarkets()
	{
		List<MarketAPI> markets = new ArrayList<>();
		for (String memberId : members)
		{
			List<MarketAPI> factionMarkets = ExerelinUtilsFaction.getFactionMarkets(memberId);
			markets.addAll(factionMarkets);
		}
		return markets;
	}
	
	/**
	 * Gets a random alliance market to report an event from
	 * Prefers markets with Headquarters, then by size
	 * @param preferHq
	 * @return
	 */
	public MarketAPI getRandomAllianceMarketForEvent(boolean preferHq)
	{
		List<MarketAPI> markets = getAllianceMarkets();
		WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>();
		WeightedRandomPicker<MarketAPI> picker2 = new WeightedRandomPicker<>();
		for (MarketAPI market : markets)
		{
			float weight = market.getSize();
			if (market.hasIndustry(Industries.MILITARYBASE))
				weight *= 1.5f;
			picker2.add(market, weight);
			if (market.hasIndustry(Industries.HIGHCOMMAND))
				picker.add(market, weight);
		}
		if (preferHq && !picker.isEmpty())
			return picker.pick();
		else return picker2.pick();
	}

	public int getNumAllianceMarkets()
	{
		int numMarkets = 0;
		for (String memberId : members)
		{
			numMarkets += ExerelinUtilsFaction.getFactionMarkets(memberId).size();
		}
		return numMarkets;
	}

	public int getAllianceMarketSizeSum()
	{
		int size = 0;
		for (String memberId : members)
		{
			for (MarketAPI market : ExerelinUtilsFaction.getFactionMarkets(memberId))
			{
				size += market.getSize();
			}
		}
		return size;
	}

	/**
	 * Returns a string of format "[Alliance name] ([member1], [member2], ...)"
	 * @return
	 */
	public String getAllianceNameAndMembers()
	{
		List<String> factionNames = StringHelper.factionIdListToFactionNameList(new ArrayList<>(getMembersCopy()), true);
		String factions = StringHelper.writeStringCollection(factionNames);
		return name + " (" + factions + ")";
	}
	
	public float getAverageRelationshipWithFaction(String factionId)
	{
		float sumRelationships = 0;
		int numFactions = 0;
		FactionAPI faction = Global.getSector().getFaction(factionId);
		for (String memberId : members)
		{
			if (memberId.equals(factionId)) continue;
			sumRelationships += faction.getRelationship(memberId);
			numFactions++;
		}
		if (numFactions == 0) return 1;
		return sumRelationships/numFactions;
	}
	
	protected AllianceIntel createAllianceEvent(String faction1, String faction2)
	{
		SectorAPI sector = Global.getSector();
		FactionAPI one = sector.getFaction(faction1);
		FactionAPI two = sector.getFaction(faction2);
		return new AllianceIntel(one, two, this.uuId, this.name);
	}
	
	public enum Alignment {
		CORPORATE(new Color(32, 178, 170)),	// Light Sea Green
		TECHNOCRATIC(Color.CYAN),
		MILITARIST(Color.RED),
		HIERARCHIAL(Color.MAGENTA),
		DIPLOMATIC(Color.YELLOW),
		IDEOLOGICAL(Color.GREEN);
		
		public final Color color;
		
		private Alignment(Color color) {
			this.color = color;
		}
	}
}

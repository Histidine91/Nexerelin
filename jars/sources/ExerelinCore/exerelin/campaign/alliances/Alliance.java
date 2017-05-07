package exerelin.campaign.alliances;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.events.AllianceChangedEvent;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


public class Alliance 
{
	protected String name;
	protected Set<String> members;
	protected Alignment alignment;
	protected AllianceChangedEvent event;
	public final String uuId = UUID.randomUUID().toString();

	public Alliance(String name, Alignment alignment, String member1, String member2)
	{
		this.name = name;
		this.alignment = alignment;
		members = new HashSet<>();
		members.add(member1);
		members.add(member2);
	}

	public void setEvent(AllianceChangedEvent event) {
		this.event = event;
	}
	
	public void reportEvent(String faction1, String faction2, Alliance alliance, String stage)
	{
		HashMap<String, Object> params = new HashMap<>();
		SectorAPI sector = Global.getSector();
		params.put("faction1", sector.getFaction(faction1));
		if (faction2 != null) params.put("faction2", sector.getFaction(faction2));
		params.put("alliance", alliance);
		params.put("stage", stage);

		CampaignEventTarget eventTarget;
		if (AllianceManager.getPlayerInteractionTarget() != null) {
			eventTarget = new CampaignEventTarget(AllianceManager.getPlayerInteractionTarget());
		} else {
			List<MarketAPI> markets = ExerelinUtilsFaction.getFactionMarkets(faction1);
			if (markets.isEmpty()) markets = alliance.getAllianceMarkets();
			MarketAPI market = (MarketAPI) ExerelinUtils.getRandomListElement(markets);

			eventTarget = new CampaignEventTarget(market);
		}
		event.setParam(params);
		event.reportEvent();
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
		String factions = "";
		int num = 0;
		for (String memberId : members)
		{
			FactionAPI faction = Global.getSector().getFaction(memberId);
			factions += Misc.ucFirst(faction.getEntityNamePrefix());
			num++;
			if (num < members.size()) factions += ", ";
		}
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
    
    public static class AllianceSyncMessage {
        public String message;
        public String party1;
        public String party2;
        
        public AllianceSyncMessage(String message, String party1, String party2)
        {
            this.message = message;
            this.party1 = party1;
            this.party2 = party2;
        }
    }
    
    public enum Alignment {
        CORPORATE,
        TECHNOCRATIC,
        MILITARIST,
        DIPLOMATIC,
        IDEOLOGICAL
    }
}

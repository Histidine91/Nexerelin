package exerelin.campaign.alliances;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.events.AllianceChangedEvent;
import exerelin.utilities.ExerelinUtilsFaction;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class Alliance 
{
	public String name;
	public Set<String> members;
	public Alignment alignment;

	public Alliance(String name, Alignment alignment, String member1, String member2)
	{
		this.name = name;
		this.alignment = alignment;
		members = new HashSet<>();
		members.add(member1);
		members.add(member2);
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

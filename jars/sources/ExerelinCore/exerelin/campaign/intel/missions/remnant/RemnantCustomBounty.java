package exerelin.campaign.intel.missions.remnant;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.missions.cb.BaseCustomBounty;
import com.fs.starfarer.api.impl.campaign.missions.cb.CBEnemyStation;
import com.fs.starfarer.api.impl.campaign.missions.cb.CBMerc;
import com.fs.starfarer.api.impl.campaign.missions.cb.CBPather;
import com.fs.starfarer.api.impl.campaign.missions.cb.CBPatrol;
import com.fs.starfarer.api.impl.campaign.missions.cb.CBTrader;
import com.fs.starfarer.api.impl.campaign.missions.cb.CustomBountyCreator;
import exerelin.campaign.intel.missions.Nex_CBHegInspector;

public class RemnantCustomBounty extends BaseCustomBounty {

	public static List<CustomBountyCreator> CREATORS = new ArrayList<CustomBountyCreator>();
	static {
		CREATORS.add(new CBTrader());
		CREATORS.add(new CBPatrol());
		CREATORS.add(new CBMerc());
		CREATORS.add(new CBPather());
		//CREATORS.add(new CBMilitaryRem());
		CREATORS.add(new CBEnemyStation());
		CREATORS.add(new Nex_CBHegInspector());
	}
	
	@Override
	public List<CustomBountyCreator> getCreators() {
		return CREATORS;
	}

	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		return super.create(createdAt, barEvent);
	}

	@Override
	protected void updateInteractionDataImpl() {
		super.updateInteractionDataImpl();
		
		String id = getMissionId();
		if (showData != null && showCreator != null) {
			if (showData.fleet != null) {
				PersonAPI p = showData.fleet.getCommander();
				set("$" + id + "_targetRank", p.getRank());
				//set("$bcb_targetRank", p.getRank());
			}
		}
	}
}












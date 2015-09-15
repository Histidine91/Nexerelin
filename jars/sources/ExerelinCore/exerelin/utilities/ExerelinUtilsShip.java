package exerelin.utilities;

import com.fs.starfarer.api.fleet.FleetMemberAPI;

public class ExerelinUtilsShip {
	
	public static float getSupplyCostToDeploy(FleetMemberAPI member, boolean base)
	{
		float crToDeploy = member.getHullSpec().getCRToDeploy();
		if (!base) crToDeploy *= member.getStats().getCRPerDeploymentPercent().mult;
		
		float suppliesPerDay = 0;
		if (base) suppliesPerDay = member.getStats().getBaseSupplyUsePerDay().base;
		else suppliesPerDay = member.getStats().getBaseSupplyUsePerDay().modified;
		
		float crRecoveryPerDay = 0;
		if (base) crRecoveryPerDay = member.getStats().getBaseCRRecoveryRatePercentPerDay().base;
		else crRecoveryPerDay = member.getStats().getBaseCRRecoveryRatePercentPerDay().modified;
		
		return crToDeploy/crRecoveryPerDay * suppliesPerDay;
	}
	
	public static float getCRPerSupplyUnit(FleetMemberAPI member, boolean base)
	{
		float suppliesPerDay = 0;
		if (base) suppliesPerDay = member.getStats().getBaseSupplyUsePerDay().base;
		else suppliesPerDay = member.getStats().getBaseSupplyUsePerDay().modified;
		
		float crRecoveryPerDay = 0;
		if (base) crRecoveryPerDay = member.getStats().getBaseCRRecoveryRatePercentPerDay().base;
		else crRecoveryPerDay = member.getStats().getBaseCRRecoveryRatePercentPerDay().modified;
		
		return crRecoveryPerDay/suppliesPerDay;
	}
}
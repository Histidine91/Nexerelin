package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.tutorial.GalatianAcademyStipend;
import static com.fs.starfarer.api.impl.campaign.tutorial.GalatianAcademyStipend.DURATION;
import exerelin.utilities.StringHelper;

// Doesn't require Ancyra to be alive (especially useful in random sector)
public class Nex_GalatianAcademyStipend extends GalatianAcademyStipend {
	
	@Override
	public void reportEconomyTick(int iterIndex) {
		if (!Global.getSettings().getBoolean("enableStipend")) return;
		
		int lastIterInMonth = (int) Global.getSettings().getFloat("economyIterPerMonth") - 1;
		if (iterIndex != lastIterInMonth) return;
		
		float daysActive = Global.getSector().getClock().getElapsedDaysSince(startTime);
		// MODIFIED
		//MarketAPI ancyra = Global.getSector().getEconomy().getMarket("ancyra_market");
		if (daysActive > DURATION) {
			Global.getSector().getListenerManager().removeListener(this);
			Global.getSector().getMemoryWithoutUpdate().unset("$playerReceivingGAStipend");
			return;
		}
		
		
		
		MonthlyReport report = SharedData.getData().getCurrentReport();


		int stipend = getStipend();
		MonthlyReport.FDNode fleetNode = report.getNode(MonthlyReport.FLEET);
		
		MonthlyReport.FDNode stipendNode = report.getNode(fleetNode, "GA_stipend");
		stipendNode.income = stipend;
		stipendNode.name = StringHelper.getString("exerelin_misc", "galatiaStipend");
		stipendNode.icon = Global.getSettings().getSpriteName("income_report", "generic_income");
		stipendNode.tooltipCreator = this;
	}
}

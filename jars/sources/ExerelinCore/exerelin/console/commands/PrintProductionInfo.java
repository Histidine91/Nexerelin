package exerelin.console.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.ConditionData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import exerelin.utilities.ExerelinUtilsMarket;
import java.util.List;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class PrintProductionInfo implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (context != CommandContext.CAMPAIGN_MAP) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }

        // do me!
        if (args.isEmpty())
        {
            return CommandResult.BAD_SYNTAX;
        }
        
        String[] tmp = args.split(" ");
        if (tmp.length < 2)
		{
            return CommandResult.BAD_SYNTAX;
        }	
		
		SectorAPI sector = Global.getSector();
        String targetName = tmp[0];
        List<SectorEntityToken> entitiesToSearch = sector.getEntitiesWithTag("planet");
        entitiesToSearch.addAll(sector.getEntitiesWithTag("station"));
		//Console.showMessage(entitiesToSearch.size() + " valid targets for search found");
        
        SectorEntityToken target = CommandUtils.findBestTokenMatch(targetName, entitiesToSearch);          
        if (target == null)
        {
            Console.showMessage("Error: could not find entity with name '" + targetName + "'!");
            return CommandResult.ERROR;
        }
        
        MarketAPI market = target.getMarket();
        if (market == null) 
        {
            Console.showMessage("Error: entity '" + target.getName() + "' does not have a market");
            return CommandResult.ERROR;
        }
        
        String commodityID = tmp[1];
		switch (commodityID) {
			case Commodities.FUEL:
				// do stuff
				float organicsMet = ExerelinUtilsMarket.getCommodityDemandFractionMet(market, Commodities.ORGANICS, true);
				float volatilesMet = ExerelinUtilsMarket.getCommodityDemandFractionMet(market, Commodities.VOLATILES, true);
				float rareMetalsMet = ExerelinUtilsMarket.getCommodityDemandFractionMet(market, Commodities.RARE_METALS, true);
				float machineryMet = ExerelinUtilsMarket.getCommodityDemandFractionMet(market, Commodities.HEAVY_MACHINERY, true);
				float crewMet = ExerelinUtilsMarket.getCommodityDemandFractionMet(market, Commodities.CREW, true);
				
				float fuelSupply = ExerelinUtilsMarket.getCommoditySupply(market, Commodities.FUEL);
				float utilization = fuelSupply / ConditionData.FUEL_PRODUCTION_FUEL;
				
				String msg1 = "Demand met: " + organicsMet + " / " + volatilesMet + " / " + rareMetalsMet + " / " + machineryMet + " / " + crewMet;
				Console.showMessage(msg1);
				String msg2 = "Production: " + (int)fuelSupply + " (capacity utilization " + (int)(utilization * 100) +"%)";
				Console.showMessage(msg2);
				break;
			case Commodities.HEAVY_MACHINERY:
				// TODO
				break;
			default:
				Console.showMessage("Sorry, not supported yet");
				return CommandResult.ERROR;
		}
        
        return CommandResult.SUCCESS;
    }
}

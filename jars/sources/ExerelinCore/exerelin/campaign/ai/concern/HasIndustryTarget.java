package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.campaign.econ.Industry;

/**
 * Any concern targetable for a {@code SabotageIndustryAction} should implement this interface.
 */
public interface HasIndustryTarget {

    Industry getTargetIndustry();

}
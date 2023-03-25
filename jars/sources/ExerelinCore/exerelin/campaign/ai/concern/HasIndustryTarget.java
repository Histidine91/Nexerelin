package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.campaign.econ.Industry;

/**
 * Concerns should implement this interface if they want to pass a specific industry to a relevant action,
 * such as {@code BuildIndustryAction} or {@code SabotageIndustryAction}
 * A concern should return a non-null value for either or both of the interface's methods; an action should be able to use both methods.
 */
public interface HasIndustryTarget {

    Industry getTargetIndustry();
    String getTargetIndustryId();
}

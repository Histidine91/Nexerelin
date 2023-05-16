package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.campaign.econ.Industry;

import java.util.List;

/**
 * Concerns should implement this interface if they want to pass a specific existing industry to a relevant action,
 * such as {@code RaidAction} or {@code SabotageIndustryAction}.
 * A concern should return a non-null value for either or both of the interface's methods; an action should be able to use both methods.
 */
public interface HasIndustryTarget {

    List<Industry> getTargetIndustries();
    List<String> getTargetIndustryIds();
}

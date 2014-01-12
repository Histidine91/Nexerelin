package exerelin.commandQueue;

import com.fs.starfarer.api.EveryFrameScript;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

// Credit: LazyWizard

// Keep a reference to this object in your mod's
// master script, otherwise it's worthless
@SuppressWarnings("unchecked")
public class CommandQueue implements EveryFrameScript
{
    private final Queue queuedCommands = new ConcurrentLinkedQueue();

    @Override
    public boolean isDone()
    {
        return false;
    }

    @Override
    public boolean runWhilePaused()
    {
        return false;
    }

    @Override
    public void advance(float amount)
    {
        synchronized (queuedCommands)
        {
            BaseCommand tmp;
            while (!queuedCommands.isEmpty())
            {
                tmp = (BaseCommand) queuedCommands.remove();
                tmp.executeCommand();
            }
        }
    }

    public synchronized void addCommandToQueue(BaseCommand command)
    {
        queuedCommands.add(command);
    }

    public synchronized boolean isQueueEmpty()
    {
        return queuedCommands.isEmpty();
    }

    public void executeAllCommands()
    {
        synchronized (queuedCommands)
        {
            BaseCommand tmp;
            while (!queuedCommands.isEmpty())
            {
                tmp = (BaseCommand) queuedCommands.remove();
                tmp.executeCommand();
            }
        }
    }
}
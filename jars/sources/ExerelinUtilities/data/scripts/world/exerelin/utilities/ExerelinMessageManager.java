package data.scripts.world.exerelin.utilities;

import java.util.*;

public class ExerelinMessageManager
{
    public List<ExerelinMessage> messages;

    public ExerelinMessageManager()
    {
        messages = new ArrayList<ExerelinMessage>();
    }

    public void addMessage(ExerelinMessage message)
    {
        this.messages.add(message);

        if(this.messages.size() > 20)
            this.messages.remove(0);
    }

    public List<ExerelinMessage> getMessages()
    {
        return this.messages;
    }
}

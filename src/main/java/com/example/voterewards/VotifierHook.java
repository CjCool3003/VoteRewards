package com.example.voterewards;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Listens for VotifierEvent using reflection, so the plugin compiles without
 * needing the Votifier API jar. VotifierPlus and NuVotifier both use the
 * original com.vexsoftware.votifier event class, so this works with either.
 */
final class VotifierHook {

    private static final String EVENT_CLASS = "com.vexsoftware.votifier.model.VotifierEvent";

    private VotifierHook() {
    }

    static boolean register(VoteRewardsPlugin plugin, VoteHandler handler) {
        final Class<? extends Event> eventClass;
        final Method getVote;
        try {
            eventClass = Class.forName(EVENT_CLASS, true, plugin.getClass().getClassLoader()).asSubclass(Event.class);
            getVote = eventClass.getMethod("getVote");
        } catch (ClassNotFoundException | ClassCastException | NoSuchMethodException | LinkageError e) {
            return false;
        }

        Listener listener = new Listener() { };
        plugin.getServer().getPluginManager().registerEvent(eventClass, listener, EventPriority.NORMAL, (l, event) -> {
            if (!eventClass.isInstance(event)) {
                return;
            }
            try {
                Object vote = getVote.invoke(event);
                if (vote == null) {
                    return;
                }
                String username = call(vote, "getUsername");
                String service = call(vote, "getServiceName");
                String address = call(vote, "getAddress");
                handler.handleVote(username, service, address);
            } catch (ReflectiveOperationException e) {
                plugin.getLogger().log(Level.WARNING, "Could not read vote data from Votifier", e);
            }
        }, plugin, false);
        return true;
    }

    private static String call(Object target, String methodName) {
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value == null ? "" : value.toString();
        } catch (ReflectiveOperationException e) {
            return "";
        }
    }
}

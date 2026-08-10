package paxoslocker.testkit;

import paxoslocker.diagnostics.*;
import paxoslocker.model.*;
import paxoslocker.worker.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit registry for ephemeral workers; no reflection or protocol-state mutation. */
public final class WorkerRegistry {
    private record ScoutKey(NodeId leader, BallotNumber ballot) { }
    private final Map<ScoutKey, Scout> scouts = new ConcurrentHashMap<>();
    private final Map<CommanderKeyWithLeader, Commander> commanders = new ConcurrentHashMap<>();
    private final Set<Object> terminal = ConcurrentHashMap.newKeySet();

    public record CommanderKeyWithLeader(NodeId leader, BallotNumber ballot, long slot) { }

    public void register(Scout scout) {
        ScoutKey key = new ScoutKey(scout.leaderId(), scout.ballot());
        Scout previous = scouts.putIfAbsent(key, scout);
        if (previous != null && previous != scout) throw new IllegalStateException("Scout already registered: " + key);
        terminal.remove(key);
    }
    public void register(Commander commander) {
        PValue pv = commander.pvalue();
        CommanderKeyWithLeader key = new CommanderKeyWithLeader(commander.leaderId(), pv.ballot(), pv.slot());
        Commander previous = commanders.putIfAbsent(key, commander);
        if (previous != null && previous != commander) throw new IllegalStateException("Commander already registered: " + key);
        terminal.remove(key);
    }
    public void unregisterScout(NodeId leader, BallotNumber ballot) { ScoutKey key=new ScoutKey(leader,ballot);scouts.remove(key);terminal.add(key); }
    public void unregisterCommander(NodeId leader, BallotNumber ballot, long slot) { CommanderKeyWithLeader key=new CommanderKeyWithLeader(leader,ballot,slot);commanders.remove(key);terminal.add(key); }
    public void markScoutTerminal(NodeId leader, BallotNumber ballot) { unregisterScout(leader,ballot); }
    public void markCommanderTerminal(NodeId leader, BallotNumber ballot, long slot) { unregisterCommander(leader,ballot,slot); }
    public boolean killScout(NodeId leader, BallotNumber ballot) {
        ScoutKey key = new ScoutKey(leader, ballot); Scout scout = scouts.get(key);
        if (scout == null) return terminal.contains(key);
        scout.kill(); terminal.add(key); return true;
    }
    public boolean killCommander(NodeId leader, BallotNumber ballot, long slot) {
        CommanderKeyWithLeader key = new CommanderKeyWithLeader(leader, ballot, slot);
        Commander commander = commanders.get(key);
        if (commander == null) return terminal.contains(key);
        commander.kill(); terminal.add(key); return true;
    }
    public Optional<Scout> scout(NodeId leader, BallotNumber ballot) { return Optional.ofNullable(scouts.get(new ScoutKey(leader, ballot))); }
    public Optional<Commander> commander(NodeId leader, BallotNumber ballot, long slot) { return Optional.ofNullable(commanders.get(new CommanderKeyWithLeader(leader, ballot, slot))); }
    public Set<String> runningWorkers() {
        Set<String> result = new TreeSet<>();
        scouts.forEach((k,v) -> { if (!v.isKilled() && !terminal.contains(k)) result.add("Scout" + k); });
        commanders.forEach((k,v) -> { if (!v.isKilled() && !terminal.contains(k)) result.add("Commander" + k); });
        return Set.copyOf(result);
    }
}

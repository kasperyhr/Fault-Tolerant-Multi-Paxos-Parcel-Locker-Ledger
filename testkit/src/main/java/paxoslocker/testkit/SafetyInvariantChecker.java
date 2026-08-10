package paxoslocker.testkit;

import paxoslocker.diagnostics.*; import paxoslocker.model.*;
import java.util.*; import java.util.concurrent.ConcurrentHashMap;

/** Independent online checker. It observes facts but never supplies protocol decisions. */
public final class SafetyInvariantChecker {
    private final Map<Long,Command> chosen=new ConcurrentHashMap<>(); private final Map<NodeId,Map<Long,Command>> learned=new ConcurrentHashMap<>();
    private final Map<NodeId,BallotNumber> acceptorBallots=new ConcurrentHashMap<>(); private final Map<NodeId,Long> executedThrough=new ConcurrentHashMap<>();
    private final Set<UUID> effectedRequests=ConcurrentHashMap.newKeySet();
    public void observeChosen(long slot,Command command){consistent(chosen,slot,command,"two chosen values");}
    public void observeReplicaDecision(NodeId replica,long slot,Command command){consistent(learned.computeIfAbsent(replica,k->new ConcurrentHashMap<>()),slot,command,"replica learned conflicting decisions");consistent(chosen,slot,command,"replica disagrees with chosen value");}
    public void observeExecuted(NodeId replica,long slot,Command command){long expected=executedThrough.getOrDefault(replica,0L)+1;if(slot!=expected)fail("executed log is not contiguous at "+replica+": expected "+expected+" got "+slot);executedThrough.put(replica,slot);if(!effectedRequests.add(command.requestId()))fail("duplicate business effect for requestId "+command.requestId());}
    public void observeAcceptorBallot(NodeId acceptor,BallotNumber ballot){acceptorBallots.compute(acceptor,(id,old)->{if(old!=null&&ballot.compareTo(old)<0)fail("acceptor ballot regressed: "+old+" -> "+ballot);return ballot;});}
    public void verifyReplicaAgreement(Collection<ReplicaStatus> statuses){Map<Long,Command> reference=new HashMap<>();for(ReplicaStatus status:statuses)status.decisions().forEach((slot,command)->consistent(reference,slot,command,"replica decision disagreement"));}
    public Map<Long,Command> chosenSnapshot(){return Map.copyOf(chosen);}
    private static void consistent(Map<Long,Command> map,long slot,Command command,String reason){Command old=map.putIfAbsent(slot,command);if(old!=null&&!old.equals(command))fail(reason+" at slot "+slot+": "+old+" vs "+command);}
    private static void fail(String message){throw new SafetyViolationException(message);}
}

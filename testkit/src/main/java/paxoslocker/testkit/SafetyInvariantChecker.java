package paxoslocker.testkit;

import paxoslocker.diagnostics.*; import paxoslocker.model.*;
import java.util.*; import java.util.concurrent.ConcurrentHashMap;

/** Independent online checker. It observes facts but never supplies protocol decisions. */
public final class SafetyInvariantChecker {
    private final Map<Long,Command> chosen=new ConcurrentHashMap<>(); private final Map<Long,BallotNumber> chosenBallots=new ConcurrentHashMap<>(); private final Map<NodeId,Map<Long,Command>> learned=new ConcurrentHashMap<>();
    private final Map<NodeId,BallotNumber> acceptorBallots=new ConcurrentHashMap<>(); private final Map<NodeId,Long> executedThrough=new ConcurrentHashMap<>();
    private final Map<NodeId,Set<UUID>> effectedRequestsPerReplica=new ConcurrentHashMap<>();
    private final Map<BallotSlot,Command> acceptedByBallotSlot=new ConcurrentHashMap<>();
    private final Map<BallotSlot,Command> commandersByBallotSlot=new ConcurrentHashMap<>();
    public void observeChosen(long slot,Command command){observeChosen(null,slot,command);}
    public void observeChosen(BallotNumber ballot,long slot,Command command){
        consistent(chosen,slot,command,"two chosen values");
        if(ballot==null)return;
        BallotNumber old=chosenBallots.putIfAbsent(slot,ballot);
        if(old!=null&&old.compareTo(ballot)!=0&&!chosen.get(slot).equals(command))fail("two chosen values at slot "+slot);
        acceptedByBallotSlot.forEach((key,value)->{if(key.slot()==slot&&key.ballot().compareTo(ballot)>0&&!value.equals(command))fail("A5 observable violation at slot "+slot);});
        commandersByBallotSlot.forEach((key,value)->{if(key.slot()==slot&&key.ballot().compareTo(ballot)>0&&!value.equals(command))fail("C2 observable violation at slot "+slot);});
    }
    public void observeReplicaDecision(NodeId replica,long slot,Command command){consistent(learned.computeIfAbsent(replica,k->new ConcurrentHashMap<>()),slot,command,"replica learned conflicting decisions");consistent(chosen,slot,command,"replica disagrees with chosen value");}
    public void observeExecuted(NodeId replica,long slot,Command command){long expected=executedThrough.getOrDefault(replica,0L)+1;if(slot!=expected)fail("executed log is not contiguous at "+replica+": expected "+expected+" got "+slot);executedThrough.put(replica,slot);Set<UUID> requests=effectedRequestsPerReplica.computeIfAbsent(replica,k->ConcurrentHashMap.newKeySet());if(!requests.add(command.requestId()))fail("duplicate business effect at "+replica+" for requestId "+command.requestId());}
    public void observeAcceptorBallot(NodeId acceptor,BallotNumber ballot){acceptorBallots.compute(acceptor,(id,old)->{if(old!=null&&ballot.compareTo(old)<0)fail("acceptor ballot regressed: "+old+" -> "+ballot);return ballot;});}
    public void observeAccepted(NodeId acceptor,BallotNumber ballot,long slot,Command command){
        observeAcceptorBallot(acceptor,ballot); BallotSlot key=new BallotSlot(ballot,slot);
        consistentKey(acceptedByBallotSlot,key,command,"A4 violation");
        Command alreadyChosen=chosen.get(slot); BallotNumber chosenBallot=chosenBallots.get(slot);
        if(alreadyChosen!=null&&chosenBallot!=null&&ballot.compareTo(chosenBallot)>0&&!alreadyChosen.equals(command)) fail("A5 observable violation at slot "+slot);
    }
    public void observeCommander(BallotNumber ballot,long slot,Command command){
        BallotSlot key=new BallotSlot(ballot,slot); consistentKey(commandersByBallotSlot,key,command,"C1 violation");
        Command alreadyChosen=chosen.get(slot); BallotNumber chosenBallot=chosenBallots.get(slot);
        if(alreadyChosen!=null&&chosenBallot!=null&&ballot.compareTo(chosenBallot)>0&&!alreadyChosen.equals(command)) fail("C2 observable violation at slot "+slot);
    }
    public void verifyReplicaAgreement(Collection<ReplicaStatus> statuses){Map<Long,Command> reference=new HashMap<>();for(ReplicaStatus status:statuses)status.decisions().forEach((slot,command)->consistent(reference,slot,command,"replica decision disagreement"));}
    public Map<Long,Command> chosenSnapshot(){return Map.copyOf(chosen);}
    private static void consistent(Map<Long,Command> map,long slot,Command command,String reason){Command old=map.putIfAbsent(slot,command);if(old!=null&&!old.equals(command))fail(reason+" at slot "+slot+": "+old+" vs "+command);}
    private static void consistentKey(Map<BallotSlot,Command> map,BallotSlot key,Command command,String reason){Command old=map.putIfAbsent(key,command);if(old!=null&&!old.equals(command))fail(reason+" at "+key+": "+old+" vs "+command);}
    private static void fail(String message){throw new SafetyViolationException(message);}
    private record BallotSlot(BallotNumber ballot,long slot){}
}

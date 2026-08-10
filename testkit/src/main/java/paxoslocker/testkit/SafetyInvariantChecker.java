package paxoslocker.testkit;

import paxoslocker.model.*;
import paxoslocker.diagnostics.ReplicaStatus;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Independent online checker. Learned values are observations, never proof of quorum choice. */
public final class SafetyInvariantChecker {
    private record BallotSlot(BallotNumber ballot,long slot) { }
    private record AcceptedValue(BallotNumber ballot,long slot,Command command) { }
    private record ChosenEvidence(BallotNumber ballot,Command command) { }

    private final int quorum;
    private final boolean strictChosenDiagnostics;
    private final Map<Long,ChosenEvidence> chosen=new ConcurrentHashMap<>();
    private final Map<NodeId,Map<Long,Command>> learned=new ConcurrentHashMap<>();
    private final Map<Long,Command> learnedAgreement=new ConcurrentHashMap<>();
    private final Map<NodeId,BallotNumber> acceptorBallots=new ConcurrentHashMap<>();
    private final Map<NodeId,Long> executedThrough=new ConcurrentHashMap<>();
    private final Map<NodeId,Set<UUID>> effectedRequestsPerReplica=new ConcurrentHashMap<>();
    private final Map<BallotSlot,Command> acceptedByBallotSlot=new ConcurrentHashMap<>();
    private final Map<AcceptedValue,Set<NodeId>> acceptorsByValue=new ConcurrentHashMap<>();
    private final Map<BallotSlot,Command> commandersByBallotSlot=new ConcurrentHashMap<>();

    /** Non-strict observer for focused unit tests where a complete diagnostic stream is unavailable. */
    public SafetyInvariantChecker(){this(0,false);}
    public SafetyInvariantChecker(int quorum,boolean strictChosenDiagnostics){
        if(quorum<0)throw new IllegalArgumentException("quorum cannot be negative");
        if(strictChosenDiagnostics&&quorum<1)throw new IllegalArgumentException("strict mode requires quorum");
        this.quorum=quorum;this.strictChosenDiagnostics=strictChosenDiagnostics;
    }

    public void observeChosen(long slot,Command command){observeChosen(null,slot,command);}
    public void observeChosen(BallotNumber ballot,long slot,Command command){
        Objects.requireNonNull(command,"command");
        if(strictChosenDiagnostics){
            if(ballot==null||acceptorsByValue.getOrDefault(new AcceptedValue(ballot,slot,command),Set.of()).size()<quorum)
                fail(SafetyViolationKind.VALUE_CHOSEN_WITHOUT_QUORUM,"VALUE_CHOSEN emitted without observed acceptor quorum at slot "+slot);
        }
        establishChosen(ballot,slot,command);
    }

    public void observeReplicaDecision(NodeId replica,long slot,Command command){
        consistent(learned.computeIfAbsent(replica,k->new ConcurrentHashMap<>()),slot,command,
                SafetyViolationKind.REPLICA_DECISION_CONFLICT,"replica learned conflicting decisions");
        consistent(learnedAgreement,slot,command,SafetyViolationKind.REPLICA_DECISION_CONFLICT,"replicas learned different decisions");
        ChosenEvidence evidence=chosen.get(slot);
        if(evidence!=null&&!evidence.command().equals(command))
            fail(SafetyViolationKind.REPLICA_DISAGREES_WITH_CHOSEN,"replica disagrees with quorum-chosen value at slot "+slot);
    }

    public void observeExecuted(NodeId replica,long slot,Command command){
        long expected=executedThrough.getOrDefault(replica,0L)+1;
        if(slot!=expected)fail(SafetyViolationKind.NON_CONTIGUOUS_EXECUTION,"at "+replica+": expected "+expected+" got "+slot);
        executedThrough.put(replica,slot);
        Set<UUID> requests=effectedRequestsPerReplica.computeIfAbsent(replica,k->ConcurrentHashMap.newKeySet());
        if(!requests.add(command.requestId()))fail(SafetyViolationKind.DUPLICATE_EXECUTION,"at "+replica+" for requestId "+command.requestId());
    }

    public void observeAcceptorBallot(NodeId acceptor,BallotNumber ballot){
        acceptorBallots.compute(acceptor,(id,old)->{
            if(old!=null&&ballot.compareTo(old)<0)fail(SafetyViolationKind.BALLOT_REGRESSION,old+" -> "+ballot+" at "+acceptor);
            return ballot;
        });
    }

    public void observeAccepted(NodeId acceptor,BallotNumber ballot,long slot,Command command){
        observeAcceptorBallot(acceptor,ballot);
        BallotSlot key=new BallotSlot(ballot,slot);
        consistentKey(acceptedByBallotSlot,key,command,SafetyViolationKind.A4,"same ballot and slot accepted different commands");
        ChosenEvidence prior=chosen.get(slot);
        if(prior!=null&&prior.ballot()!=null&&ballot.compareTo(prior.ballot())>0&&!prior.command().equals(command))
            fail(SafetyViolationKind.A5,"higher ballot accepted a value different from quorum-chosen value at slot "+slot);
        Set<NodeId> voters=acceptorsByValue.computeIfAbsent(new AcceptedValue(ballot,slot,command),ignored->ConcurrentHashMap.newKeySet());
        voters.add(acceptor);
        if(quorum>0&&voters.size()>=quorum)establishChosen(ballot,slot,command);
    }

    public void observeCommander(BallotNumber ballot,long slot,Command command){
        BallotSlot key=new BallotSlot(ballot,slot);
        consistentKey(commandersByBallotSlot,key,command,SafetyViolationKind.C1,"same ballot and slot created different Commanders");
        ChosenEvidence prior=chosen.get(slot);
        if(prior!=null&&prior.ballot()!=null&&ballot.compareTo(prior.ballot())>0&&!prior.command().equals(command))
            fail(SafetyViolationKind.C2,"higher-ballot Commander differs from quorum-chosen value at slot "+slot);
    }

    public boolean isQuorumChosen(long slot,Command command){ChosenEvidence e=chosen.get(slot);return e!=null&&e.command().equals(command);}
    public void verifyReplicaAgreement(Collection<ReplicaStatus> statuses){Map<Long,Command> reference=new HashMap<>();for(ReplicaStatus status:statuses)status.decisions().forEach((slot,command)->consistent(reference,slot,command,SafetyViolationKind.REPLICA_DECISION_CONFLICT,"replica decision disagreement"));}
    public Map<Long,Command> chosenSnapshot(){Map<Long,Command> out=new HashMap<>();chosen.forEach((slot,e)->out.put(slot,e.command()));return Map.copyOf(out);}
    public Map<NodeId,Map<Long,Command>> learnedSnapshot(){Map<NodeId,Map<Long,Command>> out=new HashMap<>();learned.forEach((node,values)->out.put(node,Map.copyOf(values)));return Map.copyOf(out);}

    private void establishChosen(BallotNumber ballot,long slot,Command command){
        ChosenEvidence candidate=new ChosenEvidence(ballot,command),old=chosen.putIfAbsent(slot,candidate);
        if(old!=null&&!old.command().equals(command))fail(SafetyViolationKind.CHOSEN_CONFLICT,"slot "+slot+": "+old.command()+" vs "+command);
        Command learnedValue=learnedAgreement.get(slot);
        if(learnedValue!=null&&!learnedValue.equals(command))fail(SafetyViolationKind.REPLICA_DISAGREES_WITH_CHOSEN,"learned value differs from quorum evidence at slot "+slot);
        acceptedByBallotSlot.forEach((key,value)->{if(ballot!=null&&key.slot()==slot&&key.ballot().compareTo(ballot)>0&&!value.equals(command))fail(SafetyViolationKind.A5,"higher accepted value conflicts at slot "+slot);});
        commandersByBallotSlot.forEach((key,value)->{if(ballot!=null&&key.slot()==slot&&key.ballot().compareTo(ballot)>0&&!value.equals(command))fail(SafetyViolationKind.C2,"higher Commander conflicts at slot "+slot);});
    }

    private static void consistent(Map<Long,Command> map,long slot,Command command,SafetyViolationKind kind,String reason){Command old=map.putIfAbsent(slot,command);if(old!=null&&!old.equals(command))fail(kind,reason+" at slot "+slot);}
    private static void consistentKey(Map<BallotSlot,Command> map,BallotSlot key,Command command,SafetyViolationKind kind,String reason){Command old=map.putIfAbsent(key,command);if(old!=null&&!old.equals(command))fail(kind,reason+" at "+key);}
    private static void fail(SafetyViolationKind kind,String message){throw new SafetyViolationException(kind,message);}
}

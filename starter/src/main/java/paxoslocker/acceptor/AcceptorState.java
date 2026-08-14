package paxoslocker.acceptor;

import paxoslocker.diagnostics.AcceptorStatus;
import paxoslocker.model.BallotNumber;
import paxoslocker.model.PValue;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

final class AcceptorState implements Serializable {
    private BallotNumber ballot;
    private Set<PValue> accepted;

    AcceptorState() {
        ballot = BallotNumber.BOTTOM;
        accepted = new HashSet<>();
    }

    BallotNumber ballot() {
        return this.ballot;
    }

    void setBallot(BallotNumber ballot) {
        this.ballot = ballot;
    }

    Set<PValue> accepted() {
        return accepted;
    }

    void addAccepted(PValue pValue) {
        accepted.add(pValue);
    }

    Optional<PValue> findAccepted(BallotNumber ballot, long slot) {
        return accepted.stream().filter(pValue -> pValue.ballot().equals(ballot)).filter(pValue -> pValue.slot() == slot).findAny();
    }

    AcceptorStatus toAcceptorStatus() {
        return new AcceptorStatus(ballot, accepted);
    }
}

package paxoslocker.testkit;
import java.time.*; import java.util.function.BooleanSupplier;
public final class Await { private Await(){} public static void until(BooleanSupplier condition,Duration timeout,String description){Instant end=Instant.now().plus(timeout);while(Instant.now().isBefore(end)){if(condition.getAsBoolean())return;Thread.onSpinWait();try{Thread.sleep(5);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError("interrupted awaiting "+description,e);}}throw new AssertionError("timed out after "+timeout+" awaiting "+description);} }

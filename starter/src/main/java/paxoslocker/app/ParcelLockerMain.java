package paxoslocker.app;

import java.util.*;

public final class ParcelLockerMain {
    private ParcelLockerMain() {
    }

    public static void main(String[] args) {
        Map<String, String> options = parse(args);
        if (options.containsKey("help")) {
            usage();
            return;
        }
        int f = Integer.parseInt(options.getOrDefault("fault-tolerance", "1"));
        int n = Integer.parseInt(options.getOrDefault("acceptors", Integer.toString(2 * f + 1)));
        ClusterOptions config = new ClusterOptions(f, n);
        System.out.printf("paxos-parcel-locker template: f=%d acceptors=%d quorum=%d%n", f, n, config.quorum());
        System.out.println("Protocol roles contain TODO(student) placeholders; see ASSIGNMENT.md.");
    }

    static Map<String, String> parse(String[] args) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.equals("--help")) {
                out.put("help", "true");
                continue;
            }
            if (!a.startsWith("--") || i + 1 >= args.length)
                throw new IllegalArgumentException("expected --name value");
            out.put(a.substring(2), args[++i]);
        }
        return out;
    }

    private static void usage() {
        System.out.println("Usage: --fault-tolerance <f> [--acceptors <n>]");
    }
}

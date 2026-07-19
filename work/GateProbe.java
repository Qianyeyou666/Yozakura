import gq.yozakura.auth.YozakuraAuthGate;

public final class GateProbe {
    public static void main(String[] args) {
        System.out.println("allow=" + YozakuraAuthGate.allowRuntime("probe"));
        System.out.println("username=" + YozakuraAuthGate.getVerifiedUsername());
    }
}

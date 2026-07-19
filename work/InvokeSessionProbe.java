import gq.yozakura.auth.NativeAuthBridge;

public final class InvokeSessionProbe {
    public static void main(String[] args) {
        System.out.println(NativeAuthBridge.isVerifiedSession());
    }
}

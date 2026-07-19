import gq.yozakura.auth.vendor.tech.skidonion.obfuscator.inline.Wrapper;

public final class WrapperProbe {
    public static void main(String[] args) {
        String build = Wrapper.getClientBuildId();
        String client = Wrapper.getClientFingerprintForNative();
        String machine = Wrapper.getMachineFingerprintForNative();
        System.out.println("build=" + build);
        System.out.println("clientLength=" + (client == null ? -1 : client.length()));
        System.out.println("machineLength=" + (machine == null ? -1 : machine.length()));
    }
}

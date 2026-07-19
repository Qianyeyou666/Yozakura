import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class InvokeGateProbe {
    public static void main(String[] args) throws Exception {
        Class<?> gate = Class.forName("gq.yozakura.auth.YozakuraAuthGate");
        Method method = gate.getDeclaredMethod("showVerification", String.class, long.class);
        method.setAccessible(true);
        try {
            method.invoke(null, "standalone", 0L);
            System.out.println("showVerification returned");
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            System.out.println("showVerification threw " + cause.getClass().getName());
            cause.printStackTrace(System.out);
        }
    }
}

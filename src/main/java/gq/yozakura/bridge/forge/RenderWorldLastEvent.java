package gq.yozakura.bridge.forge;

public class RenderWorldLastEvent extends Event {
    public final float partialTicks;

    public RenderWorldLastEvent(float partialTicks) {
        this.partialTicks = partialTicks;
    }
}

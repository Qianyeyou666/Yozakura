package gq.vapulite.bridge.forge;

public class RenderGameOverlayEvent extends Event {
    public final float partialTicks;

    public RenderGameOverlayEvent(float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public static class Text extends RenderGameOverlayEvent {
        public Text(float partialTicks) {
            super(partialTicks);
        }
    }
}

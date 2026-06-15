package gq.yozakura.bridge.forge;

public class MouseEvent extends Event {
    public final int button;
    public final boolean buttonstate;
    public final int dwheel;

    public MouseEvent(int button, boolean buttonstate, int dwheel) {
        this.button = button;
        this.buttonstate = buttonstate;
        this.dwheel = dwheel;
    }
}
